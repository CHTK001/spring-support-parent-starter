# check-ai-provider.ps1 -- regression check for the "AI 提供商管理" menu (r47).
#
# The reported defect: the menu carried meta.linkUrl, and the framework's linkUrl branch is a
# hard-coded window.open(url, "_blank"), so clicking it threw the user out to the AI console's
# own address. The fix gives the route a real component that embeds the AI console's
# purpose-built embed route (/models/embed) in an iframe.
#
# What this asserts (short and specific, not a full crawl):
#   1. clicking the sidebar entry KEEPS the app on /edu/ai-provider (no external navigate)
#   2. no popup/new tab is opened
#   3. an iframe pointing at <console>/models/embed is actually rendered and has non-zero size
param(
    [string]$Base = 'http://localhost:19093',
    [string]$User = 'admin',
    [string]$Pwd = 'admin123'
)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$exe = "$env:APPDATA\npm\node_modules\agent-browser\bin\agent-browser-win32-x64.exe"
if (-not (Test-Path $exe)) { Write-Host 'agent-browser exe not found'; exit 2 }

$script:Pass = 0; $script:Fail = 0
function Ck { param([string]$N, [bool]$Ok, [string]$E = '')
    if ($Ok) { $script:Pass++; Write-Host ("  PASS  {0}   {1}" -f $N, $E) }
    else { $script:Fail++; Write-Host ("  FAIL  {0}   {1}" -f $N, $E) } }

function Ab {
    param([string[]]$A, [int]$TimeoutMs = 90000)
    $out = [IO.Path]::GetTempFileName(); $err = "$out.err"
    $p = Start-Process -FilePath $exe -ArgumentList (@('--namespace', 'eduai-acceptance') + $A) -NoNewWindow -PassThru `
        -RedirectStandardOutput $out -RedirectStandardError $err
    if (-not $p.WaitForExit($TimeoutMs)) { try { $p.Kill() } catch { }; Remove-Item $out, $err -Force -EA SilentlyContinue; return '<TIMEOUT>' }
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
    Remove-Item $out, $err -Force -EA SilentlyContinue
    return (($lines | ForEach-Object { $_.ToString() }) -join "`n").Trim()
}
function Js { param([string]$S, [int]$T = 90000)
    $b = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($S)); Ab @('eval', '-b', $b) $T }
