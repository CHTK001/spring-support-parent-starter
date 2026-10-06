# Build the ai-starter fat jar WITHOUT the stale-embedded-dependency trap.
#
# WHY THIS EXISTS (2026-10-02, measured):
#   The fat jar is produced by repackaging spring-support-ai-starter only. Any
#   utils module it depends on is taken from the LOCAL MAVEN REPO as-is, so a
#   module that was changed but never re-installed stays stale inside the jar.
#   Concretely: BOOT-INF/lib/utils-support-deeplearning-agentscope-starter-4.0.0.42.jar
#   was the 09-30 13:58 build while that module had two later commits -- both
#   missing from every jar built since. "Deployed code != delivered code".
#
#   Fix: install every module you touched before packaging, then PROVE the jar
#   changed only where you expect by diffing its entry map against a reference.
#
# WHY ASCII ONLY:
#   Repo rule (AGENTS.md): PowerShell scripts must be pure ASCII -- PowerShell
#   5.1 reads a BOM-less .ps1 as ANSI and mangles CJK, and CJK before param()
#   is a hard ParserError. Run this with pwsh 7 -File.
#
# USAGE
#   pwsh -File build-fat-jar.ps1 `
#     -ModuleDirs 'D:\ch\project\utils-support-parent-starter\...\utils-support-deeplearning-agentscope-starter' `
#     -RefJar 'D:\ch\project\.tmp\sg\reference-app.jar' `
#     -AllowEntries 'BOOT-INF/lib/utils-support-deeplearning-agentscope-starter-4.0.0.42.jar'
#
#   -ModuleDirs defaults to installing nothing (only the app module is
#   repackaged, using whatever is already in the local repo). It is optional
#   because a mandatory [string[]] rejects @() with "empty array", which left
#   the default build with no legal invocation.
#
#   -RefJar is optional. When given, the script fails if any entry outside
#   -AllowEntries differs from the reference -- that is the guard that keeps a
#   "rebuild" from silently dragging in unrelated changes (config, DDL, other
#   modules).
param(
  [string[]]$ModuleDirs = @(),
  [string]$Repo = 'D:\maven-repo',
  [string]$Mvn = 'D:\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd',
  [string]$JavaHome = 'C:\Program Files\Amazon Corretto\jdk25.0.3_9',
  [string]$AppModule = 'D:\ch\project\spring-support-parent-starter\spring-support-ai-starter',
  [string]$RefJar = '',
  [string[]]$AllowEntries = @()
)

$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$env:PATH"

# -Dmaven.test.skip=true, NOT -DskipTests: a module POM may hardcode
# <skipTests>false</skipTests> in the surefire configuration, and POM config
# beats the command-line property -- so -DskipTests can be silently ignored and
# the (possibly hanging) test suite will run during install.
$skipFlag = '-Dmaven.test.skip=true'

foreach ($dir in $ModuleDirs) {
  if (-not (Test-Path (Join-Path $dir 'pom.xml'))) { throw "not a maven module: $dir" }
  Write-Host ("=== install " + $dir)
  # Quote every -D argument: PowerShell can split an unquoted
  # -Dspring-boot.repackage.skip=false into two tokens, and maven then reports
  # 'Unknown lifecycle phase ".repackage.skip=false"'.
  & $Mvn -f (Join-Path $dir 'pom.xml') -o "-Dmaven.repo.local=$Repo" "$skipFlag" clean install
  if ($LASTEXITCODE -ne 0) { throw "install failed: $dir" }
}

Write-Host "=== repackage $AppModule"
& $Mvn -f (Join-Path $AppModule 'pom.xml') -o "-Dmaven.repo.local=$Repo" "$skipFlag" '-Dspring-boot.repackage.skip=false' clean package
if ($LASTEXITCODE -ne 0) { throw 'repackage failed' }

$jar = Get-ChildItem (Join-Path $AppModule 'target') -Filter '*-exec.jar' |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw 'no *-exec.jar produced' }
$md5 = (Get-FileHash $jar.FullName -Algorithm MD5).Hash.ToLower()
Write-Host ("  jar   = " + $jar.FullName)
Write-Host ("  bytes = " + $jar.Length)
Write-Host ("  md5   = " + $md5)

function Get-EntryMap([string]$path) {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
  $map = @{}
  foreach ($e in $zip.Entries) { $map[$e.FullName] = $e.Length }
  $zip.Dispose()
  return $map
}

if ($RefJar) {
  if (-not (Test-Path $RefJar)) { throw "reference jar not found: $RefJar" }
  Write-Host ("=== entry diff vs " + $RefJar)
  $new = Get-EntryMap $jar.FullName
  $ref = Get-EntryMap $RefJar
  $allow = @{}
  foreach ($a in $AllowEntries) { $allow[$a] = $true }

  $unexpected = @()
  foreach ($k in $new.Keys) {
    if ($allow.ContainsKey($k)) { continue }
    if (-not $ref.ContainsKey($k)) { $unexpected += "added: $k"; continue }
    if ($ref[$k] -ne $new[$k]) { $unexpected += ("size: $k  ref=" + $ref[$k] + " new=" + $new[$k]) }
  }
  foreach ($k in $ref.Keys) {
    if ($allow.ContainsKey($k)) { continue }
    if (-not $new.ContainsKey($k)) { $unexpected += "removed: $k" }
  }

  if ($unexpected.Count -gt 0) {
    Write-Host "  UNEXPECTED CHANGES -- do NOT deploy this jar:"
    $unexpected | ForEach-Object { Write-Host ("    " + $_) }
    Write-Host "BUILD_DIFF_FAILED"
    exit 1
  }
  Write-Host "  BUILD_DIFF_OK (only the allowlisted entries differ)"
}
Write-Host "BUILD_OK"
