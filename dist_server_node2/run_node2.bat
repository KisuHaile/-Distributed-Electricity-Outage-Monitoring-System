@echo off 
echo ========================================== 
echo       SMART GRID - SECONDARY SERVER 
echo       (Independent Database Mode) 
echo ========================================== 
echo NOTE: This server uses its own local MySQL. 
echo. 
echo OPTIONAL: If auto-discovery fails, you can enter the Primary Server IP. 
echo Just press Enter to use [AUTO-DISCOVERY]. 
set /p primaryIP="Primary Server IP (Leave blank for Auto): " 
echo. 
set PEERS=auto 
if not "%primaryIP%"=="" set PEERS=1:%primaryIP%:9000 
 
echo Starting Server on Port 9001, Web 3001... 
echo Sync Mode: %PEERS% 
java -cp "lib\mysql-connector-j-9.2.0.jar;app;resources" com.electricity.server.HeadlessServer 2 9001 3001 "%PEERS%" "localhost" 
pause 
