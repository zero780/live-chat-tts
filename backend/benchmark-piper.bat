@echo off
setlocal
set "BACKEND_ROOT=%~dp0"
set "PYTHON_RUNTIME=%BACKEND_ROOT%piper-runtime\Scripts\python.exe"
set "MODEL=%BACKEND_ROOT%piper\models\es_MX-claude-high.onnx"

if not exist "%PYTHON_RUNTIME%" (
  echo Piper runtime was not found. Run setup-piper.bat first.
  exit /b 1
)
if not exist "%MODEL%" (
  echo Piper model was not found. Run setup-piper.bat first.
  exit /b 1
)

"%PYTHON_RUNTIME%" "%BACKEND_ROOT%piper\benchmark.py" --model "%MODEL%" --runs 3
