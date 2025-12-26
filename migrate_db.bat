@echo off
REM Lab 4: Database Migration for Lamport Clock Support
REM Run this once to add logical_timestamp columns to existing database

echo ========================================
echo   Database Migration
echo   Lab 4: Lamport Clock Support
echo ========================================
echo.

set DB_HOST=localhost

if not "%1"=="" set DB_HOST=%1

echo Migrating database at %DB_HOST%...
echo.

java -cp "bin;lib/*" com.electricity.db.MigrateLamportClock %DB_HOST%

echo.
echo Migration complete!
pause
