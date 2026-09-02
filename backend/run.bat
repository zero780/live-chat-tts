@echo off
setlocal
set "PROJECT_ROOT=%~dp0"
set "SOURCE_ROOT=%PROJECT_ROOT%src\main\java"
set "OUTPUT_ROOT=%PROJECT_ROOT%out"

if not exist "%OUTPUT_ROOT%" mkdir "%OUTPUT_ROOT%"
dir /s /b "%SOURCE_ROOT%\*.java" > "%OUTPUT_ROOT%\sources.txt"
javac --release 21 -encoding UTF-8 -d "%OUTPUT_ROOT%" @"%OUTPUT_ROOT%\sources.txt"
if errorlevel 1 exit /b %errorlevel%
java -cp "%OUTPUT_ROOT%" com.comext.livechattts.bootstrap.Application
