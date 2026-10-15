# probe-aiprovider-2.ps1 -- one run, everything needed to explain the AI-provider menu:
#   * the REAL sidebar DOM (which container class, how items are marked)
#   * the landing route after opening /edu/ai-provider
#   * whether the embed iframe shows up (polled, not sampled once)
param([string]$Base = 'http://localhost:19093', [string]$User = 'admin', [string]$Pwd = 'admin123')
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$exe = "$env:APPDATA\npm\node_modules\agent-browser\bin\agent-browser-win32-x64.exe"
function Ab { param([string[]]$A, [int]$TimeoutMs = 90000)
    $out = [IO.Path]::GetTempFileName(); $err = "$out.err"
    $p = Start-Process -FilePath $exe -ArgumentList (@('--namespace','eduai-acceptance') + $A) -NoNewWindow -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
    if (-not $p.WaitForExit($TimeoutMs)) { try { $p.Kill() } catch {}; Remove-Item $out,$err -Force -EA SilentlyContinue; return '<TIMEOUT>' }
    Start-Sleep -Milliseconds 300
    $lines = @()
    foreach ($f in @($out,$err)) { for ($i=0;$i -lt 40;$i++){ $d=$false; try { if (Test-Path $f) { $lines += [IO.File]::ReadAllLines($f); $d=$true } else { $d=$true } } catch {}; if ($d) { break }; Start-Sleep -Milliseconds 250 } }
    Remove-Item $out,$err -Force -EA SilentlyContinue
    return (($lines | ForEach-Object { $_.ToString() }) -join "`n").Trim() }
function Js { param([string]$S,[int]$T=90000) $b=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($S)); Ab @('eval','-b',$b) $T }
function WaitJs { param([string]$S,[int]$Sec=45) $end=(Get-Date).AddSeconds($Sec); while((Get-Date) -lt $end){ $r=''; try { $r=[string](Js "!!($S)" 15000) } catch {}; if ($r -match 'true') { return $true }; Start-Sleep -Milliseconds 500 }; return $false }
function OpenPage { param([string]$Url,[int]$Tries=3) for($i=1;$i -le $Tries;$i++){ Ab @('open',$Url) 60000 | Out-Null; if (WaitJs '(document.readyState === "complete") && (location.href.indexOf("chrome-error") < 0) && (location.href.indexOf("blank") < 0)' 25) { return $true }; Ab @('close') 30000 | Out-Null; Start-Sleep -Seconds 2 }; return $false }
function Json { param([string]$S,[int]$T=90000) $r=[string](Js $S $T); for($i=0;$i -lt 3;$i++){ try{$v=$r|ConvertFrom-Json}catch{return $null}; if($v -is [string]){$r=$v;continue}; return $v }; return $null }

# ---- session ----
$ok = $false
if (OpenPage "$Base/home") { $ok = [bool](WaitJs '(location.pathname.indexOf("/login")!==0 && document.querySelector(".el-menu-item")!==null)' 20) }
if (-not $ok) {
    Write-Host '  no session; logging in'
    OpenPage "$Base/login" | Out-Null
    if (-not (WaitJs '(document.querySelector(".slider-verify-track")!==null)' 45)) { Write-Host 'login form not ready'; exit 2 }
    Js '(function(){var t=document.querySelector(".slider-verify-track");var th=t.querySelector(".slider-verify-thumb");var r=t.getBoundingClientRect();var mw=r.width-(th?th.clientWidth:40);var x=r.left+4,y=r.top+r.height/2;window.__sv={mw:mw,x:x,y:y};t.dispatchEvent(new PointerEvent("pointerdown",{bubbles:true,cancelable:true,pointerId:1,clientX:x,clientY:y}));window.dispatchEvent(new PointerEvent("pointermove",{bubbles:true,cancelable:true,pointerId:1,clientX:x+mw+20,clientY:y}));return 1;})()' | Out-Null
    Start-Sleep -Milliseconds 400
    Js '(function(){var s=window.__sv;window.dispatchEvent(new PointerEvent("pointerup",{bubbles:true,cancelable:true,pointerId:1,clientX:s.x+s.mw+20,clientY:s.y}));return 1;})()' | Out-Null
    Ab @('find','placeholder','请输入用户名','fill',$User) 60000 | Out-Null
    Ab @('find','placeholder','请输入密码','fill',$Pwd) 60000 | Out-Null
    Ab @('press','Enter') 60000 | Out-Null
    WaitJs '(location.pathname.indexOf("/login")!==0)' 45 | Out-Null
    WaitJs '(document.querySelector(".el-menu-item")!==null)' 30 | Out-Null
}
Write-Host ('path=' + [string](Js 'location.pathname'))

# ---- 1) real sidebar structure ----
$side = Json @'
(function(){
  var out = {};
  out.asides = Array.prototype.slice.call(document.querySelectorAll('aside, .el-aside')).slice(0,4).map(function(e){
    return { tag:e.tagName, cls:String(e.className||'').slice(0,70) };
  });
  out.elMenuItemCount = document.querySelectorAll('.el-menu-item').length;
  out.elSubMenuCount = document.querySelectorAll('.el-sub-menu__title').length;
  var menu = document.querySelector('.el-menu');
  out.menuCls = menu ? String(menu.className).slice(0,80) : null;
  // every nav-ish clickable with its text, using the classes the framework really renders
  var items = Array.prototype.slice.call(document.querySelectorAll('.el-menu-item, .el-sub-menu__title, .el-menu-item-group__title'));
  out.items = items.slice(0, 40).map(function(e){ return (e.innerText||'').trim().replace(/\s+/g,' ').slice(0,18); });
  out.aiProviderFound = items.some(function(e){ return (e.innerText||'').indexOf('AI 提供商') >= 0; });
  return JSON.stringify(out);
})()
'@
Write-Host '--- sidebar ---'
Write-Host ($side | ConvertTo-Json -Compress -Depth 5)

# ---- 2) open the route and poll for the iframe ----
OpenPage "$Base/edu/ai-provider" | Out-Null
$found = $false
for ($i = 1; $i -le 12; $i++) {
    Start-Sleep -Seconds 2
    $st = Json @'
(function(){
  var f = document.querySelector('iframe');
  var r = f ? f.getBoundingClientRect() : null;
  return JSON.stringify({ path: location.pathname, hasFrame: !!f, src: f? f.getAttribute('src'):'', w: r?Math.round(r.width):0, h: r?Math.round(r.height):0,
                          bodyHead: (document.body.innerText||'').replace(/\s+/g,' ').slice(0,120) });
})()
'@
    Write-Host ("  poll {0}: path={1} hasFrame={2} src={3} {4}x{5}" -f $i, $st.path, $st.hasFrame, $st.src, $st.w, $st.h)
    if ($st.hasFrame) { $found = $true; break }
    if (([string]$st.path) -match '^/login') { Write-Host '  bounced to /login -> session expired'; break }
}
Write-Host ('final: path=' + [string](Js 'location.pathname'))
Write-Host ('iframePresent=' + $found)
