@echo off
set "LIB=lib"
set "SRC=src\main\java"
set "BIN=bin"
set "CP=%LIB%\mysql-connector-j-9.2.0.jar;%BIN%;src\main\resources"

echo Starting Secondary Server (Node 2)...
echo Web Dashboard: http://localhost:3001
echo.

REM Arguments: [id] [port] [webPort] [peers] [dbHost]
java -cp "%CP%" com.electricity.server.HeadlessServer 2 8081 3001 "1:localhost:8080" "localhost"
pause
