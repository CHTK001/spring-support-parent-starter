# =====================================================================
# Scheduler center production acceptance (repeatable, ASCII-only).
#
# Covers: remote node registration, distributed script dispatch + execution,
# CRON auto dispatch (including blank-group => node cluster group), Webhook
# alarm, Email alarm, guards (disabled trigger / negative log cleanup),
# ledger lifecycle (rename / delete).
#
# Requirements: JDK (java); Python optional (fake SMTP + Webhook receiver).
# Alarm cases report SKIP when Python is missing instead of a false PASS.
#
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 files
# with the ANSI code page; non-ASCII text breaks parsing and any shell-based
# rewrite would corrupt it (see repo AGENTS.md encoding rules).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\accept-scheduler.ps1
#   Options: -JarPath <collect jar> -WorkRoot <log root> -SkipStart
#
# Exit code: 0 all pass (SKIP allowed); 1 any FAIL.
# =====================================================================
param(
    [string]$JarPath = "D:\ch\project\spring-support-parent-starter\spring-support-collect-starter\target\spring-support-collect-starter-4.0.0.51.jar",
    [string]$WorkRoot = "D:\ch\project\logs",
    [int]$SchedulerPort = 18191,
    [int]$ExecutorPort = 18192,
    [int]$SyncPort = 18081,
    [int]$HookPort = 18099,
    [int]$SmtpPort = 2525,
    [switch]$SkipStart
)

$ErrorActionPreference = "Continue"
$script:results = New-Object System.Collections.ArrayList
$script:base = "http://127.0.0.1:$SchedulerPort/collect/api"
$script:headers = @{}
$script:started = New-Object System.Collections.ArrayList
$script:hasPython = $null -ne (Get-Command python -ErrorAction SilentlyContinue)

function Add-Result([string]$name, [bool]$pass, [string]$detail) {
    [void]$script:results.Add([pscustomobject]@{ Case = $name; Pass = $pass; Detail = $detail })
    $mark = if ($pass) { "PASS" } else { "FAIL" }
    Write-Host ("[{0}] {1} - {2}" -f $mark, $name, $detail)
}

function Test-Port([int]$port) {
    $client = New-Object Net.Sockets.TcpClient
    try { $client.Connect("127.0.0.1", $port); return $true } catch { return $false } finally { $client.Dispose() }
}

