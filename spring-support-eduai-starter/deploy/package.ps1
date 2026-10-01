# package.ps1 - build + package the eduai deploy bundle (Windows dev machine).
#
# ASCII-only on purpose: PowerShell 5.1 parses a non-ASCII .ps1 by the system
# ANSI code page and can fail before param() is reached. See repo AGENTS.md.
#
# Output: dist/eduai-app.tar.gz containing
#   spring-support-eduai-starter-<ver>.jar  (fat jar)
#   deploy/
#
# Usage: pwsh -File deploy/package.ps1
[CmdletBinding()]
param(
    [string]$Maven = "mvn"
)

$ErrorActionPreference = "Stop"

$moduleDir = Split-Path -Parent $PSScriptRoot
$jar = "spring-support-eduai-starter-4.0.0.42.jar"
$target = Join-Path $moduleDir "target"
$dist = Join-Path $moduleDir "dist"
$out = Join-Path $dist "eduai-app.tar.gz"

Write-Host "==> build $jar (fat jar; takes a while)"
& $Maven -f (Join-Path $moduleDir "pom.xml") clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "maven build failed (exit=$LASTEXITCODE)" }

if (-not (Test-Path (Join-Path $target $jar))) { throw "build produced no $jar" }

Write-Host "==> package"
New-Item -ItemType Directory -Path $dist -Force | Out-Null
Remove-Item $out -Force -ErrorAction SilentlyContinue

# Tar straight from the source dirs (no Copy-Item): copying huge artifacts with
# Copy-Item has been observed to produce all-NUL files on this host.
& tar -czf $out -C $target $jar -C $moduleDir deploy
if ($LASTEXITCODE -ne 0) { throw "tar failed (exit=$LASTEXITCODE)" }

$item = Get-Item $out
$sha = (Get-FileHash $out -Algorithm SHA256).Hash.ToLower()
Write-Host ("==> done: {0}" -f $out)
Write-Host ("    size={0:N1} MB  sha256={1}" -f ($item.Length / 1MB), $sha)
