# release.ps1 - incremental release for ai-starter (Windows dev machine).
#
# ASCII-only on purpose: PowerShell 5.1 parses a non-ASCII .ps1 by the system ANSI
# code page and can fail before param() is reached. See repo AGENTS.md.
#
# Why this exists: the fat jar is ~920MB but the business code inside it is ~3MB.
# Uploading 920MB for a one-line fix is the problem this script removes. The remote
# layout separates the two:
#
#   /data/ai-starter/app.jar      business jar   ~3MB   replaced on every release
#   /data/ai-starter/lib/*.jar    dependencies  ~920MB  uploaded once, then delta only
#   /data/ai-starter/start.sh     lifecycle (start/stop/restart/status/health)
#
# The app is launched with `-cp app.jar:lib/*`, NOT `java -jar`. Plain `-jar` cannot
# see an external lib dir; PropertiesLauncher + -Dloader.path was tried and failed
# with NoClassDefFoundError, so the explicit classpath is what works.
#
# Usage (from the module dir or anywhere):
#   pwsh -File deploy/release.ps1                  # build + upload app.jar + restart
#   pwsh -File deploy/release.ps1 -SyncDeps        # also sync changed dependencies
#   pwsh -File deploy/release.ps1 -SkipBuild       # upload current target/ artifact
#   pwsh -File deploy/release.ps1 -SkipRestart     # upload only
#   pwsh -File deploy/release.ps1 -DryRun          # print the plan, touch nothing
#
# First-time setup on a bare host is a full sync: run once with -SyncDeps (or copy
# lib/ manually). After that, -SyncDeps usually uploads 0 files.
[CmdletBinding()]
param(
    [string]$AppDir    = "/data/ai-starter",
    [string]$SshUser   = "ibssh_yosvrpgeid4br4wgybgqy7dfjq",
    [string]$SshHost   = "ssh.instabox.sh",
    [int]   $SshPort   = 2222,
    [string]$SshKey    = "",
    [string]$Maven     = "mvn",
    [string]$MavenRepo = "D:\maven-repo",
    [int]   $HttpPort  = 8787,
    [switch]$SyncDeps,
    [switch]$SkipBuild,
    [switch]$SkipRestart,
    [switch]$DryRun,
    # Health probe. This app exposes no actuator, so the login endpoint is used instead.
    # Passing credentials turns the probe into a real end-to-end check (static account +
    # login path + token issuance). Prefer the environment variables: a -HealthPass on
    # the command line is visible to anything that can read your shell history.
    [string]$HealthPath = "/v2/auth/login",
    [string]$HealthUser = $env:AI_HEALTH_USER,
    [string]$HealthPass = $env:AI_HEALTH_PASS
)

$ErrorActionPreference = "Stop"

$moduleDir = Split-Path -Parent $PSScriptRoot
$targetDir = Join-Path $moduleDir "target"
$manifest  = Join-Path $targetDir "lib-manifest.txt"

function Write-Step([string]$msg) { Write-Host "[release] $msg" -ForegroundColor Cyan }
function Write-Warn([string]$msg) { Write-Host "[release] $msg" -ForegroundColor Yellow }
function Fail([string]$msg) { throw "[release] $msg" }

$sshArgs = @("-p", "$SshPort", "-o", "StrictHostKeyChecking=accept-new")
if ($SshKey) { $sshArgs += @("-i", $SshKey) }
$scpArgs = @("-P", "$SshPort", "-o", "StrictHostKeyChecking=accept-new")
if ($SshKey) { $scpArgs += @("-i", $SshKey) }
$remote = "$SshUser@$SshHost"

function Invoke-Remote([string]$command) {
    # Single argv element on purpose: ssh joins its args and hands them to the remote
    # shell, so the command is parsed remotely, not mangled by PowerShell quoting.
    $out = & ssh @sshArgs $remote $command 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host $out
        Fail "remote command failed (exit=$LASTEXITCODE): $command"
    }
    return $out
}

function Copy-ToRemote([string]$local, [string]$remotePath) {
    & scp @scpArgs $local "${remote}:$remotePath" 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail "scp failed: $local -> $remotePath" }
}

# ---------------------------------------------------------------- 1. build
# Run inside the module itself and without -pl: -pl <module> only resolves from the
# aggregator (parent) directory, so combining it with Push-Location $moduleDir fails
# with "MavenExecutionException: Could not find the selected project in the reactor".
if (-not $SkipBuild) {
    Write-Step "building business jar (mvn package -DskipTests)"
    Push-Location $moduleDir
    try {
        & $Maven -o "-Dmaven.repo.local=$MavenRepo" -DskipTests package |
            Select-String -Pattern "BUILD|ERROR" | Select-Object -Last 5 | ForEach-Object { Write-Host "    $_" }
        if ($LASTEXITCODE -ne 0) { Fail "maven build failed (exit=$LASTEXITCODE)" }
    } finally { Pop-Location }
} else {
    Write-Step "skipping build (-SkipBuild)"
}

