@echo off
setlocal
set "BACKEND_ROOT=%~dp0"
set "PYTHON_RUNTIME=%BACKEND_ROOT%piper-runtime\Scripts\python.exe"

py -3.9 -m venv "%BACKEND_ROOT%piper-runtime"
if errorlevel 1 exit /b %errorlevel%

"%PYTHON_RUNTIME%" -m pip install --upgrade pip
if errorlevel 1 exit /b %errorlevel%

"%PYTHON_RUNTIME%" -m pip install piper-tts
if errorlevel 1 exit /b %errorlevel%

"%PYTHON_RUNTIME%" -m piper.download_voices --download-dir "%BACKEND_ROOT%piper\models" es_MX-claude-high
if errorlevel 1 exit /b %errorlevel%

echo Piper local is ready with es_MX-claude-high.
