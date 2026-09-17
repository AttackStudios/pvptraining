const { app } = require('electron');
const { spawn, execFile } = require('child_process');
const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');

/**
 * Auto update. On every launch the app asks GitHub for the latest release and pulls:
 *   - the mod jar, so the install guide always hands out the newest mod and any folder
 *     the app installed into is refreshed;
 *   - a newer build of the app itself, applied on restart (or quietly when you quit).
 *
 * The builds are not code-signed, which rules out Squirrel on macOS, so the app swaps
 * its own bundle instead. Every download is checked against the SHA-256 digest GitHub
 * publishes for the asset, and only this one repository is ever consulted.
 */
const REPO = 'AttackStudios/pvptraining';
// Mac builds ship per chip rather than as one universal file: half the download for the
// player, and release uploads small enough that GitHub reliably accepts them.
const ASSETS = {
  jar: 'pvptraining-mc1.21.11.jar',
  mac: `PVPTraining-mac-${process.arch === 'arm64' ? 'arm64' : 'x64'}.zip`,
  win: 'PVPTraining-win-x64.exe',
};

let emit = () => {};
let staged = null; // { version, file } ready to apply
let applying = false;

function dir() {
  const d = path.join(app.getPath('userData'), 'updates');
  fs.mkdirSync(d, { recursive: true });
  return d;
}

function newer(a, b) {
  const pa = String(a).replace(/^v/, '').split('.').map(Number);
  const pb = String(b).replace(/^v/, '').split('.').map(Number);
  for (let i = 0; i < 3; i++) {
    if ((pa[i] || 0) !== (pb[i] || 0)) return (pa[i] || 0) > (pb[i] || 0);
  }
  return false;
}

async function latestRelease() {
  const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
    headers: { 'User-Agent': 'PVPTraining', Accept: 'application/vnd.github+json' },
  });
  if (!res.ok) throw new Error(`GitHub answered ${res.status}`);
  return res.json();
}

async function download(asset, dest, onProgress) {
  const res = await fetch(asset.browser_download_url, { headers: { 'User-Agent': 'PVPTraining' } });
  if (!res.ok || !res.body) throw new Error(`Download failed (${res.status})`);
  const tmp = `${dest}.part`;
  const out = fs.createWriteStream(tmp);
  const hash = crypto.createHash('sha256');
  let done = 0;
  let lastPercent = -1;
  for await (const chunk of res.body) {
    hash.update(chunk);
    done += chunk.length;
    if (!out.write(chunk)) await new Promise((r) => out.once('drain', r));
    // Report whole percents only: chunks arrive far faster than the UI needs to hear about.
    const percent = asset.size ? Math.floor((done / asset.size) * 100) : 0;
    if (percent !== lastPercent) {
      lastPercent = percent;
      onProgress?.(percent);
    }
  }
  await new Promise((resolve, reject) => out.end((err) => (err ? reject(err) : resolve())));
  const digest = `sha256:${hash.digest('hex')}`;
  if (asset.digest && asset.digest !== digest) {
    fs.rmSync(tmp, { force: true });
    throw new Error('The download did not match the checksum GitHub published for it.');
  }
  fs.renameSync(tmp, dest);
  return digest;
}

/* --------------------------------------------------------------------- mod */

async function updateMod(release, settings, saveSettings, installer) {
  // The app ships with the mod of its own version, so there is only something to pull when the
  // release is newer than this app. (A fresh install must not "update" to the jar it came with.)
  if (!newer(release.tag_name, app.getVersion())) return;
  const asset = release.assets.find((a) => a.name === ASSETS.jar);
  if (!asset) return;
  const current = settings.modUpdate;
  const id = asset.digest || `${asset.id}:${asset.updated_at}`;
  if (current?.id === id && current.file && fs.existsSync(current.file)) return;

  emit({ state: 'downloading', what: 'mod', percent: 0 });
  const modDir = path.join(dir(), 'mod');
  fs.mkdirSync(modDir, { recursive: true });
  const file = path.join(modDir, `pvptraining-${release.tag_name.replace(/^v/, '')}+mc1.21.11.jar`);
  await download(asset, file, (p) => emit({ state: 'downloading', what: 'mod', percent: p }));
  for (const old of fs.readdirSync(modDir)) if (path.join(modDir, old) !== file) fs.rmSync(path.join(modDir, old), { force: true });

  // Refresh every mods folder the app put the jar into. A jar that Minecraft has open
  // (Windows locks it) simply fails here and is retried on the next launch.
  const refreshed = [];
  for (const target of settings.installedDirs || []) {
    if (!fs.existsSync(target)) continue;
    if (installer.install(file, target).ok) refreshed.push(target);
  }
  saveSettings({ modUpdate: { id, file, tag: release.tag_name } });
  emit({ state: 'mod-updated', version: release.tag_name, refreshed: refreshed.length });
}

