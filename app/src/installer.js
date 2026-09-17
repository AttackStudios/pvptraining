const fs = require('fs');
const os = require('os');
const path = require('path');

const home = os.homedir();
const isWin = process.platform === 'win32';
const isMac = process.platform === 'darwin';

function appData(...parts) {
  if (isWin) return path.join(process.env.APPDATA || path.join(home, 'AppData', 'Roaming'), ...parts);
  if (isMac) return path.join(home, 'Library', 'Application Support', ...parts);
  return path.join(process.env.XDG_DATA_HOME || path.join(home, '.local', 'share'), ...parts);
}

function vanillaDir() {
  if (isWin) return appData('.minecraft');
  if (isMac) return appData('minecraft');
  return path.join(home, '.minecraft');
}

function dirExists(p) {
  try {
    return fs.statSync(p).isDirectory();
  } catch {
    return false;
  }
}

function modrinthProfiles() {
  const roots = [appData('ModrinthApp', 'profiles'), appData('com.modrinth.theseus', 'profiles')];
  const out = [];
  for (const root of roots) {
    if (!dirExists(root)) continue;
    for (const name of fs.readdirSync(root)) {
      const dir = path.join(root, name);
      if (!dirExists(dir)) continue;
      out.push({ label: name, dir: path.join(dir, 'mods'), hint: 'Modrinth instance' });
    }
  }
  return out;
}

/**
 * Folders we can safely install into without guessing. Lunar and Dawn keep
 * their mods somewhere only the launcher knows, so those guides use
 * drag-and-drop from the app instead.
 */
function targets(launcher) {
  if (launcher === 'modrinth') return modrinthProfiles();
  if (launcher === 'other') {
    const dir = vanillaDir();
    return dirExists(dir) ? [{ label: 'Minecraft Launcher', dir: path.join(dir, 'mods'), hint: 'Default .minecraft folder' }] : [];
  }
  return [];
}

function install(jar, dir) {
  if (!jar) return { ok: false, error: 'The mod file is missing from this build.' };
  if (!dir) return { ok: false, error: 'No folder chosen.' };
  try {
    fs.mkdirSync(dir, { recursive: true });
    const removed = [];
    for (const file of fs.readdirSync(dir)) {
      if (/^pvptraining-.*\.jar$/i.test(file)) {
        fs.rmSync(path.join(dir, file), { force: true });
        removed.push(file);
      }
    }
    const dest = path.join(dir, path.basename(jar));
    fs.copyFileSync(jar, dest);
    return { ok: true, path: dest, replaced: removed.length > 0 };
  } catch (err) {
    return { ok: false, error: err.message };
  }
}

module.exports = { targets, install, vanillaDir };
