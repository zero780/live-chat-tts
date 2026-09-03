# Live Chat TTS - Backend

<p align="center"><img src="../frontend/src/assets/livechattts-logo.png" width="180" alt="Live Chat TTS logo" /></p>

[![Español](https://img.shields.io/badge/README-Espa%C3%B1ol-2ea44f?style=for-the-badge)](README.es.md)
[![Java 21](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![TikTokLiveJava](https://img.shields.io/badge/TikTokLiveJava-1.11.0-ff0050?style=flat)](https://github.com/jwdeveloper/TikTokLiveJava)
[![Piper](https://img.shields.io/badge/Piper_TTS-es_MX--claude--high-7b4bb7?style=flat)](https://github.com/OHF-Voice/piper1-gpl)
[![Architecture](https://img.shields.io/badge/Architecture-Hexagonal-6f42c1?style=flat)](#architecture)
[![Platform](https://img.shields.io/badge/Platform-Windows-0078d4?style=flat&logo=windows)](#requirements)

Local Java 21 service that receives TikTok LIVE comments through TikTokLiveJava and reads them with Windows SAPI or local Piper TTS. The service is designed to be launched by the Electron desktop frontend; it binds its HTTP control API to loopback only.

Gift and donation events are spoken first, then play the bundled local MP3 alert before the FIFO queue continues. The alert is decoded once at startup and reused in memory.

> TikTokLiveJava is an unofficial reverse-engineering project. Review its license and TikTok rules before connecting to a real LIVE. This adapter only listens to comments; it does not send chat messages, use cookies, rotate identities/IPs, or implement aggressive reconnect loops.

## Architecture

The code follows hexagonal architecture. Domain and application services do not depend on HTTP, PowerShell or TikTokLiveJava.

```mermaid
graph LR
  UI[Electron frontend] -->|loopback HTTP + token| HTTP[Inbound adapter: LocalHttpServer]
  HTTP --> IN[Input ports / use cases]
  IN --> APP[Application services]
  APP --> Q[Bounded speech queue]
  APP --> LIVE[LiveChatClient port]
  LIVE --> TT[TikTokLiveJava adapter]
  Q --> SAPI[SpeechEngine port]
  SAPI --> PS[Windows SAPI / PowerShell or local Piper]
  APP --> STORE[SettingsStore port]
  STORE --> FILE[Local settings file]
```

### Main layers

| Layer | Responsibility |
| --- | --- |
| `domain` | Platform, live source, connection state and chat message models. |
| `application/port` | Stable inbound and outbound contracts. |
| `application/service` | Connection lifecycle, sanitization, rate limiting, queueing and settings validation. |
| `adapter/in/http` | Strict, local-only JSON API for the desktop UI. |
| `adapter/out/live` | TikTokLiveJava and local test implementations. |
| `adapter/out/windows` | SAPI voices and the Windows default audio output. |
| `adapter/out/piper` | Persistent local Piper worker and PCM playback. |
| `bootstrap` | Environment configuration and dependency wiring. |

## Requirements

- Windows 10/11 x64.
- JDK 21 for building; the packaged desktop installer includes its own reduced Java runtime.
- Apache Maven 3.9+.
- Internet access only while TikTokLiveJava connects to the LIVE; no cloud TTS service is used.

## Configuration

All configuration is supplied through environment variables. No credentials or tokens are stored in source code.

| Variable | Default | Description |
| --- | --- | --- |
| `APP_PLATFORM` | `WINDOWS` | Current supported platform. |
| `LIVE_SOURCE` | `LOCAL_TEST` | `LOCAL_TEST` for offline smoke tests or `TIKTOK_LIVE_JAVA` for real comments. |
| `TTS_ENGINE` | `PIPER` | `PIPER` by default, or `SAPI` as an explicit fallback. |
| `PIPER_PYTHON` | `py` | Python command used by the local Piper worker. |
| `PIPER_WORKER` | `backend/piper/piper_worker.py` | Persistent Piper worker script. |
| `PIPER_MODEL` | `backend/piper/models/es_MX-claude-high.onnx` | Local Piper voice model. |
| `APP_PORT` | `8787` | Loopback HTTP port, 1024-65535. |
| `LOCAL_API_TOKEN` | empty | If set, API calls require `X-Local-Api-Token`. The Electron parent generates it per run. |
| `SPEECH_QUEUE_CAPACITY` | `200` | Pending messages, bounded to protect memory. |
| `APP_DATA_DIR` | `%LOCALAPPDATA%\\LiveChatTTS` | Non-secret settings location. |

## Build and production artifacts

From `backend\\` in a Windows terminal:

```bat
winget install Apache.Maven
```

Open a new terminal after installation and build the self-contained production JAR:

```bat
cd path\to\live-chat-tts\backend
build.bat
```

Output: `dist\\live-chat-tts.jar`. Maven Shade embeds TikTokLiveJava, JLayer and the bundled gift-alert MP3, so no extra `lib` folder is required.

Run the offline smoke test:

```bat
verify-jar.bat
```

Run a real connection manually (the frontend normally starts this for you):

```bat
set LIVE_SOURCE=TIKTOK_LIVE_JAVA
run-jar.bat
```

## Local Piper TTS

Piper is available as a fully local speech engine. Install the isolated runtime and the Mexican Spanish `es_MX-claude-high` voice:

```bat
setup-piper.bat
```

Then launch the Electron UI from `frontend\\` with Piper enabled:

```bat
test-piper-ui.bat
```

The worker loads the ONNX voice once and streams PCM audio to Java. Piper is selected when `TTS_ENGINE` is unset; set `TTS_ENGINE=SAPI` to use the fallback. The production installer bundles the Python runtime and Piper model, while Git ignores local development copies.

Measure synthesis time without audio playback:

```bat
benchmark-piper.bat
```

## Local API

The API is bound to `127.0.0.1` and protected by the temporary token when launched by Electron.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/health` | Basic health check. |
| `GET` | `/api/status` | Connection, queue, speech and diagnostics. |
| `GET` | `/api/voices` | Installed voices exposed by classic Windows SAPI. |
| `GET` | `/api/settings` | Current voice, rate and output settings. |
| `PUT` | `/api/settings` | Update validated speech settings. |
| `POST` | `/api/connect` | Connect `{ "username": "uniqueId" }`. |
| `POST` | `/api/disconnect` | Close the live connection. |
| `POST` | `/api/test/messages` | Inject a local test event only in `LOCAL_TEST`; accepts optional `type` such as `CHAT` or `GIFT`. |

## Resource and security controls

- FIFO queue with configurable bounded capacity.
- Single sequential speech worker for the selected SAPI or Piper engine; no unbounded parallel speech processes.
- Gift alerts are decoded once from the bundled MP3 and replayed as cached PCM by that same worker.
- Global and per-author sliding-window rate limits.
- Message size limits, strict JSON parsing and text sanitization.
- Loopback binding, temporary API token and no CORS/public listener.
- Chat content is not persisted; only non-secret speech settings are stored.
- Diagnostics expose sanitized errors and counters, not tokens or chat history.

## License and third-party notices

The project source is maintained in this workspace. TikTokLiveJava is a separate MIT-licensed dependency; retain its notices when redistributing the production JAR and review the current upstream terms.

The backend also includes JLayer under LGPL and the project-supplied gift alert MP3. Piper and its voice model have their own upstream licenses. Read [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) before redistribution.
