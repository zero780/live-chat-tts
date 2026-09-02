const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktop', {
  start: () => ipcRenderer.invoke('backend:start'),
  runtime: () => ipcRenderer.invoke('backend:runtime'),
  status: () => ipcRenderer.invoke('backend:status'),
  voices: () => ipcRenderer.invoke('backend:voices'),
  settings: () => ipcRenderer.invoke('backend:settings'),
  saveSettings: (settings) => ipcRenderer.invoke('backend:save-settings', settings),
  connect: (username) => ipcRenderer.invoke('backend:connect', username),
  disconnect: () => ipcRenderer.invoke('backend:disconnect'),
  testMessage: (message) => ipcRenderer.invoke('backend:test-message', message)
});

