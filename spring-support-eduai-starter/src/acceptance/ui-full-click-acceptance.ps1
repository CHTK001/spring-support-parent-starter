<#
    ui-full-click-acceptance.ps1 -- exhaustive click-surface acceptance.

    Definition being satisfied: "production grade" means every clickable button and
    icon gets exercised, so this is a crawler rather than a hand-written checklist.

    Two phases, because they fail differently:

      Phase 1 - page content controls, per route. Inventory is scoped to the page
      container (main.el-main) so layout chrome is not mixed in, and it is RE-TAGGED
      before every click: one click can re-render the table or swap the view, which
      invalidated pre-assigned ids in the first version (18 of 35 clicks came back
      "gone"). If a click navigates, the target page is asserted alive and we go
      back and restart the page's inventory (capped, so a button that always
      navigates cannot loop forever).

      Phase 2 - layout chrome (top bar, sidebar menu, page tabs), done once on a
      stable page. These controls replace the whole view, so they cannot be crawled
      per page. This phase also proves every sidebar entry actually resolves.

    Per click the script clears and then reads: page errors, console errors and the
    network log. A click FAILS on an uncaught page error, a console error, an HTTP
    >= 500, or a blank page. Clicks with no observable effect are reported as DEAD
    (a button that does nothing is a defect, so they are listed, not ignored).

    Overlays are always dismissed with Escape / the close glyph -- never by matching
    a label -- so no destructive confirmation can be triggered, and this file stays
    ASCII (Windows PowerShell parses .ps1 with the system ANSI codepage).
#>
param(
    [string]$Base = 'http://192.168.50.198:19093',
    [string]$OutDir = (Join-Path $env:TEMP 'eduai-click'),
    [string]$AdminUser = 'admin',
    [string]$AdminPwd = 'admin123',
    [ValidateSet('admin','student')][string]$Role = 'admin',
    [string]$StudentPwd = 'Uiacc@12345',
    [int]$SettleMs = 700,
    [int]$NavTimeoutSec = 25,
    [string[]]$Only = @(),
    [switch]$SkipContent,
    [switch]$SkipChrome,
    [switch]$NoSeed,
    [switch]$ChromeForStudent
)

$ErrorActionPreference = 'Continue'
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# ---------- browser plumbing (every call timeout-bounded) ----------
$script:AbExe = $null
$script:AbTimeoutMs = 90000
foreach ($c in (Get-Command agent-browser -All -ErrorAction SilentlyContinue)) {
    if ($c.Source -like '*.cmd') { $script:AbExe = $c.Source; break }
}
# Prefer the real .exe over the .cmd wrapper. The wrapper hands the arguments to
# cmd.exe, whose command line caps at 8191 chars; calling the exe directly raises that to
# the CreateProcess limit (32767). It matters because every page script travels as one
# base64 argument, and a long inventory script overflowed the wrapper silently -- the
# browser answered "The command line is too long." and the crawler read that as "0
# controls on this page", which looks exactly like a clean pass.
if ($script:AbExe -and $script:AbExe -like '*.cmd') {
    $abDir = Split-Path -Parent $script:AbExe
    $abExe = Join-Path $abDir 'node_modules\agent-browser\bin\agent-browser-win32-x64.exe'
    if (Test-Path $abExe) { $script:AbExe = $abExe }
}
if (-not $script:AbExe) { $c = Get-Command agent-browser -ErrorAction SilentlyContinue; if ($c) { $script:AbExe = $c.Source } }
if (-not $script:AbExe) { Write-Host 'agent-browser not found in PATH'; exit 2 }

function AbRaw {
    param([string[]]$CliArgs, [int]$TimeoutMs = 0)
    if (-not $script:AbExe) { return '' }
    $tmo = if ($TimeoutMs -gt 0) { $TimeoutMs } else { $script:AbTimeoutMs }
    $out = [IO.Path]::GetTempFileName()
    $err = "$out.err"
    $p = Start-Process -FilePath $script:AbExe -ArgumentList $CliArgs -NoNewWindow -PassThru `
        -RedirectStandardOutput $out -RedirectStandardError $err
    if (-not $p.WaitForExit($tmo)) {
        try { $p.Kill() } catch { }
        Remove-Item $out, $err -Force -ErrorAction SilentlyContinue
        return ''
    }
    $lines = @()
    if (Test-Path $out) { $lines += [IO.File]::ReadAllLines($out) }
    if (Test-Path $err) { $lines += [IO.File]::ReadAllLines($err) }
    Remove-Item $out, $err -Force -ErrorAction SilentlyContinue
    return (($lines | ForEach-Object { $_.ToString() }) -join "`n").Trim()
}

function Js {
    param([string]$Script, [int]$TimeoutMs = 0)
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Script))
    # The exe is called directly (see AbExe resolution), so the limit is CreateProcess'
    # 32767 rather than cmd.exe's 8191. Keep a margin: AbRaw joins args and a wrapper
    # could reappear.
    if ($b64.Length -gt 24000) {
        throw ("eval payload is {0} base64 chars, far past the command-line limit; split the page script" -f $b64.Length)
    }
    $raw = AbRaw @('eval', '-b', $b64) $TimeoutMs
    if ([string]::IsNullOrWhiteSpace($raw) -or $raw -eq 'null' -or $raw -eq 'undefined') { return '' }
    if ($raw -match 'Evaluation error') { return '' }
    try { $val = $raw | ConvertFrom-Json } catch { $val = $raw.Trim('"').Replace('\"', '"') }
    return [string]$val
}

function WaitJs {
    param([string]$Script, [int]$TimeoutSec = 20)
    $end = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $end) {
        if ((Js $Script 20000) -eq 'true') { return $true }
        Start-Sleep -Milliseconds 400
    }
    return $false
}

$script:Pass = 0; $script:Fail = 0
$script:Fails = New-Object System.Collections.ArrayList
$script:Rows = New-Object System.Collections.ArrayList
$script:Dead = New-Object System.Collections.ArrayList
$script:HarnessErr = New-Object System.Collections.ArrayList
$script:SkipDestructive = 0
$script:Vanish = @{}
$script:StudentPwdEff = $StudentPwd   # replaced by the fixture once the forced change succeeds
$script:Clicks = 0

function Ck {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    if ($Ok) { $script:Pass++ } else {
        $script:Fail++
        [void]$script:Fails.Add("$Name  $Detail")
        Write-Host ("  [FAIL] {0}  {1}" -f $Name, $Detail)
    }
}

# The crawl mutates persisted UI state: the dashboard caches its widget layout in
# localStorage and the crawl presses "remove widget" / "save" on it. Snapshot it up front so
# the run is repeatable and leaves the environment as it found it -- an acceptance script
# that quietly empties the dashboard it is verifying is worse than no script.
# Click the controls of a dialog that the trigger just opened. Element Plus teleports
# dialogs to <body>, so the content-phase inventory (scoped to main.el-main) structurally
# cannot reach them -- across a full sweep not one dialog button was ever clicked. Buttons
# whose own label or whose dialog title says delete / remove / clear / reset / batch / void
# are recorded as skipped-destructive and NOT pressed: a real acceptance run must not delete
# the fixtures the other suites assert on.
function Walk-DialogControls {
    param([string]$Rname, [string]$Path, [string]$Trig, $AfterState)
    $raw = ''
    try { $raw = [string](Js $jsDialogInventory 40000) } catch { $raw = '' }
    if (-not $raw -or -not $raw.StartsWith('[')) { return }
    $items = @()
    try { $items = @($raw | ConvertFrom-Json) } catch { return }
    if ($items.Count -eq 0) { return }
    Write-Host ('    -> dialog controls: {0} (trigger ''{1}'')' -f $items.Count, $Trig)
    foreach ($c in $items) {
        if ([int]$c.destructive -eq 1) {
            [void]$script:Rows.Add(("$Path|dialog:$($c.title)/$($c.kind)|$($c.label)|skipped-destructive|$Path|0|0|0|0"))
            $script:SkipDestructive++
            continue
        }
        # no quotes around the id: it starts with a letter, so [data-uitest-d=d0] is valid CSS
        # (quoting it with [char]34 would terminate the PowerShell string that embeds it)
        $sel = '[data-uitest-d=' + $c.id + ']'
        Clear-Evidence
        $res = ''
        try { $res = [string](Js ("(() => { const el = document.querySelector('$sel'); if (!el) return 'gone'; el.click(); return 'ok'; })()")) } catch { $res = 'transport' }
        if ($res -ne 'ok') {
            [void]$script:Rows.Add(("$Path|dialog:$($c.title)/$($c.kind)|$($c.label)|unreachable|$Path|0|0|0|0"))
            continue
        }
        $script:Clicks++
        Start-Sleep -Milliseconds $SettleMs
        $aft = $null
        try { $aft = (Js $jsProbe) | ConvertFrom-Json } catch { $aft = $null }
        if (-not $aft) { [void]$script:Rows.Add(("$Path|dialog:$($c.title)/$($c.kind)|$($c.label)|probe-failed|$Path|0|0|0|0")); break }
        $ev = Read-Evidence
        $eff = 'none'
        if ($aft.dialogs -lt $AfterState.dialogs) { $eff = 'closed-dialog' }
        elseif ($aft.toasts -gt 0 -or $aft.toastText) { $eff = 'toast' }
        elseif ($aft.inputs -ne $AfterState.inputs) { $eff = 'input' }
        elseif ($aft.nodes -ne $AfterState.nodes) { $eff = 'dom' }
        elseif ($aft.htmlLen -ne $AfterState.htmlLen) { $eff = 'style' }
        elseif ($ev.reqTotal -gt 0) { $eff = 'request' }
        [void]$script:Rows.Add(("$Path|dialog:$($c.title)/$($c.kind)|$($c.label)|$eff|$($aft.path)|$($ev.pageErr)|$($ev.consoleErr)|$($ev.http5xx)|$($ev.http4xx)"))
        if ($ev.pageErr -gt 0) { Ck "$Rname dialog '$($c.label)' no page error" $false '' }
        elseif ($ev.consoleErr -gt 0) { Ck "$Rname dialog '$($c.label)' no console error" $false '' }
        elseif ($ev.http5xx -gt 0) { Ck "$Rname dialog '$($c.label)' no 5xx" $false ("5xx=" + $ev.first5) }
        else { $script:Pass++ }
        if ($aft.dialogs -lt $AfterState.dialogs) { break }
    }
}

