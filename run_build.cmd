@echo off
cd /d G:\work\spring-support-parent-starter
if exist BUILD_DONE.txt del BUILD_DONE.txt
call mvn install -o -Dmaven.test.skip=true > mvn.log 2>&1
echo EXIT=%ERRORLEVEL% > BUILD_DONE.txt
