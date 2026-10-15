# ui-teacher-click-acceptance.ps1 -- exhaustive per-element click acceptance for the
# teacher portal (/edu/teacher) and the shared marking workbench (/edu/marking).
#
# "Production grade" here means the same thing the repo's generic crawler
# (ui-full-click-acceptance.ps1) means by it: every visible clickable control gets
# exercised, and a control FAILS on an uncaught page error, a console error, or an HTTP
# >= 500. Two things are different, because the generic crawler could not reach these
# pages on this host (root causes are recorded in the acceptance report, §21.7.4):
#
#   1. The whole sweep runs INSIDE one page script driven by setTimeout, so it costs one
#      IPC round-trip instead of one per element. The daemon is intermittently slow, and
#      per-click IPC made a 60-control page take minutes and time out.
#   2. Login solves the login page's slide-to-verify. It is a pure client check
#      (ReSliderVerify listens for pointerdown on .slider-verify-track plus window
#      pointermove/pointerup, and computes the thumb position inside requestAnimationFrame),
#      so synthetic PointerEvents pass it -- but the move and the up must be on separate
#      ticks or the component reads thumbX before rAF applied it and snaps back to 0.
#
# Usage:
#   pwsh -File ui-teacher-click-acceptance.ps1 -Target teacher
#   pwsh -File ui-teacher-click-acceptance.ps1 -Target marking -Base http://localhost:19093
param(
    [string]$Base = 'http://localhost:19093',
    [ValidateSet('teacher', 'marking')][string]$Target = 'teacher',
    [string]$User = 'admin',
    [string]$Pwd = 'admin123',
    [int]$StepMs = 200,
    [int]$MaxWaitSec = 180
)

$ErrorActionPreference = 'Continue'
$script:AbExe = $null
foreach ($c in (Get-Command agent-browser -All -ErrorAction SilentlyContinue)) {
    if ($c.Source -like '*.exe') { $script:AbExe = $c.Source; break }
    if ($c.Source -like '*.cmd') {
        $exe = Join-Path (Split-Path -Parent $c.Source) 'node_modules\agent-browser\bin\agent-browser-win32-x64.exe'
        if (Test-Path $exe) { $script:AbExe = $exe }
        if (-not $script:AbExe) { $script:AbExe = $c.Source }
    }
}
if (-not $script:AbExe) {
    $exe = "$env:APPDATA\npm\node_modules\agent-browser\bin\agent-browser-win32-x64.exe"
    if (Test-Path $exe) { $script:AbExe = $exe }
}
if (-not $script:AbExe) { Write-Host 'agent-browser not found'; exit 2 }

$script:Pass = 0; $script:Fail = 0; $script:Fails = @()
function Ck { param([string]$N, [bool]$Ok, [string]$E = '')
    if ($Ok) { $script:Pass++; Write-Host ("  PASS  {0}   {1}" -f $N, $E) }
    else { $script:Fail++; $script:Fails += $N; Write-Host ("  FAIL  {0}   {1}" -f $N, $E) } }

# The CLI forks a background daemon that can keep the redirected handles open for a moment
# after the parent exits. An immediate read throws "being used by another process" and the
# caller then sees an EMPTY result, which a crawler reads as "no controls" -- a silent
# false pass. Retry the read, and isolate the session so other agent sessions cannot steal
# the active tab mid-run.
function Ab {
    param([string[]]$A, [int]$TimeoutMs = 120000)
    $out = [IO.Path]::GetTempFileName(); $err = "$out.err"
    $p = Start-Process -FilePath $script:AbExe -ArgumentList (@('--namespace', 'eduai-acceptance') + $A) `
        -NoNewWindow -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
    if (-not $p.WaitForExit($TimeoutMs)) {
        try { $p.Kill() } catch { }
        Start-Sleep -Milliseconds 400
        Remove-Item $out, $err -Force -ErrorAction SilentlyContinue
        return '<TIMEOUT>'
    }
    Start-Sleep -Milliseconds 300
    $lines = @()
    foreach ($f in @($out, $err)) {
        for ($i = 0; $i -lt 40; $i++) {
            $done = $false
            try { if (Test-Path $f) { $lines += [IO.File]::ReadAllLines($f); $done = $true } else { $done = $true } } catch { }
            if ($done) { break }
            Start-Sleep -Milliseconds 250
        }
    }
    Remove-Item $out, $err -Force -ErrorAction SilentlyContinue
    return (($lines | ForEach-Object { $_.ToString() }) -join "`n").Trim()
}

