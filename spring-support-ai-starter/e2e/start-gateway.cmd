@echo off
rem AiGateway local E2E launcher — started by Playwright webServer.
rem Blocks in foreground; Playwright kills it after tests finish.
rem
rem Overridable env:
rem   AI_GATEWAY_JAVA      Java 可执行文件（默认 Corretto 25）
rem   AI_GATEWAY_CLASSES   BOOT-INF/classes 目录（默认 ai-exploded）
rem   AI_GATEWAY_LIB       BOOT-INF/lib 目录（默认 ai-exploded）
rem   AI_GATEWAY_H2        H2 jar 路径
rem   AI_GATEWAY_DB        H2 数据库文件基路径（不含 .mv.db）
rem   AI_GATEWAY_SCHEMA    schema.sql 路径
setlocal

set "JAVA_BIN=%AI_GATEWAY_JAVA%"
if "%JAVA_BIN%"=="" set "JAVA_BIN=C:\Program Files\Amazon Corretto\jdk25.0.3_9\bin\java.exe"

set "CLASSES=%AI_GATEWAY_CLASSES%"
if "%CLASSES%"=="" set "CLASSES=C:\Users\Administrator\AppData\Local\Temp\opencode\ai-exploded\BOOT-INF\classes"
set "LIB=%AI_GATEWAY_LIB%"
if "%LIB%"=="" set "LIB=C:\Users\Administrator\AppData\Local\Temp\opencode\ai-exploded\BOOT-INF\lib\*"
set "H2_JAR=%AI_GATEWAY_H2%"
if "%H2_JAR%"=="" set "H2_JAR=C:\Users\Administrator\.m2\repository\com\h2database\h2\2.3.232\h2-2.3.232.jar"
set "DB=%AI_GATEWAY_DB%"
if "%DB%"=="" set "DB=C:/Users/Administrator/AppData/Local/Temp/opencode/h2file/ai"
set "SCHEMA=%AI_GATEWAY_SCHEMA%"
if "%SCHEMA%"=="" set "SCHEMA=C:/Users/Administrator/AppData/Local/Temp/opencode/h2-schema.sql"

set "CP=%CLASSES%;%LIB%;%H2_JAR%"

"%JAVA_BIN%" -Xms512m -Xmx2g -cp "%CP%" com.chua.starter.ai.support.AiStarterApplication ^
  --spring.profiles.active=h2 ^
  --spring.datasource.url=jdbc:h2:file:%DB%;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=KEY,VALUE,DELETE,GROUP ^
  --spring.sql.init.mode=always ^
  --spring.sql.init.encoding=UTF-8 ^
  --spring.sql.init.continue-on-error=true ^
  --spring.sql.init.schema-locations=file:///%SCHEMA%