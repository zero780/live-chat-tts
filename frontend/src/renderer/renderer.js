const translations = {
  en: {
    'language.switchTo': 'Switch to Spanish',
    'mode.starting': 'Starting', 'mode.localTest': 'Local test', 'mode.tiktok': 'TikTok',
    'voice.status': 'Voice status',
    'connection.preparing': 'Preparing local backend', 'connection.localReady': 'Speech will work without cloud services.',
    'connection.connectedTo': 'Connected to @{username}', 'connection.connecting': 'Connecting to LIVE', 'connection.error': 'Connection error', 'connection.disconnected': 'Disconnected', 'connection.ready': 'Ready to start.',
    'form.tiktokUsername': 'TikTok username', 'form.usernamePlaceholder': 'your_username',
    'action.connect': 'Connect', 'action.connecting': 'Connecting…', 'action.disconnect': 'Disconnect', 'action.saveSettings': 'Save settings', 'action.testVoice': 'Test voice',
    'activity.label': 'Activity', 'activity.title': 'LIVE activity', 'activity.lastMessages': 'Last 100 messages', 'activity.empty': 'LIVE messages will appear here.',
    'metric.queue': 'In queue', 'metric.read': 'Read', 'metric.protected': 'Protected',
    'settings.title': '⚙ Voice settings', 'settings.voice': 'SAPI voice', 'settings.speed': 'Speed', 'settings.output': 'Audio output',
    'test.localMode': 'Local test mode', 'toast.settingsSaved': 'Settings saved',
    'message.user': 'User', 'message.queued': 'In queue', 'message.speaking': 'Speaking', 'message.spoken': 'Read', 'message.dropped': 'Dropped', 'message.rejected': 'Protected', 'message.failed': 'Error',
    'error.usernameRequired': 'Enter your TikTok username.', 'error.startFailed': 'Could not start the application.'
  },
  es: {
    'language.switchTo': 'Cambiar a inglés',
    'mode.starting': 'Iniciando', 'mode.localTest': 'Prueba local', 'mode.tiktok': 'TikTok',
    'voice.status': 'Estado de la voz',
    'connection.preparing': 'Preparando backend local', 'connection.localReady': 'La voz funcionará sin servicios en la nube.',
    'connection.connectedTo': 'Conectado a @{username}', 'connection.connecting': 'Conectando al LIVE', 'connection.error': 'Error de conexión', 'connection.disconnected': 'Sin conexión', 'connection.ready': 'Listo para iniciar.',
    'form.tiktokUsername': 'Usuario de TikTok', 'form.usernamePlaceholder': 'tu_usuario',
    'action.connect': 'Conectar', 'action.connecting': 'Conectando…', 'action.disconnect': 'Desconectar', 'action.saveSettings': 'Guardar ajustes', 'action.testVoice': 'Probar voz',
    'activity.label': 'Actividad', 'activity.title': 'Actividad del LIVE', 'activity.lastMessages': 'Últimos 100 mensajes', 'activity.empty': 'Los mensajes del LIVE aparecerán aquí.',
    'metric.queue': 'En cola', 'metric.read': 'Leídos', 'metric.protected': 'Protegidos',
    'settings.title': '⚙ Ajustes de voz', 'settings.voice': 'Voz SAPI', 'settings.speed': 'Velocidad', 'settings.output': 'Salida de audio',
    'test.localMode': 'Modo prueba local', 'toast.settingsSaved': 'Ajustes guardados',
    'message.user': 'Usuario', 'message.queued': 'En cola', 'message.speaking': 'Reproduciendo', 'message.spoken': 'Leído', 'message.dropped': 'Descartado', 'message.rejected': 'Protegido', 'message.failed': 'Error',
    'error.usernameRequired': 'Escribe tu usuario de TikTok.', 'error.startFailed': 'No se pudo iniciar la aplicación.'
  }
};