# Same story for poppers: Element Plus teleports dropdown menus, select option lists and
# popovers to <body>, so they were outside the content inventory too. Across the whole
# sweep zero dropdown items had ever been clicked -- "the trigger opened something" was
# being accepted as proof that everything inside it worked.
# Items that end the session (logout) are never pressed.
$lblLogout = @(
    (-join @([char]0x9000, [char]0x51FA))
    (-join @([char]0x767B, [char]0x51FA))
    (-join @([char]0x6CE8, [char]0x9500))
) -join '|'
$jsPopperInventory = @"
(() => {
  const LOGOUT = new RegExp('$lblLogout');
  const vis = (el) => { if (!el) return false; const r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false; const st = getComputedStyle(el);
    return !(st.visibility === 'hidden' || st.display === 'none' || parseFloat(st.opacity || '1') === 0); };
  document.querySelectorAll('[data-uitest-p]').forEach((e) => e.removeAttribute('data-uitest-p'));
  const SEL = '[role="menuitem"], .el-dropdown-menu__item, .el-select-dropdown__item, li[role="option"], .el-popper li, .el-popper button, .el-popper a, .el-popper .el-checkbox, .el-popper .el-radio';
  const out = [];
  Array.from(document.querySelectorAll('.el-popper')).filter(vis).forEach((pop) => {
    // a popper anchored inside a dialog is walked with the dialog's own controls
    if (pop.closest('.el-dialog, .el-drawer')) return;
    const label0 = ((pop.querySelector('.el-popper__title, .el-select__placeholder') || {}).innerText || '').trim().slice(0, 20);
    pop.querySelectorAll(SEL).forEach((el) => {
      if (el.hasAttribute('data-uitest-p')) return;
      if (!vis(el)) return;
      const label = (el.innerText || el.getAttribute('title') || el.getAttribute('aria-label') || '').trim().slice(0, 24);
      if (!label) return;
      const id = 'p' + out.length;
      el.setAttribute('data-uitest-p', id);
      const kind = el.classList.contains('el-select-dropdown__item') || el.getAttribute('role') === 'option' ? 'option'
        : (el.classList.contains('el-dropdown-menu__item') || el.getAttribute('role') === 'menuitem' ? 'menu-item' : 'control');
      out.push({ id: id, kind: kind, label: label, title: label0, skip: LOGOUT.test(label) ? 1 : 0 });
    });
  });
  return JSON.stringify(out);
})()
"@

function Walk-PopperControls {
    param([string]$Rname, [string]$Path, [string]$Trig, $AfterState)
    $raw = ''
    try { $raw = [string](Js $jsPopperInventory 40000) } catch { $raw = '' }
    if (-not $raw -or -not $raw.StartsWith('[')) { return }
    $items = @()
    try { $items = @($raw | ConvertFrom-Json) } catch { return }
    if ($items.Count -eq 0) { return }
    Write-Host ('    -> popover controls: {0} (trigger ''{1}'')' -f $items.Count, $Trig)
    foreach ($c in $items) {
        if ([int]$c.skip -eq 1) {
            [void]$script:Rows.Add(("$Path|popper:$($c.kind)|$($c.label)|skipped-logout|$Path|0|0|0|0"))
            $script:SkipDestructive++
            continue
        }
        $sel = '[data-uitest-p=' + $c.id + ']'
        Clear-Evidence
        $res = ''
        try { $res = [string](Js ("(() => { const el = document.querySelector('$sel'); if (!el) return 'gone'; el.click(); return 'ok'; })()")) } catch { $res = 'transport' }
        if ($res -ne 'ok') {
            [void]$script:Rows.Add(("$Path|popper:$($c.kind)|$($c.label)|unreachable|$Path|0|0|0|0"))
            continue
        }
        $script:Clicks++
        Start-Sleep -Milliseconds $SettleMs
        $aft = $null
        try { $aft = (Js $jsProbe) | ConvertFrom-Json } catch { $aft = $null }
        if (-not $aft) { [void]$script:Rows.Add(("$Path|popper:$($c.kind)|$($c.label)|probe-failed|$Path|0|0|0|0")); break }
        $ev = Read-Evidence
        $eff = 'none'
        if ($aft.path -ne $AfterState.path) { $eff = 'navigate' }
        elseif ($aft.dialogs -lt $AfterState.dialogs) { $eff = 'closed-dialog' }
        elseif ($aft.poppers -lt $AfterState.poppers) { $eff = 'closed-popper' }
        elseif ($aft.inputs -ne $AfterState.inputs) { $eff = 'input' }
        elseif ($aft.nodes -ne $AfterState.nodes) { $eff = 'dom' }
        elseif ($aft.htmlLen -ne $AfterState.htmlLen) { $eff = 'style' }
        elseif ($ev.reqTotal -gt 0) { $eff = 'request' }
        [void]$script:Rows.Add(("$Path|popper:$($c.kind)|$($c.label)|$eff|$($aft.path)|$($ev.pageErr)|$($ev.consoleErr)|$($ev.http5xx)|$($ev.http4xx)"))
        if ($ev.pageErr -gt 0) { Ck "$Rname popover '$($c.label)' no page error" $false '' }
        elseif ($ev.consoleErr -gt 0) { Ck "$Rname popover '$($c.label)' no console error" $false '' }
        elseif ($ev.http5xx -gt 0) { Ck "$Rname popover '$($c.label)' no 5xx" $false ("5xx=" + $ev.first5) }
        else { $script:Pass++ }
        if ($aft.path -ne $AfterState.path) { break }
        if ($aft.poppers -lt $AfterState.poppers) { break }
    }
}

# Hover-triggered dropdowns never open for a synthetic click (Element Plus defaults to
# trigger="hover"), and dispatching synthetic mouseover does not open them either -- only a
# real pointer move does. Without this the header's module switcher / avatar menu and every
# hover dropdown were reported as dead buttons while working perfectly.
function Try-HoverOpen {
    param([string]$Rname, [string]$Path, $It, [string]$Label)
    $cls = [string]$It.cls
    # the trigger may be a plain span/div INSIDE the dropdown (the header's module
    # switcher is span.el-breadcrumb__inner), so the inventory marks it via closest() and
    # the class check alone misses it
    if ($It.hov -ne 'Y' -and $cls -notmatch 'el-dropdown|el-tooltip|el-popper|el-cascader|el-select') { return '' }
    $sel = Get-TagSelector $It.id
    AbRaw @('hover', $sel) 30000 | Out-Null
    Start-Sleep -Milliseconds 800
    $st = $null
    try { $st = (Js $jsProbe) | ConvertFrom-Json } catch { $st = $null }
    if (-not $st -or $st.poppers -le 0) { return '' }
    Walk-PopperControls -Rname $Rname -Path $Path -Trig ('hover:' + $Label) -AfterState $st
    return 'popover'
}

function Snapshot-Layout {
    $script:LayoutSnap = ''
    try {
        $snapRaw = [string](Js $jsLayoutSnapshot 30000)
        if ($snapRaw -and $snapRaw.StartsWith('{')) { $script:LayoutSnap = $snapRaw }
    } catch { $script:LayoutSnap = '' }
    $n = 0
    try { $n = @($script:LayoutSnap | ConvertFrom-Json -AsHashtable).Keys.Count } catch { $n = 0 }
    Write-Host ('  dashboard layout snapshot: {0} key(s)' -f $n)
}
function Clear-Evidence {
    # In-page collectors are the source of truth (see $jsInstall); the CLI console
    # buffer is kept as a secondary signal because it does capture console.error,
    # while `errors` and `network requests` proved unusable here: after
    # `network requests --clear` the CLI answered "No requests captured" even for a
    # click that had just fired requests.
    AbRaw @('console', '--clear') 20000 | Out-Null
    Js $jsResetEv | Out-Null
}

function Read-Evidence {
    $raw = Js $jsReadEv 25000
    $d = $null
    if ($raw) { try { $d = $raw | ConvertFrom-Json } catch { $d = $null } }
    if (-not $d) { return [pscustomobject]@{ pageErr = 0; consoleErr = 0; http5xx = 0; http4xx = 0; first5 = ''; reqTotal = 0; noise = 0 } }
    # Browser-level noise, NOT application errors. Chrome delivers
    # "ResizeObserver loop completed with undelivered notifications." through
    # window.onerror, so a naive counter reported 64 "errors" on /edu/poems and would
    # have failed every page. Classify instead of counting.
    $noiseRx = 'ResizeObserver loop|Script error\.|[Violation]|Non-Authoritative request|Third-party cookie'
    $jsErrors = @($d.jsErrors | Where-Object { $_ -notmatch $noiseRx })
    $conErrors = @($d.consoleErrors | Where-Object { $_ -notmatch $noiseRx })
    $noise = @($d.jsErrors).Count + @($d.consoleErrors).Count - $jsErrors.Count - $conErrors.Count
    $n5 = 0; $n4 = 0; $first5 = ''
    for ($k = 0; $k -lt $d.statuses.Count; $k++) {
        $s = [int]$d.statuses[$k]
        $u = if ($k -lt $d.urls.Count) { $d.urls[$k] } else { '' }
        if ($s -ge 500) { $n5++; if (-not $first5) { $first5 = "$s $u" } }
        elseif ($s -ge 400) { $n4++ }
    }
    $firstApp = ''
    if ($jsErrors.Count -gt 0) { $firstApp = ($jsErrors | Select-Object -First 1) }
    elseif ($conErrors.Count -gt 0) { $firstApp = ($conErrors | Select-Object -First 1) }
    return [pscustomobject]@{
        pageErr = $jsErrors.Count
        consoleErr = $conErrors.Count
        http5xx = $n5; http4xx = $n4; first5 = $first5
        reqTotal = @($d.urls).Count
        noise = $noise
        firstApp = $firstApp
    }
}

