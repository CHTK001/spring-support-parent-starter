@echo off
netstat -ano | findstr ":8086" | findstr "LISTENING" >nul 2>&1
if %errorlevel% neq 0 (
  cd /d "D:\ch\project\spring-support-parent-starter\spring-support-hotsport-starter"
  start /b "" java -jar target\spring-support-hotsport-starter-4.0.0.42.jar >> be-out.log 2>&1
)
