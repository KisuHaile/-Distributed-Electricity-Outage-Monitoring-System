@echo off
echo Compiling Network Test...
javac -cp "lib\mysql-connector-j-9.2.0.jar" -sourcepath "src\main\java" -d "bin" -encoding UTF-8 "src\main\java\com\electricity\test\NetworkTest.java"

if %ERRORLEVEL% NEQ 0 (
    echo Compilation Failed!
    pause
    exit /b 1
)

echo.
echo Running Network Simulation Test...
echo.
java -cp "bin;lib\mysql-connector-j-9.2.0.jar" com.electricity.test.NetworkTest

echo.
pause
