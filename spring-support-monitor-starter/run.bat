@echo off
cd /d "D:\ch\project\spring-support-parent-starter\spring-support-monitor-starter"
mvn -Dspring-boot.run.arguments="--server.port=18081 --spring.profiles.active=mysql" spring-boot:run
