@echo off
set "LIB=lib"
set "SRC=src\main\java"
set "BIN=bin"
set "CP=%LIB%\mysql-connector-j-9.2.0.jar;%BIN%;src\main\resources"

echo Compiling Server 2...
if not exist "%BIN%" mkdir "%BIN%"

javac -cp "%LIB%\mysql-connector-j-9.2.0.jar" -sourcepath "%SRC%" -d "%BIN%" "%SRC%\com\electricity\server\HeadlessServer.java"

if %errorlevel% neq 0 (
    echo Compilation Failed!
    pause
    exit /b
)

echo Starting Secondary Server (Node 2)...
echo Web Dashboard: http://localhost:3001
echo.

REM Arguments: [id] [port] [webPort] [peers] [dbHost]
java -cp "%CP%" com.electricity.server.HeadlessServer 2 9001 3001 "none" "localhost"
pause
