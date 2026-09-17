const fs = require('fs');
const os = require('os');
const path = require('path');
const { mergeModes, recompute, forUpload, total } = require('./progress-merge');

/**
 * Client for the PVPTraining friends service (friends, messages, stat comparison, leaderboards,
 * challenges). Lives in the main process so the sign-in token never touches the renderer.
 *
 * Signing in needs the game: the service hands out a nonce, the mod tells Mojang "this account
 * is joining <nonce>", and the service confirms that with Mojang. No password, and nobody can
 * sign in as a name they do not own.
 */
const DEFAULT_URL = 'https://claudebox-app.onrender.com/pvpt';
const ROUTES = new Set([
  'GET /me', 'GET /friends/stats', 'GET /messages', 'GET /leaderboard',
  'POST /friends/request', 'POST /friends/accept', 'POST /friends/decline', 'POST /friends/remove',
  'POST /messages', 'POST /challenges',
]);

class Social {
  constructor({ getSettings, saveSettings, getBridge }) {
    this.getSettings = getSettings;
    this.saveSettings = saveSettings;
    this.getBridge = getBridge;
    this.waiters = new Map(); // nonce -> resolve
    this.lastUploaded = '';
  }

  get base() {
    return (process.env.PVPT_SOCIAL_URL || this.getSettings().socialUrl || DEFAULT_URL).replace(/\/+$/, '');
  }

  get session() {
    return this.getSettings().social || null;
  }

  state() {
    const s = this.session;
    return { signedIn: Boolean(s?.token), me: s?.me || null };
  }

  async request(method, route, { body, query, auth = true } = {}) {
    const url = new URL(this.base + route);
    for (const [k, v] of Object.entries(query || {})) if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v);
    const headers = { 'Content-Type': 'application/json', 'User-Agent': 'PVPTraining' };
    if (auth && this.session?.token) headers.Authorization = `Bearer ${this.session.token}`;
    let res;
    try {
      // The free host sleeps when idle and takes up to ~40 s to wake on the first request.
      res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(50000) });
    } catch {
      return { ok: false, error: 'Could not reach the friends service. Check your internet connection and try again.' };
    }
    let data = null;
    try {
      data = await res.json();
    } catch {
      /* not JSON: a proxy error page */
    }
    if (res.status === 401 && auth) this.saveSettings({ social: null });
    if (!res.ok) return { ok: false, status: res.status, error: data?.error || `The friends service answered ${res.status}.` };
    return { ok: true, data };
  }

  /** Whitelisted pass-through for the renderer. */
  api(method, route, payload = {}) {
    if (!ROUTES.has(`${method} ${route}`)) return { ok: false, error: 'Unknown request.' };
    if (!this.session?.token) return { ok: false, error: 'Sign in first.', status: 401 };
    return this.request(method, route, method === 'GET' ? { query: payload } : { body: payload });
  }

  /** The mod answered a socialAuth request. */
  onBridgeMessage(msg) {
    if (msg.t !== 'socialAuthed') return;
    const done = this.waiters.get(msg.nonce);
    if (done) {
      this.waiters.delete(msg.nonce);
      done(msg);
    }
  }

  async signIn(name) {
    const bridge = this.getBridge();
    if (!bridge) return { ok: false, error: 'Connect to Minecraft first. Signing in uses your game to prove the account is yours.' };
    const begin = await this.request('POST', '/auth/begin', { body: { name }, auth: false });
    if (!begin.ok) return begin;
    const nonce = begin.data.nonce;
    const answered = new Promise((resolve) => {
      this.waiters.set(nonce, resolve);
      setTimeout(() => {
        if (this.waiters.delete(nonce)) resolve({ ok: false, error: 'The game did not answer. Update the PVPTraining mod (Setup, install guide) and try again.' });
      }, 25000);
    });
    if (!bridge.send({ t: 'socialAuth', nonce })) return { ok: false, error: 'Connect to Minecraft first.' };
    const proof = await answered;
    if (!proof.ok) return { ok: false, error: proof.error };
    const fin = await this.request('POST', '/auth/finish', { body: { nonce }, auth: false });
    if (!fin.ok) return fin;
    this.saveSettings({ social: { token: fin.data.token, me: fin.data.me } });
    this.lastUploaded = '';
    await this.uploadStats();
    return { ok: true, me: fin.data.me };
  }

  signOut() {
    this.saveSettings({ social: null });
    return { ok: true };
  }

  /**
   * Two-way progress sync with the account. This device's progress and the account's copy are
   * MERGED (best of each drill, medal, duel record and unlock), so nothing is ever overwritten:
   * a new laptop inherits the PC's progress instead of erasing it, and vice versa.
   *   1. send local progress; the server merges it into the account and returns the result
   *   2. merge that back into the local file and recompute mastery from the combined medals
   *   3. if the recomputed totals differ from what the server has, send them once more
   *   4. tell the game (if connected) and the UI that progress changed
   */
  async sync() {
    if (!this.session?.token) return { ok: false };
    if (process.argv.includes('--shots')) return { ok: true, unchanged: true }; // screenshot runs use seeded sample data
    if (this.syncing) return this.syncing;
    this.syncing = this.runSync().finally(() => {
      this.syncing = null;
    });
    return this.syncing;
  }

  async runSync() {
    const file = path.join(process.env.PVPT_HOME || path.join(os.homedir(), '.pvptraining'), 'progress.json');
    const catalog = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'renderer', 'catalog.json'), 'utf8'));
    let progress = { schema: 1, modes: {}, history: [] };
    try {
      progress = { ...progress, ...JSON.parse(fs.readFileSync(file, 'utf8')) };
    } catch {
      /* a brand-new device: nothing local yet, everything comes from the account */
    }
    const before = JSON.stringify(progress.modes || {});
    const first = await this.request('POST', '/stats', { body: { stats: { modes: forUpload(progress.modes, catalog) } } });
    if (!first.ok) return first;

    const cloud = first.data.stats?.modes || {};
    for (const mode of Object.values(cloud)) for (const d of Object.values(mode.drills || {})) delete d.lower;
    const merged = recompute(mergeModes(progress.modes, cloud, catalog), catalog);
    const changedLocal = JSON.stringify(merged) !== before;
    if (changedLocal) {
      progress.modes = merged;
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(`${file}.tmp`, JSON.stringify(progress, null, 2));
      fs.renameSync(`${file}.tmp`, file);
    }
    // Recomputing can lift mastery above either side (one device had the medals, the other the duel wins).
    if (Math.abs(total(merged) - (first.data.totalMastery || 0)) > 0.05) {
      await this.request('POST', '/stats', { body: { stats: { modes: forUpload(merged, catalog) } } });
    }
    if (changedLocal) this.onSynced?.(progress);
    return { ok: true, changedLocal, totalMastery: total(merged) };
  }

  /** Kept for callers that only care that the account is up to date. */
  uploadStats() {
    return this.sync();
  }
}

module.exports = { Social };
