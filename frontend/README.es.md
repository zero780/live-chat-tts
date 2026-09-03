# Live Chat TTS - Frontend de escritorio

<p align="center"><img src="src/assets/livechattts-logo.png" width="180" alt="Logo Live Chat TTS" /></p>

[![English](https://img.shields.io/badge/README-English-1f6feb?style=for-the-badge)](README.md)
[![Electron](https://img.shields.io/badge/Electron-44.1.1-47848f?style=flat&logo=electron&logoColor=white)](https://www.electronjs.org/)
[![Node.js](https://img.shields.io/badge/Node.js-20%2B-339933?style=flat&logo=node.js&logoColor=white)](https://nodejs.org/)
[![Piper](https://img.shields.io/badge/Piper_TTS-es_MX--claude--high-7b4bb7?style=flat)](https://github.com/OHF-Voice/piper1-gpl)
[![TikTokLiveJava](https://img.shields.io/badge/TikTokLiveJava-1.11.0-ff0050?style=flat)](https://github.com/jwdeveloper/TikTokLiveJava)
[![Windows](https://img.shields.io/badge/Objetivo-Windows-0078d4?style=flat&logo=windows)](#requisitos)
[![Builder](https://img.shields.io/badge/Empaquetado-Electron%20Builder-6f42c1?style=flat)](#compilación-de-producción)

Cliente de escritorio Electron compacto para el backend Java local. Se conecta a un LIVE de TikTok por usuario, muestra conexión y cola, entrega los comentarios al backend y visualiza cuándo el motor local SAPI o Piper seleccionado está hablando.

## Arquitectura

```mermaid
graph LR
  R[Renderer: HTML/CSS/JS] -->|contextBridge restringido| P[Preload: IPC permitido]
  P --> M[Proceso principal]
  M -->|127.0.0.1 + token temporal| J[Backend Java 21]
  J --> T[TikTokLiveJava]
  J --> S[Windows SAPI o Piper local]
```

### Límites de seguridad

- `contextIsolation` activado, `nodeIntegration` desactivado y renderer en sandbox.
- El renderer no tiene acceso al sistema de archivos, procesos, Java ni token.
- `preload.cjs` expone únicamente operaciones IPC explícitas.
- Electron inicia Java en un puerto loopback aleatorio con token por ejecución.
- Se bloquean navegación externa y apertura de ventanas.

## Funcionalidades

- Campo `uniqueId` de TikTok con estado de conexión y desconexión.
- Modo de prueba local con botón **Probar voz**.
- Selección de voz SAPI, velocidad y salida de audio predeterminada de Windows cuando SAPI está seleccionado.
- Soporte para Piper local mediante el modelo `es_MX-claude-high`; Piper se mantiene completamente local.
- Logo de voz animado mientras la cola reproduce mensajes.
- Cola acotada y límites de recursos visibles como métricas.
- Lista de actividad en orden reciente primero que conserva hasta 100 eventos de chat y del LIVE, incluidos los pendientes y el que se está reproduciendo.
- Los regalos y donaciones se leen primero y después activan la alerta MP3 local del backend; follows, suscripciones y eventos del ciclo de vida del LIVE no la activan.
- Ajustes persistidos localmente por el backend; la lista de actividad solo vive en memoria.

## Requisitos

- Windows 10/11 x64.
- Node.js 20 o superior y npm para desarrollo.
- JAR de producción en `../backend/dist/live-chat-tts.jar`.
- Java no es necesario en la computadora destino al usar el instalador: se incluye un runtime Java 21 reducido en `resources/jre`.

## Desarrollo

Instala las dependencias en el `node_modules` local del proyecto:

```bat
cd path\to\live-chat-tts\frontend
npm install
```

Prueba offline la interfaz y SAPI:

```bat
test-ui.bat
```

Ejecuta el modo real de TikTok:

```bat
run.bat
```

El frontend usa `TIKTOK_LIVE_JAVA` por defecto; `test-ui.bat` lo cambia a `LOCAL_TEST`.

### Usar Piper localmente

Después de ejecutar `backend\\setup-piper.bat`, inicia la misma interfaz con la voz local Piper `es_MX-claude-high`:

```bat
test-piper-ui.bat
```

Este script establece explícitamente `TTS_ENGINE=PIPER`. Piper también es el motor predeterminado cuando `TTS_ENGINE` no se define; usa `TTS_ENGINE=SAPI` solo para el respaldo.

## Compilación de producción

Primero genera el JAR desde `backend\\` usando `build.bat`. Después crea el runtime Java y el instalador Windows:

```bat
cd path\to\live-chat-tts\frontend
prepare-runtime.bat
npm run dist-win
```

Electron Builder usa `asar`, `extraResources` y destino NSIS. El instalador incluye:

- Archivos de la aplicación Electron.
- `backend/live-chat-tts.jar` fuera de `app.asar`.
- `jre/` con el runtime Java 21 reducido.

El instalador incluye el runtime Python de Piper y el modelo de voz `es_MX-claude-high`, por lo que el equipo destino no necesita instalar Piper por separado.

Por defecto, el instalador queda en `dist\\`.

## Estructura del proyecto

```text
frontend/
├── src/
│   ├── assets/                 # Logo de marca
│   ├── main.cjs                # Proceso principal y ciclo de Java
│   ├── preload.cjs             # Puente IPC restringido
│   └── renderer/               # Interfaz, estilos y consulta de estado
├── prepare-runtime.bat         # Crea el runtime Java 21 incluido
├── run.bat                     # Inicia modo TikTok
└── test-ui.bat                 # Inicia modo offline LOCAL_TEST
```

## Solución de problemas

- `No se encontró el JAR`: genera primero `backend\\dist\\live-chat-tts.jar`.
- No aparecen voces: comprueba las voces SAPI de Windows y ejecuta el frontend en Windows.
- Error de conexión: verifica que la cuenta esté LIVE y revisa el estado del backend; TikTokLiveJava es una integración no oficial y su comportamiento puede cambiar.
- Si una instancia anterior bloquea la salida, ciérrala y vuelve a ejecutar `npm run dist-win`.

## Licencia y avisos de terceros

El frontend es software de escritorio local. Electron y Electron Builder conservan sus respectivas licencias. TikTokLiveJava es una dependencia MIT independiente incluida en el JAR del backend. Piper y su modelo de voz conservan sus licencias upstream.
