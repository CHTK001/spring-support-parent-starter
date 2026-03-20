param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Args
)

$candidates = @(
    'C:\Program Files\Amazon Corretto\jdk25.0.1_8',
    'C:\Program Files\Java\jdk-25',
    'C:\Users\yemen\.jdks\ms-25.0.0',
    'C:\Users\yemen\.jdks\openjdk-25'
)

$javaHome = $candidates | Where-Object { Test-Path (Join-Path $_ 'bin\java.exe') } | Select-Object -First 1
if (-not $javaHome) {
    throw '未找到可用的 JDK 25，请先安装并更新 scripts/mvn-java25.ps1 中的候选路径。'
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

Write-Host "JAVA_HOME=$javaHome"
& mvn @Args
exit $LASTEXITCODE
