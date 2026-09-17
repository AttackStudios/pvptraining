// Renders the PVPTraining crest to the PNGs the app, the mod and the site need.
// Run with: npx electron tools/make-icons.js
const { app, BrowserWindow } = require('electron');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..', '..');

const crest = (pad, radius) => `<!doctype html><html><body style="margin:0;background:transparent">
<div style="width:1024px;height:1024px;display:grid;place-items:center">
<div style="width:${1024 - pad * 2}px;height:${1024 - pad * 2}px;border-radius:${radius}px;display:grid;place-items:center;
  background:radial-gradient(circle at 30% 20%,#2a1a14 0%,#0d0f15 62%);box-shadow:inset 0 0 0 6px rgba(255,255,255,.06)">
<svg width="66%" height="66%" viewBox="0 0 48 48" fill="none">
  <defs><linearGradient id="g" x1="8" y1="44" x2="40" y2="4" gradientUnits="userSpaceOnUse"><stop stop-color="#ff5a36"/><stop offset="1" stop-color="#ffb23e"/></linearGradient></defs>
  <path d="M24 3 42 10v13c0 11-7.4 18.6-18 22C13.4 41.6 6 34 6 23V10L24 3Z" fill="url(#g)" opacity=".18"/>
  <path d="M24 3 42 10v13c0 11-7.4 18.6-18 22C13.4 41.6 6 34 6 23V10L24 3Z" stroke="url(#g)" stroke-width="2.4" stroke-linejoin="round"/>
  <path d="m16 33 12.5-12.5" stroke="url(#g)" stroke-width="3.2" stroke-linecap="round"/>
  <path d="m27 12 9 9-5.2 1.8-1.6 5.2-9-9 1.8-5.2L27 12Z" fill="url(#g)"/>
</svg></div></div></body></html>`;

async function render(html, size, outFiles) {
  const win = new BrowserWindow({ width: 1024, height: 1024, show: false, transparent: true, frame: false, webPreferences: { offscreen: true } });
  const tmp = path.join(app.getPath('temp'), `pvpt-icon-${size}.html`);
  fs.writeFileSync(tmp, html);
  await win.loadFile(tmp);
  await new Promise((r) => setTimeout(r, 400));
  let img = await win.webContents.capturePage();
  img = img.resize({ width: size, height: size, quality: 'best' });
  for (const out of outFiles) {
    fs.mkdirSync(path.dirname(out), { recursive: true });
    fs.writeFileSync(out, img.toPNG());
    console.log('wrote', path.relative(root, out), size);
  }
  win.destroy();
}

app.disableHardwareAcceleration();
app.on('window-all-closed', () => {}); // keep running between renders
app.whenReady().then(async () => {
  await render(crest(92, 190), 1024, [path.join(root, 'app', 'build', 'icon.png')]);
  await render(crest(40, 200), 256, [
    path.join(root, 'mod', 'src', 'main', 'resources', 'assets', 'pvptraining', 'icon.png'),
    path.join(root, 'site', 'assets', 'icon.png'),
  ]);
  await render(crest(40, 200), 64, [path.join(root, 'app', 'renderer', 'drag-icon.png')]);
  app.quit();
});
