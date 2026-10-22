@echo off
title Phantom GPS - Start All Servers
echo =====================================================
echo        STARTING PHANTOM GPS FULL SERVER STACK
echo =====================================================
powershell -ExecutionPolicy Bypass -File "%~dp0start-all-servers.ps1"
pause