/** The newest jar we have: a downloaded update if there is one, else the one shipped in the app. */
function downloadedJar(settings) {
  const f = settings.modUpdate?.file;
  if (!f || !fs.existsSync(f)) return null;
  // If the app itself has since been updated past that jar, the shipped one is the newer of the two.
  return newer(app.getVersion(), settings.modUpdate.tag) ? null : f;
}

/* --------------------------------------------------------------------- app */

function bundlePath() {
  // .../PVPTraining.app/Contents/MacOS/PVPTraining
  const bundle = path.resolve(process.execPath, '..', '..', '..');
  return bundle.endsWith('.app') ? bundle : null;
}

function canSelfUpdate() {
  if (!app.isPackaged) return { ok: false, reason: 'dev' };
  if (process.platform === 'win32') return { ok: true };
  if (process.platform !== 'darwin') return { ok: false, reason: 'platform' };
  const bundle = bundlePath();
  if (!bundle) return { ok: false, reason: 'bundle' };
  if (bundle.startsWith('/Volumes/')) return { ok: false, reason: 'Move PVPTraining to Applications so it can update itself.' };
  try {
    fs.accessSync(path.dirname(bundle), fs.constants.W_OK);
  } catch {
    return { ok: false, reason: 'PVPTraining cannot write to the folder it is installed in.' };
  }
  return { ok: true };
}

async function updateApp(release) {
  if (!newer(release.tag_name, app.getVersion())) return emit({ state: 'current', version: app.getVersion() });
  const able = canSelfUpdate();
  if (!able.ok) {
    if (able.reason !== 'dev') emit({ state: 'manual', version: release.tag_name, reason: able.reason, url: release.html_url });
    return;
  }
  const asset = release.assets.find((a) => a.name === (process.platform === 'win32' ? ASSETS.win : ASSETS.mac));
  if (!asset) return;
  const file = path.join(dir(), asset.name);
  const marker = `${file}.json`;
  let have = null;
  try {
    have = JSON.parse(fs.readFileSync(marker, 'utf8'));
  } catch {
    /* nothing staged yet */
  }
  if (!(have && have.id === (asset.digest || asset.id) && fs.existsSync(file))) {
    emit({ state: 'downloading', what: 'app', version: release.tag_name, percent: 0 });
    await download(asset, file, (p) => emit({ state: 'downloading', what: 'app', version: release.tag_name, percent: p }));
    fs.writeFileSync(marker, JSON.stringify({ id: asset.digest || asset.id, version: release.tag_name }));
  }
  staged = { version: release.tag_name, file };
  emit({ state: 'ready', version: release.tag_name });
}

function applyMac(relaunch) {
  const bundle = bundlePath();
  const work = fs.mkdtempSync(path.join(os.tmpdir(), 'pvpt-update-'));
  return new Promise((resolve, reject) => {
    execFile('/usr/bin/ditto', ['-x', '-k', staged.file, work], (err) => {
      if (err) return reject(err);
      const fresh = fs.readdirSync(work).find((f) => f.endsWith('.app'));
      if (!fresh) return reject(new Error('The update archive did not contain the app.'));
      const script = path.join(work, 'swap.sh');
      fs.writeFileSync(
        script,
        `#!/bin/sh
while kill -0 ${process.pid} 2>/dev/null; do sleep 0.2; done
rm -rf "${bundle}.old"
mv "${bundle}" "${bundle}.old" && mv "${path.join(work, fresh)}" "${bundle}" && rm -rf "${bundle}.old"
/usr/bin/xattr -dr com.apple.quarantine "${bundle}" 2>/dev/null
${relaunch ? `/usr/bin/open "${bundle}"` : ''}
rm -rf "${work}"
`,
        { mode: 0o755 }
      );
      spawn('/bin/sh', [script], { detached: true, stdio: 'ignore' }).unref();
      resolve();
    });
  });
}

function applyWin(relaunch) {
  // The one-click NSIS installer closes the running app, installs silently and reopens it.
  spawn(staged.file, relaunch ? ['/S', '--force-run'] : ['/S'], { detached: true, stdio: 'ignore' }).unref();
  return Promise.resolve();
}

async function apply(relaunch = true) {
  if (!staged || applying) return false;
  applying = true;
  try {
    await (process.platform === 'win32' ? applyWin(relaunch) : applyMac(relaunch));
    fs.rmSync(`${staged.file}.json`, { force: true });
    app.quit();
    return true;
  } catch (err) {
    applying = false;
    emit({ state: 'error', message: err.message });
    return false;
  }
}

/* -------------------------------------------------------------------- entry */

async function run({ send, getSettings, saveSettings, installer }) {
  emit = send;
  try {
    emit({ state: 'checking' });
    const release = await latestRelease();
    await updateMod(release, getSettings(), saveSettings, installer);
    await updateApp(release);
  } catch (err) {
    // Offline or rate limited: the app works fine without an update, so stay quiet about it.
    emit({ state: 'offline', message: err.message });
  }
}

module.exports = { run, apply, downloadedJar, hasStaged: () => Boolean(staged) && !applying };
