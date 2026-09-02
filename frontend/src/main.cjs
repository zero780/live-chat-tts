const { app, BrowserWindow, ipcMain, screen } = require('electron');
const { spawn } = require('node:child_process');
const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');

let windowRef;
let backend;

function jarPath() {
  const development = path.resolve(__dirname, '..', '..', 'backend', 'dist', 'live-chat-tts.jar');
  const packaged = path.resolve(process.resourcesPath, 'backend', 'live-chat-tts.jar');
  return app.isPackaged ? packaged : development;
}

function javaCommand() {
  const executable = process.platform === 'win32' ? 'java.exe' : 'java';
  if (app.isPackaged) {
    const bundled = path.join(process.resourcesPath, 'jre', 'bin', executable);
    if (fs.existsSync(bundled)) return bundled;
  }
  const fromJavaHome = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', executable) : '';
  return fromJavaHome && fs.existsSync(fromJavaHome) ? fromJavaHome : executable;
}

function backendArguments(jar) { return ['-jar', jar]; }

function freeLoopbackPort() {
  return new Promise((resolve, reject) => {
    const probe = http.createServer();
    probe.once('error', reject);
    probe.listen(0, '127.0.0.1', () => {
      const { port } = probe.address();
      probe.close((error) => error ? reject(error) : resolve(port));
    });
  });
}

async function startBackend() {
  if (backend?.child && !backend.child.killed) return backend.info();
  const jar = jarPath();
  if (!fs.existsSync(jar)) throw new Error(`No se encontró el JAR del backend: ${jar}. Ejecuta backend\\build.bat.`);

  const port = await freeLoopbackPort();
  const token = crypto.randomBytes(32).toString('base64url');
  const source = process.env.LIVE_SOURCE === 'LOCAL_TEST' ? 'LOCAL_TEST' : 'TIKTOK_LIVE_JAVA';
  const child = spawn(javaCommand(), backendArguments(jar), {
    windowsHide: true,
    env: { ...process.env, APP_PORT: String(port), LOCAL_API_TOKEN: token, LIVE_SOURCE: source }
  });
  backend = { child, port, token, source, lastProcessError: '', info() { return { running: true, source: this.source, port: this.port }; } };
  child.stderr.on('data', (chunk) => { backend.lastProcessError = String(chunk).slice(-500); });
  child.on('error', (error) => { if (backend) backend.lastProcessError = error.message; });
  child.on('exit', (code) => { if (backend) backend.lastProcessError = `Backend detenido (código ${code ?? 'desconocido'}).`; });

  for (let attempt = 0; attempt < 30; attempt += 1) {
    try {
      await api('/api/health');
      return backend.info();
    } catch (_) {
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
  }
  stopBackend();
  throw new Error('El backend no respondió en 8 segundos. Comprueba Java 21 y el JAR.');
}

function stopBackend() {
  if (backend?.child && !backend.child.killed) backend.child.kill();
  backend = undefined;
}

async function api(endpoint, options = {}) {
  if (!backend?.child || backend.child.killed) throw new Error(backend?.lastProcessError || 'El backend no está iniciado.');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 5_000);
  try {
    const response = await fetch(`http://127.0.0.1:${backend.port}${endpoint}`, {
      method: options.method || 'GET',
      headers: { 'X-Local-Api-Token': backend.token, ...(options.body ? { 'Content-Type': 'application/json' } : {}) },
      body: options.body ? JSON.stringify(options.body) : undefined,
      signal: controller.signal
    });
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.error || `Error HTTP ${response.status}`);
    return payload;
  } finally {
    clearTimeout(timeout);
  }
}

function setupIpc() {
  ipcMain.handle('backend:start', startBackend);
  ipcMain.handle('backend:runtime', () => backend ? backend.info() : { running: false });
  ipcMain.handle('backend:status', () => api('/api/status'));
  ipcMain.handle('backend:voices', () => api('/api/voices'));
  ipcMain.handle('backend:settings', () => api('/api/settings'));
  ipcMain.handle('backend:save-settings', (_, settings) => api('/api/settings', { method: 'PUT', body: settings }));
  ipcMain.handle('backend:connect', (_, username) => api('/api/connect', { method: 'POST', body: { username } }));
  ipcMain.handle('backend:disconnect', () => api('/api/disconnect', { method: 'POST', body: {} }));
  ipcMain.handle('backend:test-message', (_, message) => api('/api/test/messages', { method: 'POST', body: message }));
}

function createWindow() {
  const workAreaHeight = screen.getPrimaryDisplay().workAreaSize.height;
  windowRef = new BrowserWindow({
    width: 500,
    height: Math.max(680, Math.floor(workAreaHeight * 0.88)),
    minWidth: 440,
    minHeight: 600,
    resizable: true,
    maximizable: false,
    show: false,
    title: 'Live Chat TTS',
    backgroundColor: '#11131a',
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#11131a', symbolColor: '#e9e9f0', height: 36 },
    webPreferences: { preload: path.join(__dirname, 'preload.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true }
  });
  windowRef.setMenuBarVisibility(false);
  windowRef.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  windowRef.webContents.on('will-navigate', (event) => event.preventDefault());
  windowRef.once('ready-to-show', () => windowRef.show());
  windowRef.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

app.whenReady().then(() => { setupIpc(); createWindow(); });
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
app.on('before-quit', stopBackend);
