@echo off
setlocal
set "PROJECT_ROOT=%~dp0"
set "RUNTIME_ROOT=%PROJECT_ROOT%runtime-java21-final"
set "JDK_HOME=%JAVA_HOME%"
if "%JDK_HOME%"=="" set "JDK_HOME=C:\Program Files\Java\jdk-21.0.11"
if not exist "%JDK_HOME%\bin\jlink.exe" (
  echo No se encontro jlink.exe en "%JDK_HOME%".
  echo Define JAVA_HOME apuntando a un JDK 21 y vuelve a ejecutar.
  exit /b 1
)
if exist "%RUNTIME_ROOT%" rmdir /s /q "%RUNTIME_ROOT%"
"%JDK_HOME%\bin\jlink.exe" --module-path "%JDK_HOME%\jmods" --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.security.jgss,jdk.crypto.ec,jdk.httpserver,jdk.unsupported --strip-debug --no-header-files --no-man-pages --compress=2 --output "%RUNTIME_ROOT%"
if errorlevel 1 exit /b %errorlevel%
echo Runtime Java 21 preparado: %RUNTIME_ROOT%
