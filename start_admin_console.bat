@echo off
REM Lab 2: Start RMI Admin Console
REM This connects to the RMI server for remote administration

echo ========================================
echo   RMI Admin Console
echo   Lab 2: Remote Method Invocation
echo ========================================
echo.

set SERVER_HOST=localhost
set RMI_PORT=1099

if not "%1"=="" set SERVER_HOST=%1
if not "%2"=="" set RMI_PORT=%2

echo Connecting to RMI server at %SERVER_HOST%:%RMI_PORT%
echo.

REM Compile AdminConsole if needed
if not exist "bin\com\electricity\rmi\AdminConsole.class" (
    echo Compiling Admin Console...
    javac -cp "lib\mysql-connector-j-9.2.0.jar;bin" -sourcepath "src\main\java" -d "bin" "src\main\java\com\electricity\rmi\AdminConsole.java"
    if %errorlevel% neq 0 (
        echo Compilation Failed!
        pause
        exit /b
    )
)

java -cp "bin;lib/*" com.electricity.rmi.AdminConsole %SERVER_HOST% %RMI_PORT%

pause
