@echo off
cd /d G:\work\spring-support-parent-starter\spring-support-ai-starter
java -jar target\spring-support-ai-starter-4.0.0.42.jar --mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.stdout.StdOutImpl > Z:\temp\opencode\backend.log 2>&1
