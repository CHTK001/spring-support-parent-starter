<#
.SYNOPSIS
  eduai "class paper assignment" BROWSER acceptance (UI end-to-end).

.DESCRIPTION
  Drives a real browser to prove the whole business loop through the UI, not just
  the API:

    A. Teacher side : login -> /edu/teacher -> "paper" tab -> click "assign to class"
                      on a PUBLISHED paper -> pick the fixture class -> submit
                      -> the "my assignments" list shows that class as assigned.
    B. Student side : login with the fixture student -> portal -> "my papers" card
                      -> the assigned paper shows up -> click "view paper"
                      -> questions render (count > 0).
    C. Cleanup      : revoke the assignment, delete the fixture student and class.

  Fixture (class + student with an initial password) is created over HTTP through
  the same dev proxy the browser uses, and deleted at the end.

.NOTES
  Requires pwsh 7 (repo rule: powershell 5.1 must not be used), agent-browser CLI,
  and the local dev server.
  ASCII-only on purpose: 5.1 chokes on non-ASCII .ps1 files. Chinese DOM labels are
  matched with JS \uXXXX escapes so no literal non-ASCII appears in this file.

  Lessons baked in (all of these were hit for real while building this feature):
   * Navigating resets the viewport -> set viewport AFTER navigation and read back.
   * A hidden el-tab-pane still contains its buttons, but their rect is 0x0 ->
     "element exists" is NOT "element is ready"; require a real size.
   * A dropdown with max-height clips items: clipped ones still report width > 0,
     so elementFromPoint (not width) decides whether a coordinate click can land.
     el-select keyboard nav (arrows + Enter) is more reliable than clicking.
   * el-select arrow steps must be computed within the target's own list and
     relative to the currently selected item (using the page-wide index overshoots).
   * Chinese string comparison in PowerShell 5.1/console is not trustworthy ->
     do the comparison inside JS and pass back a boolean.
   * Do NOT use Write-Output inside a helper whose value is consumed: the strings
     join the return value and make any "if (-not (helper))" check meaningless.
   * The login page needs snapshot-before-fill plus a retry: fill can land before
     the SPA hydrates and get cleared.

.EXAMPLE
  pwsh -File ui-paper-assign-acceptance.ps1
  pwsh -File ui-paper-assign-acceptance.ps1 -Base http://127.0.0.1:19093
#>
[CmdletBinding()]
param(
    [string]$Base = 'http://127.0.0.1:19093',
    [string]$AdminUser = 'admin',
    [string]$AdminPwd = 'admin123',
    [string]$StudentPwd = 'UiAccept#2026'
)

$ErrorActionPreference = 'Stop'
$script:Results = @()

function Ck {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    $script:Results += , @($Ok, $Name, $Detail)
    $tag = if ($Ok) { 'PASS' } else { 'FAIL' }
    Write-Host ("  [$tag] $Name" + $(if ($Detail) { "  $Detail" } else { '' }))
}

# Resolve the CLI entry ONCE. Prefer the .cmd shim: Start-Process can run it and we
# need Start-Process to enforce a per-call timeout. A single wedged call would
# otherwise hang the whole gate (observed: an `open` sat for 30+ minutes, and the
# acceptance never reached its cleanup) -- a gate that hangs is worse than one that fails.
$script:AbExe = (Get-Command agent-browser -All -ErrorAction SilentlyContinue |
    Where-Object { $_.Source -like '*.cmd' } | Select-Object -First 1).Source
if (-not $script:AbExe) { $script:AbExe = (Get-Command agent-browser).Source }
$script:AbTimeoutMs = 90000

