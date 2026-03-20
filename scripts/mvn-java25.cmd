@echo off
setlocal

set "JAVA25_HOME="
for %%D in (
    "C:\Program Files\Amazon Corretto\jdk25.0.1_8"
    "C:\Program Files\Java\jdk-25"
    "C:\Users\yemen\.jdks\ms-25.0.0"
    "C:\Users\yemen\.jdks\openjdk-25"
) do (
    if exist "%%~D\bin\java.exe" (
        set "JAVA25_HOME=%%~D"
        goto found
    )
)

echo 未找到可用的 JDK 25，请先安装并更新 scripts\mvn-java25.cmd 中的候选路径。
exit /b 1

:found
set "JAVA_HOME=%JAVA25_HOME%"
set "Path=%JAVA_HOME%\bin;%Path%"
echo JAVA_HOME=%JAVA_HOME%
mvn %*
exit /b %ERRORLEVEL%
