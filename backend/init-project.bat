@echo off
SETLOCAL
cd /d "%~dp0"

echo [1/1] Generating SSL certificates for localhost...
powershell -ExecutionPolicy Bypass -File "scripts/generate-certs.ps1"

echo.
echo ==========================================
echo Project initialization complete!
echo ==========================================
pause