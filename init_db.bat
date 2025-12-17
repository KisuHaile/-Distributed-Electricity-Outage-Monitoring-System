@echo off
set "LIB=lib"
set "SRC=src\main\java"
set "BIN=bin"
set "CP=%LIB%\mysql-connector-j-9.2.0.jar;%BIN%;src\main\resources"

echo Compiling DBInitializer...
if not exist "%BIN%" mkdir "%BIN%"
javac -cp "%LIB%\mysql-connector-j-9.2.0.jar" -sourcepath "%SRC%" -d "%BIN%" "%SRC%\com\electricity\db\DBInitializer.java"

if %errorlevel% neq 0 (
    echo Compilation Failed!
    pause
    exit /b
)

echo Initializing Database...
java -cp "%CP%" com.electricity.db.DBInitializer
pause
