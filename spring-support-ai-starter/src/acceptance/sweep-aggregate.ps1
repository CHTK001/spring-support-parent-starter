# 汇总 sweep-b* 各批次 report.json：按路由取最优记录，产出 PASS/FAIL/WARN 结论
# v2: 区分「完整跑完」与「崩溃提前终止」—— aborted 路由 elements.length < total，
#     未处理元素不能计入 PASS；无完整记录的路由标记 NEEDS-RERUN
# v3: 扫描范围加入 sweep-fix-*（FAIL 复跑定性目录），选优顺序：
#     fix 记录 > 完整记录 > total 大 > fail 少（复跑是同条件的最新状态证据）
$ErrorActionPreference = 'SilentlyContinue'
$Root = 'C:\Users\Administrator\AppData\Local\Temp\opencode'
$AllRoutes = @(
  '/chat/new-session', '/characters', '/bots', '/tavern', '/setting', '/setting/default-models',
  '/plugins', '/knowledge-base', '/knowledge-base/__ID__', '/scheduler',
  '/models', '/image-generate', '/face-search', '/image-search',
  '/app-keys', '/manufacturer-keys', '/roles', '/mcp', '/agents',
  '/usage', '/usage/sync-logs', '/skills', '/connectors', '/connectors/chat/__CONN__', '/capabilities/ocr'
)
function Read-JsonFile([string]$path) {
  try {
    $fs = [System.IO.File]::Open($path, 'Open', 'Read', 'ReadWrite')
    $sr = New-Object System.IO.StreamReader($fs, [System.Text.Encoding]::UTF8)
    $txt = $sr.ReadToEnd(); $sr.Close(); $fs.Close()
    return ($txt | ConvertFrom-Json)
  } catch { return $null }
}
$cands = @{}
Get-ChildItem $Root -Directory | Where-Object { $_.Name -like 'sweep-b*' -or $_.Name -like 'sweep-fix-*' } | ForEach-Object {
  $rj = Join-Path $_.FullName 'report.json'
  if (-not (Test-Path $rj)) { return }
  $rep = Read-JsonFile $rj
  if (-not $rep -or -not $rep.routes) { return }
  foreach ($rt in $rep.routes) {
    $t = 0; try { $t = [int]$rt.total } catch { $t = 0 }
    if ($t -le 0) { continue }
    $proc = 0; try { $proc = @($rt.elements).Count } catch { $proc = 0 }
    $fc = 0; try { $fc = @($rt.elements | Where-Object { $_.state -in @('click-error', 'not-clickable') }).Count } catch { $fc = 0 }
    if (-not $cands.ContainsKey($rt.route)) { $cands[$rt.route] = @() }
    $cands[$rt.route] += , @{ rec = $rt; src = $_.Name; total = $t; processed = $proc; fails = $fc; complete = ($proc -ge $t); isfix = ($_.Name -like 'sweep-fix-*') }
  }
}
$failStates = @('click-error', 'not-clickable')
$warnStates = @('no-effect', 'needs-force', 'vanished', 'state-dependent')
$totPass = 0; $totFail = 0; $totWarn = 0; $totProc = 0; $totTotal = 0
$rows = @(); $missing = @(); $rerun = @()
foreach ($r in $AllRoutes) {
  if (-not $cands.ContainsKey($r)) { $missing += $r; continue }
  # 选优：fix 复跑记录优先（同条件最新状态证据），再完整优先，再 total 大，再 fail 少
  $best = $null; $bestSrc = ''
  foreach ($c in $cands[$r]) {
    if (-not $best) { $best = $c; continue }
    if ($c.isfix -ne $best.isfix) { if ($c.isfix) { $best = $c }; continue }
    if ($c.complete -ne $best.complete) { if ($c.complete) { $best = $c }; continue }
    if ($c.total -ne $best.total) { if ($c.total -gt $best.total) { $best = $c }; continue }
    if ($c.fails -lt $best.fails) { $best = $c; continue }
  }
  # 审计：同路由还有别的记录（尤其原始 FAIL 记录）时全部留档
  $others = @($cands[$r] | Where-Object { $_.src -ne $best.src } | ForEach-Object { "$($_.src)(total=$($_.total) fail=$($_.fails) complete=$($_.complete))" })
  $rec = $best.rec
  $f = @(); $w = 0
  foreach ($e in @($rec.elements)) {
    if ($failStates -contains $e.state) { $f += ("'{0}'[{1}]" -f $e.name, $e.state) }
    elseif ($warnStates -contains $e.state) { $w++ }
  }
  $fc = $f.Count
  $p = [int]$best.processed - $fc - $w
  $totPass += $p; $totFail += $fc; $totWarn += $w; $totProc += [int]$best.processed; $totTotal += [int]$best.total
  $incomplete = -not $best.complete
  if ($incomplete) { $rerun += $r }
  $rows += [pscustomobject]@{
    route = $r; src = $bestSrc; total = $best.total; processed = $best.processed
    complete = $best.complete; pass = $p; fail = $fc; warn = $w
    fails = ($f -join '; ')
    altRecs = ($others -join ' | ')
    pageErr = (@($rec.pageErrors) | Where-Object { $_ }).Count
    net5xx = (@($rec.net5xx) | Where-Object { $_ }).Count
    notes = (@($rec.notes) | Where-Object { $_ })
  }
}
Write-Output ('=' * 95)
foreach ($x in $rows) {
  $mark = if ($x.fail -gt 0) { 'FAIL' } elseif (-not $x.complete) { 'RERUN' } else { 'ok  ' }
  $cmp = if ($x.complete) { '' } else { " INCOMPLETE($($x.processed)/$($x.total))" }
  Write-Output ("[{0}] {1,-34} total={2,-4} processed={3,-4} pass={4,-4} warn={5,-3} fail={6,-3}{7}" -f $mark, $x.route, $x.total, $x.processed, $x.pass, $x.warn, $x.fail, $cmp)
  if ($x.fails) { Write-Output ("         fails: {0}" -f $x.fails) }
  if ($x.altRecs) { Write-Output ("         other-records: {0}" -f $x.altRecs) }
  if ($x.pageErr -gt 0) { Write-Output ("         pageErrors={0} net5xx={1}" -f $x.pageErr, $x.net5xx) }
  foreach ($n in $x.notes) { if ($n) { Write-Output ("         note: {0}" -f $n) } }
}
Write-Output ('=' * 95)
Write-Output ("ROUTES={0}/25  TOTAL={1}  PROCESSED={2}  PASS={3}  WARN={4}  FAIL={5}" -f ($rows.Count), $totTotal, $totProc, $totPass, $totWarn, $totFail)
if ($missing.Count) { Write-Output ("MISSING: " + ($missing -join ', ')) }
if ($rerun.Count) { Write-Output ("NEEDS-RERUN(未跑完): " + ($rerun -join ', ')) }
$summ = [pscustomobject]@{ rows = $rows; missing = $missing; rerun = $rerun; totals = @{ routes = $rows.Count; total = $totTotal; processed = $totProc; pass = $totPass; warn = $totWarn; fail = $totFail } }
$summ | ConvertTo-Json -Depth 6 | Set-Content -Path (Join-Path $Root 'sweep-summary.json') -Encoding UTF8
