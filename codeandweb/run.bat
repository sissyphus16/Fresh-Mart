@echo off
cd /d "%~dp0"
javac -encoding UTF-8 GroceryShop.java
if errorlevel 1 (
  echo Compile failed. Check that a JDK is installed.
  pause
  exit /b
)
start "" cmd /c "timeout /t 3 >nul && start http://localhost:8080"
echo Server running. Close this window to stop it.
java GroceryShop
pause