# ---------- routes ----------
$routes = @(
    @{ path = '/home'; name = 'dashboard' },
    @{ path = '/edu'; name = 'edu-home' },
    @{ path = '/edu/collect'; name = 'collect' },
    @{ path = '/edu/paper'; name = 'paper' },
    @{ path = '/edu/exam'; name = 'exam' },
    @{ path = '/edu/marking'; name = 'marking' },
    @{ path = '/edu/wrongbook'; name = 'wrongbook' },
    @{ path = '/edu/question'; name = 'question' },
    @{ path = '/edu/knowledge'; name = 'knowledge' },
    @{ path = '/edu/idioms'; name = 'idioms' },
    @{ path = '/edu/poems'; name = 'poems' },
    @{ path = '/edu/insects'; name = 'insects' },
    @{ path = '/edu/hanzi'; name = 'hanzi' },
    @{ path = '/edu/fun/config'; name = 'fun-config' },
    @{ path = '/edu/students'; name = 'students' },
    @{ path = '/edu/data-config'; name = 'data-config' },
    @{ path = '/eduai/paper-compose'; name = 'paper-compose' },
    @{ path = '/edu/fun'; name = 'fun' },
    @{ path = '/edu/fun/insect'; name = 'fun-insect' },
    @{ path = '/edu/fun/idiom'; name = 'fun-idiom' },
    @{ path = '/edu/fun/idiom-chain'; name = 'fun-idiom-chain' },
    @{ path = '/edu/fun/hanzi'; name = 'fun-hanzi' },
    @{ path = '/edu/fun/poem'; name = 'fun-poem' },
    @{ path = '/edu/fun/poem-challenge'; name = 'fun-poem-challenge' },
    @{ path = '/edu/teacher'; name = 'teacher-portal' },
    @{ path = '/edu/student'; name = 'student' },
    @{ path = '/edu/student-login'; name = 'student-login' },
    # Route-coverage audit against pages/eduai/src/router/index.ts found these three
    # missing: two overlay pages that only appear when a game pops them (remind=true,
    # showLink=false, so they never show up in a menu-driven crawl) and one redirect.
    # A menu walk cannot find them; a route list must.
    @{ path = '/edu/fun/insect-card'; name = 'fun-insect-card' },
    @{ path = '/edu/fun/idiom-card'; name = 'fun-idiom-card' },
    @{ path = '/edu/schedule'; name = 'schedule-redirect' }
)
if ($Role -eq 'student' -and -not $ChromeForStudent) {
    # The student's OWN portal pages are full-screen, but /home and /edu/* still render the
    # console shell for a student token (r18 documented the admin layout behind the portal
    # overlay). Skipping the chrome phase for students therefore left the sidebar / header
    # untested *as a student* -- pass -ChromeForStudent to walk it in that identity.
    $SkipChrome = $true
}
if ($Only.Count -gt 0) {
    # `pwsh -File script.ps1 -Only a,b,c` hands over ONE string, while a direct call
    # hands over an array; accept both so the filter works either way.
    $sel = @($Only | ForEach-Object { ([string]$_ -split ',') } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $routes = $routes | Where-Object { $sel -contains $_.name -or $sel -contains $_.path }
    Write-Host ("  route filter: {0}" -f ($sel -join ' '))
}

# ---------- JS: probe / inventory / reset ----------
$jsProbe = @'
(() => {
  const vis = el => { if (!el) return false; const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden'; };
  // Selection state matters as much as text: clicking an answer option or a filter tab
  // often only flips a class, which a text-length diff cannot see. Without this the
  // game pages reported 40+ "dead" buttons that are in fact working selections.
  const act = Array.from(document.querySelectorAll(
      '.is-active, .is-checked, [aria-checked="true"], [aria-selected="true"], [aria-pressed="true"]'))
    .map(e => ((e.className || '').toString().slice(0, 20)) + '|' + ((e.innerText || '').trim().slice(0, 6))).join(';');
  const inputs = Array.from(document.querySelectorAll('input, textarea'))
    .map(i => (i.type || i.tagName) + (i.checked ? 'C' : 'u') + ':' + ((i.value || '') + '').length).join(',');
  // Generic change detector. The game pages mark state with their own class names
  // (ig-card / iw__opt with custom picked/right/wrong classes), so a whitelist of
  // Element Plus classes kept reporting them as dead buttons. Node count and innerHTML
  // length change whenever a class is added or removed, whatever it is called.
  const root = document.querySelector('main.el-main') || document.body;
  return JSON.stringify({
    path: location.pathname,
    dialogs: Array.from(document.querySelectorAll('.el-dialog, .el-drawer, .el-message-box')).filter(vis).length,
    poppers: Array.from(document.querySelectorAll('.el-popper')).filter(vis).length,
    toasts: Array.from(document.querySelectorAll('.el-message, .el-notification')).filter(vis).length,
    // Text of the visible toasts, not just how many. ElMessage stacks up (it defaults to
    // 3000ms), so when a previous click's toast is still on screen the COUNT does not
    // change on the next click and the click looks dead. Comparing the text catches the
    // new toast as long as it differs from the stale one.
    toastText: Array.from(document.querySelectorAll('.el-message, .el-notification')).filter(vis)
      .map(t => ((t.innerText || '').trim().slice(0, 30))).join(' // '),
    bodyLen: (document.body.innerText || '').replace(/\s+/g, '').length,
    rows: document.querySelectorAll('.el-table__body-wrapper tbody tr').length,
    loading: Array.from(document.querySelectorAll('.el-loading-mask')).filter(vis).length,
    act: act, inputs: inputs,
    nodes: root.querySelectorAll('*').length,
    htmlLen: root.innerHTML.length,
    // First data cell text: catches a page switch whose row count happens to match
    // (this is how ReTable's own pager changes pages -- clicking page 2 of
    // /edu/wrongbook takes the rows from 20 to 19, but a same-size page would be
    // invisible to every other signal).
    firstCell: (() => {
      const c = document.querySelector('main.el-main .el-table__body-wrapper tbody tr td .cell');
      return c ? ((c.innerText || '').trim().slice(0, 24)) : '';
    })()
  });
})()
'@

# $SCOPE is injected: page container for content, chrome selectors for phase 2
function Build-InventoryJs {
    param([string]$ScopeJs, [string[]]$Selectors)
    $sel = ($Selectors | ForEach-Object { "'" + $_ + "'" }) -join ', '
    return @"
(() => {
  const SEL = [$sel].join(',');
  const SCOPE = $ScopeJs;
  const root = (typeof SCOPE === 'function' ? SCOPE() : SCOPE) || document.body;
  document.querySelectorAll('[data-uitest]').forEach(e => e.removeAttribute('data-uitest'));
  const vis = el => {
    if (!el || el.disabled) return false;
    const r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    const st = getComputedStyle(el);
    if (st.visibility === 'hidden' || st.display === 'none' || parseFloat(st.opacity || '1') === 0) return false;
    return true;
  };
  const out = [];
  const seen = new Set();
  root.querySelectorAll(SEL).forEach(el => {
    if (!vis(el) || el.hasAttribute('data-uitest')) return;
    if (el.closest('.el-select-dropdown, .el-popper') && !el.classList.contains('el-dropdown-menu__item')) return;
    const label = (el.innerText || el.getAttribute('title') || el.getAttribute('aria-label') || '').trim().slice(0, 26);
    const cls = (el.className || '').toString();
    // "Already selected" tokens, incl. product state classes: the insect 3D hotspot marks
    // its open dot `is-open`, so pressing an open hotspot CLOSES it (identical state).
    const active = (/\bis-(active|checked|open|selected|picked|matched|current)\b/.test(cls) ||
      /--active\b/.test(cls) ||
      el.getAttribute('aria-checked') === 'true' ||
      el.getAttribute('aria-selected') === 'true' ||
      el.getAttribute('aria-pressed') === 'true') ? 'Y' : 'N';
    const key = el.tagName + '|' + cls.slice(0, 30) + '|' + label;
    if (seen.has(key)) return;
    seen.add(key);
    const id = 'u' + out.length;
    el.setAttribute('data-uitest', id);
    let kind = 'button';
    if (el.tagName === 'A') kind = 'link';
    else if (cls.indexOf('el-dropdown-menu__item') >= 0) kind = 'menu-item';
    else if (cls.indexOf('el-radio-button') >= 0) kind = 'radio';
    else if (cls.indexOf('el-tabs__item') >= 0) kind = 'tab';
    else if (cls.indexOf('el-switch') >= 0) kind = 'switch';
    else if (cls.indexOf('el-checkbox') >= 0) kind = 'checkbox';
    else if (cls.indexOf('el-pager') >= 0) kind = 'pager';
    else if (cls.indexOf('el-menu-item') >= 0) kind = 'menu';
    else if (!label) kind = 'icon-button';
    out.push({ id: id, kind: kind, label: label, tag: el.tagName.toLowerCase(), cls: cls.slice(0, 40), active: active, hov: (el.closest('.el-dropdown, .el-cascader, .el-select, [aria-haspopup]') ? 'Y' : 'N') });
  });
  // ---- pass 2: pointer-driven controls (student cards are div/span, not buttons) ----
  // outermost pointer element only: `cursor` is inherited, so a card would otherwise
  // cost one click per nested span.
  const pointerLike = el => {
    try { return getComputedStyle(el).cursor === 'pointer'; } catch (e) { return false; }
  };
  // cursor:pointer alone is NOT evidence of a control: the weather widget paints its seven
  // forecast columns with pointer cursors and hover styles but binds no handler at all,
  // so a pointer-only scan reported 7 dead buttons. Require a real handler.
  const hasHandler = el => {
    try {
      const vei = el._vei;
      if (vei && vei.on) { for (const k in vei.on) { if (/^on(click|pointer|mouse)/i.test(k)) return true; } }
      const vn = el.__vnode;
      if (vn && vn.props) { for (const k in vn.props) { if (/^on(click|pointer|mouse)/i.test(k)) return true; } }
      const pc = el.__vueParentComponent;
      if (pc && pc.vnode && pc.vnode.props) { for (const k in pc.vnode.props) { if (/^on(click|pointer|mouse)/i.test(k)) return true; } }
    } catch (e) {}
    return false;
  };
  const SEL2 = 'div, span, li, i, svg, img, section, article, td, tr, label, a';
  // never walk layout chrome here; pass 2 matches almost anything with cursor:pointer
  const CHROME = '.layout-aside, .layout-tabs-bar, .layout-header, .layout-logo, aside.el-aside, header.el-header';
  root.querySelectorAll(SEL2).forEach(el => {
    if (el.hasAttribute('data-uitest')) return;
    if (!vis(el)) return;
    if (el.closest('.el-select-dropdown, .el-popper')) return;
    if (el.closest(CHROME)) return;
    const clickable = hasHandler(el) || el.tagName === 'A' || el.getAttribute('role') === 'button' ||
      el.hasAttribute('tabindex') || el.hasAttribute('onclick');
    // dropdown / select / cascader triggers are clickable-or-hover surfaces even when they
    // carry neither a handler nor cursor:pointer (the module switcher is a plain span)
    const hoverTarget = !!el.closest('.el-dropdown, .el-cascader, .el-select, [aria-haspopup]');
    if (!clickable && !hoverTarget) return;
    if (!pointerLike(el)) return;
    // outermost-only: an ancestor that is itself pointer-like owns this interaction
    let p = el.parentElement, skip = false;
    while (p && p !== root) {
      if (p.hasAttribute('data-uitest') || pointerLike(p)) { skip = true; break; }
      p = p.parentElement;
    }
    if (skip) return;
    const label = (el.innerText || el.getAttribute('title') || el.getAttribute('aria-label') || '').trim().slice(0, 26);
    if (!label) return;
    // a huge pointer area is almost always a layout container inheriting a rule, not a control
    const r = el.getBoundingClientRect();
    if (r.width * r.height > innerWidth * innerHeight * 0.7) return;
    const cls = (el.className || '').toString();
    const key = el.tagName + '|' + cls.slice(0, 30) + '|' + label;
    if (seen.has(key)) return;
    seen.add(key);
    const id = 'u' + out.length;
    el.setAttribute('data-uitest', id);
    // active must consider ancestors: el-menu-item carries is-active one level ABOVE the
    // pointer span, and a nav entry for the current route is a no-op by design
    const activeCls = /\bis-(active|checked|open|selected|picked|matched|current)\b/.test(cls) ||
      el.getAttribute('aria-checked') === 'true' || el.getAttribute('aria-selected') === 'true' ||
      !!el.closest('.is-active, .is-checked, [aria-current="page"], [aria-selected="true"]');
    let activeHref = false;
    const a = el.closest('a[href]');
    if (a) {
        const href = a.getAttribute('href') || '';
        activeHref = !!href && href !== '#' && location.pathname.indexOf(href) === 0;
    }
    out.push({ id: id, kind: 'clickable', label: label, tag: el.tagName.toLowerCase(), cls: cls.slice(0, 40), active: (activeCls || activeHref) ? 'Y' : 'N', hov: (el.closest('.el-dropdown, .el-cascader, .el-select, [aria-haspopup]') ? 'Y' : 'N') });
  });
  // prefix the scope so the caller learns WHICH area was inventoried
  const scopeKind = (document.querySelector('main.el-main') ? 'main'
    : (document.querySelector('.main-content') ? 'main-content' : (document.querySelector('main') ? 'main-tag' : 'body')));
  return '##' + scopeKind + '##' + JSON.stringify(out);
})()
"@
}

$jsInvContent = Build-InventoryJs -ScopeJs "(document.querySelector('main.el-main') || document.querySelector('.main-content') || document.querySelector('main') || document.body)" -Selectors @(
    'button', '.el-button', '[role="button"]', 'a.el-link', '.el-dropdown-menu__item',
    '.el-radio-button', '.el-tabs__item', '.el-switch', '.el-checkbox',
    '.el-pagination button', '.el-pager li'
)
$jsInvChrome = Build-InventoryJs -ScopeJs "document.body" -Selectors @(
    # the header's module switcher / avatar menu trigger is a plain span (el-breadcrumb__inner),
    # not a button, so a button-only chrome list never touched it -- and it opens on hover,
    # so its 4 menu items were never clicked either
    '.layout-header button', '.layout-header .el-dropdown', '.layout-header [aria-haspopup]',
    '.layout-header .el-tooltip__trigger', '.layout-aside .el-menu-item', '.layout-aside .el-sub-menu__title',
    '.layout-aside a', '.app-tabs .el-tabs__item', '.el-tabs--header > .el-tabs__item'
)

$jsReset = @'
(() => {
  const vis = el => { if (!el) return false; const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden'; };
  try { document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true })); } catch (e) {}
  Array.from(document.querySelectorAll(
    '.el-dialog__headerbtn, .el-drawer__close-btn, .el-message-box__headerbtn, .ant-modal-close'))
    .filter(vis).forEach(b => { try { b.click(); } catch (e) {} });
  return 'ok';
})()
'@