function Ab {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
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

# JS is passed base64-encoded: inline JS with quotes gets mangled by the
# PowerShell -> agent-browser -> browser chain (observed repeatedly).
function Js {
    param([string]$Script)
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Script))
    $out = & agent-browser eval -b $b64 2>&1
    $raw = (($out | ForEach-Object { $_.ToString() }) -join "`n").Trim()
    if ([string]::IsNullOrWhiteSpace($raw) -or $raw -eq 'null' -or $raw -eq 'undefined') { return '' }
    try { $val = $raw | ConvertFrom-Json } catch { $val = $raw.Trim('"').Replace('\"', '"') }
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
    $h = @{ 'User-Agent' = 'Mozilla/5.0 (eduai-ui-acceptance)' }
    if ($Token) { $h['Authorization'] = "Bearer $Token" }
    $a = @{ Method = $Method; Uri = ($Base + $Path); Headers = $h; SkipHttpErrorCheck = $true; TimeoutSec = 30 }
    if ($null -ne $Body) { $a['ContentType'] = 'application/json'; $a['Body'] = ($Body | ConvertTo-Json -Depth 6 -Compress) }
    $r = Invoke-WebRequest @a
    $json = $null
    try { $json = $r.Content | ConvertFrom-Json } catch { }
    return @{ code = [int]$r.StatusCode; json = $json }
}

# ---------- 0. preconditions ----------
Write-Host '########## 0. preconditions ##########'
if (-not (Get-Command agent-browser -ErrorAction SilentlyContinue)) { Write-Host 'FATAL: agent-browser not found'; exit 2 }
$reach = $null
try { $reach = Api POST '/v2/user/login' @{ username = $AdminUser; password = $AdminPwd } }
catch { Write-Host "FATAL: dev server not reachable at $Base ($($_.Exception.Message))"; exit 2 }
if ($null -eq $reach) { Write-Host "FATAL: dev server not reachable at $Base"; exit 2 }
$adminToken = ($reach.json.data).accessToken
Ck 'admin login over the dev proxy' ([bool]$adminToken) "http=$($reach.code)"
if (-not $adminToken) { exit 2 }

