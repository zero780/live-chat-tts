@echo off
setlocal
call mvn.cmd -DskipTests clean package
if errorlevel 1 exit /b %errorlevel%
if not exist dist mkdir dist
copy /y target\live-chat-tts-0.1.0.jar dist\live-chat-tts.jar > nul
echo JAR creado: dist\live-chat-tts.jar
