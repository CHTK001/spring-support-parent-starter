# 全站 click-sweep 分批 runner v3（对抗整批 node 被杀 + 慢路由）
# v3 变更：
#   - stall 600s -> 1200s：Plugins 这类路由新脚本采集 130+ 可点元素，单路由要 10-15 分钟
#   - 用改名的 nodesweep.exe 跑 sweep：并发会话的清理脚本按 image name 杀 node.exe，
#     改名后 sweep 不被误杀（dev vite 被杀时由 dev-down 检测自动重启续跑）
#   - 每次尝试只跑当前缺失的路由（跨尝试并集推进）
#   - 不等外部 sweep 实例（对方会循环重启，等它等于永远等）——并集逻辑天然容忍重复
param(
  [string]$Root = 'C:\Users\Administrator\AppData\Local\Temp\opencode',
  [int]$MaxAttempts = 5,
  [int]$StallSeconds = 1200
)
$ErrorActionPreference = 'SilentlyContinue'
$NodeReal = 'C:\Program Files\nodejs\node.exe'
$NodeExe = Join-Path $Root 'nodesweep.exe'
if (-not (Test-Path $NodeExe)) { Copy-Item $NodeReal $NodeExe -Force }
$AppDir = 'D:\ch\project\vue-support-parent-starter\apps\vue-support-ai-guada'
$Log = Join-Path $Root 'runner.log'
function Log($m) {
  $line = "$(Get-Date -Format 'HH:mm:ss') $m"
  Add-Content -Path $Log -Value $line -Encoding UTF8
  Write-Output $line
}
$Batches = @(
  @{ name = 'b2';  routes = @('/plugins', '/scheduler') },
  @{ name = 'b3';  routes = @('/knowledge-base', '/knowledge-base/__ID__') },
  @{ name = 'b4';  routes = @('/models', '/image-generate') },
  @{ name = 'b5';  routes = @('/face-search', '/image-search') },
  @{ name = 'b6';  routes = @('/app-keys', '/manufacturer-keys') },
  @{ name = 'b7';  routes = @('/roles', '/mcp', '/agents') },
  @{ name = 'b8';  routes = @('/usage', '/usage/sync-logs') },
  @{ name = 'b9';  routes = @('/skills', '/capabilities/ocr') },
  @{ name = 'b10'; routes = @('/connectors', '/connectors/chat/__CONN__') }
)
function Read-JsonFile([string]$path) {
  try {
    $fs = [System.IO.File]::Open($path, 'Open', 'Read', 'ReadWrite')
    $sr = New-Object System.IO.StreamReader($fs, [System.Text.Encoding]::UTF8)
    $txt = $sr.ReadToEnd(); $sr.Close(); $fs.Close()
    return ($txt | ConvertFrom-Json)
  } catch { return $null }
}
function Get-DoneRoutes {
  $best = @{}
  Get-ChildItem $Root -Directory -Filter 'sweep-b*' -ErrorAction SilentlyContinue | ForEach-Object {
    $rj = Join-Path $_.FullName 'report.json'
    if (-not (Test-Path $rj)) { return }
    $rep = Read-JsonFile $rj
    if (-not $rep -or -not $rep.routes) { return }
    foreach ($rt in $rep.routes) {
      if (-not $rt.route) { continue }
      $t = 0; try { $t = [int]$rt.total } catch { $t = 0 }
      if ($t -le 0) { continue }
      if (-not $best.ContainsKey($rt.route) -or $t -gt $best[$rt.route]) { $best[$rt.route] = $t }
    }
  }
  return $best
}
function Get-DevUp {
  $c = Get-NetTCPConnection -LocalPort 15689 -State Listen -ErrorAction SilentlyContinue
  if (-not $c) { return $false }
  try {
    $r = Invoke-WebRequest -Uri 'http://localhost:15689/' -UseBasicParsing -TimeoutSec 6
    return ($r.StatusCode -eq 200)
  } catch { return $false }
}
function Start-Dev {
  Log 'dev 15689 不可用，重启 pnpm dev'
  Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'pnpm dev --port 15689 --strictPort >> C:\Users\Administrator\AppData\Local\Temp\opencode\aiguada-dev.log 2>&1' -WindowStyle Hidden -WorkingDirectory $AppDir | Out-Null
  for ($i = 0; $i -lt 30; $i++) { Start-Sleep -Seconds 2; if (Get-DevUp) { Log "dev 恢复（等了 $($i*2+2)s）"; return $true } }
  Log 'dev 重启失败（30 次探测后仍不可用）'
  return $false
}
Log "=== runner v3 启动 maxAttempts=$MaxAttempts stall=$StallSeconds node=$NodeExe ==="
foreach ($b in $Batches) {
  $done = Get-DoneRoutes
  $missing = @($b.routes | Where-Object { -not $done.ContainsKey($_) })
  if ($missing.Count -eq 0) { Log "[$($b.name)] 全部已完成，跳过"; continue }
  Log "[$($b.name)] 待跑 $($missing.Count)/$($b.routes.Count): $($missing -join ', ')"
  $ok = $false
  for ($a = 1; $a -le $MaxAttempts -and -not $ok; $a++) {
    if (-not (Get-DevUp)) { if (-not (Start-Dev)) { Start-Sleep -Seconds 10; continue } }
    $argList = @()
    foreach ($m in $missing) {
      if ($m -eq '/knowledge-base') { $argList += '/knowledge-base' }
      elseif ($m -eq '/knowledge-base/__ID__') { $argList += '__ID__' }
      elseif ($m -eq '/connectors/chat/__CONN__') { $argList += '__CONN__' }
      elseif ($m -eq '/usage/sync-logs') { $argList += 'sync-logs' }
      else { $argList += $m }
    }
    $dir = Join-Path $Root ("sweep-{0}-a{1}" -f $b.name, $a)
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    Remove-Item (Join-Path $dir 'report.json') -Force -ErrorAction SilentlyContinue
    $env:NODE_PATH = 'D:\ch\project\vue-support-parent-starter\node_modules'
    $env:BASE = 'http://localhost:15689'
    $env:SWEEP_DIR = $dir
    $env:SWEEP_DEBUG = '1'
    Log "[$($b.name)] 尝试 $a/$MaxAttempts 启动 args=$($argList -join ' ')"
    $p = Start-Process -FilePath $NodeExe -ArgumentList (@('src\acceptance\ui-click-sweep.cjs') + $argList) `
      -WorkingDirectory $AppDir -WindowStyle Hidden -PassThru `
      -RedirectStandardOutput (Join-Path $dir 'sweep.log') -RedirectStandardError (Join-Path $dir 'sweep.err')
    $lastChange = Get-Date; $lastCount = -1
    $finished = $false
    while (-not $finished) {
      Start-Sleep -Seconds 15
      $doneNow = Get-DoneRoutes
      $stillMissing = @($b.routes | Where-Object { -not $doneNow.ContainsKey($_) })
      if ($stillMissing.Count -eq 0) { Log "[$($b.name)] 尝试 $a 完成该批全部路由"; $ok = $true; $finished = $true; break }
      $cnt = $doneNow.Count
      if ($cnt -ne $lastCount) { $lastCount = $cnt; $lastChange = Get-Date }
      $exited = $false
      try { $p.Refresh(); $exited = $p.HasExited } catch { $exited = $true }
      if ($exited) {
        $errTail = ''
        try { $errTail = (Get-Content (Join-Path $dir 'sweep.err') -Tail 2 -ErrorAction SilentlyContinue) -join ' ' } catch {}
        Log "[$($b.name)] 尝试 $a 进程退出（exit=$($p.ExitCode)）仍缺: $($stillMissing -join ', ') err=$errTail"
        break
      }
      if (-not (Get-DevUp)) { Log "[$($b.name)] 尝试 $a 检测到 dev 掉线，终止本尝试"; try { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } catch {}; Start-Dev; break }
      if (((Get-Date) - $lastChange).TotalSeconds -gt $StallSeconds) {
        Log "[$($b.name)] 尝试 $a 超过 ${StallSeconds}s 无新路由落盘，判定挂死并终止"
        try { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } catch {}
        break
      }
    }
    if ($ok) {
      Start-Sleep -Seconds 15
      try { $p.Refresh(); if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue; Log "[$($b.name)] 批次完成后收掉自己的 summary 进程 $($p.Id)" } } catch {}
      break
    }
    Start-Sleep -Seconds 3
  }
  if (-not $ok) { Log "[$($b.name)] 放弃：已达最大尝试次数 $($MaxAttempts)（重跑 runner 可续跑补齐）" }
}
$final = Get-DoneRoutes
Log "=== runner 结束，已覆盖路由 $($final.Count)/25 ==="
$allRoutes = @('/chat/new-session','/characters','/bots','/tavern','/setting','/setting/default-models','/plugins','/knowledge-base','/knowledge-base/__ID__','/scheduler','/models','/image-generate','/face-search','/image-search','/app-keys','/manufacturer-keys','/roles','/mcp','/agents','/usage','/usage/sync-logs','/skills','/connectors','/connectors/chat/__CONN__','/capabilities/ocr')
$miss = @($allRoutes | Where-Object { -not $final.ContainsKey($_) })
if ($miss.Count) { Log "仍缺失: $($miss -join ', ')" } else { Log '25 条路由全部完成' }