# ------------------------------------------------- 2. locate the business jar
$candidates = Get-ChildItem $targetDir -Filter "spring-support-ai-starter-*.jar" -File |
    Where-Object { $_.Name -notlike "*-exec.jar" -and $_.Name -notlike "*-sources.jar" } |
    Sort-Object LastWriteTime -Descending
if (-not $candidates) { Fail "no business jar in $targetDir; run without -SkipBuild" }
$jar = $candidates[0]

# The exec fat jar is ~920MB; the plain one ~3MB. Shipping the wrong one silently
# defeats the whole point of the split, so refuse loudly.
if ($jar.Length -gt 50MB) {
    Fail ("$($jar.Name) is $([math]::Round($jar.Length/1MB,1))MB - that looks like the exec fat jar. " +
          "Use the plain jar produced before spring-boot:repackage.")
}
$localSha  = (Get-FileHash $jar.FullName -Algorithm SHA256).Hash.ToLower()
$localSize = $jar.Length
Write-Step ("business jar: {0}  {1:N0} KB  sha256={2}..." -f $jar.Name, ($localSize / 1KB), $localSha.Substring(0, 16))

# --------------------------------------------------------- 3. remote dirs
Write-Step "target: ${remote}:$AppDir"
if (-not $DryRun) {
    Invoke-Remote "mkdir -p '$AppDir/lib' '$AppDir/logs' '$AppDir/config'" | Out-Null
}

# ------------------------------------------------------ 3b. sync start.sh
# Small and versioned with the jar, so it is kept in step automatically. Doing this
# here means a bare host only needs `lib/` to exist -- there is no manual first step
# that can be forgotten and then surface later as "start.sh: No such file".
$localStart = Join-Path $PSScriptRoot "start.sh"
if (Test-Path $localStart) {
    $localStartSha = (Get-FileHash $localStart -Algorithm SHA256).Hash.ToLower()
    $remoteStartSha = ""
    try {
        $remoteStartSha = "$(Invoke-Remote "sha256sum '$AppDir/start.sh' 2>/dev/null | cut -d' ' -f1" | Select-Object -First 1)".Trim()
    } catch { $remoteStartSha = "" }
    if ($remoteStartSha -eq $localStartSha) {
        Write-Step "start.sh up to date"
    } elseif ($DryRun) {
        Write-Step "(dry-run) would upload start.sh"
    } else {
        Write-Step "uploading start.sh"
        Copy-ToRemote $localStart "/tmp/ai-start.sh"
        Invoke-Remote "install -m 0755 /tmp/ai-start.sh '$AppDir/start.sh' && rm -f /tmp/ai-start.sh" | Out-Null
    }
}

