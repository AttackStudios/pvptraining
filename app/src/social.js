const fs = require('fs');
const os = require('os');
const path = require('path');

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

  /** Sends the trainee's progress (without the session history) so friends and leaderboards can see it. */
  async uploadStats() {
    if (!this.session?.token) return { ok: false };
    if (process.argv.includes('--shots')) return { ok: true, unchanged: true }; // screenshot runs use seeded sample data
    let progress;
    try {
      progress = JSON.parse(fs.readFileSync(path.join(os.homedir(), '.pvptraining', 'progress.json'), 'utf8'));
    } catch {
      return { ok: false };
    }
    const stats = { modes: progress.modes || {} };
    const encoded = JSON.stringify(stats);
    if (encoded === this.lastUploaded) return { ok: true, unchanged: true };
    const res = await this.request('POST', '/stats', { body: { stats } });
    if (res.ok) this.lastUploaded = encoded;
    return res;
  }
}

module.exports = { Social };