function Wait-Port([int]$port, [int]$seconds) {
    for ($i = 0; $i -lt $seconds; $i++) {
        if (Test-Port $port) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Test-FileFree([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return $true }
    try {
        $stream = [System.IO.File]::Open($path, "Open", "ReadWrite", "None")
        $stream.Close()
        return $true
    } catch {
        return $false
    }
}

function Start-Background([string]$file, [string[]]$arguments, [string]$workingDirectory, [string]$outFile, [string]$errFile) {
    $process = Start-Process -FilePath $file -ArgumentList $arguments -WorkingDirectory $workingDirectory `
        -RedirectStandardOutput $outFile -RedirectStandardError $errFile -WindowStyle Hidden -PassThru
    [void]$script:started.Add($process)
    return $process
}

function Stop-ByPort([int]$port) {
    $lines = netstat -ano | Select-String -Pattern (":$port\s+.*LISTENING")
    foreach ($line in $lines) {
        $parts = ($line.Line -split "\s+") | Where-Object { $_ }
        $pidValue = $parts[-1]
        if ($pidValue -match "^\d+$") { Stop-Process -Id ([int]$pidValue) -Force -ErrorAction SilentlyContinue }
    }
}

function Login() {
    $body = @{ username = "admin"; password = "123456"; loginType = "WEB" } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$script:base/v2/user/login" -Method Post `
        -ContentType "application/json;charset=UTF-8" -Body $body -TimeoutSec 10
    if ($response.code -ne "00000") { throw "login failed: $($response.msg)" }
    $token = $response.data.accessToken
    $script:headers = @{ "x-oauth-token" = $token; "Authorization" = "Bearer $token" }
}

function Api([string]$method, [string]$path, $body = $null) {
    if ($null -ne $body) {
        return Invoke-RestMethod -Uri "$script:base$path" -Method $method -Headers $script:headers `
            -ContentType "application/json;charset=UTF-8" -Body ($body | ConvertTo-Json -Depth 8) -TimeoutSec 15
    }
    return Invoke-RestMethod -Uri "$script:base$path" -Method $method -Headers $script:headers -TimeoutSec 15
}

function Wait-Log([string]$jobKey, [int]$seconds, [string]$triggerType = "") {
    for ($i = 0; $i -lt $seconds; $i++) {
        Start-Sleep -Seconds 2
        $page = Api "Get" "/v2/scheduler/log/page?pageNum=1&pageSize=5&jobKey=$([uri]::EscapeDataString($jobKey))"
        $rows = @($page.data.data)
        if ($triggerType -ne "") { $rows = @($rows | Where-Object { $_.triggerType -eq $triggerType }) }
        if ($rows.Count -gt 0) { return $rows[0] }
    }
    return $null
}

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$reportFile = Join-Path $WorkRoot "accept-report-$stamp.txt"
$schedDir = Join-Path $WorkRoot "acc-sched"
$execDir = Join-Path $WorkRoot "acc-exec"
$schedDb = Join-Path $schedDir "data\sched_h2.mv.db"
$execDb = Join-Path $execDir "data\exec_h2.mv.db"
$hookCapture = Join-Path $WorkRoot "hook_capture.log"
$mailCapture = Join-Path $WorkRoot "mail_capture.log"
$createdKeys = New-Object System.Collections.ArrayList

Write-Host "=== scheduler acceptance $stamp ==="
if (-not (Test-Path -LiteralPath $JarPath)) { Write-Host "jar not found: $JarPath"; exit 1 }

try {
    if (-not $SkipStart) {
        foreach ($port in @($SchedulerPort, $ExecutorPort, $SyncPort)) { if (Test-Port $port) { Stop-ByPort $port } }
        Start-Sleep -Seconds 3

        # Pre-flight: H2 file locks from stale instances surface as a confusing
        # routing-datasource NPE later; fail fast with a clear message instead.
        if (-not (Test-FileFree $schedDb)) { throw "scheduler DB is locked by another process: $schedDb" }
        if (-not (Test-FileFree $execDb)) { throw "executor DB is locked by another process: $execDb" }

        if ($script:hasPython) {
            Remove-Item $hookCapture, $mailCapture -ErrorAction SilentlyContinue
            Start-Background "python" @("fake_smtp.py") $WorkRoot (Join-Path $WorkRoot "fs_out.log") (Join-Path $WorkRoot "fs_err.log") | Out-Null
            Start-Background "python" @("hook_server.py") $WorkRoot (Join-Path $WorkRoot "hs_out.log") (Join-Path $WorkRoot "hs_err.log") | Out-Null
            Wait-Port $HookPort 10 | Out-Null
            Wait-Port $SmtpPort 10 | Out-Null
        }

        Start-Background "java" @(
            "-Dfile.encoding=UTF-8", "-jar", $JarPath,
            "--server.port=$SchedulerPort",
            "--plugin.job.role=SCHEDULER",
            "--plugin.job.sync.scheduler.listen-host=127.0.0.1",
            "--plugin.job.sync.scheduler.listen-port=$SyncPort",
            "--spring.datasource.url=jdbc:h2:file:./data/sched_h2;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        ) $schedDir (Join-Path $WorkRoot "accept-sched-out.log") (Join-Path $WorkRoot "accept-sched-err.log") | Out-Null
        if (-not (Wait-Port $SchedulerPort 90)) { throw "scheduler not ready within 90s" }
        Start-Sleep -Seconds 5

        Start-Background "java" @(
            "-Dfile.encoding=UTF-8", "-jar", $JarPath,
            "--server.port=$ExecutorPort",
            "--plugin.job.role=EXECUTOR",
            "--plugin.job.sync.executor.server-url=tcp://127.0.0.1:$SyncPort",
            "--spring.mail.host=127.0.0.1", "--spring.mail.port=$SmtpPort",
            "--spring.mail.properties.mail.smtp.connectiontimeout=10000",
            "--spring.mail.properties.mail.smtp.timeout=10000",
            "--spring.mail.properties.mail.smtp.writetimeout=10000",
            "--spring.datasource.url=jdbc:h2:file:./data/exec_h2;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        ) $execDir (Join-Path $WorkRoot "accept-exec-out.log") (Join-Path $WorkRoot "accept-exec-err.log") | Out-Null
    }

    Login
    # Purge rows left by previous interrupted runs (all acceptance rows use the acc-* prefix).
    $ledgerPage = Api "Get" "/v2/scheduler/page?pageNum=1&pageSize=500"
    $staleRows = @($ledgerPage.data.data | Where-Object { $_.jobKey -like "acc-*" })
    foreach ($staleRow in $staleRows) {
        try { [void](Api "Delete" "/v2/scheduler/$([uri]::EscapeDataString($staleRow.jobKey))") } catch { }
    }
    Write-Host ("preflight: purged {0} stale acc-* rows" -f $staleRows.Count)

    $summary = Api "Get" "/v2/scheduler/summary"
    Add-Result "summary" ($summary.code -eq "00000") "role=$($summary.data.role) group=$($summary.data.group) leader=$($summary.data.leader)"

    $node = $null
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        $nodes = Api "Get" "/v2/scheduler/nodes"
        if ($nodes.data.Count -gt 0) { $node = $nodes.data[0]; break }
    }
    Add-Result "remote-node-register" ($null -ne $node) $(if ($node) { "nodeId=$($node.nodeId) handlers=$($node.handlers -join ',')" } else { "no node within 60s" })

    $hookKey = "acc-hook-$stamp"
    [void]$createdKeys.Add($hookKey)
    if ($script:hasPython) {
        $body = @{ jobKey = $hookKey; jobName = "acc-webhook-$stamp"; scheduleType = "NONE"; enabled = $true;
            dispatchMode = "LOCAL"; glueType = "groovy"; glueSource = "throw new RuntimeException('acc-hook')";
            alarmType = "WEBHOOK"; alarmEmail = "http://127.0.0.1:$HookPort/alarm"; failRetry = 0 }
        [void](Api "Post" "/v2/scheduler/save" $body)
        [void](Api "Post" "/v2/scheduler/trigger?jobKey=$([uri]::EscapeDataString($hookKey))")
        $row = Wait-Log $hookKey 20
        $hookHit = (Test-Path $hookCapture) -and ((Get-Content $hookCapture -Raw) -match [regex]::Escape($hookKey))
        $detail = if ($row) { "executeCode=$($row.executeCode) nodeId=$($row.nodeId) webhookDelivered=$hookHit" } else { "no log" }
        Add-Result "distributed-script-webhook-alarm" (($null -ne $row) -and ($row.executeCode -eq "FAILURE") -and $hookHit) $detail
    } else { Add-Result "distributed-script-webhook-alarm" $true "SKIP (no python)" }

    $mailKey = "acc-mail-$stamp"
    [void]$createdKeys.Add($mailKey)
    if ($script:hasPython) {
        $body = @{ jobKey = $mailKey; jobName = "acc-mail-$stamp"; scheduleType = "NONE"; enabled = $true;
            dispatchMode = "LOCAL"; glueType = "groovy"; glueSource = "throw new RuntimeException('acc-mail')";
            alarmType = "EMAIL"; alarmEmail = "ops@example.com"; failRetry = 0 }
        [void](Api "Post" "/v2/scheduler/save" $body)
        [void](Api "Post" "/v2/scheduler/trigger?jobKey=$([uri]::EscapeDataString($mailKey))")
        $row = Wait-Log $mailKey 20
        $mailHit = (Test-Path $mailCapture) -and ((Get-Content $mailCapture -Raw) -match [regex]::Escape($mailKey))
        $detail = if ($row) { "executeCode=$($row.executeCode) mailDelivered=$mailHit" } else { "no log" }
        Add-Result "email-alarm" (($null -ne $row) -and $mailHit) $detail
    } else { Add-Result "email-alarm" $true "SKIP (no python)" }

    $badMailKey = "acc-badmail-$stamp"
    [void]$createdKeys.Add($badMailKey)
    if ($script:hasPython) {
        $body = @{ jobKey = $badMailKey; jobName = "acc-badmail-$stamp"; scheduleType = "NONE"; enabled = $true;
            dispatchMode = "LOCAL"; glueType = "groovy"; glueSource = "throw new RuntimeException('acc-badmail')";
            alarmType = "EMAIL"; alarmEmail = "bad-address"; failRetry = 0 }
        [void](Api "Post" "/v2/scheduler/save" $body)
        [void](Api "Post" "/v2/scheduler/trigger?jobKey=$([uri]::EscapeDataString($badMailKey))")
        $row = Wait-Log $badMailKey 20
        $alarmFailReported = ($null -ne $row) -and ($row.message -match "\[alarm\]") -and ($row.message -match "\(EMAIL\)") -and ($row.message -match "bad-address")
        Add-Result "email-alarm-failure-reported" $alarmFailReported $(if ($row) { "alarmFailureSegment=$alarmFailReported" } else { "no log" })
    } else { Add-Result "email-alarm-failure-reported" $true "SKIP (no python)" }

    $cbKey = "acc-callback-$stamp"
    [void]$createdKeys.Add($cbKey)
    $body = @{ jobKey = $cbKey; jobName = "acc-callback-$stamp"; scheduleType = "NONE"; enabled = $true;
        dispatchMode = "LOCAL"; glueType = "groovy"; glueSource = "throw new RuntimeException('acc-callback')";
        failRetry = 1; retryInterval = 0;
        retryCallbackBean = "collect.self.check"; exceptionCallbackBean = "collect.self.check" }
    [void](Api "Post" "/v2/scheduler/save" $body)
    [void](Api "Post" "/v2/scheduler/trigger?jobKey=$([uri]::EscapeDataString($cbKey))")
    $row = Wait-Log $cbKey 25
    $retryOk = ($null -ne $row) -and ($row.message -match "\[retry\]") -and ($row.message -match "collect\.self\.check")
    $excOk = ($null -ne $row) -and ($row.message -match "\[exception\]") -and ($row.message -match "collect\.self\.check")
    Add-Result "retry-and-exception-callbacks" ($retryOk -and $excOk) "retryCallback=$retryOk exceptionCallback=$excOk"

    $cronKey = "acc-cron-$stamp"
    [void]$createdKeys.Add($cronKey)
    $body = @{ jobKey = $cronKey; jobName = "acc-cron-$stamp"; scheduleType = "CRON"; scheduleTime = "*/10 * * * * ?";
        enabled = $true; dispatchMode = "LOCAL"; glueType = "groovy"; glueSource = "return 'acc-ok'" }
    [void](Api "Post" "/v2/scheduler/save" $body)
    $detailRow = Api "Get" "/v2/scheduler/detail/$cronKey"
    $groupOk = $detailRow.data.group -eq $summary.data.group
    $cronRow = Wait-Log $cronKey 40 "CRON"
    $cronCode = if ($cronRow) { $cronRow.executeCode } else { "none" }
    $cronNode = if ($cronRow) { $cronRow.nodeId } else { "-" }
    Add-Result "cron-auto-dispatch-group-default" ($groupOk -and ($null -ne $cronRow)) `
        "resolvedGroup=$($detailRow.data.group) expected=$($summary.data.group) executeCode=$cronCode nodeId=$cronNode"

    [void](Api "Put" "/v2/scheduler/status" @{ jobKey = $cronKey; enabled = $false })
    $guard1 = Api "Post" "/v2/scheduler/trigger?jobKey=$([uri]::EscapeDataString($cronKey))"
    Add-Result "guard-disabled-trigger-rejected" ($guard1.ok -eq $false) "code=$($guard1.code)"
    $guard2 = Api "Post" "/v2/scheduler/log/cleanup?retentionDays=-1"
    Add-Result "guard-negative-cleanup-rejected" ($guard2.ok -eq $false) "code=$($guard2.code)"

    $renameKey = "$cronKey-r"
    $body = @{ jobKey = $renameKey; originJobKey = $cronKey; jobName = "acc-rename-$stamp"; scheduleType = "NONE";
        enabled = $true; dispatchMode = "LOCAL"; glueType = "groovy"; glueSource = "return 'acc-r'" }
    [void](Api "Post" "/v2/scheduler/save" $body)
    [void]$createdKeys.Add($renameKey)
    $renamed = Api "Get" "/v2/scheduler/detail/$renameKey"
    $oldGone = Api "Get" "/v2/scheduler/detail/$cronKey"
    Add-Result "ledger-rename" (($renamed.code -eq "00000") -and ($oldGone.code -ne "00000")) "newKey=$($renamed.data.jobKey) oldGone=$($oldGone.code -ne '00000')"

    $deleted = Api "Delete" "/v2/scheduler/$([uri]::EscapeDataString($renameKey))"
    $afterDelete = Api "Get" "/v2/scheduler/detail/$renameKey"
    Add-Result "ledger-delete" (($deleted.code -eq "00000") -and ($afterDelete.code -ne "00000")) "delete=$($deleted.code)"
    $createdKeys.Remove($renameKey) | Out-Null
}
catch {
    Add-Result "acceptance-exception" $false $_.Exception.Message
}
finally {
    try { Login } catch { }
    $cleaned = 0
    $cleanFailed = 0
    foreach ($key in $createdKeys) {
        try { [void](Api "Delete" "/v2/scheduler/$([uri]::EscapeDataString($key))"); $cleaned++ } catch { $cleanFailed++ }
    }
    Write-Host ("cleanup: removed {0} / failed {1} acceptance rows" -f $cleaned, $cleanFailed)
    # Verify the cleanup really happened: a delete answered by a dying instance still
    # returns a success envelope, so re-read the ledger and retry once.
    $leftover = @()
    try {
        $checkPage = Api "Get" "/v2/scheduler/page?pageNum=1&pageSize=500"
        $leftover = @($checkPage.data.data | Where-Object { $_.jobKey -like "acc-*" })
        foreach ($row in $leftover) {
            try { [void](Api "Delete" "/v2/scheduler/$([uri]::EscapeDataString($row.jobKey))") } catch { }
        }
        if ($leftover.Count -gt 0) {
            $checkPage2 = Api "Get" "/v2/scheduler/page?pageNum=1&pageSize=500"
            $leftover = @($checkPage2.data.data | Where-Object { $_.jobKey -like "acc-*" })
        }
    } catch { }
    if ($leftover.Count -gt 0) {
        $names = ($leftover | ForEach-Object { $_.jobKey }) -join ","
        Add-Result "cleanup-verify" $false ("residual acc-* rows: " + $names)
    } else {
        Add-Result "cleanup-verify" $true "ledger has no acceptance rows"
    }
    if (-not $SkipStart) {
        # Let H2/MVStore persist the cleanup before the hard kill; otherwise the deletes
        # can roll back and the rows reappear on the next start.
        Start-Sleep -Seconds 4
        foreach ($process in $script:started) { try { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue } catch { } }
        foreach ($port in @($SchedulerPort, $ExecutorPort, $SyncPort, $HookPort, $SmtpPort)) { Stop-ByPort $port }
    }
    $total = @($script:results).Count
    $failed = @($script:results | Where-Object { -not $_.Pass }).Count
    $skipped = @($script:results | Where-Object { $_.Detail -like "SKIP*" }).Count
    Write-Host ""
    Write-Host ("=== result: total {0} / failed {1} / skipped {2} ===" -f $total, $failed, $skipped)
    $reportText = $script:results | Format-Table -AutoSize | Out-String
    [System.IO.File]::WriteAllText($reportFile, $reportText, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "report: $reportFile"
    if ($failed -gt 0) { exit 1 } else { exit 0 }
}
