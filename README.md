# Live Chat TTS

<p align="center">
  <img src="frontend/src/assets/livechattts-logo.png" width="180" alt="Live Chat TTS logo" />
</p>

<p align="center">A local Windows desktop tool that turns TikTok LIVE activity into speech with Piper TTS.</p>

[![Español](https://img.shields.io/badge/README-Espa%C3%B1ol-2ea44f?style=for-the-badge)](README.es.md)
[![Java](https://img.shields.io/badge/Backend-Java%2021-007396?style=flat&logo=openjdk&logoColor=white)](backend/README.md)
[![Electron](https://img.shields.io/badge/Frontend-Electron%2044.1.1-47848f?style=flat&logo=electron&logoColor=white)](frontend/README.md)
[![Piper](https://img.shields.io/badge/Piper_TTS-es_MX--claude--high%20%2B%20en_US--hfc_female--medium-7b4bb7?style=flat)](https://github.com/OHF-Voice/piper1-gpl)
[![TikTokLiveJava](https://img.shields.io/badge/TikTokLiveJava-1.11.0-ff0050?style=flat)](https://github.com/jwdeveloper/TikTokLiveJava)
[![Platform](https://img.shields.io/badge/Platform-Windows-0078d4?style=flat&logo=windows)](#requirements)
[![Architecture](https://img.shields.io/badge/Architecture-Hexagonal-6f42c1?style=flat)](#architecture)

Live Chat TTS is a privacy-oriented desktop application for creators and moderators. It connects to a TikTok LIVE through the unofficial TikTokLiveJava client, receives chat and supported LIVE events locally, places speech requests in a bounded FIFO queue, and reads them with local Piper TTS. No cloud TTS provider or external application server is required.

> TikTokLiveJava is an unofficial reverse-engineering project. Review its license, TikTok's terms and applicable rules before using the integration on a real LIVE. The application is a listener: it does not send chat messages, automate accounts, rotate IPs or use cookies.

## Application preview

![Live Chat TTS application](docs/application-example.jpg)

## What is included

- TikTok LIVE connection by `uniqueId`.
- Chat comments plus supported activity events such as gifts, follows, subscriptions and LIVE lifecycle notifications.
- Local Piper TTS support with Mexican Spanish `es_MX-claude-high` and English US `en_US-hfc_female-medium` voices. Spanish is selected by default.
- Voice speed control and local audio playback.
- A compact Electron interface with Spanish/English switching, connection state, desktop notifications, streamer avatar, queue counters, animated speech indicator and a newest-first 100-message activity view.
- A local test mode that exercises the queue and Piper without connecting to TikTok.
- A self-contained backend JAR and a Windows NSIS installer that bundles a reduced Java 21 runtime.

## Architecture

The project is split into independent `backend/` and `frontend/` applications. The backend follows hexagonal architecture: domain and use cases depend on ports, while HTTP, TikTokLiveJava, Piper and file storage are adapters.

```mermaid
graph LR
  UI[Electron renderer] -->|restricted contextBridge| IPC[Preload IPC]
  IPC --> MAIN[Electron main process]
  MAIN -->|127.0.0.1 + per-run token| API[Local HTTP API]
  API --> APP[Application services]
  APP --> QUEUE[Bounded speech queue]
  APP --> LIVE[LiveChatClient port]
  LIVE --> TIKTOK[TikTokLiveJava adapter]
  QUEUE --> SPEECH[SpeechEngine port]
  SPEECH --> PIPER[Local Piper adapter]
  APP --> SETTINGS[SettingsStore port]
  SETTINGS --> FILE[Local settings file]
```

### Runtime flow

1. Electron starts the bundled Java process on an available loopback port and creates a temporary API token.
2. The renderer communicates only with the allowlisted preload IPC methods.
3. The backend validates and sanitizes content, applies rate limits and places accepted messages in the bounded queue.
4. One sequential speech worker invokes the selected Piper voice.
5. The frontend polls local status and displays connection, queue, message history and speaking state.

## Repository layout

```text
live-chat-tts/
├── backend/                 # Java 21 service, TikTokLiveJava and local HTTP API
├── frontend/                # Electron desktop application
├── BUILDING.md              # Short build reference
├── README.md                # This document (English)
└── README.es.md             # General documentation (Spanish)
```

Detailed documentation: [backend/README.md](backend/README.md) and [frontend/README.md](frontend/README.md). Spanish versions: [backend/README.es.md](backend/README.es.md) and [frontend/README.es.md](frontend/README.es.md).

## Requirements

- Windows 10/11 x64.
- Java Development Kit 21 and Apache Maven 3.9+ to build the backend.
- Node.js 20+ and npm to develop or package the frontend.
- Internet access only for the TikTok LIVE connection. Speech synthesis is fully local through Piper.

The packaged installer includes a reduced Java 21 runtime, so Java is not required on the target computer.

## Quick start for development

Build the backend first:

```bat
cd backend
build.bat
```

Run the offline local test interface:

```bat
cd frontend
npm install
test-ui.bat
```

For a real LIVE, use `run.bat` after the backend JAR has been built and enter the TikTok `uniqueId` without `@`.

## Production build

```bat
cd backend
build.bat

cd ..\frontend
prepare-runtime.bat
npm run dist-win
```

Outputs:

- `backend/dist/live-chat-tts.jar`: shaded backend JAR with TikTokLiveJava, JLayer and the bundled gift-alert MP3.
- `frontend/dist/Live-Chat-TTS-Setup-<version>.exe`: Windows NSIS installer containing Electron, the JAR and the reduced Java runtime.

The installer bundles Piper's native Windows executable, required DLLs and both local voice models (`es_MX-claude-high` and `en_US-hfc_female-medium`). Piper is the only documented speech engine and the Spanish voice is selected by default.

## Security, privacy and resource controls

- The backend binds its HTTP API to `127.0.0.1`; it is not a public listener.
- Electron uses context isolation, disabled Node integration, sandboxing and an allowlisted IPC bridge.
- A temporary per-run token protects the local API when launched by Electron.
- No credentials, cookies or tokens are hard-coded.
- The queue is bounded, speech is sequential, and global/per-author sliding-window limits protect CPU, memory and audio resources.
- Gifts with a quantity of 10 or more are spoken, then replay a locally cached MP3 cue; smaller gifts, follows, subscriptions and LIVE status events do not play the cue. Event wording follows the selected voice language (`dice`/`says`).
- Piper keeps its selected ONNX model loaded in one local worker process; it does not call a cloud TTS service.
- Text is sanitized and length-limited; chat history is kept in memory only and capped at 100 entries.
- Settings are stored locally under `%LOCALAPPDATA%\\LiveChatTTS` by default.

This protects the local application from accidental exposure and overload; it cannot guarantee that TikTok will always permit an unofficial client connection.

## Limitations and responsible use

The integration depends on TikTokLiveJava and may require updates if TikTok changes its protocols. The current production target is Windows; the `Platform` domain variable leaves room for future macOS/Linux adapters. Offensive-language filtering is not enabled by default and can be added as a local moderation policy before queue submission.

## License and third-party notices

This project is released under the [MIT License](LICENSE). A Spanish convenience translation is available in [LICENSE.es.md](LICENSE.es.md). Electron, Java, Piper, its voice models and TikTokLiveJava remain subject to their respective licenses. Retain third-party notices before redistribution.
