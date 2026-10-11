# ui-hanzi-bank-acceptance.ps1 - browser acceptance for the Hanzi bank page and the
# HANZI collect source in the Collect page.
#
# ASCII-only on purpose: PowerShell 5.1 parses a non-ASCII .ps1 with the system ANSI
# code page and can fail before param() is reached (see repo AGENTS.md). Every Chinese
# literal below is passed to the browser as Unicode escapes or read back through the API.
#
# What it asserts (all through the real UI, not the API):
#   1. /edu/hanzi renders the seeded bank: rows present, pagination total >= 3400
#   2. searching a seeded character shows it, and its pinyin/strokes/radical cells are filled
#      (UI total must equal the API total -- catches "list renders but page size is wrong")
#   3. /edu/collect offers HANZI as a source type and the preset button fills the config
#   4. editing the seeded HANZI task shows no pre/import processor sections (self-persisted)
#
# Usage:
#   pwsh -File ui-hanzi-bank-acceptance.ps1                                # dev site
#   pwsh -File ui-hanzi-bank-acceptance.ps1 -Base http://192.168.50.198:19093  # packaged site
[CmdletBinding()]
param(
    [string]$Base = 'http://127.0.0.1:19093',
    [string]$AdminUser = 'admin',
    [string]$AdminPwd = 'admin123',
    # Screenshots go to a temp dir: writing them next to the script drops PNGs into the repo.
    [string]$OutDir = (Join-Path ([IO.Path]::GetTempPath()) 'eduai-ui-acceptance')
)
if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }

$ErrorActionPreference = 'Stop'
$script:Results = @()

function Ck {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    $script:Results += , @($Ok, $Name, $Detail)
    $tag = if ($Ok) { 'PASS' } else { 'FAIL' }
    Write-Host ("  [$tag] $Name" + $(if ($Detail) { "  $Detail" } else { '' }))
}

# Resolve the CLI entry ONCE and enforce a per-call timeout: a single wedged call
# otherwise hangs the whole gate (observed: an `open` sat for 30+ minutes).
$script:AbExe = (Get-Command agent-browser -All -ErrorAction SilentlyContinue |
    Where-Object { $_.Source -like '*.cmd' } | Select-Object -First 1).Source
if (-not $script:AbExe) { $script:AbExe = (Get-Command agent-browser -ErrorAction SilentlyContinue).Source }
$script:AbTimeoutMs = 90000

