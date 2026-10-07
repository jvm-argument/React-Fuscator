@echo off
cd /d "%~dp0"
if not exist "dist\React-Fuscator.jar" (
  echo Run scripts\Build.ps1 first.
  pause
  exit /b 1
)
start "" /b javaw -jar "%~dp0dist\React-Fuscator.jar" gui
