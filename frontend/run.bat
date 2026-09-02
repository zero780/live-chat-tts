@echo off
setlocal
set "PORTABLE_NODE=%~dp0.tools\node-v24.20.0-win-x64"
if exist "%PORTABLE_NODE%\node.exe" set "PATH=%PORTABLE_NODE%;%PATH%"
set "ELECTRON_RUN_AS_NODE="
where npm >nul 2>nul
if errorlevel 1 (
  echo Node.js y npm no estan instalados. Instala Node.js LTS y ejecuta este archivo de nuevo.
  exit /b 1
)
if not exist node_modules call npm install
call npm run start