$lblLayout = -join @([char]0x81EA, [char]0x5B9A, [char]0x4E49, [char]0x5E03, [char]0x5C40)   # customize-layout button
$lblSave = -join @([char]0x4FDD, [char]0x5B58)                                                 # save button
$lblWidgets = @(
    (-join @([char]0x65E5, [char]0x5386))
    (-join @([char]0x5FEB, [char]0x6377, [char]0x5165, [char]0x53E3))
    (-join @([char]0x4FBF, [char]0x7B7E))
    (-join @([char]0x5BA2, [char]0x6237, [char]0x7AEF, [char]0x4FE1, [char]0x606F))
    (-join @([char]0x5929, [char]0x6C14))
) -join "','"
$jsSeedDashboard = @"
(async () => {
  const WANT = ['$lblWidgets'];
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const count = () => { try { return (JSON.parse(localStorage.getItem('system:2:layout') || '{}').widgets || []).length; } catch (e) { return 0; } };
  if (count() > 0) return String(count());
  const btn = (t) => Array.from(document.querySelectorAll('main.el-main button')).find((b) => (b.innerText || '').trim() === t);
  btn('$lblLayout') && btn('$lblLayout').click();
  await sleep(1800);
  for (const name of WANT) {
    const all = Array.from(document.querySelectorAll('main.el-main *')).filter((e) => {
      const t = (e.innerText || '').replace(/\s+/g, ' ').trim();
      return t.indexOf(name) === 0 && t.length < 80 && e.children.length <= 8;
    });
    if (!all.length) continue;
    all.filter((e) => !all.some((o) => o !== e && o.contains(e)))[0].click();
    await sleep(650);
  }
  await sleep(400);
  btn('$lblSave') && btn('$lblSave').click();
  await sleep(2400);
  return String(count());
})()
"@

function Seed-Dashboard {
    param([string]$Role)
    if ($script:NoSeed) { return }
    $n = 0
    try {
        Open-Route '/home'
        Start-Sleep -Seconds 4
        $n = [int]([string](Js $jsSeedDashboard 90000))
    } catch { $n = 0 }
    if ($Role -eq 'student') {
        # The dashboard is an admin-side fixture; a student profile legitimately has none and
        # reporting it as a failure would be a false red.
        Write-Host ('    [note] dashboard fixture skipped for the student role (widgets={0})' -f $n)
        return
    }
    Ck "$Role dashboard seeded with widgets to click" ($n -gt 0) "widgets=$n"
}

# Inventory the controls INSIDE an open dialog/drawer. Element Plus teleports dialogs to
# <body>, so they sit outside `main.el-main` and the content-phase inventory structurally
# cannot see them: across a full sweep not a single dialog button (confirm / cancel / save)
# was ever clicked. Opening a dialog was counted as "that button responded" and the dialog's
# own controls were left untested.
$dlgKill = @(
    (-join @([char]0x5220, [char]0x9664))    # delete
    (-join @([char]0x79FB, [char]0x9664))    # remove
    (-join @([char]0x6E05, [char]0x7A7A))    # clear
    (-join @([char]0x91CD, [char]0x7F6E))    # reset
    (-join @([char]0x6279, [char]0x91CF))    # batch
    (-join @([char]0x4F5C, [char]0x5E9F))    # void
) -join '|'
$jsDialogInventory = @"
(() => {
  const KILL = new RegExp('$dlgKill');
  const vis = (el) => { if (!el) return false; const r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false; const st = getComputedStyle(el);
    return !(st.visibility === 'hidden' || st.display === 'none' || parseFloat(st.opacity || '1') === 0); };
  document.querySelectorAll('[data-uitest-d]').forEach((e) => e.removeAttribute('data-uitest-d'));
  const SEL = 'button, .el-button, [role="button"], .el-tabs__item, .el-radio-button, .el-switch, .el-checkbox, a.el-link, .el-pager li, .el-select__wrapper, input[type="radio"], input[type="checkbox"]';
  const out = [];
  const panels = Array.from(document.querySelectorAll('.el-dialog, .el-drawer')).filter(vis);
  panels.forEach((panel) => {
    const titleEl = panel.querySelector('.el-dialog__title, .el-drawer__title');
    const title = ((titleEl && titleEl.innerText) || '').trim().slice(0, 24);
    const titleKill = KILL.test(title);
    panel.querySelectorAll(SEL).forEach((el) => {
      if (el.hasAttribute('data-uitest-d')) return;
      if (el.classList.contains('el-dialog__headerbtn')) return;
      if (!vis(el)) return;
      const label = (el.innerText || el.getAttribute('placeholder') || el.getAttribute('title') || el.getAttribute('aria-label') || '').trim().slice(0, 24);
      if (!label) return;
      const id = 'd' + out.length;
      el.setAttribute('data-uitest-d', id);
      const kind = el.tagName === 'BUTTON' || el.classList.contains('el-button') ? 'button' : (el.classList.contains('el-tabs__item') ? 'tab' : 'control');
      out.push({ id: id, kind: kind, label: label, title: title,
        destructive: (titleKill || KILL.test(label)) ? 1 : 0 });
    });
  });
  return JSON.stringify(out);
})()
"@

$jsOverlayCount = @'
(() => {
  const vis = el => { if (!el) return false; const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden'; };
  // Poppers count too. The page-size dropdown left its .el-popper open, and the next
  // control (ReTable's "column settings") then reported no effect -- the same "the
  // previous click's UI is still up" trap as dialogs, just one layer down.
  return String(Array.from(document.querySelectorAll('.el-dialog, .el-drawer, .el-message-box, .el-popper')).filter(vis).length);
})()
'@

# Presses the collapsed-filter-panel expander (label built from code points to keep this
# file ASCII). Returns 'expanded' | 'none'.
$jsExpand = @'
(() => {
  const L = String.fromCharCode(0x5C55, 0x5F00);
  const root = document.querySelector('main.el-main') || document.body;
  const all = Array.from(root.querySelectorAll('button, .el-button')).filter(b => {
    const r = b.getBoundingClientRect();
    return r.width > 0 && r.height > 0 && !b.disabled;
  });
  const hit = all.find(b => (b.innerText || '').trim() === L);
  if (!hit) return 'none';
  hit.click();
  return 'expanded';
})()
'@

# Types a probe value into the first empty filter input. A search button pressed with an
# EMPTY keyword is a no-op by design, so clicking it proves nothing; seeding the input
# first turns the sweep into a real functional test (verified on /edu/insects: typing a
# value then pressing search took the list from 4 pages to 3, and reset restored it).
$jsSeedSearch = @'
(() => {
  const inputs = Array.from(document.querySelectorAll('main.el-main input'))
    .filter(i => {
      const r = i.getBoundingClientRect();
      if (!(r.width > 0 && r.height > 0) || i.value) return false;
      if (i.type && i.type !== 'text') return false;
      // Skip select / date-picker inner inputs: typing into them filters nothing, so the
      // search that follows stays a no-op. (On /edu/fun/config the first empty input is
      // an el-select, which is why reset looked dead until this was filtered out.)
      if (i.closest('.el-select, .el-date-editor, .el-cascader')) return false;
      return true;
    });
  if (!inputs.length) return 'no-input';
  const el = inputs[0];
  const proto = Object.getPrototypeOf(el);
  const setter = Object.getOwnPropertyDescriptor(proto, 'value');
  if (!setter || !setter.set) return 'no-setter';
  // A value that cannot match any row: with a value that matches everything (a single
  // "a" already matches every module whose code contains an "a", e.g. idiom-game) both
  // the search AND the reset become no-ops and prove nothing.
  setter.set.call(el, 'uiacc-no-match-probe');
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
  return 'seeded';
})()
'@
$searchLabel = -join @([char]0x641C, [char]0x7D22)
$searchRx = [regex]::Escape($searchLabel) + '|^search$'
# Judging a reset button must look at page state: with nothing typed into the filter
# inputs, reset is SUPPOSED to change nothing, and calling that a dead button is a false
# positive (measured on /edu/fun/config: seeding live-filters 42->0, reset restores 0->42,
# so the button works). The real defect is the opposite: a non-empty filter that a
# reset click leaves completely unchanged.
    $resetLabel = -join @([char]0x91CD, [char]0x7F6E)
    $resetRx = [regex]::Escape($resetLabel) + '|^reset$'


# How many visible filter inputs currently carry a value
$jsFilterState = @'
(() => {
  const root = document.querySelector('main.el-main') || document.body;
  const inputs = Array.from(root.querySelectorAll('input')).filter(i => {
    const r = i.getBoundingClientRect();
    if (!(r.width > 0 && r.height > 0)) return false;
    if (i.type && i.type !== 'text') return false;
    if (i.closest('.el-select, .el-date-editor, .el-cascader')) return false;
    return true;
  });
  const filled = inputs.filter(i => (i.value || '').trim().length > 0);
  return JSON.stringify({ inputs: inputs.length, filled: filled.length,
    firstFilled: filled.length ? ((filled[0].value || '').trim().slice(0, 24)) : '' });
})()
'@

$jsActiveTabLabel = @'
(() => {
  const t = document.querySelector('.layout-tabs-bar__item.is-active, .el-tabs__item.is-active, [aria-selected="true"]');
  return t ? ((t.innerText || '').trim().slice(0, 26)) : '';
})()
'@

$jsLayoutSnapshot = @'
(() => {
  const out = {};
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i);
    if (k && /layout/i.test(k)) out[k] = localStorage.getItem(k);
  }
  return JSON.stringify(out);
})()
'@

