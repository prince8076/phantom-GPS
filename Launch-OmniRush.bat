@echo off
title OmniRush GeoIP Chameleon Launcher
echo ====================================================
echo   OmniRush GeoIP Chameleon (Real IP & GPS Rotator)
echo ====================================================
echo.
echo Starting Desktop Web Control server...
start /b node "%~dp0desktop-app\server.js"
timeout /t 2 >nul
echo.
echo Opening OmniRush Dashboard in your browser...
start http://127.0.0.1:3300
echo.
echo Dashboard is now running at:
echo   - Laptop: http://127.0.0.1:3300
echo   - Mobile: Check dashboard header for your phone link!
echo.
pause