const elements = {
  username: document.querySelector('#username'), connect: document.querySelector('#connect-button'), disconnect: document.querySelector('#disconnect-button'),
  state: document.querySelector('#connection-state'), detail: document.querySelector('#connection-detail'), orb: document.querySelector('#voice-orb'),
  queue: document.querySelector('#queue-depth'), accepted: document.querySelector('#accepted-count'), dropped: document.querySelector('#dropped-count'),
  toggle: document.querySelector('#settings-toggle'), arrow: document.querySelector('#settings-arrow'), form: document.querySelector('#settings-form'), voice: document.querySelector('#voice'),
  rate: document.querySelector('#rate'), rateValue: document.querySelector('#rate-value'), output: document.querySelector('#audio-output'), error: document.querySelector('#error-message'), mode: document.querySelector('#mode-badge'), testCard: document.querySelector('#test-card'), testVoice: document.querySelector('#test-voice'), toast: document.querySelector('#save-toast'), chatList: document.querySelector('#chat-list'), chatEmpty: document.querySelector('#chat-empty'), chatCount: document.querySelector('#chat-count'), language: document.querySelector('#language-toggle'), languageFlag: document.querySelector('#language-flag')
};

let language = localStorage.getItem('live-chat-tts.language');
if (!translations[language]) language = navigator.language?.toLowerCase().startsWith('es') ? 'es' : 'en';
let toastTimer;
let messagesSignature = '';
let lastMessages = [];
let isBusy = false;
let runtimeSource = '';

function t(key, values = {}) {
  return (translations[language][key] || translations.en[key] || key).replace(/\{(\w+)\}/g, (_, name) => values[name] ?? '');
}

function translateDocument() {
  document.documentElement.lang = language;
  document.querySelectorAll('[data-i18n]').forEach((node) => { node.textContent = t(node.dataset.i18n); });
  document.querySelectorAll('[data-i18n-placeholder]').forEach((node) => { node.placeholder = t(node.dataset.i18nPlaceholder); });
  document.querySelectorAll('[data-i18n-alt]').forEach((node) => { node.alt = t(node.dataset.i18nAlt); });
  document.querySelectorAll('[data-i18n-aria-label]').forEach((node) => { node.setAttribute('aria-label', t(node.dataset.i18nAriaLabel)); });
  elements.languageFlag.src = language === 'es' ? '../assets/flag-es.svg' : '../assets/flag-us.svg';
  elements.language.title = t('language.switchTo');
  elements.language.setAttribute('aria-label', t('language.switchTo'));
  elements.chatEmpty.textContent = t('activity.empty');
  if (runtimeSource) elements.mode.textContent = runtimeSource === 'LOCAL_TEST' ? t('mode.localTest') : t('mode.tiktok');
}

function showError(error = '') { elements.error.textContent = error instanceof Error ? error.message : error; }
function showSaved() {
  clearTimeout(toastTimer);
  elements.toast.hidden = false;
  requestAnimationFrame(() => elements.toast.classList.add('visible'));
  toastTimer = setTimeout(() => {
    elements.toast.classList.remove('visible');
    setTimeout(() => { elements.toast.hidden = true; }, 180);
  }, 2600);
}
function setBusy(busy) {
  isBusy = busy;
  elements.connect.disabled = busy;
  elements.connect.textContent = t(busy ? 'action.connecting' : 'action.connect');
}
function option(select, value, text) {
  const item = document.createElement('option');
  item.value = value;
  item.textContent = text;
  select.append(item);
}

function messageState(state) {
  return t({ QUEUED: 'message.queued', SPEAKING: 'message.speaking', SPOKEN: 'message.spoken', DROPPED: 'message.dropped', REJECTED: 'message.rejected', FAILED: 'message.failed' }[state] || state);
}

function renderMessages(messages) {
  const items = Array.isArray(messages) ? messages.slice(-100).reverse() : [];
  lastMessages = items;
  const signature = `${language}:${items.map((message) => `${message.id}:${message.state}`).join('|')}`;
  if (signature === messagesSignature) return;
  messagesSignature = signature;
  const nearTop = elements.chatList.scrollTop < 48;
  elements.chatCount.textContent = String(items.length);
  if (!items.length) {
    elements.chatEmpty.textContent = t('activity.empty');
    elements.chatList.replaceChildren(elements.chatEmpty);
    return;
  }
  const fragment = document.createDocumentFragment();
  let active;
  items.forEach((message) => {
    const row = document.createElement('article');
    row.className = `chat-message state-${String(message.state || '').toLowerCase()}`;
    if (message.state === 'SPEAKING') {
      row.classList.add('active');
      row.setAttribute('aria-current', 'true');
      active = row;
    }
    const author = document.createElement('strong'); author.textContent = message.author || t('message.user');
    const text = document.createElement('p'); text.textContent = message.text || '';
    const state = document.createElement('span'); state.textContent = messageState(message.state);
    row.append(author, text, state);
    fragment.append(row);
  });
  elements.chatList.replaceChildren(fragment);
  if (active) active.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  else if (nearTop) elements.chatList.scrollTop = 0;
}

