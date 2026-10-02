# win-release-selfcheck.ps1 -- Windows 发布逻辑真机自检（不依赖平台 exec、不依赖 java）
#
# 背景：
#   平台到 win101 的 exec 通道整机返回 "Access is denied."，无法经平台做真机验收。
#   本脚本把「平台将会下发的什么」原样取出（ReleaseScriptPrinter），在控制台真机执行，
#   验证本次改动的两处 Windows 独有逻辑：
#     T2 孤儿回收：app.pid 陈旧时，仍能把「命令行匹配本项目主入口」的残留进程全部清掉
#     T3 端口归属探活：端口只有被 app.pid 指向的进程监听才算健康；被无关进程占住必须判不健康
#
# 两条刻意设计：
#   1) 夹具不用 java：Windows 启动脚本是 `cmd /c java -jar ...`，WMI 子进程继承的是
#      **机器/用户**环境，若 java 不在机器级 PATH 上就会静默退出（实测本机即如此）。
#      为让自检可移植，T2 用 ping.exe 的副本、T3 用 PowerShell TcpListener，都不依赖 java。
#   2) 执行生成的脚本一律走 `powershell -EncodedCommand`（UTF-16LE base64）——这正是
#      平台的下发方式，可完全规避「Windows PowerShell 5.1 按 GBK 读无 BOM 脚本导致
#      中文注释破坏语法」的问题（仓库已记录过同类事故）。
#
# 用法：
#   pwsh -File src\acceptance\win-release-selfcheck.ps1 [-Port 18091] [-Keep]
#
# 退出码：0=全通过；1=有失败；2=环境不满足

param(
    [int]$Port = 18091,
    [switch]$Keep
)

$ErrorActionPreference = 'Continue'
$script:pass = 0
$script:fail = 0

function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { $script:pass++; Write-Output ("[selfcheck] PASS " + $name) }
    else { $script:fail++; Write-Output ("[selfcheck] FAIL " + $name + "  <<< " + $detail) }
}