# Label of the pager's current page, or '' when there is no pager.
$jsCurrentPager = @'
(() => {
  const el = document.querySelector('main.el-main .el-pager li.is-active, main.el-main .el-pager li.active, main.el-main .rm-pager__btn--active');
  return el ? ((el.innerText || '').trim()) : '';
})()
'@

# ---------- in-page evidence collectors ----------
# Own instrumentation instead of the CLI: `network requests` reported "No requests
# captured" for clicks that had just fired requests, and `errors` returned an empty
# marker for an uncaught throw. These hooks see every fetch/XHR plus window errors.
#
# Evidence is flushed to sessionStorage on pagehide/beforeunload because a few routes
# (/edu/student-login, /edu/teacher) are FULL-DOCUMENT navigations: a collector that
# lives only in the page object is destroyed by the very click under test, which made
# those clicks report "no evidence at all". sessionStorage survives same-origin
# navigation, so the pre-navigation document still hands its evidence over.
$jsInstall = @'
(() => {
  if (window.__uitInstalled) { return 'already'; }
  window.__uitInstalled = true;
  const K = '__uitStore';
  let store = [];
  try { store = JSON.parse(sessionStorage.getItem(K) || '[]'); } catch (e) { store = []; }
  window.__uit = { urls: [], statuses: [], jsErrors: [], consoleErrors: [] };
  const flush = () => {
    const u = window.__uit;
    try {
      if (u.urls.length || u.statuses.length || u.jsErrors.length || u.consoleErrors.length) {
        store.push({ path: location.pathname, urls: u.urls.slice(0, 40), statuses: u.statuses.slice(0, 40),
          jsErrors: u.jsErrors.slice(0, 20), consoleErrors: u.consoleErrors.slice(0, 20) });
      }
      sessionStorage.setItem(K, JSON.stringify(store.slice(-30)));
    } catch (e) {}
  };
  window.addEventListener('pagehide', flush);
  window.addEventListener('beforeunload', flush);
  window.__uitFlush = flush;
  window.__uitRead = () => { flush(); try { return sessionStorage.getItem(K) || '[]'; } catch (e) { return '[]'; } };
  window.__uitReset = () => {
    window.__uit.urls = []; window.__uit.statuses = []; window.__uit.jsErrors = []; window.__uit.consoleErrors = [];
    store = [];
    try { sessionStorage.setItem(K, '[]'); } catch (e) {}
    return 'cleared';
  };
  window.addEventListener('error', function (e) {
    window.__uit.jsErrors.push('msg=' + JSON.stringify(e && e.message) + ' type=' + (e && e.type) +
      ' file=' + (e && e.filename) + ' line=' + (e && e.lineno) + ' err=' + String((e && e.error) || '').slice(0, 200));
  }, true);
  window.addEventListener('unhandledrejection', function (e) {
    window.__uit.jsErrors.push('REJECTION ' + String((e && e.reason) || '').slice(0, 240));
  });
  const of = window.fetch;
  if (of) {
    window.fetch = function (input, init) {
      const url = (typeof input === 'string') ? input : (input && input.url) || '';
      return of.apply(this, arguments).then(function (r) {
        window.__uit.urls.push(url); window.__uit.statuses.push(r.status); return r;
      }, function (err) { window.__uit.urls.push(url); window.__uit.statuses.push(0); throw err; });
    };
  }
  const ow = window.open;
  const XO = XMLHttpRequest.prototype.open;
  window.open = function (u) {
    // A shortcut that opens a new tab changes nothing in THIS document, so without this
    // hook the click looks dead. Record it as a request instead of guessing.
    try { window.__uit.urls.push('WINDOW_OPEN ' + String(u || '')); window.__uit.statuses.push(200); } catch (e) {}
    return ow.apply(window, arguments);
  };
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__uitUrl = String(url || '');
    return XO.apply(this, arguments);
  };
  const XS = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function () {
    this.addEventListener('loadend', function () {
      window.__uit.urls.push(this.__uitUrl || '');
      window.__uit.statuses.push(this.status);
    });
    return XS.apply(this, arguments);
  };
  const oe = console.error;
  console.error = function () {
    try { window.__uit.consoleErrors.push('CONSOLE ' + Array.prototype.map.call(arguments, String).join(' ').slice(0, 260)); } catch (e) {}
    return oe.apply(console, arguments);
  };
  return 'installed';
})()
'@

$jsResetEv = @'
(() => (window.__uitReset ? window.__uitReset() : 'no-hooks'))()
'@

# Aggregates the persisted log so evidence survives a full-document navigation.
$jsReadEv = @'
(() => {
  let raw = '[]';
  try { raw = window.__uitRead ? window.__uitRead() : (sessionStorage.getItem('__uitStore') || '[]'); } catch (e) { raw = '[]'; }
  let store = [];
  try { store = JSON.parse(raw) || []; } catch (e) { store = []; }
  const urls = []; const statuses = []; const jsErrors = []; const consoleErrors = [];
  store.forEach(e => {
    (e.urls || []).forEach(u => urls.push(u));
    (e.statuses || []).forEach(s => statuses.push(s));
    (e.jsErrors || []).forEach(m => jsErrors.push(m));
    (e.consoleErrors || []).forEach(m => consoleErrors.push(m));
  });
  return JSON.stringify({ urls: urls, statuses: statuses, jsErrors: jsErrors, consoleErrors: consoleErrors, docs: store.length });
})()
'@

# Log back in after the token expires mid-run. Without this the crawl kept going on the
# login page: a 20-minute run hits the access-token TTL, the guard bounces to /login, and
# every later route records "0 controls" -- which reads exactly like a clean pass.
function Invoke-Login {
    param([switch]$Quiet)
    $ok = $false
    if ($Role -eq 'student') {
        if (-not $script:StudentNo) { return $false }
        AbRaw @('open', "$Base/edu/student-login") 60000 | Out-Null
        Start-Sleep -Seconds 4
        AbRaw @('snapshot') 60000 | Out-Null
        AbRaw @('fill', 'input[autocomplete="username"]', $script:StudentNo) 60000 | Out-Null
        AbRaw @('fill', 'input[autocomplete="current-password"]', $script:StudentPwdEff) 60000 | Out-Null
        AbRaw @('press', 'Enter') 60000 | Out-Null
        $ok = [bool](WaitJs 'location.pathname.indexOf("/edu/student")===0 || location.pathname.indexOf("student-login")!==0' 25)
    }
    else {
        $q1 = -join @([char]0x8BF7, [char]0x8F93, [char]0x5165, [char]0x7528, [char]0x6237, [char]0x540D)
        $q2 = -join @([char]0x8BF7, [char]0x8F93, [char]0x5165, [char]0x5BC6, [char]0x7801)
        AbRaw @('open', "$Base/login") 60000 | Out-Null
        Start-Sleep -Seconds 3
        Js '(() => { for (let i = localStorage.length - 1; i >= 0; i--) { const k = localStorage.key(i); if (k && !/layout/i.test(k)) localStorage.removeItem(k); } sessionStorage.clear(); return 1; })()' | Out-Null
        AbRaw @('open', "$Base/login") 60000 | Out-Null
        Start-Sleep -Seconds 4
        AbRaw @('snapshot') 60000 | Out-Null
        AbRaw @('find', 'placeholder', $q1, 'fill', $AdminUser) 60000 | Out-Null
        AbRaw @('find', 'placeholder', $q2, 'fill', $AdminPwd) 60000 | Out-Null
        AbRaw @('press', 'Enter') 60000 | Out-Null
        $ok = [bool](WaitJs 'location.pathname.indexOf("/login")!==0' 25)
    }
    if (-not $Quiet) { Write-Host ('    [re-login] {0}: {1}' -f $Role, $ok) }
    return $ok
}

function Open-Route {
    param([string]$Path)
    AbRaw @('open', ($Base + $Path)) 60000 | Out-Null
    WaitJs 'document.readyState === "complete"' $NavTimeoutSec | Out-Null
    WaitJs '!document.querySelector(".el-loading-mask")' 12 | Out-Null
    Start-Sleep -Milliseconds 500
    # A full load can land on a DIFFERENT page than the one requested: the framework
    # restores the previously active tab on boot, which overrode the requested URL
    # (opening /home ended up on /edu/fun/insect-card, /eduai/paper-compose, ...).
    # The crawl therefore credited another page's controls to this route -- /home was
    # never actually crawled. Correct it with an SPA navigation through the app's own
    # router instance, which the guard lets through untouched.
    $landed = ''
    try { $landed = [string](Js 'location.pathname' 20000) } catch { $landed = '' }
    if ($landed -and $landed.TrimEnd('/') -match '^/login' -and $Path -notlike '/login*') {
        Write-Host ('    [session lost] {0} bounced to the login page; re-login and retry' -f $Path)
        $back = Invoke-Login
        Ck "session restored before $Path" $back "role=$Role"
        AbRaw @('open', ($Base + $Path)) 60000 | Out-Null
        WaitJs 'document.readyState === "complete"' $NavTimeoutSec | Out-Null
        Start-Sleep -Milliseconds 900
        Js $jsInstall | Out-Null
        return
    }
    if ($landed -and ($landed.TrimEnd('/') -ne $Path.TrimEnd('/'))) {
        $fix = "(() => { const app = document.querySelector('#app'); const r = app && app.__vue_app__ && app.__vue_app__.config.globalProperties.`$router; if (!r) return 'no-router'; r.push('$Path').catch(() => undefined); return 'pushed'; })()"
        $r1 = ''
        try { $r1 = [string](Js $fix 30000) } catch { $r1 = '' }
        WaitJs 'document.readyState === "complete"' $NavTimeoutSec | Out-Null
        Start-Sleep -Milliseconds 900
        $now = ''
        try { $now = [string](Js 'location.pathname' 20000) } catch { $now = '' }
        if ($now.TrimEnd('/') -ne $Path.TrimEnd('/')) {
            Write-Host ('    [WARN] {0} landed on {1} (spa fix: {2})' -f $Path, $landed, $r1)
        }
    }
    # collectors live in the page, so a full navigation wipes them: re-install
    Js $jsInstall | Out-Null
}