# --------------------------------------------------- 4. optional dep sync
# The authoritative dependency list is the fat jar's BOOT-INF/lib: that is exactly
# what Spring Boot would put on the classpath. `mvn dependency:list` is NOT used --
# it yields 442 entries vs 433 in the fat jar (aggregator starters in, provided-scope
# jars out), and uploading the difference would leave two versions of one artifact
# side by side in lib/.
if ($SyncDeps) {
    $execJar = Get-ChildItem $targetDir -Filter "spring-support-ai-starter-*-exec.jar" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $execJar) {
        Fail ("dep sync needs the exec fat jar (target/*-exec.jar). Build it with:`n" +
              "  mvn -o -DskipTests -Dspring-boot.repackage.skip=false -pl spring-support-ai-starter package")
    }
    # repackage is skipped by default in this pom, so a stale exec jar is easy to end up
    # with. Comparing against the plain jar catches "forgot to rebuild before -SyncDeps",
    # which would otherwise sync the *previous* dependency set and look successful.
    if ($execJar.LastWriteTime -lt $jar.LastWriteTime) {
        Fail ("$($execJar.Name) is older than $($jar.Name) - it does not reflect the current build.`n" +
              "  Rebuild with: mvn -o -DskipTests -Dspring-boot.repackage.skip=false -pl spring-support-ai-starter package")
    }

    Write-Step "deriving dependency manifest from $($execJar.Name)"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($execJar.FullName)
    try {
        # name SPACE size. Space, not tab: the diff runs over `ssh host '<cmd>'`, and the
        # remote login shell is not guaranteed to be bash -- `IFS=$'\t'` is a bashism that
        # degrades to the literal string under dash/sh, so no field is ever split, `$s`
        # stays empty and every line is reported as changed. Jar names never contain
        # spaces, so one space is unambiguous and works in any shell.
        #
        # `$_.Length` IS the uncompressed size (the jar's real byte length, which is what
        # `stat -c %s` reports remotely). `$_.Size` does NOT exist on ZipArchiveEntry and
        # silently formats as empty -- that shipped a manifest with a blank size field and
        # every jar compared unequal. `CompressedLength` happens to be identical here
        # because Spring Boot stores nested jars without re-deflating.
        #
        # Size (not just the name) is required: every module in this repo is versioned
        # 4.0.0.42, so editing a utils module and rebuilding yields the SAME file name
        # with different content. A name-only test reports "unchanged" and silently ships
        # a jar whose dependency was never updated.
        $lines = $zip.Entries |
            Where-Object { $_.FullName -like "BOOT-INF/lib/*" -and $_.Length -gt 0 } |
            ForEach-Object { "{0} {1}" -f ($_.FullName -replace "^BOOT-INF/lib/", ""), $_.Length } |
            Sort-Object
        # LF only, no CR: a CRLF manifest leaves a trailing CR on every field, so nothing
        # compares equal and each -SyncDeps re-ships all ~900MB instead of nothing.
        [IO.File]::WriteAllText($manifest, (($lines -join "`n") + "`n"), [System.Text.Encoding]::ASCII)
        Write-Step "manifest: $($lines.Count) jars"
    } finally { $zip.Dispose() }

    if (-not $DryRun) {
        Copy-ToRemote $manifest "/tmp/lib-manifest.txt"
        Write-Step "diffing remote lib/ ..."
        # Name + size. Size is what catches a rebuilt jar whose file name did not change
        # (every module here is fixed at version 4.0.0.42). Plain `read -r n s` with the
        # default IFS and no bashisms, so it works under sh/dash as well as bash.
        $diffCmd = "cd '$AppDir/lib' && while read -r n s; do " +
                   "n=`${n%`$'\r'}; s=`${s%`$'\r'}; [ -z `"`$n`" ] && continue; " +
                   "if [ ! -f `"`$n`" ] || [ `"`$(stat -c %s `"`$n`" 2>/dev/null)`" != `"`$s`" ]; then echo `"`$n`"; fi; " +
                   "done < /tmp/lib-manifest.txt"
        $diffRaw = @(Invoke-Remote $diffCmd | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne "" })
        # Only `.jar` lines count as "missing". Anything else is the remote command
        # talking (stderr text, shell diagnostics). Treating that as a file name once made
        # the script stage nothing, report a "0.0 MB" delta, print "dependencies synced"
        # and ship a jar whose dependency had never been updated -- a silent mismatch.
        $missing = @($diffRaw | Where-Object { $_ -like "*.jar" })
        $noise = @($diffRaw | Where-Object { $_ -notlike "*.jar" })
        if ($noise.Count -gt 0) {
            Write-Warn "remote diff produced $($noise.Count) non-jar line(s); first 3:"
            $noise | Select-Object -First 3 | ForEach-Object { Write-Warn "    $_" }
            Fail ("dependency diff is not returning file names - refusing to continue, " +
                  "because a broken diff is indistinguishable from 'nothing to do'.")
        }
        Write-Step "missing/changed dependencies: $($missing.Count)"

        if ($missing.Count -gt 0) {
            $stage = Join-Path $targetDir ".lib-stage"
            Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
            New-Item -ItemType Directory -Path $stage -Force | Out-Null

            $zip = [System.IO.Compression.ZipFile]::OpenRead($execJar.FullName)
            try {
                foreach ($name in $missing) {
                    $entry = $zip.GetEntry("BOOT-INF/lib/$name")
                    if (-not $entry) { Write-Warn "not in fat jar: $name"; continue }
                    $dest = Join-Path $stage $name
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
                }
            } finally { $zip.Dispose() }

            $delta = Join-Path $targetDir "lib-delta.tar.gz"
            Remove-Item $delta -Force -ErrorAction SilentlyContinue
            & tar -czf $delta -C $stage .
            if ($LASTEXITCODE -ne 0) { Fail "tar failed (exit=$LASTEXITCODE)" }
            Write-Step ("delta package {0:N1} MB" -f ((Get-Item $delta).Length / 1MB))

            Copy-ToRemote $delta "/tmp/lib-delta.tar.gz"
            Invoke-Remote "tar -xzf /tmp/lib-delta.tar.gz -C '$AppDir/lib' && rm -f /tmp/lib-delta.tar.gz" | Out-Null
            Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
            Write-Step "dependencies synced; remote lib now $(Invoke-Remote "ls '$AppDir/lib'/*.jar | wc -l") jars"
        } else {
            Write-Step "dependencies unchanged, nothing to upload"
        }
    }
} else {
    Write-Step "skipping dependency sync (pass -SyncDeps to enable)"
}

# --------------------------------------------------- 5. upload business jar
$remoteSha = ""
try { $remoteSha = "$(Invoke-Remote "sha256sum '$AppDir/app.jar' 2>/dev/null | cut -d' ' -f1" | Select-Object -First 1)".Trim() } catch { $remoteSha = "" }

if ($remoteSha -eq $localSha) {
    Write-Step "remote app.jar already up to date (sha256 match), upload skipped"
} elseif ($DryRun) {
    Write-Step "(dry-run) would upload $($jar.Name) -> $AppDir/app.jar"
} else {
    Write-Step ("uploading business jar ({0:N0} KB) ..." -f ($localSize / 1KB))
    Copy-ToRemote $jar.FullName "/tmp/app.jar.new"
    $swap = "set -e; cd '$AppDir'; " +
            "if [ -f app.jar ]; then cp -p app.jar `"app.jar.bak-`$(date +%Y%m%d%H%M%S)`"; fi; " +
            "mv /tmp/app.jar.new app.jar; " +
            "echo `"deployed sha256=`$(sha256sum app.jar | cut -d' ' -f1 | cut -c1-16)...`""
    Invoke-Remote $swap | ForEach-Object { Write-Host "    $_" }
    # keep only the 5 most recent backups
    Invoke-Remote "cd '$AppDir' && ls -1t app.jar.bak-* 2>/dev/null | tail -n +6 | xargs -r rm -f" | Out-Null
}

