@echo off
title OmniRush Mobile App Launcher (Expo)
echo ====================================================
echo      OmniRush Mobile App (Expo Metro Bundler)
echo ====================================================
echo.
cd /d "%~dp0cybersecuria-mobile"
echo Starting Expo in cybersecuria-mobile...
echo Scan the QR code using the Expo Go app on your phone!
echo.
npx expo start --tunnel
pause