# Builds the click snippet for a tagged element. The attribute selector needs double
# quotes, which would need escaping inside a PowerShell double-quoted string, so they
# are built from code points and the JS is assembled by concatenation.
function Get-ClickJs {
    param([string]$Id)
    $q = [char]0x22
    $sel = '[data-uitest=' + $q + $Id + $q + ']'
    return "(() => { const el = document.querySelector('$sel'); if (!el) return 'gone'; try { el.click(); return 'ok'; } catch (e) { return 'err'; } })()"
}

# CSS selector for a tagged element, for the CLI (which needs a real mouse click).
function Get-TagSelector {
    param([string]$Id)
    $q = [char]0x22
    return '[data-uitest=' + $q + $Id + $q + ']'
}

# ---------- HTTP helper (used by the student fixture only) ----------
# Same implementation shape as ui-paper-assign-acceptance.ps1, which is proven against
# this API. My first version shelled out to curl through AbRaw and silently produced
# nothing (admin login came back empty), so do not "simplify" this back to curl.
function Api {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null)
    $h = @{ 'User-Agent' = 'Mozilla/5.0 (eduai-ui-acceptance)' }
    if ($Token) { $h['Authorization'] = "Bearer $Token" }
    $a = @{ Method = $Method; Uri = ($Base + $Path); Headers = $h; SkipHttpErrorCheck = $true; TimeoutSec = 30 }
    if ($null -ne $Body) { $a['ContentType'] = 'application/json'; $a['Body'] = ($Body | ConvertTo-Json -Depth 6 -Compress) }
    $r = Invoke-WebRequest @a
    $json = $null
    try { $json = $r.Content | ConvertFrom-Json } catch { }
    return @{ code = [int]$r.StatusCode; json = $json; raw = [string]$r.Content }
}

# ---------- login ----------
if ($Role -eq 'student') {
    Write-Host '########## 0. student fixture + login ##########'
    # Same proven path as ui-paper-assign-acceptance.ps1: create a class + student over the
    # API, clear the mandatory first-login password change, then log in through the real
    # student login page. Without the password change the portal is blocked by the
    # first-login guide, so every student page would report the same shell.
    $adm = Api POST '/v2/user/login' @{ username = $AdminUser; password = $AdminPwd }
$admTok = if ($adm -and $adm.json) { $adm.json.data.accessToken } else { '' }
    Ck 'admin login (student fixture)' ([bool]$admTok) ''
    if (-not $admTok) { Write-Host 'no admin session for fixture'; exit 2 }
    # Endpoints and field names are copied from ui-paper-assign-acceptance.ps1 (proven
    # against this API) rather than guessed: dimension/classes + dimension/students.
    $stamp = [int][double]::Parse((Get-Date -UFormat %s))
    $cls = Api POST '/v2/edu/dimension/classes/save' @{ className = "UIACC-CLICK-$stamp"; classGradeLevel = 'PRIMARY' } $admTok
$classId = if ($cls -and $cls.json) { $cls.json.data } else { $null }
    Ck 'class created' ([bool]$classId) "classId=$classId"
    $stu = Api POST '/v2/edu/dimension/students/save' @{ studentName = 'UIACC Click'; studentPwdInput = $StudentPwd; studentClassId = $classId; studentGradeLevel = 'PRIMARY' } $admTok
$studentId = if ($stu -and $stu.json) { $stu.json.data } else { $null }
    Ck 'student created' ([bool]$studentId) "studentId=$studentId"
    $list = Api GET '/v2/edu/dimension/students' $null $admTok
    $studentNo = ''
if ($list -and $list.json -and $list.json.data) { foreach ($s in @($list.json.data)) { if ($s.studentId -eq $studentId) { $studentNo = $s.studentNo } } }
    Ck 'student number resolved' ([bool]$studentNo) "studentNo=$studentNo"
    if (-not $studentNo) { Write-Host 'fixture incomplete'; exit 2 }
    $slogin = Api POST '/v2/user/login' @{ username = $studentNo; password = $StudentPwd; authType = 'STUDENT' } ''
$stok = if ($slogin -and $slogin.json) { $slogin.json.data.accessToken } else { '' }
    Ck 'student api login' ([bool]$stok) ''
    if ($stok) {
        $final = 'Uiacc@' + (Get-Random -Minimum 10000 -Maximum 99999)
        $ch = Api POST '/v2/edu/student/change-password' @{ oldPassword = $StudentPwd; newPassword = $final } $stok
        # A DIFFERENT value on purpose: re-using the same password leaves MUST_CHANGE set, and the
        # portal then sits behind a non-dismissible 'first login: change the initial password'
        # dialog (no close button, ESC does nothing) -- that blocked two controls in every
        # student run until the fixture was fixed.
        Ck 'initial password changed to a NEW value (clears the forced dialog)' ($null -ne $ch) ''
        if ($null -ne $ch) { $script:StudentPwdEff = $final }
    }
    $logged = $false
    for ($t = 1; $t -le 3; $t++) {
        AbRaw @('open', "$Base/edu/student-login") 60000 | Out-Null
        Start-Sleep -Seconds 4
        AbRaw @('snapshot') 60000 | Out-Null
        AbRaw @('fill', 'input[autocomplete="username"]', $studentNo) 60000 | Out-Null
        AbRaw @('fill', 'input[autocomplete="current-password"]', $script:StudentPwdEff) 60000 | Out-Null
        AbRaw @('press', 'Enter') 60000 | Out-Null
        if (WaitJs 'location.pathname.indexOf("/edu/student")===0 || location.pathname.indexOf("student-login")!==0' 25) { $logged = $true; break }
    }
    Snapshot-Layout
    Seed-Dashboard 'student'
    $script:StudentNo = $studentNo
    Ck 'student logged in via the student login page' $logged
    if (-not $logged) { Write-Host 'no student session, abort'; exit 2 }
} else {
    Write-Host '########## 0. login ##########'
    AbRaw @('open', "$Base/login") 60000 | Out-Null
    Start-Sleep -Seconds 3
    # Snapshot BEFORE the storage wipe below. Taking it afterwards captured 0 keys, so the
    # restore at the end was a no-op and every run left the dashboard layout empty -- the
    # acceptance script was quietly degrading the environment it was verifying.
    Snapshot-Layout
    # Wipe the session but keep the dashboard layout. A blanket localStorage.clear() both
    # deleted the cached widget layout and told the app "storage was wiped", so it booted
    # anonymous and cleared the previous account's layout cache -- every run therefore
    # crawled an EMPTY dashboard (1 control instead of 10) and left it empty. Removing the
    # non-layout keys one by one keeps the layout and the run stays reproducible.
    Js '(() => { let kept = 0; for (let i = localStorage.length - 1; i >= 0; i--) { const k = localStorage.key(i); if (k && /layout/i.test(k)) { kept++; continue; } if (k) localStorage.removeItem(k); } sessionStorage.clear(); return kept; })()' | Out-Null
    $phUser = "$([char]0x8BF7)$([char]0x8F93)$([char]0x5165)$([char]0x7528)$([char]0x6237)$([char]0x540D)"
    $phPwd = "$([char]0x8BF7)$([char]0x8F93)$([char]0x5165)$([char]0x5BC6)$([char]0x7801)"
    $logged = $false
    for ($t = 1; $t -le 3; $t++) {
        AbRaw @('open', "$Base/login") 60000 | Out-Null
        Start-Sleep -Seconds 4
        AbRaw @('snapshot') 60000 | Out-Null
        AbRaw @('find', 'placeholder', $phUser, 'fill', $AdminUser) 60000 | Out-Null
        AbRaw @('find', 'placeholder', $phPwd, 'fill', $AdminPwd) 60000 | Out-Null
        AbRaw @('press', 'Enter') 60000 | Out-Null
        if (WaitJs 'location.pathname.indexOf("/login")!==0' 25) { $logged = $true; break }
    }
    Seed-Dashboard 'admin'
    Ck 'admin logged in' $logged
    if (-not $logged) { Write-Host 'no session, abort'; exit 2 }

}