function Js {
    param([string]$S, [int]$T = 120000)
    $b = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($S))
    Ab @('eval', '-b', $b) $T
}

function WaitJs {
    param([string]$S, [int]$Sec = 60)
    $end = (Get-Date).AddSeconds($Sec)
    while ((Get-Date) -lt $end) {
        $r = ''
        try { $r = [string](Js "!!($S)" 15000) } catch { $r = '' }
        if ($r -match 'true') { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function OpenPage {
    param([string]$Url, [int]$Tries = 4)
    for ($i = 1; $i -le $Tries; $i++) {
        Ab @('open', $Url) 60000 | Out-Null
        if (WaitJs '(document.readyState === "complete") && (location.href.indexOf("chrome-error") < 0) && (location.href.indexOf("blank") < 0)' 25) { return $true }
        Ab @('close') 30000 | Out-Null
        Start-Sleep -Seconds 2
    }
    return $false
}

# agent-browser returns the evaluated value JSON-encoded, so a JSON.stringify(...) payload
# arrives as a STRING whose content is the JSON text: unwrap until it is an object.
function Json {
    param([string]$S, [int]$T = 120000)
    $r = [string](Js $S $T)
    for ($i = 0; $i -lt 3; $i++) {
        try { $v = $r | ConvertFrom-Json } catch { return $null }
        if ($v -is [string]) { $r = $v; continue }
        return $v
    }
    return $null
}

# ---------- login (slide-to-verify included) ----------
function Do-Login {
    OpenPage "$Base/login" | Out-Null
    if (-not (WaitJs '(document.querySelector(".slider-verify-track")!==null)' 45)) { return $false }
    Js '(function(){var t=document.querySelector(".slider-verify-track");var th=t.querySelector(".slider-verify-thumb");var r=t.getBoundingClientRect();var mw=r.width-(th?th.clientWidth:40);var x=r.left+4,y=r.top+r.height/2;window.__sv={mw:mw,x:x,y:y};t.dispatchEvent(new PointerEvent("pointerdown",{bubbles:true,cancelable:true,pointerId:1,clientX:x,clientY:y}));window.dispatchEvent(new PointerEvent("pointermove",{bubbles:true,cancelable:true,pointerId:1,clientX:x+mw+20,clientY:y}));return "down";})()' 60000 | Out-Null
    Start-Sleep -Milliseconds 400
    Js '(function(){var s=window.__sv;window.dispatchEvent(new PointerEvent("pointerup",{bubbles:true,cancelable:true,pointerId:1,clientX:s.x+s.mw+20,clientY:s.y}));return "up";})()' 60000 | Out-Null
    Ab @('find', 'placeholder', '请输入用户名', 'fill', $User) 90000 | Out-Null
    Ab @('find', 'placeholder', '请输入密码', 'fill', $Pwd) 90000 | Out-Null
    Ab @('press', 'Enter') 90000 | Out-Null
    if (WaitJs '(location.pathname.indexOf("/login")!==0)' 40) { return $true }
    if (WaitJs '(function(){var b=document.querySelector(".slider-verify");return !!(b && b.className.indexOf("slider-verify--success")>=0);})()' 6) {
        Ab @('click', 'button:has-text("登录")') 60000 | Out-Null
        return [bool](WaitJs '(location.pathname.indexOf("/login")!==0)' 40)
    }
    return $false
}

# ---------- reach the target route ----------
if ($Target -eq 'teacher') {
    $Path = '/edu/teacher'; $ReadySel = '.tp-tabs'
    # The portal is a full-screen fixed layer, so scoping at document.body also picks up the
    # sidebar BEHIND it: clicking one of those navigates, wipes the in-page collector, and the
    # whole run reports nothing. Scope to the portal itself plus any teleported dialog/drawer.
    $Scope = '.tp, .el-overlay'
}
else { $Path = '/edu/marking'; $ReadySel = '.marking-workbench'; $Scope = '.main-content, .el-overlay' }

# A cold load of a layout route can bounce to /login, and the app restores its persisted
# active tab on boot (overriding the URL just opened), so: land on a same-origin page,
# clear the tab / keep-alive keys, make sure a token exists, then open the target and
# verify the URL actually stuck.
$ready = $false
for ($i = 1; $i -le 4 -and -not $ready; $i++) {
    OpenPage "$Base/login" | Out-Null
    Js '(() => { for (let i = localStorage.length - 1; i >= 0; i--) { const k = localStorage.key(i); if (k && (/(^|:)tabs$/.test(k) || /keepAlive/i.test(k))) localStorage.removeItem(k); } return 1; })()' 30000 | Out-Null
    $tok = [string](Js 'String(!!localStorage.getItem("system:accessToken"))' 30000)
    if ($tok -notmatch 'true') { Write-Host ("  attempt {0}: no token, logging in" -f $i); Do-Login | Out-Null }
    OpenPage "$Base$Path" | Out-Null
    if (([string](Js 'location.pathname' 30000)) -match '^/login') {
        Write-Host ("  attempt {0}: bounced to /login, re-login" -f $i)
        Do-Login | Out-Null
        OpenPage "$Base$Path" | Out-Null
    }
    $ready = WaitJs "(document.querySelector('$ReadySel')!==null)" 40
    if (-not $ready) { Write-Host ("  attempt {0}: not ready (path={1})" -f $i, [string](Js 'location.pathname' 30000)) }
}
if (-not $ready) { Write-Host ("page not ready: " + $Path + " path=" + [string](Js 'location.pathname')); exit 2 }
Write-Host ("########## $Target  $Path  (path=" + [string](Js 'location.pathname') + ") ##########")
if ($Target -eq 'teacher') { Start-Sleep -Seconds 3 }

# ---------- exhaustive click sweep, driven in-page ----------
$drive = @"
(function(){
  var scopeSel = '$Scope';
  window.__ca = { done:false, items:[], errs:[], total:0, startPath: location.pathname };
  // Mirror the record into localStorage after every click: one of the controls can navigate
  // (a link, a logout, a menu entry), which destroys any purely in-page collector -- the whole
  // run then reads back as "no result" even though 85 controls were clicked. localStorage
  // survives same-origin navigation, so the outcome is still readable afterwards.
  function persist(){
    try { localStorage.setItem('__ca_log', JSON.stringify(window.__ca)); } catch(e){}
    try { sessionStorage.setItem('__ca_log', JSON.stringify(window.__ca)); } catch(e){}
  }
  persist();
  window.addEventListener('error', function(e){ window.__ca.errs.push('pageerror: ' + ((e && e.message) || 'unknown')); persist(); });
  window.addEventListener('unhandledrejection', function(e){ window.__ca.errs.push('rejection: ' + String((e && e.reason) || 'unknown').slice(0,150)); persist(); });
  var ce = console.error;
  console.error = function(){ try { window.__ca.errs.push('console: ' + Array.prototype.slice.call(arguments).join(' ').slice(0,150)); } catch(x){} return ce.apply(this, arguments); };
  try {
    var xo = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(m,u){ var self=this; self.addEventListener('load', function(){ if (self.status>=500) { window.__ca.errs.push('http' + self.status + ': ' + String(u).slice(0,120)); persist(); } }); return xo.apply(this, arguments); };
  } catch(x){}

  function visible(e){
    if (!e || !e.getBoundingClientRect) return false;
    var r = e.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    var s = getComputedStyle(e);
    return s.visibility !== 'hidden' && s.display !== 'none' && s.opacity !== '0';
  }
  var SEL = 'button, [role=button], a[href], .el-select, .el-switch, .el-radio, .el-checkbox, ' +
            '.el-tabs__item, .el-pager li, .el-pagination button, .rm-pager__btn, .rm-pager__size-btn, ' +
            '.el-table .el-button, .el-dialog .el-button, .el-drawer .el-button, .el-input__icon, ' +
            '.el-select__caret, [class*=icon-btn], svg[class*=icon]';
  var roots = Array.prototype.slice.call(document.querySelectorAll(scopeSel));
  if (!roots.length) roots = [document.body];
  var nodes = [];
  roots.forEach(function(root){
    Array.prototype.slice.call(root.querySelectorAll(SEL)).forEach(function(e){ nodes.push(e); });
  });
  nodes = nodes.filter(function(e, i){ return nodes.indexOf(e) === i && visible(e); });
  // de-duplicate nested matches: an svg inside a button is the same control
  var kept = [];
  nodes.forEach(function(e){
    for (var i = 0; i < kept.length; i++) { if (kept[i].contains(e)) return; }
    kept.push(e);
  });
  nodes = kept;
  nodes.forEach(function(e, i){
    e.setAttribute('data-ca', String(i));
    window.__ca.items.push({ i:i, tag:e.tagName, txt:(e.innerText||e.textContent||'').trim().replace(/\s+/g,' ').slice(0,24),
                             cls:String(e.className||'').slice(0,60), res:'pending', errs:[] });
  });
  window.__ca.total = nodes.length;
  persist();

  var i = 0;
  function step(){
    if (i >= nodes.length) { window.__ca.done = true; persist(); return; }
    // a click that navigates kills this loop; record where it stopped
    if (location.pathname !== window.__ca.startPath) {
      window.__ca.navigatedTo = location.pathname; window.__ca.done = true; persist(); return;
    }
    var rec = window.__ca.items[i];
    var n0 = window.__ca.errs.length;
    var el = document.querySelector('[data-ca="' + i + '"]');
    if (!el) { rec.res = 'gone'; }
    else { try { el.click(); rec.res = 'ok'; } catch(e){ rec.res = 'err'; } }
    setTimeout(function(){
      rec.errs = window.__ca.errs.slice(n0);
      // dismiss overlays by Escape / the close glyph -- never by matching a label, so no
      // destructive confirmation can be triggered
      try { document.dispatchEvent(new KeyboardEvent('keydown', { key:'Escape', bubbles:true })); } catch(e){}
      try { var m = document.querySelector('.el-overlay'); if (m) { var close = m.querySelector('.el-dialog__headerbtn, .el-drawer__close-btn'); if (close) close.click(); } } catch(e){}
      i++;
      persist();
      setTimeout(step, $StepMs);
    }, $StepMs);
  }
  step();
  return 'driving:' + nodes.length;
})()
"@
Write-Host ("  inventory: " + [string](Js $drive 120000))

$waited = 0
while ($waited -lt $MaxWaitSec) {
    Start-Sleep -Seconds 3
    $waited += 3
    if (([string](Js '(function(){try{var v=localStorage.getItem("__ca_log")||sessionStorage.getItem("__ca_log");if(!v)return "0";var o=JSON.parse(v);return o && o.done ? "1" : "0";}catch(e){return "0";}})()' 30000)) -match '1') { break }
}
# read the mirrored record (survives a navigation that killed the in-page collector)
$res = Json '(function(){try{return localStorage.getItem("__ca_log")||sessionStorage.getItem("__ca_log")||"null";}catch(e){return "null";}})()' 60000
if (-not $res) { $res = Json 'JSON.stringify(window.__ca || null)' 120000 }
if (-not $res) { Write-Host 'no result'; exit 2 }

$items = @($res.items)
$withErr = @($items | Where-Object { @($_.errs).Count -gt 0 })
$gone = @($items | Where-Object { $_.res -eq 'gone' })
$pend = @($items | Where-Object { $_.res -eq 'pending' })
Write-Host ("  clicks={0}  done={1}  withError={2}  gone={3}  pending={4}" -f $items.Count, $res.done, $withErr.Count, $gone.Count, $pend.Count)
if ($res.navigatedTo) { Write-Host ("  NOTE: a control navigated away to {0}; the sweep stopped there and the record was read from localStorage" -f $res.navigatedTo) }
if ($withErr.Count) {
    Write-Host '  --- produced an error ---'
    foreach ($it in $withErr) { Write-Host ("    [{0}] {1} '{2}' -> {3}" -f $it.i, $it.tag, $it.txt, (@($it.errs) -join ' | ')) }
}
if ($gone.Count) {
    Write-Host '  --- vanished before click (re-render race) ---'
    foreach ($it in $gone) { Write-Host ("    [{0}] {1} '{2}'" -f $it.i, $it.tag, $it.txt) }
}

Ck '页面存在可点击控件（清单非空）' ($items.Count -gt 0) ("total=" + $items.Count)
Ck '全部元素都完成了点击（无 pending）' ($pend.Count -eq 0) ("pending=" + $pend.Count)
Ck '点击过程中无 页面错误/console 错误/HTTP 5xx' ($withErr.Count -eq 0) ("bad=" + $withErr.Count)

Write-Host ''
Write-Host ('########## RESULT[{0}]: clicks={1} pass={2} fail={3} ##########' -f $Target, $items.Count, $script:Pass, $script:Fail)
if ($script:Fails.Count) { Write-Host ('failed: ' + ($script:Fails -join '; ')) }
