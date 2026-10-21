@echo off
cd /d "%~dp0"
java -jar backend\eatpick-api.jar --spring.profiles.active=demo
pause
