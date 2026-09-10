@echo off
cd /d "D:\ch\project\spring-support-parent-starter\spring-support-monitor-starter"

echo [1/3] Compiling...
call mvn -q compile -DskipTests -o
if %ERRORLEVEL% NEQ 0 (echo COMPILE FAILED && exit /b 1)

echo [2/3] Building classpath...
call mvn -q -o dependency:build-classpath -Dmdep.outputFile=classpath.txt
if not exist classpath.txt (echo CLASSPATH GENERATION FAILED && exit /b 1)

echo [3/3] Starting MonitorApplication on port 8081...
set /p CP=<classpath.txt
set CLASSPATH=target/classes;%CP%
java com.chua.starter.monitor.support.MonitorApplication --server.port=8081
