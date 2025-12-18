@echo off
set "LIB=lib"
set "SRC=src\main\java"
set "BIN=bin"
set "CP=%LIB%\mysql-connector-j-9.2.0.jar;%BIN%;src\main\resources"

echo Compiling Server (Web Enabled)...
if not exist "%BIN%" mkdir "%BIN%"

REM Compile everything in com.electricity...
javac -cp "%LIB%\mysql-connector-j-9.2.0.jar" -sourcepath "%SRC%" -d "%BIN%" "%SRC%\com\electricity\server\HeadlessServer.java"

if %errorlevel% neq 0 (
    echo Compilation Failed!
    pause
    exit /b
)

echo Starting Web Server...
echo Open Browser: http://localhost:3000
echo (This Console must stay open)
echo.

REM Arguments: [id] [port] [webPort] [peers] [dbHost]
REM peerConfig = auto (recommended)
java -cp "%CP%" com.electricity.server.HeadlessServer 1 9000 3000 "auto" "localhost"
pause
