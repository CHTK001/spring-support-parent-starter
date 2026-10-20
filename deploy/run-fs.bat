@echo off
REM ===========================================================================
REM File Storage Server + Monitor Backend 启动脚本 (Windows)
REM 使用紧凑头减少内存: UseCompressedOops + UseCompressedClassPointers
REM ===========================================================================
setlocal enabledelayedexpansion

REM ---- 停止相同项目的旧进程 (8082/18082/29181) ----
for %%P in (8082 18082 29181) do (
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%%P " ^| findstr "LISTEN"') do (
        echo 停止占用端口 %%P 的进程 (PID %%a)
        taskkill /F /PID %%a 2>nul
    )
)

REM ---- JVM 参数 (紧凑头 + 内存优化) ----
set JAVA_OPTS=-server
set JAVA_OPTS=%JAVA_OPTS% -XX:+UseCompressedOops
set JAVA_OPTS=%JAVA_OPTS% -XX:+UseCompressedClassPointers
set JAVA_OPTS=%JAVA_OPTS% -XX:MaxRAMPercentage=75
set JAVA_OPTS=%JAVA_OPTS% -XX:MaxMetaspaceSize=256m
set JAVA_OPTS=%JAVA_OPTS% -Xss256k
set JAVA_OPTS=%JAVA_OPTS% -XX:+UseG1GC
set JAVA_OPTS=%JAVA_OPTS% -Dfile.encoding=UTF-8

REM ---- 数据库 (MySQL，非 H2) ----
if "%SPRING_DATASOURCE_URL%"=="" set SPRING_DATASOURCE_URL=jdbc:mysql://172.16.0.40:3306/report?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true

set FILESTORAGE_PORT=18082
if not "%FILESTORAGE_PORT%"=="" set FILESTORAGE_PORT=%1

REM ---- 启动 ----
for %%f in (spring-support-monitor-starter-*-exec.jar) do (
    set JAR=%%f
    goto :start
)
echo ERROR: 未找到可执行 JAR
exit /b 1

:start
echo 正在启动监控后端 + 文件服务器 (紧凑头模式)...
echo JAR: %JAR%
echo 主端口: 8082
echo 文件服务器端口: %FILESTORAGE_PORT% (CORS 直接访问)
echo 数据库: MySQL (non-H2)

java %JAVA_OPTS% ^
  -Dplugin.filesystem.storage-server.mode=standalone ^
  -Dplugin.filesystem.storage-server.port=%FILESTORAGE_PORT% ^
  -Dspring.datasource.url=%SPRING_DATASOURCE_URL% ^
  -Dspring.datasource.password=%MONITOR_DB_PASSWORD% ^
  -jar %JAR%
