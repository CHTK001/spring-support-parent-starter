#!/usr/bin/env pwsh
# 服务管理 —— 端到端验收套件（对运行中的后台做黑盒验证）
#
# 与单元测试的分工：单元测试守住纯逻辑接缝；本套件守住「只有跑起来才暴露」的行为
# （协议能否真正实例化、socket 是否真的监听、参数是否真的即时生效、失败是否被诚实上报）。
#
# 用法（凭据全部来自环境变量，不落盘）：
#   $env:MS_HOST   = '192.168.50.198'
#   $env:MS_PORT   = '8112'
#   $env:MS_SSH_USER = 'kali'
#   $env:MS_SSH_PASS = '...'
#   $env:MS_SSH_HOSTKEY = 'ssh-ed25519 255 SHA256:...'   # 必填，避免连到错误主机
#   pwsh -File service-acceptance.ps1
#
# 退出码：0 = 全部通过；1 = 存在失败项。

param(
    [string]$Host_ = $(if ($env:MS_HOST) { $env:MS_HOST } else { '192.168.50.198' }),
    [int]$Port = $(if ($env:MS_PORT) { [int]$env:MS_PORT } else { 8112 }),
    [string]$SshUser = $(if ($env:MS_SSH_USER) { $env:MS_SSH_USER } else { 'kali' }),
    [string]$SshPass = $env:MS_SSH_PASS,
    [string]$SshHostKey = $env:MS_SSH_HOSTKEY
)

$ErrorActionPreference = 'Stop'
$base = "http://${Host_}:$Port"
$plink = 'C:\Program Files\PuTTY\plink.exe'

if (-not $SshPass) { throw '缺少 MS_SSH_PASS 环境变量' }
if (-not $SshHostKey) { throw '缺少 MS_SSH_HOSTKEY 环境变量（用于校验主机密钥，避免连到错误主机）' }
if (-not (Test-Path $plink)) { throw "未找到 plink: $plink" }

$script:results = New-Object System.Collections.Generic.List[object]

function Add-Result {
    param([string]$Name, [bool]$Pass, [string]$Detail)
    $script:results.Add([PSCustomObject]@{ Name = $Name; Pass = $Pass; Detail = $Detail })
    $tag = if ($Pass) { 'PASS' } else { 'FAIL' }
    Write-Host ("  [{0}] {1}{2}" -f $tag, $Name, $(if ($Detail) { "  -- $Detail" } else { '' }))
}

function Get-Api {
    param([string]$Path)
    $r = Invoke-WebRequest -Uri "$base$Path" -UseBasicParsing -TimeoutSec 60
    $ms = New-Object IO.MemoryStream
    $r.RawContentStream.CopyTo($ms)
    [Text.Encoding]::UTF8.GetString($ms.ToArray())
}

function Invoke-Api {
    param([string]$Path, [string]$Method = 'POST', $Body)
    $req = [Net.HttpWebRequest]::Create("$base$Path")
    $req.Method = $Method
    $req.Timeout = 60000
    if ($null -ne $Body) {
        $req.ContentType = 'application/json; charset=utf-8'
        $bytes = [Text.Encoding]::UTF8.GetBytes($Body)
        $req.ContentLength = $bytes.Length
        $rs = $req.GetRequestStream(); $rs.Write($bytes, 0, $bytes.Length); $rs.Close()
    }
    try {
        $resp = $req.GetResponse()
        $ms = New-Object IO.MemoryStream
        $resp.GetResponseStream().CopyTo($ms)
        $code = [int]$resp.StatusCode; $resp.Close()
        return @{ Code = $code; Json = ([Text.Encoding]::UTF8.GetString($ms.ToArray()) | ConvertFrom-Json) }
    } catch {
        $resp = $null; try { $resp = $_.Exception.Response } catch { }
        if ($resp) {
            $sr = New-Object IO.StreamReader($resp.GetResponseStream())
            $text = $sr.ReadToEnd()
            $parsed = $null; try { $parsed = $text | ConvertFrom-Json } catch { }
            return @{ Code = [int]$resp.StatusCode; Json = $parsed }
        }
        throw
    }
}

function Remove-Node {
    param([long]$Id)
    try { $null = Invoke-Api -Path "/ms/monitor/server/$Id" -Method 'DELETE'; return $true } catch { return $false }
}

