@echo off
cd /d "D:\ch\project\spring-support-parent-starter\spring-support-monitor-starter"

echo [1/3] Compiling...
call mvn -q compile -DskipTests
if %ERRORLEVEL% NEQ 0 (echo COMPILE FAILED && exit /b 1)

echo [2/3] Building classpath...
for /f "delims=" %%i in ('mvn -q dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=nul -Dmdep.includeScope=compile -Dmdep.includeArtifactIds="" -Dmdep.outputFile=classpath.txt 2^>nul') do set CP=%%i
set /p CP=<classpath.txt

echo [3/3] Starting MonitorApplication on port 18081...
java -cp "target/classes;%CP%" com.chua.starter.monitor.support.MonitorApplication --server.port=18081 --spring.profiles.active=mysql
