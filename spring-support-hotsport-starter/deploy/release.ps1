# release.ps1 - split release for hotsport (Windows dev machine).
#
# ASCII-only on purpose: PowerShell 5.1 parses a non-ASCII .ps1 by the system ANSI
# code page and can fail before param() is reached. See repo AGENTS.md.
#
# Why this exists: the fat jar is ~108MB but the business code inside it is ~3MB.
# Uploading 108MB for a one-line fix is the problem this script removes. The remote
# layout separates the two:
#
#   /data/hotsport/app.jar        business jar   ~3MB    replaced on every release
#   /data/hotsport/lib/*.jar      dependencies  ~107MB   uploaded once, then delta only
#   /data/hotsport/start.sh       lifecycle (start/stop/restart/status/health)
#
# The app is launched with `-cp app.jar:lib/*` + main class, NOT `java -jar`.
# Plain `-jar` cannot see an external lib dir; PropertiesLauncher + -Dloader.path
# was tried in this repo and failed with NoClassDefFoundError.
#
# Usage (from the module dir):
#   pwsh -File deploy/release.ps1                  # build + upload app.jar + restart
#   pwsh -File deploy/release.ps1 -SyncDeps        # also sync changed dependencies
#   pwsh -File deploy/release.ps1 -LibBase <url>   # let the host pull missing jars
#                                                  # straight from that URL instead
#   pwsh -File deploy/release.ps1 -SkipBuild       # upload current target/ artifact
#   pwsh -File deploy/release.ps1 -SkipRestart     # upload only
#   pwsh -File deploy/release.ps1 -DryRun          # print the plan, touch nothing
#
# The dependency manifest comes from the fat jar's BOOT-INF/lib, not from
# `mvn dependency:list`: that is exactly what Spring Boot puts on the classpath.
[CmdletBinding()]
param(
    [string]$AppDir    = "/data/hotsport",
    [string]$SshUser   = "ibssh_giyhcylyqifgpdbtaq4m3znbne",
    [string]$SshHost   = "ssh.instabox.sh",
    [int]   $SshPort   = 2222,
    [string]$SshKey    = "",
    [string]$Maven     = "mvn",
    [string]$MavenRepo = "D:\maven-repo",
    [int]   $HttpPort  = 18086,
    # HTTP base that serves the lib jars (one entry per file). When set together with
    # -SyncDeps, missing jars are pulled by the host itself instead of being uploaded
    # from this machine -- the host usually has far better bandwidth.
    [string]$LibBase   = "",
    [string]$HealthPath = "/api/apm/stats",
    [switch]$SyncDeps,
    [switch]$SkipBuild,
    [switch]$SkipRestart,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$moduleDir = Split-Path -Parent $PSScriptRoot
$targetDir = Join-Path $moduleDir "target"
$classesDir = Join-Path $targetDir "classes"
$manifest  = Join-Path $targetDir "lib-manifest.txt"
$appJar    = Join-Path $targetDir "app-thin.jar"
$mainClass = "com.chua.starter.hotsport.support.HotsportApplication"

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
    # shell, so the command is parsed remotely instead of being mangled by PowerShell.
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
if (-not $SkipBuild) {
    Write-Step "building (mvn package -DskipTests)"
    Push-Location $moduleDir
    try {
        & $Maven -o "-Dmaven.repo.local=$MavenRepo" -DskipTests package |
            Select-String -Pattern "BUILD|ERROR" | Select-Object -Last 5 | ForEach-Object { Write-Host "    $_" }
        if ($LASTEXITCODE -ne 0) { Fail "maven build failed (exit=$LASTEXITCODE)" }
    } finally { Pop-Location }
} else {
    Write-Step "skipping build (-SkipBuild)"
}

# ------------------------------------------------- 2. thin app.jar from target/classes
# The build leaves a repackaged fat jar as the main artifact; the business code is
# still in target/classes. Zip that into app.jar with explicit forward-slash entry
# names (ZipFile.CreateFromDirectory is not relied upon: its separator differs
# between .NET Framework and .NET Core, and a jar with backslashes does not run).
if (-not (Test-Path $classesDir)) { Fail "missing $classesDir; run without -SkipBuild" }
if (Test-Path $appJar) { Remove-Item $appJar -Force }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$files = @(Get-ChildItem $classesDir -Recurse -File)
# Directory entries are NOT optional. Spring's component scan resolves the package
# root with ClassLoader.getResources("com/chua/.../support/"); for a jar that lookup
# only succeeds when the archive contains a matching directory entry. A jar built
# from file entries alone starts fine, serves the agent, and then answers every
# /api/apm/* with "No static resource" because no controller was ever scanned.
$dirs = New-Object System.Collections.Generic.SortedSet[string]
foreach ($f in $files) {
    $rel = $f.FullName.Substring($classesDir.Length + 1).Replace("\", "/")
    $dir = $rel
    while (($idx = $dir.LastIndexOf("/")) -gt 0) {
        $dir = $dir.Substring(0, $idx)
        if (-not $dirs.Add($dir)) { break }
    }
}
$zipStream = [System.IO.File]::Open($appJar, [System.IO.FileMode]::CreateNew)
try {
    $archive = New-Object System.IO.Compression.ZipArchive($zipStream, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($d in $dirs) {
            $entry = $archive.CreateEntry("$d/", [System.IO.Compression.CompressionLevel]::NoCompression)
            $entry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
        }
        $count = 0
        foreach ($f in $files) {
            $rel = $f.FullName.Substring($classesDir.Length + 1).Replace("\", "/")
            $entry = $archive.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
            $entry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
            $out = $entry.Open()
            try {
                $in = [System.IO.File]::OpenRead($f.FullName)
                try { $in.CopyTo($out) } finally { $in.Dispose() }
            } finally { $out.Dispose() }
            $count++
        }
    } finally { $archive.Dispose() }
} finally { $zipStream.Dispose() }

$localSha = (Get-FileHash $appJar -Algorithm SHA256).Hash.ToLower()
$localSize = (Get-Item $appJar).Length
Write-Step ("business jar: app.jar  {0:N0} KB  ({1} entries)  sha256={2}..." -f ($localSize / 1KB), $count, $localSha.Substring(0, 16))

# --------------------------------------------------------- 3. remote dirs
Write-Step "target: ${remote}:$AppDir"
if (-not $DryRun) {
    Invoke-Remote "mkdir -p '$AppDir/lib' '$AppDir/logs' '$AppDir/config'" | Out-Null
}

# ------------------------------------------------------ 3b. sync start.sh
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
        Copy-ToRemote $localStart "/tmp/hotsport-start.sh"
        Invoke-Remote "install -m 0755 /tmp/hotsport-start.sh '$AppDir/start.sh' && rm -f /tmp/hotsport-start.sh" | Out-Null
    }
}

# --------------------------------------------------- 4. optional dep sync
if ($SyncDeps) {
    $fatJar = Get-ChildItem $targetDir -Filter "spring-support-hotsport-starter-*.jar" -File |
        Where-Object { $_.Length -gt 50MB } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $fatJar) {
        Fail "dep sync needs the fat jar (target/spring-support-hotsport-starter-*.jar > 50MB)"
    }

    Write-Step "deriving dependency manifest from $($fatJar.Name)"
    $zip = [System.IO.Compression.ZipFile]::OpenRead($fatJar.FullName)
    try {
        $names = $zip.Entries |
            Where-Object { $_.FullName -like "BOOT-INF/lib/*.jar" -and $_.Length -gt 0 } |
            ForEach-Object { $_.FullName -replace "^BOOT-INF/lib/", "" } |
            Sort-Object
        # LF only, no CR: the remote loop reads this with `IFS= read -r n`, and a
        # trailing CR would make every jar look missing (re-uploading the whole lib).
        [IO.File]::WriteAllText($manifest, (($names -join "`n") + "`n"), [System.Text.Encoding]::ASCII)
        Write-Step "manifest: $($names.Count) jars"
    } finally { $zip.Dispose() }

    if (-not $DryRun) {
        Copy-ToRemote $manifest "/tmp/hotsport-lib-manifest.txt"
        Write-Step "diffing remote lib/ ..."
        # Presence-only identity test: a Maven file name already pins
        # groupId+artifactId+version.
        $diffCmd = "cd '$AppDir/lib' && while IFS= read -r n; do " +
                   "n=`${n%`$'\r'}; [ -z `"`$n`" ] && continue; " +
                   "if [ ! -f `"`$n`" ]; then echo `"`$n`"; fi; " +
                   "done < /tmp/hotsport-lib-manifest.txt"
        $missing = @(Invoke-Remote $diffCmd | Where-Object { "$_".Trim() -ne "" })
        Write-Step "missing dependencies: $($missing.Count)"

        if ($missing.Count -gt 0 -and $LibBase) {
            # Pull on the host: one curl per jar, checksum-free but far cheaper than
            # pushing 100MB+ over the dev machine's uplink.
            Write-Step "pulling $($missing.Count) jars from $LibBase on the host"
            $pull = "set -e; cd '$AppDir/lib'; " +
                    "while IFS= read -r n; do " +
                    "n=`${n%`$'\r'}; [ -z `"`$n`" ] && continue; " +
                    "[ -f `"`$n`" ] && continue; " +
                    "echo `"  pull `$n`"; " +
                    "curl -fsSL --retry 3 --max-time 300 -o `"`$n.part`" '$LibBase/'`"`$n`"; " +
                    "mv `"`$n.part`" `"`$n`"; " +
                    "done < /tmp/hotsport-lib-manifest.txt"
            Invoke-Remote $pull | ForEach-Object { Write-Host "    $_" }
        } elseif ($missing.Count -gt 0) {
            $stage = Join-Path $targetDir ".lib-stage"
            Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
            New-Item -ItemType Directory -Path $stage -Force | Out-Null
            $zip = [System.IO.Compression.ZipFile]::OpenRead($fatJar.FullName)
            try {
                foreach ($name in $missing) {
                    $entry = $zip.GetEntry("BOOT-INF/lib/$name")
                    if (-not $entry) { Write-Warn "not in fat jar: $name"; continue }
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $stage $name), $true)
                }
            } finally { $zip.Dispose() }

            $delta = Join-Path $targetDir "lib-delta.tar"
            Remove-Item $delta -Force -ErrorAction SilentlyContinue
            & tar -cf $delta -C $stage .
            if ($LASTEXITCODE -ne 0) { Fail "tar failed (exit=$LASTEXITCODE)" }
            Write-Step ("delta package {0:N1} MB" -f ((Get-Item $delta).Length / 1MB))
            Copy-ToRemote $delta "/tmp/hotsport-lib-delta.tar"
            Invoke-Remote "tar -xf /tmp/hotsport-lib-delta.tar -C '$AppDir/lib' && rm -f /tmp/hotsport-lib-delta.tar" | Out-Null
            Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
        } else {
            Write-Step "dependencies unchanged, nothing to upload"
        }
        if ($missing.Count -gt 0) {
            Write-Step "remote lib now $(Invoke-Remote "ls '$AppDir/lib'/*.jar | wc -l") jars"
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
    Write-Step "(dry-run) would upload app.jar"
} else {
    Write-Step ("uploading business jar ({0:N0} KB) ..." -f ($localSize / 1KB))
    Copy-ToRemote $appJar "/tmp/app.jar.new"
    $swap = "set -e; cd '$AppDir'; " +
            "if [ -f app.jar ]; then cp -p app.jar `"app.jar.bak-`$(date +%Y%m%d%H%M%S)`"; fi; " +
            "mv /tmp/app.jar.new app.jar; " +
            "echo `"deployed sha256=`$(sha256sum app.jar | cut -d' ' -f1 | cut -c1-16)...`""
    Invoke-Remote $swap | ForEach-Object { Write-Host "    $_" }
    Invoke-Remote "cd '$AppDir' && ls -1t app.jar.bak-* 2>/dev/null | tail -n +6 | xargs -r rm -f" | Out-Null
}

# --------------------------------------------------------------- 6. restart
if ($SkipRestart) {
    Write-Warn "restart skipped (-SkipRestart). Apply with: ssh -p $SshPort ${remote} 'APP_DIR=$AppDir bash $AppDir/start.sh restart'"
    return
}
if ($DryRun) { Write-Step "(dry-run) would restart and health-check"; return }

Write-Step "restarting service"
Invoke-Remote "APP_DIR='$AppDir' PORT='$HttpPort' bash '$AppDir/start.sh' restart" | ForEach-Object { Write-Host "    $_" }

# No actuator in this app: /actuator/health would 404. /api/apm/stats is served by
# the APM controller and needs no external database (H2 fallback), so a 200 with
# "ok": true proves Tomcat + Spring MVC + the APM service layer are all up.
Write-Step "health check (GET $HealthPath)"
$ok = $false
$body = ""
for ($i = 1; $i -le 30; $i++) {
    try {
        $body = (@(Invoke-Remote "curl -s --max-time 5 -w '|%{http_code}' 'http://127.0.0.1:$HttpPort$HealthPath'") -join ' ').Trim()
        if ($body -match '\|200$' -and $body -match '"ok"\s*:\s*true') { $ok = $true; break }
    } catch { }
    Start-Sleep -Seconds 3
}

if ($ok) {
    Write-Host "[release] DONE - $HealthPath reachable" -ForegroundColor Green
} else {
    Write-Warn "health check failed within 90s; last response: $body"
    try { Invoke-Remote "tail -60 '$AppDir/logs/app.log'" | ForEach-Object { Write-Host "    $_" } } catch { }
    throw "[release] release failed: service unhealthy"
}
