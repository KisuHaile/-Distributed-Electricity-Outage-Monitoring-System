@echo off
echo ==========================================
echo    SMART GRID - FIREWALL CONFIGURATOR
echo    (Run this as ADMINISTRATOR)
echo ==========================================

REM Check for Admin Privileges
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo.
    echo ERROR: You must right-click this file and "Run as administrator".
    echo.
    pause
    exit /b
)

echo [1/3] Opening Server Ports (9000, 9001, 3000, 3001)...
powershell -Command "New-NetFirewallRule -DisplayName 'SmartGrid_TCP' -Direction Inbound -LocalPort 9000,9001,3000,3001 -Protocol TCP -Action Allow -Profile Any -Force" >nul 2>&1

echo [2/3] Opening Auto-Discovery Port (4446 UDP)...
powershell -Command "New-NetFirewallRule -DisplayName 'SmartGrid_UDP' -Direction Inbound -LocalPort 4446 -Protocol UDP -Action Allow -Profile Any -Force" >nul 2>&1

echo [3/3] Allowing ICMP (Ping) requests...
netsh advfirewall firewall add rule name="SmartGrid_Ping" protocol=icmpv4 dir=in action=allow >nul 2>&1

echo.
echo ==========================================
echo SUCCESS: Firewall rules have been added!
echo Your servers can now communicate.
echo ==========================================
pause
