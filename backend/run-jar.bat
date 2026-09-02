@echo off
setlocal
set "PROJECT_ROOT=%~dp0"
set "APP_JAR=%PROJECT_ROOT%dist\live-chat-tts.jar"
if not exist "%APP_JAR%" (
  echo No existe el JAR. Ejecuta build.bat primero.
  exit /b 1
)
java -jar "%APP_JAR%" %*
