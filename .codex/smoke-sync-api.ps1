param(
    [string]$BaseUrl = "http://127.0.0.1:19170/monitor/api/v1/sync",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$TaskPrefix = "codex-smoke-sync"
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$artifactDir = Join-Path $scriptRoot "artifacts"
$artifactPath = Join-Path $artifactDir "sync-smoke-last.json"

if (-not (Test-Path $artifactDir)) {
    New-Item -ItemType Directory -Path $artifactDir | Out-Null
}

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Test-SyncSuccessCode {
    param([AllowNull()]$Code)

    return $Code -eq 200 -or $Code -eq 0 -or $Code -eq "200" -or $Code -eq "0" -or $Code -eq "00000"
}

function New-QueryString {
    param([hashtable]$Query)

    if (-not $Query -or $Query.Count -eq 0) {
        return ""
    }

    $pairs = foreach ($entry in $Query.GetEnumerator()) {
        if ($null -eq $entry.Value -or [string]::IsNullOrWhiteSpace([string]$entry.Value)) {
            continue
        }
        "{0}={1}" -f [Uri]::EscapeDataString([string]$entry.Key), [Uri]::EscapeDataString([string]$entry.Value)
    }

    if (-not $pairs) {
        return ""
    }

    return "?" + ($pairs -join "&")
}

function Invoke-SyncApi {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [AllowNull()][object]$Body,
        [AllowNull()][hashtable]$Query
    )

    $uri = "{0}{1}{2}" -f $BaseUrl.TrimEnd('/'), $Path, (New-QueryString $Query)
    $params = @{
        Method     = $Method
        Uri        = $uri
        WebSession = $session
        Headers    = @{
            Accept = "application/json"
        }
    }

    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }

    $response = Invoke-RestMethod @params
    if (-not (Test-SyncSuccessCode $response.code)) {
        throw ("{0} {1} failed: code={2}, message={3}" -f $Method, $uri, $response.code, $response.msg)
    }

    return $response
}

function Get-TaskRecords {
    $result = Invoke-SyncApi -Method GET -Path "/task/list" -Query @{
        page = 1
        size = 200
    }
    return @($result.data.records)
}

function Wait-ForLogs {
    param(
        [Parameter(Mandatory = $true)][long]$TaskId,
        [int]$MaxAttempts = 20,
        [int]$DelaySeconds = 1
    )

    for ($i = 0; $i -lt $MaxAttempts; $i++) {
        $result = Invoke-SyncApi -Method GET -Path "/task/logs/$TaskId" -Query @{
            page = 1
            size = 10
        }

        $records = @($result.data.records)
        if ($records.Count -gt 0) {
            return ,$records
        }

        Start-Sleep -Seconds $DelaySeconds
    }

    return ,@()
}

Write-Host "[sync-smoke] login -> $BaseUrl/auth/login"
$null = Invoke-SyncApi -Method POST -Path "/auth/login" -Body @{
    username   = $Username
    password   = $Password
    rememberMe = $false
}

$info = Invoke-SyncApi -Method GET -Path "/auth/info"
Write-Host "[sync-smoke] user -> $($info.data.username)"

$existingSmokeTasks = Get-TaskRecords | Where-Object {
    $_.syncTaskName -like "$TaskPrefix*"
}

foreach ($task in $existingSmokeTasks) {
    try {
        if ($task.syncTaskStatus -eq "RUNNING") {
            Write-Host "[sync-smoke] stop old task -> $($task.syncTaskId) / $($task.syncTaskName)"
            $null = Invoke-SyncApi -Method POST -Path "/task/stop/$($task.syncTaskId)"
        }
    } catch {
        Write-Warning "[sync-smoke] stop old task failed: $($_.Exception.Message)"
    }

    try {
        Write-Host "[sync-smoke] delete old task -> $($task.syncTaskId) / $($task.syncTaskName)"
        $null = Invoke-SyncApi -Method DELETE -Path "/task/delete/$($task.syncTaskId)"
    } catch {
        Write-Warning "[sync-smoke] delete old task failed: $($_.Exception.Message)"
    }
}

$taskName = "{0}-{1}" -f $TaskPrefix, (Get-Date -Format "yyyyMMdd-HHmmss")

$createResult = Invoke-SyncApi -Method POST -Path "/task/create" -Body @{
    syncTaskName          = $taskName
    syncTaskDesc          = "Codex smoke test for sync job integration"
    syncTaskBatchSize     = 100
    syncTaskRetryCount    = 1
    syncTaskRetryInterval = 500
}

$taskId = [long]$createResult.data.syncTaskId
Write-Host "[sync-smoke] created -> taskId=$taskId name=$taskName"

$designResult = Invoke-SyncApi -Method POST -Path "/task/design/$taskId" -Body @{
    layout      = '{"x":0,"y":0,"zoom":1}'
    nodes       = @(
        @{
            syncNodeType     = "INPUT"
            syncNodeSpiName  = "mock"
            syncNodeName     = "Mock Input"
            syncNodeKey      = "node_input"
            syncNodeConfig   = '{"count":5,"interval":0}'
            syncNodePosition = '{"x":80,"y":120}'
            syncNodeOrder    = 1
        },
        @{
            syncNodeType     = "OUTPUT"
            syncNodeSpiName  = "console"
            syncNodeName     = "Console Output"
            syncNodeKey      = "node_output"
            syncNodeConfig   = '{"format":"JSON","maxLength":1000}'
            syncNodePosition = '{"x":420,"y":120}'
            syncNodeOrder    = 2
        }
    )
    connections = @(
        @{
            sourceNodeKey  = "node_input"
            sourceHandle   = "output"
            targetNodeKey  = "node_output"
            targetHandle   = "input"
            connectionType = "DATA"
            connectionLabel = "mock->console"
        }
    )
}

if (-not $designResult.data) {
    throw "save design failed"
}

Write-Host "[sync-smoke] design saved"

$startResult = Invoke-SyncApi -Method POST -Path "/task/start/$taskId"
if (-not $startResult.data) {
    throw "start task failed"
}

Write-Host "[sync-smoke] started"

$executeResult = Invoke-SyncApi -Method POST -Path "/task/execute/$taskId"
$executeLogId = $executeResult.data
Write-Host "[sync-smoke] executeOnce submitted -> logId=$executeLogId"

Start-Sleep -Seconds 2

$records = @(Wait-ForLogs -TaskId $taskId)
$latestLog = if ($records.Count -gt 0) { $records[0] } else { $null }

$taskList = Get-TaskRecords
$taskRecord = $taskList | Where-Object { $_.syncTaskId -eq $taskId } | Select-Object -First 1
$summary = [ordered]@{
    baseUrl      = $BaseUrl
    taskId       = $taskId
    taskName     = $taskName
    taskStatus   = $taskRecord.syncTaskStatus
    executeLogId = $executeLogId
    latestLogId  = $latestLog.syncLogId
    latestStatus = $latestLog.syncLogStatus
    latestMessage = $latestLog.syncLogMessage
    artifactPath = $artifactPath
}

$summary | ConvertTo-Json -Depth 8 | Set-Content -Path $artifactPath -Encoding UTF8
$summary | ConvertTo-Json -Depth 8
