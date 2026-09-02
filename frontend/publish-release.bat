@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0publish-release.ps1" %*
if errorlevel 1 (
  echo Release publication failed.
  exit /b %errorlevel%
)
echo Release publication completed.
