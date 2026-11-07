@echo off
echo ============================================================
echo  CricketAI Backend — Startup and Auth Test Script
echo ============================================================

echo.
echo [1/4] Checking MySQL connection...
mysql -u root -e "SELECT 1;" >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo ERROR: MySQL is not running or root has no empty-password access.
    echo Please start MySQL and re-run this script.
    pause
    exit /b 1
)
echo OK: MySQL is running.

echo.
echo [2/4] Creating database if not exists...
mysql -u root -e "CREATE DATABASE IF NOT EXISTS cricket_prediction_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
IF %ERRORLEVEL% NEQ 0 (
    echo ERROR: Could not create database.
    pause
    exit /b 1
)
echo OK: cricket_prediction_db ready.

echo.
echo [3/4] Starting Spring Boot backend in background...
start "CricketAI Backend" cmd /c "mvnw.cmd spring-boot:run"
echo Waiting 30 seconds for Spring Boot to start...
timeout /t 30 /nobreak >nul

echo.
echo [4/4] Running HTTP tests with curl...

echo.
echo --- TEST 1: Register new user ---
curl -s -X POST http://localhost:8080/api/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"fullName\":\"Test User\",\"email\":\"testuser@cricketai.com\",\"password\":\"TestPassword123\"}"

echo.
echo.
echo --- TEST 2: Login with same user ---
curl -s -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"testuser@cricketai.com\",\"password\":\"TestPassword123\"}"

echo.
echo.
echo --- TEST 3: Duplicate registration (should fail) ---
curl -s -X POST http://localhost:8080/api/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"fullName\":\"Test User\",\"email\":\"testuser@cricketai.com\",\"password\":\"TestPassword123\"}"

echo.
echo.
echo --- TEST 4: Protected endpoint without token (should be 401/403) ---
curl -s -o nul -w "HTTP Status: %%{http_code}" http://localhost:8080/api/players

echo.
echo.
echo ============================================================
echo Done. Check the output above for test results.
echo ============================================================
pause