# 远端：检查一批端口的 TCP/UDP 监听情况（UDP 必须用 -lnupe，-lnt 看不到）
function Get-BoundPorts {
    param([int[]]$Ports)
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(@'
for p in "$@"; do
  t=$(ss -lntpe 2>/dev/null | grep -F ":$p " | head -1)
  u=$(ss -lnupe 2>/dev/null | grep -F ":$p " | head -1)
  if [ -n "$t" ]; then echo "$p TCP"; fi
  if [ -n "$u" ]; then echo "$p UDP"; fi
done
'@))
    $argstr = ($Ports | ForEach-Object { "$_" }) -join ' '
    $out = & $plink -ssh -pw $SshPass -batch -hostkey $SshHostKey "$SshUser@${Host_}" `
        "echo $b64 | base64 -d > /tmp/_acc.sh; sed -i 's/\r$//' /tmp/_acc.sh; bash /tmp/_acc.sh $argstr; rm -f /tmp/_acc.sh" 2>&1
    $set = @{}
    foreach ($line in $out) {
        $parts = ("$line").Trim() -split '\s+'
        if ($parts.Count -ge 2 -and $parts[0] -match '^\d+$') { $set[[int]$parts[0]] = $parts[1] }
    }
    return $set
}

function New-Node {
    param([hashtable]$Spec)
    $json = $Spec | ConvertTo-Json -Depth 8 -Compress
    return Invoke-Api -Path '/ms/monitor/server' -Body $json
}

Write-Host "=== 服务管理验收套件  target=$base ==="

# ---------- 1. file-storage 参数 schema 必须含 layout ----------
Write-Host "`n[1] file-storage 参数 schema"
$filters = (Get-Api '/ms/monitor/server/filters') | ConvertFrom-Json
$fsMeta = @($filters.data) | Where-Object { $_.type -eq 'file-storage' }
$names = @($fsMeta.params | ForEach-Object { $_.name })
Add-Result 'schema 含 layout（目录列表页总开关）' ($names -contains 'layout') ("参数共 " + $names.Count + " 个")
Add-Result 'schema 含 showFormat/showSize/showTime/imageFilter' `
    (('showFormat','showSize','showTime','imageFilter' | Where-Object { $names -notcontains $_ }).Count -eq 0) `
    ($names -join ',')

# ---------- 2. 所有协议：能否真正创建并监听 ----------
Write-Host "`n[2] 协议可用性（创建 + 真实 socket）"
$protos = @((Get-Api '/ms/monitor/server/protocols') | ConvertFrom-Json).data
$port0 = 21000
$created = @()
foreach ($p in $protos) {
    $port0++
    $spec = [ordered]@{
        name = "acc-$($p.alias)"; protocol = $p.alias; engine = $p.defaultEngine
        host = '0.0.0.0'; port = $port0; enabled = 1; remark = 'acceptance'; filters = @()
    }
    if ($p.requiresBackend) {
        if ("$($p.code)" -match 'HTTP') { $spec['config'] = @{ backendHost = '127.0.0.1'; backendPort = $Port } }
        else { $spec['config'] = @{ backendHost = '127.0.0.1'; backendPort = 6379 } }
    }
    $r = New-Node -Spec $spec
    if ($r.Json.success) { $created += [PSCustomObject]@{ Proto = $p; Id = $r.Json.data.id; Port = $port0 } }
    else { Add-Result "协议 $($p.code) 创建" $false "$($r.Json.msg)" }
}
Start-Sleep -Seconds 8
$bound = Get-BoundPorts -Ports $created.Port
$notRunning = @(); $notListening = @()
foreach ($c in $created) {
    $view = (Get-Api "/ms/monitor/server/$($c.Id)") | ConvertFrom-Json
    if ($view.data.status -ne 'RUNNING') { $notRunning += "$($c.Proto.code)($($view.data.status))" }
    if (-not $bound.ContainsKey([int]$c.Port)) { $notListening += $c.Proto.code }
}
Add-Result '全部协议创建成功' ($created.Count -eq $protos.Count) ("$($created.Count)/$($protos.Count)")
Add-Result '全部协议进入 RUNNING' ($notRunning.Count -eq 0) ($notRunning -join ', ')
Add-Result '全部协议端口真实监听' ($notListening.Count -eq 0) ($notListening -join ', ')

# ---------- 3. FTP / FTPS 必须监听配置端口而非默认 21 / 990 ----------
Write-Host "`n[3] FTP / FTPS 端口绑定"
$ftpPorts = @($created | Where-Object { $_.Proto.code -in @('FTP','FTPS') } | ForEach-Object { [int]$_.Port })
if ($ftpPorts.Count -eq 2) {
    $defaults = Get-BoundPorts -Ports @(21, 990)
    Add-Result 'FTP 监听配置端口' ($bound.ContainsKey($ftpPorts[0])) ("端口 " + $ftpPorts[0])
    Add-Result 'FTPS 监听配置端口' ($bound.ContainsKey($ftpPorts[1])) ("端口 " + $ftpPorts[1])
    $leaked = @()
    foreach ($d in @(21, 990)) { if ($defaults.ContainsKey([int]$d)) { $leaked += $d } }
    Add-Result '未占用默认端口 21/990' ($leaked.Count -eq 0) ("被占用: " + ($leaked -join ','))
} else {
    Add-Result 'FTP/FTPS 参与协议扫描' $false '未能在协议列表中同时找到 FTP 与 FTPS'
}
foreach ($c in $created) { $null = Remove-Node -Id $c.Id }
Start-Sleep -Seconds 4

# ---------- 4. 启动失败必须被诚实上报 ----------
Write-Host "`n[4] 创建接口的失败上报"
$conflictPort = 21500
& $plink -ssh -pw $SshPass -batch -hostkey $SshHostKey "$SshUser@${Host_}" `
    "nohup nc -l -p $conflictPort >/dev/null 2>&1 & sleep 1" 2>&1 | Out-Null
$r = New-Node -Spec ([ordered]@{ name = 'acc-conflict'; protocol = 'jdk-tcp'; engine = 'jdk-tcp'; host = '0.0.0.0'; port = $conflictPort; enabled = 1; remark = 'acceptance'; filters = @() })
Add-Result '端口被占用时 success=false' (-not $r.Json.success) ("success=" + $r.Json.success + " msg=" + $r.Json.msg)
if ($r.Json.data) { $null = Remove-Node -Id $r.Json.data.id }
& $plink -ssh -pw $SshPass -batch -hostkey $SshHostKey "$SshUser@${Host_}" "pkill -f '[n]c -l -p $conflictPort'" 2>&1 | Out-Null
$ok = New-Node -Spec ([ordered]@{ name = 'acc-ok'; protocol = 'jdk-tcp'; engine = 'jdk-tcp'; host = '0.0.0.0'; port = 21501; enabled = 1; remark = 'acceptance'; filters = @() })
Add-Result '正常节点仍 success=true' ([bool]$ok.Json.success) ("status=" + $(if ($ok.Json.success) { $ok.Json.data.status } else { '-' }))
if ($ok.Json.success) { $null = Remove-Node -Id $ok.Json.data.id }

# ---------- 5. 非法枚举必须被拒绝且不落库 ----------
Write-Host "`n[5] 过滤器参数校验"
$fsPort = 21502
$fsSpec = [ordered]@{
    name = 'acc-fs'; protocol = 'http'; engine = 'jdk'; host = '0.0.0.0'; port = $fsPort; enabled = 1; remark = 'acceptance'
    filters = @(@{ name = 'fs'; type = 'file-storage'; order = 1; enabled = $true; params = @{ layout = 'true'; layoutType = 'SYSTEM' } })
}
$r = New-Node -Spec $fsSpec
$fsId = $r.Json.data.id
Add-Result 'file-storage 节点创建' ([bool]$r.Json.success) ("id=" + $fsId)
# OrderedDictionary 没有 Clone()，这里重新组装一份只把 layoutType 改成非法值的请求体
$bogusSpec = [ordered]@{
    name = 'acc-fs'; protocol = 'http'; engine = 'jdk'; host = '0.0.0.0'; port = $fsPort; enabled = 1; remark = 'acceptance'
    filters = @(@{ name = 'fs'; type = 'file-storage'; order = 1; enabled = $true; params = @{ layout = 'true'; layoutType = 'BOGUS' } })
}
$r2 = Invoke-Api -Path "/ms/monitor/server/$fsId/hot-update" -Body ($bogusSpec | ConvertTo-Json -Depth 8 -Compress)
Add-Result '非法 layoutType 被拒绝' (-not $r2.Json.success) ("msg=" + $r2.Json.msg)
$view = (Get-Api "/ms/monitor/server/$fsId") | ConvertFrom-Json
$dbLayout = (@($view.data.filters)[0].params.PSObject.Properties | Where-Object { $_.Name -eq 'layoutType' }).Value
Add-Result '非法值未落库' ($dbLayout -eq 'SYSTEM') ("db layoutType=" + $dbLayout)
if ($fsId) { $null = Remove-Node -Id $fsId }

# ---------- 汇总 ----------
Start-Sleep -Seconds 3
Write-Host "`n=== 汇总 ==="
$failed = @($script:results | Where-Object { -not $_.Pass })
Write-Host ("  总计 {0} 项，通过 {1}，失败 {2}" -f $script:results.Count, ($script:results.Count - $failed.Count), $failed.Count)
if ($failed.Count -gt 0) {
    foreach ($f in $failed) { Write-Host ("  FAILED: " + $f.Name + "  " + $f.Detail) }
    exit 1
}
Write-Host '  ACCEPTANCE_OK'
exit 0