function Ab {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    if (-not $script:AbExe) { return @() }
    $quoted = @()
    foreach ($a in $Args) {
        if ($a -match '[\s"]') { $quoted += ('"' + ($a -replace '"', '\"') + '"') } else { $quoted += $a }
    }
    $outFile = [IO.Path]::GetTempFileName()
    $errFile = "$outFile.err"
    $p = Start-Process -FilePath $script:AbExe -ArgumentList $quoted -NoNewWindow -PassThru `
        -RedirectStandardOutput $outFile -RedirectStandardError $errFile
    if (-not $p.WaitForExit($script:AbTimeoutMs)) {
        try { $p.Kill() } catch { }
        Write-Host ("    [WARN] agent-browser timeout: " + ($Args -join ' '))
        Remove-Item $outFile, $errFile -Force -ErrorAction SilentlyContinue
        return @()
    }
    $lines = @()
    if (Test-Path $outFile) { $lines += [IO.File]::ReadAllLines($outFile) }
    if (Test-Path $errFile) { $lines += [IO.File]::ReadAllLines($errFile) }
    Remove-Item $outFile, $errFile -Force -ErrorAction SilentlyContinue
    return @($lines)
}

# JS goes over the wire base64-encoded: inline JS with quotes gets mangled by the
# PowerShell -> agent-browser -> browser chain (observed repeatedly).
function Js {
    param([string]$Script)
    if (-not $script:AbExe) { return "" }
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Script))
    # Must go through Start-Process with a timeout, exactly like Ab().
    # Calling the CLI directly (& $exe) has NO timeout: one wedged `eval` blocks the
    # whole acceptance forever (observed: the run sat in section 1 until the outer
    # 40-minute timeout killed it, while the page was waiting on a restarting backend).
    # A gate that can hang is worse than one that fails.
    $outFile = [IO.Path]::GetTempFileName()
    $errFile = "$outFile.err"
    $p = Start-Process -FilePath $script:AbExe -ArgumentList @('eval', '-b', $b64) -NoNewWindow -PassThru `
        -RedirectStandardOutput $outFile -RedirectStandardError $errFile
    if (-not $p.WaitForExit($script:AbTimeoutMs)) {
        try { $p.Kill() } catch { }
        Remove-Item $outFile, $errFile -Force -ErrorAction SilentlyContinue
        return ""
    }
    $lines = @()
    if (Test-Path $outFile) { $lines += [IO.File]::ReadAllLines($outFile) }
    if (Test-Path $errFile) { $lines += [IO.File]::ReadAllLines($errFile) }
    Remove-Item $outFile, $errFile -Force -ErrorAction SilentlyContinue
    $raw = (($lines | ForEach-Object { $_.ToString() }) -join "`n").Trim()
    if ([string]::IsNullOrWhiteSpace($raw) -or $raw -eq 'null' -or $raw -eq 'undefined') { return "" }
    try { $val = $raw | ConvertFrom-Json } catch { $val = $raw.Trim('"').Replace('\"\', '"') }
    return [string]$val
}

# Write-Host (never Write-Output): a helper whose output is consumed must not leak text.
function WaitTrue {
    param([string]$Pred, [string]$Label = 'cond', [int]$TimeoutSec = 30)
    $t0 = Get-Date
    while (((Get-Date) - $t0).TotalSeconds -lt $TimeoutSec) {
        if ((Js $Pred) -eq 'true') { Write-Host "    ok[$Label]"; return $true }
        Start-Sleep -Milliseconds 700
    }
    Write-Host "    TIMEOUT[$Label]"
    return $false
}

function Api {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null)
    $h = @{ 'User-Agent' = 'Mozilla/5.0 (eduai-ui-hanzi-acceptance)' }
    if ($Token) { $h['Authorization'] = "Bearer $Token" }
    $a = @{ Method = $Method; Uri = ($Base + $Path); Headers = $h; SkipHttpErrorCheck = $true; TimeoutSec = 30 }
    if ($null -ne $Body) { $a['ContentType'] = 'application/json'; $a['Body'] = ($Body | ConvertTo-Json -Depth 6 -Compress) }
    $r = Invoke-WebRequest @a
    $json = $null
    try { $json = $r.Content | ConvertFrom-Json } catch { }
    return @{ code = [int]$r.StatusCode; json = $json; raw = [string]$r.Content }
}

# ---------- 0. preconditions ----------
Write-Host '########## 0. preconditions ##########'
if (-not (Get-Command agent-browser -ErrorAction SilentlyContinue)) { Write-Host 'FATAL: agent-browser not found'; exit 2 }
try { $reach = Api POST '/v2/user/login' @{ username = $AdminUser; password = $AdminPwd } }
catch { Write-Host "FATAL: site not reachable at $Base ($($_.Exception.Message))"; exit 2 }
if ($null -eq $reach) { Write-Host "FATAL: site not reachable at $Base"; exit 2 }
$adminToken = ($reach.json.data).accessToken
Ck 'admin login' ([bool]$adminToken) "http=$($reach.code)"
if (-not $adminToken) { exit 2 }

# API-side baseline: what the UI must agree with.
$apiHanzi = Api GET '/v2/edu/hanzi/page?pageNum=1&pageSize=1' $null $adminToken
$apiTotal = [int]$apiHanzi.json.data.total
Ck 'api seeded total >= 3400' ($apiTotal -ge 3400) "apiTotal=$apiTotal"

$probeChar = [char]0x660E   # U+660E, built from the code point so this file stays ASCII

# ---------- 1. open the bank page ----------
Write-Host '########## 1. hanzi bank page renders the seed ##########'
# Login: reuse the proven flow from ui-paper-assign-acceptance.ps1 -> open /login,
# snapshot BEFORE filling (fill lands before hydration and always fails otherwise),
# locate inputs by placeholder, then press Enter.
Start-Sleep -Seconds 3
Js 'localStorage.clear(); sessionStorage.clear(); "c"' | Out-Null
$phUser = "$([char]0x8BF7)$([char]0x8F93)$([char]0x5165)$([char]0x7528)$([char]0x6237)$([char]0x540D)"
$phPwd = "$([char]0x8BF7)$([char]0x8F93)$([char]0x5165)$([char]0x5BC6)$([char]0x7801)"
$logged = $false
for ($t = 1; $t -le 3; $t++) {
    Ab open "$Base/login" | Out-Null; Start-Sleep -Seconds 4
    Ab snapshot | Out-Null
    Ab find placeholder $phUser fill $AdminUser | Out-Null
    Ab find placeholder $phPwd fill $AdminPwd | Out-Null
    if ((Js '(document.querySelectorAll("input").length>=2) ? "true" : "false"') -ne 'true') { continue }
    Ab press Enter | Out-Null
    if (WaitTrue 'location.pathname.indexOf("/login")!==0' "admin login try$t" 25) { $logged = $true; break }
}
Ck 'admin logged in via the login page' $logged

Ab open "$Base/edu/hanzi" | Out-Null
# The page is a keep-alive route: rows appear only after the API answers.
WaitTrue 'document.querySelectorAll(".el-table__body-wrapper tbody tr").length > 0' 'hanzi rows rendered' 45 | Out-Null
$rowCount = [int](Js 'document.querySelectorAll(".el-table__body-wrapper tbody tr").length')
Ck 'hanzi table has rows' ($rowCount -gt 0) "rows=$rowCount"
# Element Plus pagination footer prints only "<size>TIAO/YE" here (no "GONG N TIAO"),
# so the total is derived from the pager itself: pages = last pager button, and
# total must satisfy (pages-1)*size < total <= pages*size. That is a real cross-check
# against the API total, not a restatement of it.
$pager = Js '(() => { const el = document.querySelector(".el-pagination"); if (!el) return "{}"; const items = Array.from(el.querySelectorAll(".el-pager li")).map(li => (li.innerText || "").trim()); const nums = items.map(t => parseInt(t, 10)).filter(n => !isNaN(n)); const sizeSel = el.querySelector(".el-select input, select"); const m = (el.innerText || "").match(/(\d+)/); return JSON.stringify({ pages: nums.length ? Math.max.apply(null, nums) : 0, size: m ? parseInt(m[1], 10) : 0, text: (el.innerText || "").replace(/\s+/g, " ").trim() }); })()'
$pagerObj = $pager | ConvertFrom-Json
Ck 'pagination shows pager and page size' (($pagerObj.pages -gt 0) -and ($pagerObj.size -gt 0)) ("pages={0} size={1} text={2}" -f $pagerObj.pages, $pagerObj.size, $pagerObj.text)
$lo = ($pagerObj.pages - 1) * $pagerObj.size
$hi = $pagerObj.pages * $pagerObj.size
Ck 'ui page count matches api total' (($apiTotal -gt $lo) -and ($apiTotal -le $hi)) ("pages={0} size={1} -> ({2},{3}] contains api={4}" -f $pagerObj.pages, $pagerObj.size, $lo, $hi, $apiTotal)
Ab screenshot (Join-Path $OutDir 'ui-hanzi-list.png') | Out-Null

# ---------- 2. search a seeded character and read its cells ----------
Write-Host '########## 2. search a seeded character (UI cells carry the seeded values) ##########'
# Keyword is a draft + explicit search button: typing does NOT query, so the
# script must click the search button (v-model=searchDraft + commitSearch).
$jsSearch = @"
(() => {
  const target = String.fromCharCode(0x660E);
  const box = document.querySelector('.filter-card input');
  if (!box) return 'no-input';
  const proto = Object.getPrototypeOf(box);
  Object.getOwnPropertyDescriptor(proto, 'value').set.call(box, target);
  box.dispatchEvent(new Event('input', { bubbles: true }));
  box.dispatchEvent(new Event('change', { bubbles: true }));
  const label = String.fromCharCode(0x641C, 0x7D22); // search button label
  const btn = Array.from(document.querySelectorAll('.filter-actions button'))
      .find(b => b.offsetParent !== null && (b.innerText || '').trim().indexOf(label) >= 0);
  if (!btn) return 'typed-no-button';
  btn.click();
  return 'searched';
})()
"@
$searchResult = Js $jsSearch
Ck 'keyword typed and search triggered' ($searchResult -eq 'searched') "result=$searchResult"
Start-Sleep -Seconds 2
# Column order: char / pinyin / strokes / radical / structure / stroke-order /
# meaning / image / grade / actions
$jsRead = @"
(() => {
  const target = String.fromCharCode(0x660E);
  const rows = Array.from(document.querySelectorAll('.el-table__body-wrapper tbody tr'));
  const row = rows.find(r => (r.innerText || '').indexOf(target) >= 0);
  if (!row) return JSON.stringify({ found: false, rows: rows.length });
  const cells = Array.from(row.querySelectorAll('td')).map(td => (td.innerText || '').trim());
  return JSON.stringify({ found: true, count: rows.length, cells: cells });
})()
"@
$readRaw = Js $jsRead
Ck 'search finds the character' ($readRaw -match '"found":\s*true') "raw=$($readRaw.Substring(0, [Math]::Min(240, $readRaw.Length)))"
# Real assertion: the pinyin / strokes / radical cells must equal the seeded values.
# Expected values are built from code points so this file stays ASCII.
$expectPinyin  = "m$([char]0x00ED)ng"                              # ming, tone mark via code point
$expectRadical = [string][char]0x65E5                                    # ri (sun radical)
$expectStrokes = '8'                                       # seeded stroke count for the char
if ($readRaw -match '"cells":\s*\[(.*?)\]\s*\}') {
    $cellsJson = $Matches[1]
    $cellValues = [regex]::Matches($cellsJson, '"((?:[^"\\]|\\.)*)"') | ForEach-Object { $_.Groups[1].Value }
    Ck 'row cell count is the full column set' ($cellValues.Count -ge 9) "cells=$($cellValues.Count)"
    if ($cellValues.Count -ge 5) {
        # cells[0]=char cells[1]=pinyin cells[2]=strokes cells[3]=radical cells[4]=structure
        Ck 'UI pinyin cell matches the seed' ($cellValues[1] -eq $expectPinyin) "ui='$($cellValues[1])' expect='$expectPinyin'"
        Ck 'UI strokes cell matches the seed' ($cellValues[2] -eq $expectStrokes) "ui='$($cellValues[2])' expect=$expectStrokes"
        Ck 'UI radical cell matches the seed' ($cellValues[3] -eq $expectRadical) "ui='$($cellValues[3])'"
    }
} else {
    Ck 'row cells readable', $false, "no cells in $readRaw"
}
Ab screenshot (Join-Path $OutDir 'ui-hanzi-search.png') | Out-Null

# 原第 3 节（collect page offers HANZI + preset）随 eduai 采集管理一起移除：
#   它打 /v2/edu/collect/source-types，并操作 /edu/collect 页里的「新建任务」弹窗，
#   那套 UI 与后端控制器已整体删除，采集能力改由采集平台的 /v2/collect/** 提供。
#   上面的汉字库检索与种子值比对仍然有效，故保留。
$passed = ($script:Results | Where-Object { $_[0] -eq $true }).Count
$failed = $script:Results.Count - $passed
Write-Host ("===== {0}: passed {1} / failed {2} =====" -f $(if ($failed -eq 0) { 'PASS' } else { 'FAIL' }), $passed, $failed)
# A gate that cannot fail is worse than no gate: exit non-zero on any failure.
exit $(if ($failed -eq 0) { 0 } else { 1 })
