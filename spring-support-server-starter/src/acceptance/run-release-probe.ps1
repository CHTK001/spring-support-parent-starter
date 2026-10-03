# run-release-probe.ps1 -- run the release-script regression probe
#
# Why this script now delegates to Maven instead of compiling with javac itself:
#   ReleaseScriptProbe now lives in the regular surefire lifecycle as 7 @Test methods
#   (42 assertions in total). It was previously a main()-only probe that had to be
#   hand-compiled, because surefire's default include patterns are name-based
#   (**/Test*.java, **/*Test.java, **/*Tests.java, **/*TestCase.java) and this repo
#   forbids *Test class names outside example modules -- so a @Test-annotated *Probe
#   class was never discovered, and hand-rolled javac was the only way to run it.
#     - spring-support-server-starter/pom.xml now declares junit-jupiter (test scope)
#       and adds **/*Probe.java to surefire includes, so `mvn test` runs it directly.
#     - Hand-rolling javac meant re-resolving the classpath by hand, which silently
#       omitted JUnit and broke as soon as the probe gained @Test annotations.
#   The original justification for hand-compiling ("bad tests elsewhere in the module
#   block compilation") no longer holds: as of 2026-10-03 both spring-support-server-starter
#   and spring-support-monitor-starter pass `clean test-compile`.
#
# Usage:
#   pwsh -ExecutionPolicy Bypass -File src\acceptance\run-release-probe.ps1
#   powershell -ExecutionPolicy Bypass -File src\acceptance\run-release-probe.ps1
# Exit codes: 0 = all assertions passed; non-zero = failures or environment not satisfied
#
# Note: the script body stays pure ASCII (repo convention); probe output Chinese is unaffected.

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$module = Resolve-Path (Join-Path $here '..\..')
$repo = Resolve-Path (Join-Path $module '..')
$pom = Join-Path $repo 'pom.xml'

Write-Output "module  = $module"
Write-Output "pom     = $pom"

if (-not (Test-Path $pom)) {
    Write-Output "ERROR: parent pom not found: $pom"
    exit 2
}

# Locate the Maven launcher: MAVEN_HOME first, then mvn on PATH.
if ($env:MAVEN_HOME -and (Test-Path (Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'))) {
    $mvn = Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'
} else {
    $mvn = 'mvn'
}

Write-Output "--- run the probe through the Maven test lifecycle ---"
Write-Output "  $mvn -o -pl spring-support-server-starter -DskipTests=false test"

Push-Location $repo
try {
    & $mvn -o -pl spring-support-server-starter -DskipTests=false test
    $rc = $LASTEXITCODE
} finally {
    Pop-Location
}

Write-Output "probe exit=$rc"
exit $rc