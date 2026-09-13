@echo off
rem 文件存储验收服务启动器（计划任务 FileStorageAccept 专用，勿删）
rem 使用 Java @argfile 承载完整 classpath，规避 set/p 单行 1024 字符截断导致 SPI jar 丢失
cd /d "%~dp0"
"C:\Program Files\Amazon Corretto\jdk25.0.3_9\bin\java.exe" @target\cp.args > fs-accept.log 2> fs-accept.err.log