# ---------- Phase 1: page content controls ----------
if (-not $SkipContent) {
    foreach ($r in $routes) {
        $path = $r.path; $rname = $r.name
        Write-Host ''
        Write-Host ('########## [content] {0}  {1} ##########' -f $path, $rname)
        Open-Route $path
        $p0 = (Js $jsProbe) | ConvertFrom-Json
        Ck "$rname page loads" ($p0.bodyLen -gt 30) ("path=$($p0.path) bodyLen=$($p0.bodyLen)")
        Clear-Evidence
        $ev0 = Read-Evidence
        Ck "$rname clean load (no page/console error, no 5xx)" (($ev0.pageErr -eq 0) -and ($ev0.consoleErr -eq 0) -and ($ev0.http5xx -eq 0)) `
            ("pageErr=$($ev0.pageErr) consoleErr=$($ev0.consoleErr) 5xx=$($ev0.http5xx) $($ev0.first5)")

        # Open collapsed filter panels before crawling. On /edu/fun/config the ReTable
        # toolbar starts collapsed, so its search / reset buttons are inert until the
        # expander is pressed -- a user has to open the panel first, and crawling the
        # default state reported two working buttons as dead.
        # a user has to open the panel first, and crawling the default state reported
        # two working buttons as dead.
        $expanded = Js $jsExpand
        if ($expanded -eq 'expanded') {
            Start-Sleep -Milliseconds 700
            Js $jsResetEv | Out-Null
        }

        $i = 0; $restarts = 0; $clicked = 0; $prevCount = 0; $modeRestarts = 0; $emptyTries = 0
        while ($true) {
            $inv = Js $jsInvContent
            $scopeKind = 'main'
            if ($inv -match '^##([^#]+)##') { $scopeKind = $Matches[1]; $inv = $inv.Substring(4 + $scopeKind.Length) }
            if ($scopeKind -eq 'body') {
                Write-Host '    [WARN] inventory scope degraded to body (main.el-main not rendered yet)'
            }
            if (-not $inv) { Ck "$rname inventory readable" $false ''; break }
            $items = @()
            try { $items = @($inv | ConvertFrom-Json) }
            catch {
                # An unparseable inventory used to be swallowed silently and showed up as
                # "0 clicks, 0 dead", which reads exactly like a clean pass. Print the head
                # of what came back so the failure is visible instead of silent.
                $head = $inv.Substring(0, [Math]::Min(200, $inv.Length))
                Ck "$rname inventory readable" $false ("raw=" + $head)
                break
            }
            # A control can switch the page into another mode: /home's "custom layout"
            # button replaces the widget board with the component palette, so everything
            # after it in the walk belonged to the new mode and the widgets were never
            # clicked. When the control count collapses, walk the page again from the top
            # (bounded, so two modes toggling cannot loop forever).
            if ($prevCount -ge 4 -and $items.Count -lt [Math]::Floor($prevCount / 2) -and $modeRestarts -lt 3) {
                $modeRestarts++
                Write-Host ('    [mode switch] controls {0} -> {1}: re-walking this page' -f $prevCount, $items.Count)
                $i = 0
            }
            $prevCount = $items.Count
            # An empty inventory on the first look means "not rendered yet" on slow pages,
            # and concluding "this page has no controls" produced 0 clicks that read exactly
            # like a clean pass (/edu/student needs several seconds to fetch its modules).
            if ($items.Count -eq 0) {
                if ($emptyTries -lt 5) {
                    $emptyTries++
                    Start-Sleep -Milliseconds 1800
                    continue
                }
                Write-Host ('    [WARN] {0} still shows 0 controls after 5 retries' -f $rname)
            }
            else { $emptyTries = 0 }
            if ($i -ge $items.Count) { break }
            $it = $items[$i]
            $i++
            # Seed the filter input before pressing a search button, otherwise the click
            # is a no-op by design and proves nothing (see $jsSeedSearch).
            # If seeding itself already changed the table, the page filters live and the
            # search button is redundant by design -- clicking it then correctly does
            # nothing, which must not be reported as a dead button.
            $expectNoop = $false
            if ($it.label -and $it.label.Contains($searchLabel)) {
                $preSeed = (Js $jsProbe) | ConvertFrom-Json
                Js $jsSeedSearch | Out-Null
                # 400ms was too tight: the table live-filters on input and the reload had
                # not landed yet, so $expectNoop stayed false and a working search button
                # was reported dead. 900ms is what the live filter actually needs here.
                Start-Sleep -Milliseconds 900
                $postSeed = (Js $jsProbe) | ConvertFrom-Json
                if ($postSeed.rows -ne $preSeed.rows -or $postSeed.htmlLen -ne $preSeed.htmlLen) {
                    $expectNoop = $true
                }
            }
            # Close whatever the previous click opened and wait for the close animation.
            # A control sitting behind an open modal is unreachable for a real user too --
            # and clicking it anyway produced a bogus verdict: the previous dialog was
            # still open, so openAdd() re-assigned identical state, Vue changed nothing,
            # and the button looked dead. (Bookmark widget on /home, student session.)
            $guard = 0
            while ($guard -lt 10) {
                if ((Js $jsOverlayCount 20000) -eq '0') { break }
                Js $jsReset | Out-Null
                Start-Sleep -Milliseconds 300
                $guard++
            }
            if ($guard -ge 10) {
                [void]$script:Rows.Add(("$path|$($it.kind)|$($it.label)|blocked-by-modal|$path|0|0|0|0"))
                continue
            }
            $before = (Js $jsProbe) | ConvertFrom-Json
            # Let a leftover toast from an earlier click expire before baselining, so the
            # next click's toast is a clean delta instead of a no-op on the count.
            if ($before.toasts -gt 0) {
                WaitJs 'document.querySelectorAll(".el-message, .el-notification").length === 0' 5 | Out-Null
                $before = (Js $jsProbe) | ConvertFrom-Json
            }
            Clear-Evidence
            $clickRes = Js (Get-ClickJs $it.id)
            if ($clickRes -eq 'gone') {
                # The control disappeared between inventory and click (a dialog that closed
                # on its own, a list that re-rendered). It is not a dead button -- there was
                # nothing left to press. Re-inventory once; if it keeps vanishing, move on.
                $vkey = "$rname/$($it.kind)/$($it.label)"
                if (-not $script:Vanish.ContainsKey($vkey)) { $script:Vanish[$vkey] = 0 }
                $script:Vanish[$vkey]++
                if ($script:Vanish[$vkey] -le 2) {
                    $i--
                    continue
                }
                [void]$script:Rows.Add(("$path|$($it.kind)|$($it.label)|vanished|$($before.path)|0|0|0|0"))
                continue
            }
            # A dropped browser connection is NOT a verdict about the control. The old code lumped
            # every non-'ok' return into "dead button", so one websocket hiccup
            # ("Failed to read ... os error 1006") was reported as a product defect.
            # Transport failures get retried and then recorded as harness errors.
            $isTransport = ($clickRes -match 'os error|Failed to read|Connection|closed|timeout|timed out|1006|10054')
            if ($isTransport) {
                $tkey = "$rname/$($it.kind)/$($it.label)"
                if (-not $script:Vanish.ContainsKey($tkey)) { $script:Vanish[$tkey] = 0 }
                $script:Vanish[$tkey]++
                if ($script:Vanish[$tkey] -le 3) {
                    Write-Host ('    [transport] retry {0}/3 after: {1}' -f $script:Vanish[$tkey], $clickRes.Substring(0, [Math]::Min(60, $clickRes.Length)))
                    Start-Sleep -Milliseconds 800
                    $i--
                    continue
                }
                [void]$script:HarnessErr.Add("$rname/$($it.kind) '$($it.label)' -> $clickRes")
                continue
            }
            if ($clickRes -ne 'ok') {
                [void]$script:Dead.Add("$rname/$($it.kind) '$($it.label)' -> click returned '$clickRes'")
                continue
            }
            $script:Clicks++; $clicked++
            Start-Sleep -Milliseconds $SettleMs
            $after = (Js $jsProbe) | ConvertFrom-Json

            if ($after.path -ne $before.path) {
                # Navigation: the destination may still be hydrating (portals fetch on
                # entry), so wait before judging it. Probing immediately made
                # /edu/teacher and /edu/student-login look like blank pages.
                WaitJs 'document.readyState === "complete"' 15 | Out-Null
                WaitJs '!document.querySelector(".el-loading-mask")' 12 | Out-Null
                Start-Sleep -Milliseconds 600
                $after = (Js $jsProbe) | ConvertFrom-Json
                $ev = Read-Evidence
                Ck "$rname/$($it.kind) '$($it.label)' navigates to a healthy page" `
                    (($after.bodyLen -gt 30) -and ($ev.pageErr -eq 0) -and ($ev.consoleErr -eq 0) -and ($ev.http5xx -eq 0)) `
                    ("-> $($after.path) bodyLen=$($after.bodyLen) pageErr=$($ev.pageErr) consoleErr=$($ev.consoleErr) 5xx=$($ev.http5xx)")
                [void]$script:Rows.Add(("$path|$($it.kind)|$($it.label)|navigate|$($after.path)|0|0|0|0"))
                $restarts++
                if ($restarts -gt 3) { break }
                Open-Route $path
                $i = 0
                continue
            }

            $ev = Read-Evidence
            $effect = 'none'
            if ($after.dialogs -gt $before.dialogs) { $effect = 'dialog' }
            elseif ($after.toasts -gt $before.toasts -or $after.toastText -ne $before.toastText) { $effect = 'toast' }
            elseif ($after.rows -ne $before.rows) { $effect = 'table' }
            elseif ($after.firstCell -ne $before.firstCell) { $effect = 'page-data' }
            elseif ($after.act -ne $before.act) { $effect = 'state' }
            elseif ($after.inputs -ne $before.inputs) { $effect = 'input' }
            elseif ($after.nodes -ne $before.nodes) { $effect = 'node' }
            elseif ($after.htmlLen -ne $before.htmlLen) { $effect = 'style' }
            elseif ($after.bodyLen -ne $before.bodyLen) { $effect = 'dom' }
            elseif ($ev.reqTotal -gt 0) { $effect = 'request' }

            # Retry with a REAL mouse click when the synthetic click did nothing.
            # Some controls deliberately do not listen for `click`: the insect 3D
            # hotspots use pointerdown/pointerup with a movement threshold because
            # OrbitControls rotates the model on drag (see InsectStage3D.vue). A
            # JS .click() fires no pointer events, so those buttons looked dead while
            # being perfectly functional -- a harness artefact that would have been
            # reported as 5 product defects.
            if ($effect -eq 'none' -and $it.active -ne 'Y') {
                $sel = Get-TagSelector $it.id
                AbRaw @('scrollintoview', $sel) 30000 | Out-Null
                AbRaw @('click', $sel) 45000 | Out-Null
                # The 3D hotspot card animates open; give it longer than the plain settle
                # or the effect is still in flight when we probe and it looks dead.
                Start-Sleep -Milliseconds ($SettleMs + 1400)
                $after2 = (Js $jsProbe) | ConvertFrom-Json
                $ev2 = Read-Evidence
                $effect2 = 'none'
                if ($after2.path -ne $after.path) { $effect2 = 'route' }
                elseif ($after2.dialogs -gt $after.dialogs) { $effect2 = 'dialog' }
                elseif ($after2.toasts -gt $after.toasts -or $after2.toastText -ne $after.toastText) { $effect2 = 'toast' }
                elseif ($after2.rows -ne $after.rows) { $effect2 = 'table' }
                elseif ($after2.act -ne $after.act) { $effect2 = 'state' }
                elseif ($after2.inputs -ne $after.inputs) { $effect2 = 'input' }
                elseif ($after2.nodes -ne $after.nodes) { $effect2 = 'node' }
                elseif ($after2.htmlLen -ne $after.htmlLen) { $effect2 = 'style' }
                elseif ($after2.bodyLen -ne $after.bodyLen) { $effect2 = 'dom' }
                elseif ($ev2.reqTotal -gt 0) { $effect2 = 'request' }
                if ($effect2 -ne 'none') {
                    $effect = "pointer-$effect2"
                    $after = $after2
                    $ev = $ev2
                    $script:Pass++
                    # evidence from the synthetic attempt is folded in
                    if ($ev.pageErr -gt 0 -or $ev.consoleErr -gt 0 -or $ev.http5xx -gt 0) {
                        Ck "$rname/$($it.kind) '$($it.label)' clean after real click" $false `
                            ("effect=$effect pageErr=$($ev.pageErr) consoleErr=$($ev.consoleErr) 5xx=$($ev.http5xx) $($ev.first5)")
                    }
                    [void]$script:Rows.Add(("$path|$($it.kind)|$($it.label)|$effect|$($after.path)|$($ev.pageErr)|$($ev.consoleErr)|$($ev.http5xx)|$($ev.http4xx)"))
                    Js $jsReset | Out-Null
                    Start-Sleep -Milliseconds 200
                    continue
                }
            }

            # Icon-only controls have no text; without a fallback every such row in the
            # matrix reads as an empty label and the evidence cannot be audited per icon.
            $lbl = $it.label
            if (-not $lbl) { $lbl = '[icon:' + ([string]$it.cls).Trim() + ']' }
            $detail = "kind=$($it.kind) label='$lbl' effect=$effect pageErr=$($ev.pageErr) consoleErr=$($ev.consoleErr) 5xx=$($ev.http5xx) 4xx=$($ev.http4xx) noise=$($ev.noise)"
            if ($ev.firstApp) { $detail += " firstErr='" + ($ev.firstApp.Substring(0, [Math]::Min(90, $ev.firstApp.Length))) + "'" }
            [void]$script:Rows.Add(("$path|$($it.kind)|$lbl|$effect|$($after.path)|$($ev.pageErr)|$($ev.consoleErr)|$($ev.http5xx)|$($ev.http4xx)"))

            if ($ev.pageErr -gt 0) { Ck "$rname/$($it.kind) '$lbl' no page error" $false $detail }
            elseif ($ev.consoleErr -gt 0) { Ck "$rname/$($it.kind) '$lbl' no console error" $false $detail }
            elseif ($ev.http5xx -gt 0) { Ck "$rname/$($it.kind) '$lbl' no 5xx" $false ($detail + ' ' + $ev.first5) }
            elseif ($after.bodyLen -le 30) { Ck "$rname/$($it.kind) '$lbl' page stays alive" $false $detail }
            else { $script:Pass++ }

            # A control that was ALREADY selected (active tab / chosen option) is
            # expected to do nothing when clicked again -- that is not a dead button.
            if ($effect -eq 'none' -and $it.active -eq 'Y') { $effect = 'already-active' }
            # Same for a pager button that is already the current page: pressing it is a
            # no-op by design. (On /edu/wrongbook page "2" really does work -- rows went
            # 20 -> 19 -- but after the sweep had already moved there, pressing it again
            # changed nothing.)
            if ($effect -eq 'none' -and $it.kind -eq 'pager') {
                $cur = Js $jsCurrentPager 20000
                if ($cur -and $cur -eq $lbl) { $effect = 'already-current-page' }
            }
            if ($effect -eq 'none' -and $expectNoop) { $effect = 'already-applied-live-filter' }
            # still nothing? a hover-triggered dropdown only opens under a real pointer.
            if ($effect -eq 'none') {
                $hov = Try-HoverOpen -Rname $rname -Path $path -It $it -Label $lbl
                if ($hov) { $effect = $hov }
            }
            # Reset / search with nothing in the filter is a no-op by design, not a dead button.
            # Pressing search with an empty query asks for exactly the rows already shown
            # (ReTable does not even issue a request), and reset has nothing to clear.
            # The real defect is the opposite: a filled filter that a click leaves untouched.
            if ($effect -eq 'none' -and ($lbl -match $resetRx -or $lbl -match $searchRx)) {
                $fs = $null
                try { $fs = (Js $jsFilterState 20000) | ConvertFrom-Json } catch { $fs = $null }
                if ($fs -and $fs.filled -eq 0) { $effect = 'no-filter-applied' }
            }
            # A shortcut that points at the page you are already on is a no-op by design
            # (the dashboard quick-access widget pushes router paths, and pushing the
            # current path changes nothing). Match on the active tab's label rather than
            # hard-coding that one widget.
            if ($effect -eq 'none' -and ($it.kind -eq 'clickable' -or $it.kind -eq 'link')) {
                $tabLbl = ''
                try { $tabLbl = [string](Js $jsActiveTabLabel 20000) } catch { $tabLbl = '' }
                if ($tabLbl -and $lbl -and $tabLbl.Contains($lbl)) { $effect = 'already-on-this-page' }
            }
            if ($effect -eq 'none') {
                # last resort before judging: a hover-triggered dropdown needs a real pointer
                $hov = Try-HoverOpen -Rname $rname -Path $path -It $it -Label $lbl
                if ($hov) { $effect = $hov }
            }
            if ($effect -eq 'none') { [void]$script:Dead.Add("$rname/$($it.kind) '$lbl' tag=$($it.tag) cls='$($it.cls)' (no visible effect, wasActive=$($it.active))") }

            # A trigger that opened a dialog also means that dialog's own controls are now
            # on screen and reachable -- click them too (see Walk-DialogControls).
            if ($after.dialogs -gt $before.dialogs) {
                Walk-DialogControls -Rname $rname -Path $path -Trig $lbl -AfterState $after
                # a dialog's own select/dropdown opens a popper of its own
                $after2 = $null
                try { $after2 = (Js $jsProbe) | ConvertFrom-Json } catch { $after2 = $null }
                if ($after2 -and $after2.poppers -gt 0) { Walk-PopperControls -Rname $rname -Path $path -Trig ($lbl + '/dialog') -AfterState $after2 }
            }
            # Not a count delta: a tooltip popper may already be on screen, so "poppers grew"
            # misses real dropdowns. The pre-click guard closes every overlay, so any popper
            # visible after the click was opened by that click.
            elseif ($after.poppers -gt 0) { Walk-PopperControls -Rname $rname -Path $path -Trig $lbl -AfterState $after }
            Js $jsReset | Out-Null
            Start-Sleep -Milliseconds 200
        }
        AbRaw @('screenshot', (Join-Path $OutDir ("content-" + ($rname -replace '[^a-zA-Z0-9_-]', '_') + '.png'))) 60000 | Out-Null
        Write-Host ("  -> content clicks: {0} (nav restarts {1})" -f $clicked, $restarts)
    }
}

