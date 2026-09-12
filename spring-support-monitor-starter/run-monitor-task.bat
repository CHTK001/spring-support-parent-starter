@echo off
cd /d D:\ch\project\spring-support-parent-starter\spring-support-monitor-starter
"C:\Program Files\Amazon Corretto\jdk25.0.3_9\bin\java.exe" -cp target/classes;target/dependency/* com.chua.starter.monitor.support.MonitorApplication --server.port=8081 >> backend-task.log 2>&1