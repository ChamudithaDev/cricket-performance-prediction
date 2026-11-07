@echo off
echo === Testing MySQL with empty password ===
mysql -u root -e "SHOW DATABASES;" 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo === Testing MySQL with password 'admin' ===
    mysql -u root -padmin -e "SHOW DATABASES;" 2>&1
)
