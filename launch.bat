@echo off
setlocal EnableExtensions

cd /d "%~dp0"

echo Starting Codeworks...

set "JAVA_HOME="
if exist "C:\Program Files\Java\jdk-25.0.5.0.3\bin\java.exe" (
  set "JAVA_HOME=C:\Program Files\Java\jdk-25.0.5.0.3"
) else if exist "C:\Program Files\Java\latest\bin\java.exe" (
  set "JAVA_HOME=C:\Program Files\Java\latest"
)

if defined JAVA_HOME (
  set "PATH=%JAVA_HOME%\bin;%PATH%"
  goto run
)

where java >nul 2>&1
if errorlevel 1 (
  echo.
  echo ERROR: Could not find JDK 25.
  echo Tried:
  echo   C:\Program Files\Java\jdk-25.0.5.0.3
  echo   C:\Program Files\Java\latest
  echo   java on PATH
  echo.
  echo Install JDK 25 or add java.exe to your PATH, then try again.
  echo.
  pause
  exit /b 1
)

:run
call gradlew.bat runClient
if errorlevel 1 (
  echo.
  echo Codeworks failed to start. See errors above.
  echo.
  pause
  exit /b 1
)

endlocal
exit /b 0
