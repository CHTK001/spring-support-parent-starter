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
    [int]$SettleMs = 700,
    [int]$NavTimeoutSec = 25,
    [string[]]$Only = @(),
    [switch]$SkipContent,
    [switch]$SkipChrome
)

$ErrorActionPreference = 'Continue'
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# ---------- browser plumbing (every call timeout-bounded) ----------
$script:AbExe = $null
$script:AbTimeoutMs = 90000
foreach ($c in (Get-Command agent-browser -All -ErrorAction SilentlyContinue)) {
    if ($c.Source -like '*.cmd') { $script:AbExe = $c.Source; break }
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
$script:Clicks = 0

function Ck {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    if ($Ok) { $script:Pass++ } else {
        $script:Fail++
        [void]$script:Fails.Add("$Name  $Detail")
        Write-Host ("  [FAIL] {0}  {1}" -f $Name, $Detail)
    }
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
    @{ path = '/edu/teacher'; name = 'teacher-portal' }
)
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
    toasts: Array.from(document.querySelectorAll('.el-message, .el-notification')).filter(vis).length,
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
    // "Already selected" tokens, including the product's own state classes: the insect 3D
    // hotspot marks its open dot with `is-open` (see InsectStage3D.vue styles), so pressing
    // an open hotspot CLOSES the card and the before/after state is identical -- without
    // this it was reported as a dead button.
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
    out.push({ id: id, kind: kind, label: label, tag: el.tagName.toLowerCase(), active: active });
  });
  return JSON.stringify(out);
})()
"@
}

$jsInvContent = Build-InventoryJs -ScopeJs "(document.querySelector('main.el-main') || document.querySelector('.main-content') || document.querySelector('main') || document.body)" -Selectors @(
    'button', '.el-button', '[role="button"]', 'a.el-link', '.el-dropdown-menu__item',
    '.el-radio-button', '.el-tabs__item', '.el-switch', '.el-checkbox',
    '.el-pagination button', '.el-pager li'
)
$jsInvChrome = Build-InventoryJs -ScopeJs "document.body" -Selectors @(
    '.layout-header button', '.layout-aside .el-menu-item', '.layout-aside .el-sub-menu__title',
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
  const XO = XMLHttpRequest.prototype.open;
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

function Open-Route {
    param([string]$Path)
    AbRaw @('open', ($Base + $Path)) 60000 | Out-Null
    WaitJs 'document.readyState === "complete"' $NavTimeoutSec | Out-Null
    WaitJs '!document.querySelector(".el-loading-mask")' 12 | Out-Null
    Start-Sleep -Milliseconds 500
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

# ---------- login ----------
Write-Host '########## 0. login ##########'
AbRaw @('open', "$Base/login") 60000 | Out-Null
Start-Sleep -Seconds 3
Js 'localStorage.clear(); sessionStorage.clear(); "c"' | Out-Null
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
Ck 'admin logged in' $logged
if (-not $logged) { Write-Host 'no session, abort'; exit 2 }

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

        $i = 0; $restarts = 0; $clicked = 0
        while ($true) {
            $inv = Js $jsInvContent
            if (-not $inv) { Ck "$rname inventory readable" $false ''; break }
            $items = @($inv | ConvertFrom-Json)
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
                Start-Sleep -Milliseconds 400
                $postSeed = (Js $jsProbe) | ConvertFrom-Json
                if ($postSeed.rows -ne $preSeed.rows -or $postSeed.htmlLen -ne $preSeed.htmlLen) {
                    $expectNoop = $true
                }
            }
            $before = (Js $jsProbe) | ConvertFrom-Json
            Clear-Evidence
            $clickRes = Js (Get-ClickJs $it.id)
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
            elseif ($after.toasts -gt $before.toasts) { $effect = 'toast' }
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
                elseif ($after2.toasts -gt $after.toasts) { $effect2 = 'toast' }
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

            $lbl = $it.label
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
            if ($effect -eq 'none') { [void]$script:Dead.Add("$rname/$($it.kind) '$lbl' (no visible effect, wasActive=$($it.active))") }

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
        # always come back to a known-good page
        Js $jsReset | Out-Null
        Open-Route $homePath
        $restarts++
        if ($restarts -gt 60) { break }
    }
    AbRaw @('screenshot', (Join-Path $OutDir 'chrome-after.png')) 60000 | Out-Null
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
Write-Host ('=========== SUMMARY: routes {0} | clicks {1} | passed {2} | failed {3} | dead {4} ===========' -f $routes.Count, $script:Clicks, $script:Pass, $script:Fail, $script:Dead.Count)
Write-Host ("matrix: $csv")
Write-Host ("        $md")
if ($script:Fails.Count -gt 0) {
    Write-Host ''
    Write-Host 'failures:'
    $script:Fails | ForEach-Object { Write-Host ('  * ' + $_) }
}
exit $(if ($script:Fail -gt 0) { 1 } else { 0 })
