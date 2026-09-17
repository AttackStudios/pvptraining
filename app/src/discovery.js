const fs = require('fs');
const path = require('path');

function alive(pid) {
  if (!pid) return false;
  try {
    process.kill(pid, 0);
    return true;
  } catch (err) {
    return err.code === 'EPERM';
  }
}

/**
 * Every Minecraft client running the mod drops a small JSON file in
 * ~/.pvptraining/instances. That is how the app finds any client on this
 * machine, whichever launcher started it.
 */
function list(homeDir) {
  const dir = path.join(homeDir, 'instances');
  let files = [];
  try {
    files = fs.readdirSync(dir).filter((f) => f.endsWith('.json'));
  } catch {
    return [];
  }
  const found = [];
  for (const file of files) {
    const full = path.join(dir, file);
    try {
      const info = JSON.parse(fs.readFileSync(full, 'utf8'));
      if (!alive(info.pid)) {
        fs.rmSync(full, { force: true });
        continue;
      }
      found.push(info);
    } catch {
      /* half-written file: pick it up on the next poll */
    }
  }
  return found.sort((a, b) => (b.startedAt || 0) - (a.startedAt || 0));
}

module.exports = { list };