# 按平台的派发方式执行一段 PowerShell：EncodedCommand（UTF-16LE base64）
# 经 cmd 重定向到文件再读：直接用 `& powershell ...` 捕获输出时，
# 在「标准输出已被上层重定向」的调用上下文里会报
# "StandardOutputEncoding is only supported when standard output is redirected"，
# 导致脚本根本没执行（实测踩到，表现为 T2/T3 全挂）。
function InvokeEncoded([string]$script) {
    $b64 = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($script))
    $out = Join-Path $env:TEMP ('enc-' + [guid]::NewGuid().ToString('N') + '.out')
    cmd /c "powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand $b64 > `"$out`" 2>&1"
    $t = ''
    if (Test-Path $out) {
        $t = (Get-Content $out -Raw -ErrorAction SilentlyContinue)
        Remove-Item $out -Force -ErrorAction SilentlyContinue
    }
    return $t
}

function CountByPattern([string]$pat) {
    return @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -match $pat }).Count
}

Write-Output "=== 0) 前置检查 ==="
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$module = Resolve-Path (Join-Path $here '..\..')
$classes = Join-Path $module 'target\classes'
if (-not (Test-Path $classes)) {
    Write-Output ("ERROR: " + $classes + " 不存在，请先 mvn -o -pl spring-support-server-starter -Dmaven.test.skip=true compile")
    exit 2
}
$javaHome = $env:JAVA_HOME
if (-not $javaHome) { Write-Output "ERROR: 未设置 JAVA_HOME（仅用于编译生成器）"; exit 2 }
$javac = Join-Path $javaHome 'bin\javac.exe'
$java = Join-Path $javaHome 'bin\java.exe'
foreach ($t in @($javac, $java)) { if (-not (Test-Path $t)) { Write-Output ("ERROR: 找不到 " + $t); exit 2 } }

$cimOk = $true
try { $null = Get-CimInstance Win32_Process -ErrorAction Stop } catch { $cimOk = $false }
Write-Output ("  CIM 可用 = " + $cimOk)
if (-not $cimOk) {
    Write-Output "  提示：CIM 不可用时孤儿回收会静默降级（这是产品内的既定降级路径），但本自检无法验证 T2"
}

$work = Join-Path $env:TEMP 'rel-selfcheck'
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Item -ItemType Directory -Force -Path $work | Out-Null
$proj = Join-Path $work 'proj'
New-Item -ItemType Directory -Force -Path $proj | Out-Null
# 唯一入口名：孤儿回收按命令行匹配进程，固定名可能误伤其它会话中命令行恰含同名字符串的进程
$entry = 'relcheck-' + $PID + '.exe'
$log = Join-Path $proj 'app.log'

# ---------- 取出平台同款脚本 ----------
Write-Output "=== 1) 生成平台同款脚本（ReleaseScriptPrinter）==="
$genOut = Join-Path $env:TEMP 'release-probe-classes'
New-Item -ItemType Directory -Force -Path $genOut | Out-Null
$genSrc = Join-Path $module 'src\test\java\com\chua\starter\server\support\probe\ReleaseScriptPrinter.java'
& $javac -encoding UTF-8 -d $genOut -cp $classes $genSrc
if ($LASTEXITCODE -ne 0) { Write-Output "ERROR: 生成器编译失败"; exit 2 }
$raw = & $java -cp "$classes;$genOut" com.chua.starter.server.support.probe.ReleaseScriptPrinter windows springboot $entry $proj $log demo $Port
$txt = $raw -join "`n"
function Section([string]$n) {
    $m = [regex]::Match($txt, "(?s)===" + $n + "===\r?\n(.*?)(?=\r?\n===[A-Z]+===|\z)")
    if ($m.Success) { return $m.Groups[1].Value } else { return "" }
}
$stopScript = Section 'STOP'
$lportScript = Section 'LPORT'
Check "1.1 取到 STOP" ($stopScript.Length -gt 0) "empty"
Check "1.2 停止段含 CIM 命令行回收（孤儿兜底）" ($stopScript -match 'Get-CimInstance Win32_Process') $stopScript
Check "1.3 停止段用 -match 而非 -like（旧写法会匹配到脚本自身并自杀）" (($stopScript -match '-match') -and ($stopScript -notmatch '-like')) $stopScript
Check "1.4 端口探活函数已抽出（与 Posix 的 listening_port 对称）" ($lportScript -match 'function listening_port') "empty"
# 敏感性对照：把函数体里的 pid 归属判断去掉，必须不再满足上述形状
Check "1.5 敏感性对照：探活函数确实含 pid 归属比对" ($lportScript -match 'app\.pid') $lportScript

# ---------- T2 孤儿回收 ----------
Write-Output "=== T2 孤儿回收（app.pid 陈旧 + 两个命令行匹配的残留进程）==="
if (-not $cimOk) {
    Write-Output "[selfcheck] SKIP T2（CIM 不可用）"
} else {
    # 用 ping.exe 的副本当「应用」：命令行里会带 <entry> 名字，且不依赖 java
    $pingSrc = Join-Path $env:SystemRoot 'System32\ping.exe'
    if (-not (Test-Path $pingSrc)) { Write-Output ("ERROR: 找不到 " + $pingSrc); exit 2 }
    $fake1 = Join-Path $proj $entry
    Copy-Item $pingSrc $fake1 -Force
    # 两个残留实例：命令行里都带 <entry>
    $a = Start-Process -FilePath $fake1 -ArgumentList '-t', '127.0.0.1' -PassThru -WindowStyle Hidden
    $b = Start-Process -FilePath $fake1 -ArgumentList '-t', '127.0.0.2' -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 3
    $pat = [regex]::Escape($entry)
    $before = CountByPattern $pat
    Write-Output ("  制造后匹配进程数=" + $before)
    # 关键：写一个必然陈旧的 app.pid，使「按 app.pid 停止」这条路彻底失效
    Set-Content -Path (Join-Path $proj 'app.pid') -Value '999999' -Encoding ASCII
    $out = InvokeEncoded ("Set-Location -LiteralPath '" + $proj + "'; " + $stopScript)
    Write-Output ("  stop 输出: " + ($out -replace "`r?`n", ' | '))
    Start-Sleep -Seconds 3
    $after = CountByPattern $pat
    Check "T2.1 app.pid 陈旧时仍回收全部残留实例（=0）" ($after -eq 0) ("before=" + $before + " after=" + $after)
    # 敏感性对照：清掉停止段里的 CIM 兜底后再跑一次，应留下残留，证明 T2.1 不是恒真
    $noReap = ($stopScript -split "`n" | Where-Object { $_ -notmatch 'Get-CimInstance|taskkill|foreach \(\$op|catch' }) -join "`n"
    $c = Start-Process -FilePath $fake1 -ArgumentList '-t', '127.0.0.3' -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 3
    Set-Content -Path (Join-Path $proj 'app.pid') -Value '999999' -Encoding ASCII
    $null = InvokeEncoded ("Set-Location -LiteralPath '" + $proj + "'; " + $noReap)
    Start-Sleep -Seconds 3
    $leftover = CountByPattern $pat
    Check "T2.2 敏感性对照：去掉兜底后残留仍在（证明 T2.1 有效）" ($leftover -gt 0) ("leftover=" + $leftover)
    # 收尾：用完整停止段清干净
    $null = InvokeEncoded ("Set-Location -LiteralPath '" + $proj + "'; " + $stopScript)
    Start-Sleep -Seconds 2
}

