@echo off
set "LIB=lib"
set "SRC=src\main\java"
set "BIN=bin"
set "CP=%LIB%\mysql-connector-j-9.2.0.jar;%BIN%;src\main\resources"

echo Compiling Client Simulator (Web Enabled)...
if not exist "%BIN%" mkdir "%BIN%"

REM Compile everything in com.electricity...
javac -cp "%LIB%\mysql-connector-j-9.2.0.jar" -sourcepath "%SRC%" -d "%BIN%" "%SRC%\com\electricity\client\HeadlessClient.java"

if %errorlevel% neq 0 (
    echo Compilation Failed!
    pause
    exit /b
)

echo Starting Client Simulator...
echo Web Dashboard: http://localhost:3002/client/
echo.
REM Arguments: [nodeId] [webPort]
java -cp "%CP%" com.electricity.client.HeadlessClient addis_001 3002
pause