# ---------- 1. fixture ----------
Write-Host '########## 1. fixture (class + student) ##########'
$stamp = [int][double]::Parse((Get-Date -UFormat %s))
$className = "UIACC-$stamp"     # ASCII name on purpose: no Chinese literal in this file
$classId = $null; $studentId = $null; $studentNo = $null
try {
    $r = Api POST '/v2/edu/dimension/classes/save' @{ className = $className; classGradeLevel = 'PRIMARY' } $adminToken
    $classId = $r.json.data
    Ck 'create class' ([bool]$classId) "http=$($r.code) classId=$classId"

    $r = Api POST '/v2/edu/dimension/students/save' @{ studentName = "UIACC Student"; studentPwdInput = $StudentPwd; studentClassId = $classId; studentGradeLevel = 'PRIMARY' } $adminToken
    $studentId = $r.json.data
    Ck 'create student with initial password' ([bool]$studentId) "http=$($r.code) studentId=$studentId"

    $r = Api GET '/v2/edu/dimension/students' $null $adminToken
    foreach ($s in @($r.json.data)) { if ($s.studentId -eq $studentId) { $studentNo = $s.studentNo } }
    Ck 'student is attached to the class' ([bool]$studentNo) "studentNo=$studentNo"
    if (-not $studentNo) { throw 'fixture incomplete' }

    # Normalise the student's first-login state BEFORE driving the UI.
    # A freshly created student carries "must change initial password", and the portal then
    # shows a deliberately NON-dismissable guide dialog that blocks everything else --
    # the card click would land on that overlay and the run would fail for a reason that has
    # nothing to do with paper assignment. We clear it through the API (the change itself is
    # covered by first-login-password-acceptance.py).
    $studentPwdFinal = "$StudentPwd-b"
    $r = Api POST '/v2/user/login' @{ username = $studentNo; password = $StudentPwd; authType = 'STUDENT'; accountType = 'student' }
    $stk = ($r.json.data).accessToken
    $r = Api POST '/v2/edu/student/change-password' @{ oldPassword = $StudentPwd; newPassword = $studentPwdFinal } $stk
    Ck 'student initial password changed (clears the blocking first-login guide)' ([bool]$r.json.ok) "http=$($r.code)"

    # ---------- 2. teacher side (UI) ----------
    Write-Host '########## 2. teacher assigns via the UI ##########'
    Ab open "$Base/login" | Out-Null
    Start-Sleep -Seconds 3
    Js 'localStorage.clear(); sessionStorage.clear(); "c"' | Out-Null
    $logged = $false
    for ($t = 1; $t -le 3; $t++) {
        Ab open "$Base/login" | Out-Null; Start-Sleep -Seconds 4
        Ab snapshot | Out-Null                       # must snapshot before fill
        Ab find placeholder "$([char]0x8BF7)$([char]0x8F93)$([char]0x5165)$([char]0x7528)$([char]0x6237)$([char]0x540D)" fill $AdminUser | Out-Null
        Ab find placeholder "$([char]0x8BF7)$([char]0x8F93)$([char]0x5165)$([char]0x5BC6)$([char]0x7801)" fill $AdminPwd | Out-Null
        if ((Js '(document.querySelectorAll("input").length>=2) ? "true" : "false"') -ne 'true') { continue }
        Ab press Enter | Out-Null
        if (WaitTrue 'location.pathname.indexOf("/login")!==0' "admin login try$t" 20) { $logged = $true; break }
    }
    Ck 'teacher (admin) logged in via the login page' $logged

    Ab open "$Base/edu/teacher" | Out-Null; Start-Sleep -Seconds 5
    Ab set viewport 1440 1000 | Out-Null; Start-Sleep -Milliseconds 800
    $vp = Js 'JSON.stringify({w:window.innerWidth,h:window.innerHeight})'
    Ck 'viewport applied after navigation' ($vp -match '"w":1440') $vp

    # active-bar is the first child of .el-tabs__nav, so the paper tab is child 3
    Ab click '.el-tabs__item:nth-child(3)' | Out-Null
    $ready = WaitTrue '(function(){var b=document.querySelector(".tp-assign-open");return !!(b&&b.getBoundingClientRect().width>10)})()' 'paper tab rendered' 25
    Ck 'paper tab rendered (assign button has a real size)' $ready

    $pick = Js '(function(){var b=Array.from(document.querySelectorAll(".tp-assign-open"));for(var i=0;i<b.length;i++){var tr=b[i].closest("tr");if(!tr)continue;if((tr.textContent||"").indexOf("\u5DF2\u53D1\u5E03")>=0){return JSON.stringify({idx:i,paper:(tr.querySelectorAll("td")[0]||{}).textContent.trim(),total:b.length})}}return JSON.stringify({idx:-1,total:b.length})})()'
    $pk = $pick | ConvertFrom-Json
    Ck 'found a PUBLISHED paper to assign' ($pk.idx -ge 0) "idx=$($pk.idx) paper=$($pk.paper) of $($pk.total)"
    if ($pk.idx -lt 0) { throw 'no published paper' }

    Js 'window.__ac=0;document.querySelectorAll(".tp-assign-open").forEach(function(b){b.addEventListener("click",function(){window.__ac++},true)});"ok"' | Out-Null
    Ab scrollintoview '.tp-assign-open' | Out-Null; Start-Sleep -Milliseconds 700
    Ab find nth $pk.idx '.tp-assign-open' click | Out-Null
    Start-Sleep -Seconds 2
    Ck 'pointer click reached the assign button' ((Js 'String(window.__ac)') -eq '1')
    Ck 'assign dialog opened' ((Js 'String((document.querySelector(".el-dialog__title")||{}).textContent||"")') -match '.') (Js 'String((document.querySelector(".el-dialog__title")||{}).textContent||"")')

    Ab click '.el-dialog .el-select' | Out-Null
    $dropPred = '(function(){return Array.from(document.querySelectorAll(".el-select-dropdown__item")).some(function(e){return e.textContent.indexOf("' + $className + '")>=0})})()'
    $drop = WaitTrue $dropPred 'class dropdown open' 25
    Ck 'class dropdown opened with the fixture class' $drop

    # arrow steps = target position minus currently selected position, within its own list
    $steps = Js ('(function(){var t=Array.from(document.querySelectorAll(".el-select-dropdown__item")).filter(function(e){return e.textContent.indexOf("' + $className + '")>=0})[0];if(!t)return "-999";var k=Array.from(t.parentElement.children);var pos=k.indexOf(t);var cur=k.findIndex(function(x){return (x.className||"").indexOf("is-selected")>=0});return String(pos-(cur>=0?cur:0))})()')
    if ([int]$steps -lt 0 -or [int]$steps -eq -999) { throw "cannot compute arrow steps ($steps)" }
    for ($k = 0; $k -lt [int]$steps; $k++) { Ab press ArrowDown | Out-Null; Start-Sleep -Milliseconds 150 }
    Ab press Enter | Out-Null
    Start-Sleep -Seconds 2
    $selOk = Js ('String((document.querySelector(".el-dialog .el-select")||{}).textContent||"").indexOf("' + $className + '")>=0 ? "true" : "false"')
    Ck 'fixture class selected in the dialog' ($selOk -eq 'true') "arrowSteps=$steps"

    Ab click '.el-dialog .el-button--primary' | Out-Null
    Start-Sleep -Seconds 5
    # Compare INSIDE JS and pass back a boolean: Chinese that travels through the
    # console/stdout pipeline gets re-encoded and cannot be matched reliably in PowerShell.
    $rowPred = '(function(){var r=document.querySelector(".tp-assign tbody tr");if(!r)return "false";var t=r.textContent;return (t.indexOf("' + $className + '")>=0 && t.indexOf("\u5DF2\u5E03\u7F6E")>=0)?"true":"false"})()'
    $rowOk = (Js $rowPred) -eq 'true'
    Ck 'assignment list shows the fixture class as assigned' $rowOk ("row=" + (Js 'String((document.querySelector(".tp-assign tbody tr")||{}).textContent.replace(/\s+/g," ").trim().slice(0,60)||"")'))
    Ab screenshot "$PSScriptRoot/ui-assign-teacher.png" | Out-Null

    # Pull the expected question count as a NUMBER from the API: numbers survive every
    # encoding hop, so the later UI assertion can be exact without transporting Chinese.
    $paperQ = -1
    $r = Api GET '/v2/edu/paper-assign/mine' $null $adminToken
    foreach ($row in @($r.json.data)) {
        if ($row.classId -eq $classId -and $row.paperId) { $paperQ = [int]$row.paperQuestionCount }
    }
    Ck 'assign record exposes a question count for the assertion' ($paperQ -gt 0) "paperQuestionCount=$paperQ"

    # ---------- 3. student side (UI) ----------
    Write-Host '########## 3. student sees it in the portal ##########'
    Js 'localStorage.clear(); sessionStorage.clear(); "c"' | Out-Null
    $sLogged = $false
    for ($t = 1; $t -le 3; $t++) {
        Ab open "$Base/edu/student-login" | Out-Null; Start-Sleep -Seconds 5
        Ab snapshot | Out-Null
        Ab fill 'input[autocomplete="username"]' $studentNo | Out-Null
        Ab fill 'input[autocomplete="current-password"]' $studentPwdFinal | Out-Null
        $filled = Js 'String((document.querySelector("input[autocomplete=username]")||{}).value||"")'
        if ($filled -ne $studentNo) { continue }
        Ab click 'button[type=submit]' | Out-Null
        if (WaitTrue 'location.pathname.indexOf("/edu/student")===0' "student login try$t" 20) { $sLogged = $true; break }
    }
    Ck 'student logged in and reached the portal' $sLogged

    Ab set viewport 1440 1000 | Out-Null; Start-Sleep -Milliseconds 800
    # The card's text is "title + subtitle"; take the SMALLEST element whose text
    # starts with the module title (that is the card itself, not its wrapper).
    $cardJs = '(function(){var all=Array.from(document.querySelectorAll("*"));var t=all.filter(function(e){var s=e.textContent.trim();var r=e.getBoundingClientRect();return s.indexOf("\u6211\u7684\u8BD5\u5377")===0 && r.width>80 && r.height>20 && r.width<700});if(!t.length)return JSON.stringify({err:1});t.sort(function(a,b){var ra=a.getBoundingClientRect(),rb=b.getBoundingClientRect();return ra.width*ra.height-rb.width*rb.height});var e=t[0],r=e.getBoundingClientRect();return JSON.stringify({x:Math.round(r.left+r.width/2),y:Math.round(r.top+r.height/2)})})()'
    $cj = Js $cardJs | ConvertFrom-Json
    if ($cj.err) { Ck 'my-papers card found in the portal' $false 'card not found'; throw 'card not found' }
    Ab mouse move ([int]$cj.x) ([int]$cj.y) | Out-Null
    Ab mouse down | Out-Null
    Ab mouse up | Out-Null
    $listed = WaitTrue 'document.querySelectorAll(".sp-card").length>0' 'my-papers list rendered' 25
    Ck 'my-papers list rendered' $listed
    # Exactly one paper was assigned to this fresh class, so the student must see exactly one
    # card. (Deliberately no Chinese comparison here: a Chinese string round-tripped through
    # the console gets re-encoded, so the old `-match` style check reported false failures.)
    $cardCount = [int](Js 'String(document.querySelectorAll(".sp-card").length)')
    Ck 'exactly the one assigned paper is listed for the student' ($cardCount -eq 1) "cards=$cardCount"
    Ab screenshot "$PSScriptRoot/ui-student-list.png" | Out-Null

    Ab scrollintoview '.sp-card .el-button' | Out-Null; Start-Sleep -Milliseconds 700
    Ab click '.sp-card .el-button' | Out-Null
    $qs = WaitTrue 'document.querySelectorAll(".sp-q").length>0' 'paper questions rendered' 25
    $qCount = [int](Js 'String(document.querySelectorAll(".sp-q").length)')
    Ck 'view paper: questions rendered (not an empty shell)' $qs "qCount=$qCount"
    # The decisive cross-check: the UI must show exactly as many questions as the paper has.
    Ck 'rendered question count equals the paper question count' ($qCount -eq $paperQ) "ui=$qCount api=$paperQ"
    Ab screenshot "$PSScriptRoot/ui-student-detail.png" | Out-Null
}
finally {
    Write-Host '########## 4. cleanup ##########'
    $back = Api POST '/v2/user/login' @{ username = $AdminUser; password = $AdminPwd }
    $tk = ($back.json.data).accessToken
    if ($tk -and $classId) {
        $mine = Api GET '/v2/edu/paper-assign/mine' $null $tk
        foreach ($row in @($mine.json.data)) {
            if ($row.classId -eq $classId -and $row.assignStatus -eq 1) {
                $d = Api DELETE ("/v2/edu/paper-assign/" + $row.assignId) $null $tk
                Write-Host "    revoked assignId=$($row.assignId) ok=$($d.json.ok)"
            }
        }
    }
    if ($tk -and $studentId) { $d = Api DELETE ("/v2/edu/dimension/students/" + $studentId) $null $tk; Write-Host "    deleted studentId=$studentId ok=$($d.json.ok)" }
    if ($tk -and $classId) { $d = Api DELETE ("/v2/edu/dimension/classes/" + $classId) $null $tk; Write-Host "    deleted classId=$classId ok=$($d.json.ok)" }
}

$passed = @($script:Results | Where-Object { $_[0] }).Count
$total = $script:Results.Count
Write-Host ''
Write-Host "===== UI paper-assign acceptance: passed $passed / failed $($total - $passed) ====="
foreach ($r in $script:Results) { if (-not $r[0]) { Write-Host "  FAIL -> $($r[1])  $($r[2])" } }
# Non-zero exit on failure: the release runner only looks at the exit code.
exit $(if ($passed -eq $total) { 0 } else { 1 })
