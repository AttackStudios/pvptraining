const { app, BrowserWindow, ipcMain, shell, dialog, nativeTheme, nativeImage } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { Bridge } = require('./bridge');
const discovery = require('./discovery');
const installer = require('./installer');

const HOME_DIR = path.join(os.homedir(), '.pvptraining');
const SHOTS = process.argv.includes('--shots');
const APP_ICON = path.join(__dirname, 'app-icon.png');

let win = null;
let bridge = null;

function settingsPath() {
  return path.join(app.getPath('userData'), 'settings.json');
}

function readSettings() {
  try {
    return JSON.parse(fs.readFileSync(settingsPath(), 'utf8'));
  } catch {
    return {};
  }
}

function writeSettings(next) {
  fs.mkdirSync(path.dirname(settingsPath()), { recursive: true });
  fs.writeFileSync(settingsPath(), JSON.stringify(next, null, 2));
}

function resourcesDir() {
  return app.isPackaged ? process.resourcesPath : path.join(__dirname, '..', 'resources');
}

function bundledJar() {
  const dir = path.join(resourcesDir(), 'mod');
  try {
    const jar = fs.readdirSync(dir).filter((f) => f.endsWith('.jar')).sort().pop();
    return jar ? path.join(dir, jar) : null;
  } catch {
    return null;
  }
}

function send(channel, payload) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
}

function createWindow() {
  nativeTheme.themeSource = 'dark';
  const isMac = process.platform === 'darwin';
  win = new BrowserWindow({
    width: 1200,
    height: 780,
    minWidth: 1000,
    minHeight: 660,
    show: false,
    backgroundColor: '#0a0c11',
    title: 'PVPTraining',
    icon: APP_ICON,
    titleBarStyle: isMac ? 'hiddenInset' : 'hidden',
    trafficLightPosition: { x: 18, y: 18 },
    titleBarOverlay: isMac ? false : { color: '#00000000', symbolColor: '#9aa3b5', height: 44 },
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });
  win.removeMenu?.();
  win.loadFile(path.join(__dirname, '..', 'renderer', 'index.html'));
  win.once('ready-to-show', () => {
    if (!SHOTS) win.show();
  });
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.on('closed', () => {
    win = null;
  });
}

function wireIpc() {
  ipcMain.handle('app:info', () => ({
    version: app.getVersion(),
    platform: process.platform,
    jar: bundledJar() ? path.basename(bundledJar()) : null,
    shots: SHOTS,
  }));

  ipcMain.handle('settings:get', () => readSettings());
  ipcMain.handle('settings:set', (_e, patch) => {
    const next = { ...readSettings(), ...patch };
    writeSettings(next);
    return next;
  });

  ipcMain.handle('catalog:get', () => {
    const file = path.join(__dirname, '..', 'renderer', 'catalog.json');
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  });

  ipcMain.handle('progress:get', () => {
    try {
      return JSON.parse(fs.readFileSync(path.join(HOME_DIR, 'progress.json'), 'utf8'));
    } catch {
      return null;
    }
  });

  ipcMain.handle('discovery:list', () => discovery.list(HOME_DIR));

  ipcMain.handle('bridge:connect', async (_e, instance) => {
    if (bridge) bridge.close();
    bridge = new Bridge(instance);
    bridge.on('message', (msg) => send('bridge:message', msg));
    bridge.on('status', (status) => send('bridge:status', status));
    return bridge.open();
  });
  ipcMain.handle('bridge:send', (_e, msg) => (bridge ? bridge.send(msg) : false));
  ipcMain.handle('bridge:close', () => {
    if (bridge) bridge.close();
    bridge = null;
    return true;
  });

  ipcMain.handle('installer:targets', (_e, launcher) => installer.targets(launcher));
  ipcMain.handle('installer:install', (_e, dir) => installer.install(bundledJar(), dir));
  ipcMain.handle('installer:reveal', () => {
    const jar = bundledJar();
    if (!jar) return false;
    shell.showItemInFolder(jar);
    return true;
  });
  ipcMain.handle('installer:saveAs', async () => {
    const jar = bundledJar();
    if (!jar) return { ok: false, error: 'The mod file is missing from this build.' };
    const res = await dialog.showSaveDialog(win, {
      title: 'Save the PVPTraining mod',
      defaultPath: path.join(app.getPath('downloads'), path.basename(jar)),
      filters: [{ name: 'Fabric mod', extensions: ['jar'] }],
    });
    if (res.canceled || !res.filePath) return { ok: false, canceled: true };
    fs.copyFileSync(jar, res.filePath);
    shell.showItemInFolder(res.filePath);
    return { ok: true, path: res.filePath };
  });
  // Lets the player drag the jar straight out of the guide into their launcher.
  ipcMain.on('installer:drag', (event) => {
    const jar = bundledJar();
    if (!jar) return;
    const iconFile = path.join(__dirname, '..', 'renderer', 'drag-icon.png');
    event.sender.startDrag({ file: jar, icon: nativeImage.createFromPath(iconFile) });
  });
  ipcMain.handle('installer:pick', async () => {
    const res = await dialog.showOpenDialog(win, {
      title: 'Choose your mods folder',
      properties: ['openDirectory', 'createDirectory'],
    });
    return res.canceled ? null : res.filePaths[0];
  });
  ipcMain.handle('shell:open', (_e, url) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url);
    return true;
  });
  ipcMain.handle('shell:openPath', (_e, p) => shell.openPath(p));
}

// Identity: without these a dev run shows up as "Electron" with the stock icon, and on
// Windows the taskbar groups the window under Electron's app id instead of ours.
app.setName('PVPTraining');
if (process.platform === 'win32') app.setAppUserModelId('net.attackstudioyt.pvptraining');

app.whenReady().then(() => {
  if (process.platform === 'darwin' && app.dock) app.dock.setIcon(nativeImage.createFromPath(APP_ICON));
  wireIpc();
  createWindow();
  if (SHOTS) require('./shots').run(win);
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (bridge) bridge.close();
  app.quit();
});
