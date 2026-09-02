@echo off
setlocal
call "%~dp0build.bat"
if errorlevel 1 exit /b %errorlevel%
set "LIVE_SOURCE=LOCAL_TEST"
set "APP_PORT=8791"
set "APP_DATA_DIR=%TEMP%\live-chat-tts-smoke-test"
java -jar "%~dp0dist\live-chat-tts.jar" --self-test