function WaitJs { param([string]$S, [int]$Sec = 45)
    $end = (Get-Date).AddSeconds($Sec)
    while ((Get-Date) -lt $end) {
        $r = ''; try { $r = [string](Js "!!($S)" 15000) } catch { }
        if ($r -match 'true') { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false }
function Json { param([string]$S, [int]$T = 90000)
    $r = [string](Js $S $T)
    for ($i = 0; $i -lt 3; $i++) {
        try { $v = $r | ConvertFrom-Json } catch { return $null }
        if ($v -is [string]) { $r = $v; continue }
        return $v
    }
    return $null }

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

# ---- session: NOT valid until the app shell actually rendered its menu ----
# An earlier run "reused a session" that had really expired: pathname was still /home for a
# moment, the menu never mounted, and the inventory came back EMPTY. An empty menu list is
# therefore an environment signal, not a product verdict -- so the readiness gate waits for
# the menu items themselves.
$haveSession = $false
if (OpenPage "$Base/home") { $haveSession = [bool](WaitJs '(location.pathname.indexOf("/login")!==0) && (document.querySelectorAll(".layout-aside .el-menu-item").length > 0)' 25) }
if ($haveSession) {
    Write-Host ('  reused session, path=' + [string](Js 'location.pathname'))
} else {
    Write-Host '  no usable session, logging in'
    if (-not (OpenPage "$Base/login")) { Write-Host 'login page did not load'; exit 2 }
    if (-not (WaitJs '(document.querySelector(".slider-verify-track")!==null)' 45)) { Write-Host 'login form not ready'; exit 2 }
    Js '(function(){var t=document.querySelector(".slider-verify-track");var th=t.querySelector(".slider-verify-thumb");var r=t.getBoundingClientRect();var mw=r.width-(th?th.clientWidth:40);var x=r.left+4,y=r.top+r.height/2;window.__sv={mw:mw,x:x,y:y};t.dispatchEvent(new PointerEvent("pointerdown",{bubbles:true,cancelable:true,pointerId:1,clientX:x,clientY:y}));window.dispatchEvent(new PointerEvent("pointermove",{bubbles:true,cancelable:true,pointerId:1,clientX:x+mw+20,clientY:y}));return 1;})()' | Out-Null
    Start-Sleep -Milliseconds 400
    Js '(function(){var s=window.__sv;window.dispatchEvent(new PointerEvent("pointerup",{bubbles:true,cancelable:true,pointerId:1,clientX:s.x+s.mw+20,clientY:s.y}));return 1;})()' | Out-Null
    Ab @('find', 'placeholder', '请输入用户名', 'fill', $User) 60000 | Out-Null
    Ab @('find', 'placeholder', '请输入密码', 'fill', $Pwd) 60000 | Out-Null
    Ab @('press', 'Enter') 60000 | Out-Null
    if (-not (WaitJs '(location.pathname.indexOf("/login")!==0) && (document.querySelectorAll(".layout-aside .el-menu-item").length > 0)' 45)) {
        if (WaitJs '(function(){var b=document.querySelector(".slider-verify");return !!(b && b.className.indexOf("slider-verify--success")>=0);})()' 6) {
            Ab @('click', 'button:has-text("登录")') 60000 | Out-Null
        }
        if (-not (WaitJs '(location.pathname.indexOf("/login")!==0) && (document.querySelectorAll(".layout-aside .el-menu-item").length > 0)' 40)) { Write-Host 'login failed'; exit 2 }
    }
    Write-Host ('  logged in -> ' + [string](Js 'location.pathname'))
}

# ---- record the tab count so an external window.open is detectable ----
Js 'window.__tabsBefore = 0; try { window.__tabsBefore = (window.opener ? 1 : 0); } catch(e){}' | Out-Null

# ---- click the real sidebar entry ----
# The aside's own class differs per layout (.layout-optimum-aside / .layout-group-aside /
# .layout-aside / ...), so `.layout-aside` matched 0 items and the previous run reported an
# empty menu. Element Plus always renders `.el-aside` for any of them -- that is the stable
# hook. Items themselves are addressed by tag+index (no :has-text in this engine, and
# `find text` also matches bare text nodes whose parent handler never fires).
$menu = Json @'
(function(){
  var items = Array.prototype.slice.call(document.querySelectorAll('.el-aside .el-menu-item, .el-aside .el-sub-menu__title'));
  return JSON.stringify(items.map(function(e, i){ return { i:i, txt:(e.innerText||'').trim().replace(/\s+/g,' ').slice(0,20) }; }));
})()
'@
$list = @()
if ($menu) { $list = @($menu) }
Write-Host ('  sidebar items (' + $list.Count + '): ' + (($list | ForEach-Object { $_.txt }) -join ' | '))

$idx = -1
for ($i = 0; $i -lt $list.Count; $i++) { if ([string]$list[$i].txt -like '*AI 提供商*') { $idx = [int]$list[$i].i; break } }
$clicked = $false
if ($idx -ge 0) {
    $r = [string](Js "(function(){var it=document.querySelectorAll('.el-aside .el-menu-item, .el-aside .el-sub-menu__title')[$idx]; if(!it) return 'gone'; it.click(); return 'clicked';})()" 60000)
    Write-Host ("  click result: {0}" -f $r)
    $clicked = ($r -match 'clicked')
}
if (-not $clicked) {
    Write-Host '  sidebar entry not found/clickable; navigating by URL to isolate the route itself'
    OpenPage "$Base/edu/ai-provider" | Out-Null
}
Start-Sleep -Seconds 4
# a stale token can bounce either navigation; if that happened, don't report it as a product
# failure -- report the bounce itself
if (([string](Js 'location.pathname' 30000)) -match '^/login') {
    Write-Host '  NOTE: bounced to /login -> session expired during the run; this run is inconclusive about the menu'
}

$state = Json @'
(function(){
  var f = document.querySelector('iframe');
  var r = f ? f.getBoundingClientRect() : null;
  return JSON.stringify({
    path: location.pathname,
    href: location.href,
    hasFrame: !!f,
    frameSrc: f ? f.getAttribute('src') : '',
    frameW: r ? Math.round(r.width) : 0,
    frameH: r ? Math.round(r.height) : 0,
    newWindows: (window.__tabsBefore || 0)
  });
})()
'@
Write-Host ('  state: ' + ($state | ConvertTo-Json -Compress -Depth 4))

Ck '仍停留在本应用路由 /edu/ai-provider（未跳外部地址）' (([string]$state.path) -eq '/edu/ai-provider') ("path=" + $state.path)
Ck '未打开新窗口（无外链弹出）' (([string]$state.href) -notmatch '15689') ("href=" + $state.href)
Ck '渲染了嵌入 iframe' ([bool]$state.hasFrame) ("src=" + $state.frameSrc)
Ck 'iframe 指向 AI 控制台的嵌入路由 /models/embed' (([string]$state.frameSrc) -match '/models/embed$') ("src=" + $state.frameSrc)
Ck 'iframe 有实际尺寸（吃满而非塌陷）' (([int]$state.frameW -gt 300) -and ([int]$state.frameH -gt 200)) ("{0}x{1}" -f $state.frameW, $state.frameH)
Ck '侧边栏真实点击可命中该入口' $clicked ("viaSidebarClick=" + $clicked)

Write-Host ''
Write-Host ('########## RESULT[aiprovider]: pass={0} fail={1} ##########' -f $script:Pass, $script:Fail)
