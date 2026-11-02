@echo off
cd /d G:\work\spring-support-parent-starter
if exist BUILD_DONE.txt del BUILD_DONE.txt
call mvn install -Dmaven.test.skip=true -rf :spring-support-aliyun-starter > mvn.log 2>&1
echo EXIT=%ERRORLEVEL% > BUILD_DONE.txt
