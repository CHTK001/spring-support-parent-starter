@echo off
rem 验收服务启动器（计划任务调用，脱离会话进程树）
cd /d "%~dp0"
set /p CP=<target\cp.txt
"C:\Program Files\Amazon Corretto\jdk25.0.3_9\bin\java.exe" -cp "target\classes;target\test-classes;%CP%" com.chua.starter.filesystem.support.server.StartFileStorageServer > fs-accept.log 2> fs-accept.err.log
