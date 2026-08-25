@echo off
rem ============================================================
rem FileStorage 文件存储服务启动脚本
rem 用法: run-filestorage.bat [端口]（默认 18080）
rem 存储: local -> %TEMP%\filesystem-local-test\demo
rem 接口: http://127.0.0.1:%PORT%/local/{key}
rem ============================================================
chcp 65001 >nul
title FileStorage Server

set PORT=18080
if not "%~1"=="" set PORT=%~1

java @Z:\temp\opencode\fs-cp-only.txt com.chua.starter.filesystem.support.server.StartFileStorageServer %PORT%
pause
