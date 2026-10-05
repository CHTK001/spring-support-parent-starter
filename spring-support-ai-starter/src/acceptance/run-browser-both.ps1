# 浏览器验收：dev(15689) + 产物(15690) 两侧各 11 套
# 用 -File 落盘执行，避免把带引号的命令内联（历史上被多层引号破坏过多次）。
#
# ⚠️ 2026-10-05：`.run/deploy/run-browser-accept.ps1` 与 `.run/deploy/serve-dist.mjs`
#    **均已不存在**（`.run/deploy` 只剩 vite-guard.log），这个脚本一度会静默跑不出
#    任何东西却看不出失败。现在改为**显式前置检查 + dev 侧回落到仓库内运行器**。
$ErrorActionPreference = 'Continue'
$root = 'D:\ch\project'
$acc  = Join-Path $root 'vue-support-parent-starter\apps\vue-support-ai-guada\src\acceptance'
$dist = Join-Path $root 'vue-support-parent-starter\apps\vue-support-ai-guada\dist'
$run  = Join-Path $root '.run\deploy\run-browser-accept.ps1'
$serve= Join-Path $root '.run\deploy\serve-dist.mjs'

Write-Output '########## A. dev 侧（15689）##########'
$env:AI_ACCEPT_BASE = 'http://localhost:15689'
$env:AI_ACCEPT_USER = 'admin'
$env:AI_ACCEPT_PASS = 'admin123'
if (Test-Path $run) {
    Write-Output '  用 .run/deploy 运行器（自带前端自愈与冷编译预热）'
    & pwsh -NoProfile -File $run 2>&1
} else {
    Write-Output '  [.run/deploy/run-browser-accept.ps1 已不存在] 回落到仓库内运行器'
    Write-Output '  注意：仓库内运行器**没有**前端自愈与冷编译预热，dev server 必须已起好'
    Push-Location $acc
    & node run-all-against-dist.mjs 2>&1
    Pop-Location
}

Write-Output ''
Write-Output '########## B. 产物侧（15690）##########'
if (-not (Test-Path $serve)) {
    Write-Output '  [ABORT] .run/deploy/serve-dist.mjs 已不存在，产物侧无运行器。'
    Write-Output '         产物形态本轮**未覆盖**，不产出结论 —— 需要时先补回该脚本与 dist。'
    Write-Output 'BROWSER_ALL_DONE'
    return
}
# 静态 shim 可能已退出，先确保 15690 在听
$up = (Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq 15690 } | Measure-Object).Count
if ($up -eq 0) {
    Write-Output '  15690 未监听，启动静态 shim…'
    Start-Process -FilePath node -ArgumentList 'serve-dist.mjs', $dist, '15690' -WorkingDirectory (Join-Path $root '.run\deploy') -WindowStyle Hidden
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 1
        if ((Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq 15690 } | Measure-Object).Count -gt 0) { break }
    }
}
$code = & curl.exe -s -o NUL -w '%{http_code}' -m 10 'http://localhost:15690/'
Write-Output ("  15690 GET / = " + $code)
if ($code -ne '200') { Write-Output '  [ABORT] 产物 shim 不可达，跳过产物侧（不产出结论）' }
else {
    Push-Location $acc
    $env:AI_ACCEPT_BASE = 'http://localhost:15690'
    $env:AI_ACCEPT_PASS = 'admin123'
    & node run-all-against-dist.mjs 2>&1
    Pop-Location
}
Write-Output 'BROWSER_ALL_DONE'