async function loadSettings() {
  const [voiceResponse, settings] = await Promise.all([window.desktop.voices(), window.desktop.settings()]);
  elements.voice.replaceChildren();
  voiceResponse.voices.forEach((voice) => option(elements.voice, voice.id, voice.displayName));
  elements.output.replaceChildren();
  settings.audioOutputs.forEach((audio) => option(elements.output, audio.id, audio.displayName));
  elements.voice.value = settings.voiceId;
  elements.rate.value = settings.speechRate;
  elements.rateValue.value = settings.speechRate;
  elements.output.value = settings.audioOutputId;
}

async function refreshStatus() {
  try {
    const status = await window.desktop.status();
    const connection = status.connection;
    elements.state.textContent = connection.state === 'CONNECTED' ? t('connection.connectedTo', { username: connection.username }) : connection.state === 'CONNECTING' ? t('connection.connecting') : connection.state === 'ERROR' ? t('connection.error') : t('connection.disconnected');
    elements.detail.textContent = status.lastError || connection.detail || t('connection.ready');
    elements.orb.classList.toggle('speaking', Boolean(status.speaking));
    elements.queue.textContent = status.queueDepth;
    elements.accepted.textContent = status.acceptedMessages;
    elements.dropped.textContent = status.droppedMessages + status.rejectedMessages;
    renderMessages(status.messages);
    const connected = connection.state === 'CONNECTED' || connection.state === 'CONNECTING';
    elements.disconnect.hidden = !connected;
    elements.connect.hidden = connected;
  } catch (error) { showError(error); }
}

function changeLanguage() {
  language = language === 'es' ? 'en' : 'es';
  localStorage.setItem('live-chat-tts.language', language);
  messagesSignature = '';
  translateDocument();
  setBusy(isBusy);
  renderMessages(lastMessages);
  refreshStatus();
}

elements.language.addEventListener('click', changeLanguage);
elements.toggle.addEventListener('click', () => {
  const open = elements.form.hidden;
  elements.form.hidden = !open;
  elements.toggle.setAttribute('aria-expanded', String(open));
  elements.arrow.textContent = open ? '⌃' : '⌄';
});
elements.rate.addEventListener('input', () => { elements.rateValue.value = elements.rate.value; });
elements.form.addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    showError();
    await window.desktop.saveSettings({ voiceId: elements.voice.value, speechRate: Number(elements.rate.value), audioOutputId: elements.output.value });
    showSaved();
  } catch (error) { showError(error); }
});
elements.connect.addEventListener('click', async () => {
  try {
    const username = elements.username.value.trim().replace(/^@/, '');
    if (!username) throw new Error(t('error.usernameRequired'));
    setBusy(true);
    showError();
    await window.desktop.connect(username);
    await refreshStatus();
  } catch (error) { showError(error); } finally { setBusy(false); }
});
elements.disconnect.addEventListener('click', async () => {
  try {
    showError();
    await window.desktop.disconnect();
    await refreshStatus();
  } catch (error) { showError(error); }
});
elements.testVoice.addEventListener('click', async () => {
  try {
    showError();
    const status = await window.desktop.status();
    if (status.connection.state !== 'CONNECTED') {
      await window.desktop.connect('prueba_local');
      await refreshStatus();
    }
    await window.desktop.testMessage({ author: 'Prueba', text: 'La voz local está lista.' });
  } catch (error) { showError(error); }
});

translateDocument();
(async () => {
  try {
    const runtime = await window.desktop.start();
    runtimeSource = runtime.source;
    elements.mode.textContent = runtimeSource === 'LOCAL_TEST' ? t('mode.localTest') : t('mode.tiktok');
    elements.testCard.hidden = runtime.source !== 'LOCAL_TEST';
    await loadSettings();
    await refreshStatus();
    setInterval(refreshStatus, 900);
  } catch (error) {
    elements.state.textContent = t('error.startFailed');
    showError(error);
  }
})();
