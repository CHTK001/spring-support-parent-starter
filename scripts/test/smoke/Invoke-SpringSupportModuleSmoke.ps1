param(
    [string[]]$Modules = @(),
    [switch]$ContinueOnError,
    [string]$MavenOpts = '-Xms256m -Xmx768m'
)

$ErrorActionPreference = 'Stop'
$originalMavenOpts = $env:MAVEN_OPTS
$env:MAVEN_OPTS = $MavenOpts

$repoRoot = 'H:\workspace\2\spring-support-parent-starter'
$resultsDir = Join-Path $repoRoot 'test-results'
New-Item -ItemType Directory -Force -Path $resultsDir | Out-Null

$profileMap = [ordered]@{
    'spring-support-datasource-starter' = 'smoke-datasource'
    'spring-support-common-starter' = 'smoke-common'
    'spring-support-mybatis-starter' = 'smoke-mybatis'
    'spring-support-oauth-client-starter' = 'smoke-oauth-client'
    'spring-support-redis-starter' = 'smoke-redis'
    'spring-support-filesystem-minio-starter' = 'smoke-filesystem-minio'
    'spring-support-filesystem-starter' = 'smoke-filesystem'
    'spring-support-swagger-starter' = 'smoke-swagger'
    'spring-support-swagger2-starter' = 'smoke-swagger2'
    'spring-support-queue-starter' = 'smoke-queue'
    'spring-support-queue-mqtt-starter' = 'smoke-queue-mqtt'
    'spring-support-queue-kafka-starter' = 'smoke-queue-kafka'
    'spring-support-queue-rabbitmq-starter' = 'smoke-queue-rabbitmq'
    'spring-support-queue-rocketmq-starter' = 'smoke-queue-rocketmq'
    'spring-support-socket-starter' = 'smoke-socket'
    'spring-support-socket-websocket-starter' = 'smoke-socket-websocket'
    'spring-support-socket-io-starter' = 'smoke-socket-io'
    'spring-support-socket-rsocket-starter' = 'smoke-socket-rsocket'
    'spring-support-socket-sse-starter' = 'smoke-socket-sse'
    'spring-support-rpc-starter' = 'smoke-rpc'
    'spring-support-sync-starter' = 'smoke-sync'
    'spring-support-elasticsearch-starter' = 'smoke-elasticsearch'
    'spring-support-discovery-starter' = 'smoke-discovery'
    'spring-support-configcenter-starter' = 'smoke-configcenter'
    'spring-support-report-client-starter' = 'smoke-report-client'
    'spring-support-report-client-arthas-starter' = 'smoke-report-client-arthas'
    'spring-support-tencent-starter' = 'smoke-tencent'
    'spring-support-aliyun-starter' = 'smoke-aliyun'
    'spring-support-payment-starter' = 'smoke-payment'
    'spring-support-strategy-starter' = 'smoke-strategy'
    'spring-support-shell-starter' = 'smoke-shell'
    'spring-support-ssh-starter' = 'smoke-ssh'
    'spring-support-job-starter' = 'smoke-job'
    'spring-support-ai-starter' = 'smoke-ai'
    'spring-support-message-starter' = 'smoke-message'
    'spring-support-email-starter' = 'smoke-email'
    'spring-support-gateway-starter' = 'smoke-gateway'
    'spring-support-proxy-starter' = 'smoke-proxy'
    'spring-support-sync-data-starter' = 'smoke-sync-data'
}

if ($Modules.Count -eq 0) {
    $Modules = $profileMap.Keys
}

if ($Modules.Count -eq 1 -and $Modules[0].Contains(',')) {
    $Modules = $Modules[0].Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ }
}

$results = @()
foreach ($module in $Modules) {
    if (-not $profileMap.Contains($module)) {
        $results += [pscustomobject]@{
            Module = $module
            Profile = ''
            Status = 'SKIPPED'
            Detail = 'No smoke profile configured'
        }
        continue
    }

    $profile = $profileMap[$module]
    Write-Host "==> Smoke testing $module via profile $profile"

    Push-Location $repoRoot
    try {
        & mvn '-DskipTests=false' '-Dmaven.test.skip=false' '-Dsurefire.failIfNoSpecifiedTests=false' `
            '-pl' 'spring-support-module-smoke-test' '-am' '-P' $profile 'test'

        $results += [pscustomobject]@{
            Module = $module
            Profile = $profile
            Status = 'PASS'
            Detail = 'Context smoke test passed'
        }
    } catch {
        $results += [pscustomobject]@{
            Module = $module
            Profile = $profile
            Status = 'FAIL'
            Detail = $_.Exception.Message
        }

        if (-not $ContinueOnError) {
            Pop-Location
            break
        }
    } finally {
        Pop-Location
    }
}

if ($null -ne $originalMavenOpts) {
    $env:MAVEN_OPTS = $originalMavenOpts
} else {
    Remove-Item Env:\MAVEN_OPTS -ErrorAction SilentlyContinue
}

$jsonPath = Join-Path $resultsDir 'spring-support-module-smoke-results.json'
$results | ConvertTo-Json -Depth 6 | Set-Content $jsonPath

$mdPath = Join-Path $resultsDir 'spring-support-module-smoke-results.md'
$lines = @(
    '# Spring Support Module Smoke Results',
    '',
    '| Module | Profile | Status | Detail |',
    '| --- | --- | --- | --- |'
)
foreach ($item in $results) {
    $detail = ($item.Detail -replace '\|', '/')
    $lines += "| $($item.Module) | $($item.Profile) | $($item.Status) | $detail |"
}
$lines | Set-Content $mdPath

$results
