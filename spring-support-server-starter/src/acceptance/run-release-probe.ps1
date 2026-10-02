# run-release-probe.ps1 -- 独立运行发布脚本回归探针
#
# 为什么不直接用 `mvn test-compile`：
#   spring-support-monitor-starter 的 src/test 里有一批已入库但编译不过的测试
#   （引用了不存在的包），而 `mvn package` 默认会编译整个模块的测试，
#   于是一处坏测试会连带阻断本探针的编译。本脚本改为**只编译探针这一个文件**，
#   与同模块其它测试完全解耦。
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File src\acceptance\run-release-probe.ps1
# 退出码：0=全部断言通过；非 0=有失败或环境不满足
#
# 注意：脚本本身保持纯 ASCII（仓库约定），探针输出的中文不受影响。

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$module = Resolve-Path (Join-Path $here '..\..')
$classes = Join-Path $module 'target\classes'
$probeSrc = Join-Path $module 'src\test\java\com\chua\starter\server\support\probe\ReleaseScriptProbe.java'

Write-Output "module  = $module"
Write-Output "classes = $classes"

if (-not (Test-Path $classes)) {
    Write-Output "ERROR: $classes 不存在。先执行："
    Write-Output "  mvn -o -pl spring-support-server-starter -Dmaven.test.skip=true compile"
    exit 2
}
if (-not (Test-Path $probeSrc)) {
    Write-Output "ERROR: 探针源码不存在：$probeSrc"
    exit 2
}

$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    Write-Output "ERROR: 未设置 JAVA_HOME"
    exit 2
}
$javac = Join-Path $javaHome 'bin\javac.exe'
$java = Join-Path $javaHome 'bin\java.exe'
foreach ($t in @($javac, $java)) {
    if (-not (Test-Path $t)) { Write-Output "ERROR: 找不到 $t"; exit 2 }
}

$out = Join-Path $env:TEMP 'release-probe-classes'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null

Write-Output "--- 只编译探针（绕开模块其它测试）---"
& $javac -encoding UTF-8 -d $out -cp $classes $probeSrc
if ($LASTEXITCODE -ne 0) {
    Write-Output "ERROR: 探针编译失败"
    exit 3
}

Write-Output "--- 运行探针 ---"
& $java -cp "$classes;$out" com.chua.starter.server.support.probe.ReleaseScriptProbe
$rc = $LASTEXITCODE
Write-Output "probe exit=$rc"
exit $rc
