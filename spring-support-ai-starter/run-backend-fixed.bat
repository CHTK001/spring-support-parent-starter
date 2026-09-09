@echo off
cd /d D:\ch\project\spring-support-parent-starter\spring-support-ai-starter
java -Xmx1g -Xms256m -jar target\spring-support-ai-starter-4.0.0.42.jar --spring.profiles.active=local --server.port=8787 >> D:\ch\project\backend.log 2>&1