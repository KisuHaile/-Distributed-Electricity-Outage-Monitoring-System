@echo off
set "LIB=lib"
set "SRC=src\main\java"
set "BIN=bin_node2"
set "CP=%LIB%\mysql-connector-j-9.2.0.jar;%BIN%;src\main\resources"

echo ==========================================
echo    SMART GRID - SECONDARY SERVER (LOCAL)
echo    (Running from same source as Node 1)
echo ==========================================

echo Compiling Server for Node 2...
if not exist "%BIN%" mkdir "%BIN%"

REM Compile everything in com.electricity...
javac -cp "%LIB%\mysql-connector-j-9.2.0.jar" -sourcepath "%SRC%" -d "%BIN%" "%SRC%\com\electricity\server\HeadlessServer.java"

if %errorlevel% neq 0 (
    echo Compilation Failed!
    pause
    exit /b
)

echo Starting Secondary Server Locally...
echo Open Browser: http://localhost:3001
echo (Port: 9001, WebPort: 3001)
echo.

REM Arguments: [id] [port] [webPort] [peers] [dbHost]
REM peerConfig = auto (will search for Port 9000)
java -cp "%CP%" com.electricity.server.HeadlessServer 2 9001 3001 "auto" "localhost"
pause
