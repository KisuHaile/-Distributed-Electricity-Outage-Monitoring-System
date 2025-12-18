@echo off
echo ==================================================
echo   FIXING WINDOWS FIREWALL FOR SMART GRID SYSTEM
echo ==================================================
echo.
echo Requesting Admin Privileges...
fltmc >nul 2>&1 || (
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\getadmin.vbs"
    echo UAC.ShellExecute "%~fs0", "", "", "runas", 1 >> "%temp%\getadmin.vbs"
    "%temp%\getadmin.vbs"
    del "%temp%\getadmin.vbs"
    exit /b
)

echo 1. Allowing TCP Port 9000 (Server Communication)...
netsh advfirewall firewall add rule name="Electricity_Server_TCP" dir=in action=allow protocol=TCP localport=9000

echo 2. Allowing UDP Port 4446 (Auto-Discovery Multicast)...
netsh advfirewall firewall add rule name="Electricity_Discovery_UDP" dir=in action=allow protocol=UDP localport=4446

echo 3. Allowing Ping (ICMPv4)...
netsh advfirewall firewall add rule name="Allow_Ping_ICMP" dir=in action=allow protocol=icmpv4:8,any

echo.
echo ==================================================
echo   SUCCESS! FIREWALL RULES ADDED.
echo   PLEASE RUN THIS SCRIPT ON THE OTHER COMPUTER TOO.
echo ==================================================
pause