# --------------------------------------------------------------- 6. restart
if ($SkipRestart) {
    Write-Warn "restart skipped (-SkipRestart). Apply with: ssh -p $SshPort ${remote} 'APP_DIR=$AppDir bash $AppDir/start.sh restart'"
    return
}
if ($DryRun) { Write-Step "(dry-run) would restart and health-check"; return }

Write-Step "restarting service"
Invoke-Remote "APP_DIR='$AppDir' bash '$AppDir/start.sh' restart" | ForEach-Object { Write-Host "    $_" }

# This app has NO actuator (spring-boot-starter-actuator is not a dependency), so
# /actuator/health is served as a static-resource lookup and 404s:
#   "No static resource actuator/health for request '/actuator/health'"
# The whitelist entry for /actuator/health in ai-starter.yml is only a whitelist
# pattern, not proof the endpoint exists -- probing it made every successful deploy
# report failure. Probe the login endpoint instead: reaching it proves Tomcat +
# Spring MVC + the auth filter chain are up, and with credentials it also proves the
# static account and the login path actually work (that was the original 403 bug).
#
# The request body goes through a FILE, not an inline string. Embedding
# {"username":"admin"} inside the remote command loses its double quotes on the way
# through PowerShell -> ssh.exe -> remote shell, and the server then rejects it with
# "JSON parse error: Unexpected character ('u' ...): was expecting double-quote".
Write-Step "health check (POST $HealthPath)"
$ok = $false
$body = ""
$probeFile = Join-Path $targetDir ".health-probe.json"
if ($HealthUser -and $HealthPass) {
    $probeJson = @{ username = $HealthUser; password = $HealthPass } | ConvertTo-Json -Compress
    Set-Content -Path $probeFile -Value $probeJson -Encoding ASCII -NoNewline
    if (-not $DryRun) { Copy-ToRemote $probeFile "/tmp/ai-health.json" }
}
for ($i = 1; $i -le 40; $i++) {
    try {
        if ($HealthUser -and $HealthPass) {
            $body = ((Invoke-Remote ("curl -s --max-time 5 -X POST 'http://127.0.0.1:$HttpPort$HealthPath' " +
                     "-H 'Content-Type: application/json' --data-binary '@/tmp/ai-health.json'")) -join ' ').Trim()
            if ($body -match '"ok"\s*:\s*true') { $ok = $true; break }
        } else {
            # No credentials: a POST without a body is rejected by the server (415 /
            # unsupported format) but still proves the route exists and is served, so
            # any of these codes counts as alive.
            $body = "$(Invoke-Remote ("curl -s -o /dev/null -w '%{http_code}' --max-time 5 -X POST " +
                     "'http://127.0.0.1:$HttpPort$HealthPath'") | Select-Object -First 1)".Trim()
            if ($body -in @("200", "400", "401", "403", "405", "415")) { $ok = $true; break }
        }
    } catch { }
    Start-Sleep -Seconds 3
}
Remove-Item $probeFile -Force -ErrorAction SilentlyContinue

if ($ok) {
    Write-Host "[release] DONE - $HealthPath reachable" -ForegroundColor Green
    if ($body) { Write-Host "    $body" }
} else {
    Write-Warn "health check failed within 120s; last response: $body"
    Write-Warn "log tail:"
    try { Invoke-Remote "tail -60 '$AppDir/logs/app.log'" | ForEach-Object { Write-Host "    $_" } } catch { }
    throw "[release] release failed: service unhealthy"
}
