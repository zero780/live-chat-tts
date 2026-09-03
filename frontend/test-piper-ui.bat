@echo off
setlocal
for %%I in ("%~dp0..") do set "PROJECT_ROOT=%%~fI"
set "TTS_ENGINE=PIPER"
set "PIPER_PYTHON=%PROJECT_ROOT%\backend\piper-runtime\Scripts\python.exe"
set "PIPER_WORKER=%PROJECT_ROOT%\backend\piper\piper_worker.py"
set "PIPER_MODEL=%PROJECT_ROOT%\backend\piper\models\es_MX-claude-high.onnx"
call "%~dp0run.bat"