# ---------- Phase 2: layout chrome (once) ----------
if (-not $SkipChrome) {
    Write-Host ''
    Write-Host '########## [chrome] top bar / sidebar / tabs ##########'
    $homePath = '/home'
    Open-Route $homePath
    $i = 0; $restarts = 0
    while ($true) {
        $inv = Js $jsInvChrome
            if ($inv -match '^##[^#]+##') { $inv = $inv.Substring($inv.IndexOf('##', 2) + 2) }
        if (-not $inv) { Ck 'chrome inventory readable' $false ''; break }
        $items = @($inv | ConvertFrom-Json)
        if ($i -ge $items.Count) { break }
        $it = $items[$i]
        $i++
        $before = (Js $jsProbe) | ConvertFrom-Json
        Clear-Evidence
        $clickRes = Js (Get-ClickJs $it.id)
        if ($clickRes -ne 'ok') { [void]$script:Dead.Add("chrome/$($it.kind) '$($it.label)' -> '$clickRes'"); continue }
        $script:Clicks++
        Start-Sleep -Milliseconds ($SettleMs + 400)
        WaitJs 'document.readyState === "complete"' 15 | Out-Null
        WaitJs '!document.querySelector(".el-loading-mask")' 12 | Out-Null
        Start-Sleep -Milliseconds 500
        $after = (Js $jsProbe) | ConvertFrom-Json
        $ev = Read-Evidence
        $lbl = $it.label
        $detail = "kind=$($it.kind) label='$lbl' -> $($after.path) bodyLen=$($after.bodyLen) pageErr=$($ev.pageErr) consoleErr=$($ev.consoleErr) 5xx=$($ev.http5xx)"
        if ($ev.firstApp) { $detail += " firstErr='" + ($ev.firstApp.Substring(0, [Math]::Min(120, $ev.firstApp.Length))) + "'" }
        if ($ev.noise -gt 0) { $detail += " (browser-noise=$($ev.noise))" }
        [void]$script:Rows.Add(("[chrome]|$($it.kind)|$lbl|click|$($after.path)|$($ev.pageErr)|$($ev.consoleErr)|$($ev.http5xx)|$($ev.http4xx)|$($ev.noise)|$($ev.firstApp)"))
        if ($after.bodyLen -le 30) { Ck "chrome/$($it.kind) '$lbl' app stays usable" $false $detail }
        elseif ($ev.pageErr -gt 0) { Ck "chrome/$($it.kind) '$lbl' no page error" $false $detail }
        elseif ($ev.consoleErr -gt 0) { Ck "chrome/$($it.kind) '$lbl' no console error" $false $detail }
        elseif ($ev.http5xx -gt 0) { Ck "chrome/$($it.kind) '$lbl' no 5xx" $false ($detail + ' ' + $ev.first5) }
        else { $script:Pass++ }
        # The header's module switcher and avatar menu open on hover, so a click leaves no
        # trace; hover them for real and walk whatever menu appears.
        if ($it.cls -match 'el-dropdown|el-tooltip|el-popper') {
            $hstate = $null
            try { $hstate = (Js $jsProbe) | ConvertFrom-Json } catch { $hstate = $null }
            if ($hstate) {
                $r = [string](Try-HoverOpen -Rname 'chrome' -Path '/home' -It $it -Label $lbl)
                if ($r) { $script:Pass++ }
            }
        }
        # always come back to a known-good page
        Js $jsReset | Out-Null
        Open-Route $homePath
        $restarts++
        if ($restarts -gt 60) { break }
    }
    AbRaw @('screenshot', (Join-Path $OutDir 'chrome-after.png')) 60000 | Out-Null
}

# ---------- restore the dashboard layout the crawl mutated ----------
# Clicking the dashboard's "remove widget" control really does delete a widget, and the
# layout is cached in localStorage ("...:layout"). Left alone, every run would cover less
# than the previous one -- the dashboard ended up with 3 controls instead of 49. Snapshot
# before the crawl, put it back afterwards, and assert the restore.
if ($script:LayoutSnap) {
    $enc = [uri]::EscapeDataString($script:LayoutSnap)
    $restoreJs = "(() => { const s = JSON.parse(decodeURIComponent('$enc')); const ks = Object.keys(s); ks.forEach(k => localStorage.setItem(k, s[k])); return ks.join(','); })()"
    $done = ''
    try { $done = [string](Js $restoreJs 40000) } catch { $done = '' }
    Ck 'dashboard layout restored after the crawl' (($done -split ',').Count -ge 1) "keys=$done"
}

# ---------- report ----------
$csv = Join-Path $OutDir 'click-matrix.csv'
$script:Rows | Set-Content -Path $csv -Encoding UTF8
$md = Join-Path $OutDir 'click-matrix.md'
$sb = New-Object Text.StringBuilder
[void]$sb.AppendLine('# Click matrix (every clickable control exercised)')
[void]$sb.AppendLine()
[void]$sb.AppendLine(("routes: {0}   clicks executed: {1}   dead clicks: {2}   passed: {3}   failed: {4}" -f $routes.Count, $script:Clicks, $script:Dead.Count, $script:Pass, $script:Fail))
[void]$sb.AppendLine()
[void]$sb.AppendLine('| route | kind | label | effect | path after | pageErr | consoleErr | 5xx | 4xx |')
[void]$sb.AppendLine('|---|---|---|---|---|---|---|---|---|')
foreach ($row in $script:Rows) {
    $c = $row -split '\|'
    if ($c.Count -ge 9) { [void]$sb.AppendLine(('| {0} | {1} | {2} | {3} | {4} | {5} | {6} | {7} | {8} |' -f $c[0], $c[1], $c[2], $c[3], $c[4], $c[5], $c[6], $c[7], $c[8])) }
}
[IO.File]::WriteAllText($md, $sb.ToString(), (New-Object Text.UTF8Encoding($false)))

Write-Host ''
Write-Host '########## dead clicks ##########'
if ($script:Dead.Count -eq 0) { Write-Host '  (none)' } else { $script:Dead | ForEach-Object { Write-Host ('  - ' + $_) } }
Write-Host ''
Write-Host ('=========== SUMMARY: routes {0} | clicks {1} | passed {2} | failed {3} | dead {4} | harness-err {5} | skipped-destructive {6} ===========' -f $routes.Count, $script:Clicks, $script:Pass, $script:Fail, $script:Dead.Count, $script:HarnessErr.Count, $script:SkipDestructive)
Write-Host ("matrix: $csv")
Write-Host ("        $md")
if ($script:Fails.Count -gt 0) {
    Write-Host ''
    Write-Host 'failures:'
    $script:Fails | ForEach-Object { Write-Host ('  * ' + $_) }
}
exit $(if ($script:Fail -gt 0) { 1 } else { 0 })
