const { contextBridge, ipcRenderer } = require('electron');

const call = (channel) => (...args) => ipcRenderer.invoke(channel, ...args);

contextBridge.exposeInMainWorld('pvpt', {
  info: call('app:info'),
  settings: { get: call('settings:get'), set: call('settings:set') },
  catalog: call('catalog:get'),
  progress: call('progress:get'),
  discover: call('discovery:list'),
  bridge: {
    connect: call('bridge:connect'),
    send: call('bridge:send'),
    close: call('bridge:close'),
    onMessage: (fn) => ipcRenderer.on('bridge:message', (_e, msg) => fn(msg)),
    onStatus: (fn) => ipcRenderer.on('bridge:status', (_e, status) => fn(status)),
  },
  installer: {
    targets: call('installer:targets'),
    install: call('installer:install'),
    reveal: call('installer:reveal'),
    saveAs: call('installer:saveAs'),
    pick: call('installer:pick'),
    drag: () => ipcRenderer.send('installer:drag'),
  },
  open: call('shell:open'),
  openPath: call('shell:openPath'),
});