# ---------- T3 端口归属探活 ----------
Write-Output "=== T3 端口归属探活（用 PowerShell TcpListener，不依赖 java）==="
if (-not ($lportScript -match 'function listening_port')) {
    Check "T3.1 探活函数可用" $false "未取到函数"
} else {
    # 起一个监听器；它的命令行不含入口名，但进程号会被写进 app.pid
    $listenCmd = ('$l = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Any, ' + $Port + '); $l.Start(); Start-Sleep -Seconds 120')
    $lp = Start-Process -FilePath 'powershell' -ArgumentList '-NoProfile', '-Command', $listenCmd -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 4
    $listening = [bool](& netstat -ano | Select-String -Pattern (':' + $Port + '\s') | Select-String -Pattern 'LISTENING')
    Write-Output ("  监听器 pid=" + $lp.Id + "  端口已监听=" + $listening)
    Set-Content -Path (Join-Path $proj 'app.pid') -Value ($lp.Id) -Encoding ASCII
    $hit = InvokeEncoded ("Set-Location -LiteralPath '" + $proj + "'; " + $lportScript + "`nif (listening_port " + $Port + ") { 'HIT' } else { 'MISS' }")
    Check "T3.1 端口由 app.pid 指向的进程监听 → HIT" ($hit -match 'HIT') $hit

    # 敏感性对照：把 app.pid 指到无关 pid，必须 MISS（旧的「只看端口有人听」写法会误判 HIT）
    Set-Content -Path (Join-Path $proj 'app.pid') -Value '999999' -Encoding ASCII
    $miss = InvokeEncoded ("Set-Location -LiteralPath '" + $proj + "'; " + $lportScript + "`nif (listening_port " + $Port + ") { 'HIT' } else { 'MISS' }")
    Check "T3.2 敏感性对照：app.pid 指向无关进程 → MISS" ($miss -match 'MISS') $miss

    # 无关端口必须 MISS
    Set-Content -Path (Join-Path $proj 'app.pid') -Value ($lp.Id) -Encoding ASCII
    $miss2 = InvokeEncoded ("Set-Location -LiteralPath '" + $proj + "'; " + $lportScript + "`nif (listening_port 1) { 'HIT' } else { 'MISS' }")
    Check "T3.3 无关端口 → MISS" ($miss2 -match 'MISS') $miss2

    if ($lp -and -not $lp.HasExited) { Stop-Process -Id $lp.Id -Force -ErrorAction SilentlyContinue }
}

if (-not $Keep) {
    if (Test-Path $work) { Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue }
} else {
    Write-Output ("  临时目录保留：" + $work)
}
Write-Output ("[selfcheck] ---- 汇总：passed=" + $script:pass + " failed=" + $script:fail + " ----")
if ($script:fail -gt 0) { exit 1 }
exit 0