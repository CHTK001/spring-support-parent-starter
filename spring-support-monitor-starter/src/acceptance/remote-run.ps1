#!/usr/bin/env pwsh
# Remote script runner with MANDATORY transfer verification.
#
# Why this exists (AGENTS.md 4.8): pscp/plink transfer failures usually only write to
# stderr. If you then read output with something like `Select-Object -Last 1`, a failed
# transfer looks like "no output", and you end up EXECUTING THE STALE COPY ALREADY SITTING
# in the remote directory -- which presents as "installed an old version but believed the
# new one was installed". So: verify md5 AND byte count on the remote side, and refuse to
# execute unless both match. No match -> abort, do not run.
#
# ASCII-only on purpose (AGENTS.md): a .ps1 containing non-ASCII gets mangled when read as
# ANSI. Any Chinese output belongs in a separate UTF-8 file that is read explicitly.
#
# Usage:
#   pwsh -NoProfile -File remote-run.ps1 -Local <script.sh> -Remote <remote-name> [-NoDelete]
#
# Exit codes:
#   0  transferred, verified, executed, remote script exited 0
#   9  transfer verification FAILED (md5 or size mismatch) -- remote script NOT executed
#   2  bad usage / local file missing

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Local,
    [Parameter(Mandatory = $true)][string]$Remote,
    [string]$RemoteDir = '/home/kali/ms-accept',
    [switch]$NoDelete,
    # Self-test switch: truncate the payload so the remote side receives a PARTIAL file.
    # That is what a real failed transfer looks like, and the guard must refuse to execute
    # it. Without this, a guard that always prints VERIFIED would look identical to a
    # working one.
    [int]$TruncateBase64 = 0
)

$ErrorActionPreference = 'Stop'

$plink = 'C:\Program Files\PuTTY\plink.exe'
$hostKey = 'ssh-ed25519 255 SHA256:cwfyC8w0qteYq8lXud9P9U2aSS8O/bxQOK6Y60BOmsI'
$sshUser = 'kali'
$sshHost = '192.168.50.198'

if (-not (Test-Path -LiteralPath $plink)) {
    Write-Output "PLINK_MISSING $plink"
    exit 2
}

# AGENTS.md: .NET static methods resolve relative paths against the PROCESS CWD and do
# NOT follow PowerShell's Set-Location, while Test-Path / Get-Content -LiteralPath DO.
# Mixing them yields opposite conclusions about the same file. So resolve to an absolute
# path FIRST, then do the existence check and the read with the SAME mechanism.
$resolved = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Local)
if (-not [IO.File]::Exists($resolved)) {
    Write-Output "LOCAL_MISSING $resolved"
    exit 2
}

# Normalise line endings LOCALLY, before measuring and before encoding.
#
# The remote side strips CR (`sed -i 's/\r$//'`), so comparing its post-strip size against the
# raw local size can never match whenever the local file has CRLF. That is not hypothetical: the
# guard caught exactly this on the first file produced via Out-File (593 vs 594 bytes). Normalising
# here makes the remote sed a no-op and the comparison exact, and it also means bash receives LF
# endings as it requires.
$raw = [IO.File]::ReadAllBytes($resolved)
$out = New-Object System.Collections.Generic.List[byte]
for ($i = 0; $i -lt $raw.Length; $i++) {
    if ($raw[$i] -eq 13) {
        # swallow CR; if a LF follows, the LF alone becomes the newline
        continue
    }
    $out.Add($raw[$i])
}
$bytes = $out.ToArray()
$size = $bytes.Length
$md5 = (Get-FileHash -LiteralPath $resolved -Algorithm MD5).Hash.ToLower()
$b64 = [Convert]::ToBase64String($bytes)

# The md5 must describe the NORMALISED bytes, not the file on disk, so recompute it from those.
$tmp = [IO.Path]::GetTempFileName()
[IO.File]::WriteAllBytes($tmp, $bytes)
$md5 = (Get-FileHash -LiteralPath $tmp -Algorithm MD5).Hash.ToLower()
Remove-Item -LiteralPath $tmp -Force
$rawLen = $raw.Length
if ($rawLen -ne $size) {
    Write-Output "normalised line endings: on-disk $rawLen bytes -> payload $size bytes (CR stripped)"
}

if ($TruncateBase64 -gt 0) {
    $keep = [Math]::Min($TruncateBase64, $b64.Length - 1)
    $b64 = $b64.Substring(0, $keep)
    Write-Output "SELF-TEST: truncated payload to $keep of $($bytes.Length) bytes worth of base64"
}

Write-Output "local  path=$resolved"
Write-Output "local  name=$Remote  bytes=$size  md5=$md5"

$remotePath = "$RemoteDir/$Remote"

# Build the remote command with single-quoted pieces so PowerShell does not expand
# the bash-side $( ) substitutions; they must reach the remote shell intact.
#
# Two lessons are baked in here:
#  * The success marker must live INSIDE the then-branch. An earlier version printed
#    TRANSFER_VERIFIED unconditionally after the if statement, so when that if failed to
#    parse (quoting mangled across PowerShell -> plink -> zsh) the script still claimed
#    success -- a guard that verifies nothing while looking like it does. That false
#    green was observed, not hypothesised.
#  * Avoid cut/awk for field extraction: the quoting of their delimiter argument did not
#    survive the trip. Parameter expansion (${VAR%% *}) needs no nested quoting at all.
$cmd = 'echo ' + $b64 + ' | base64 -d > ' + $remotePath + '; '
$cmd += 'sed -i ''s/\r$//'' ' + $remotePath + '; '
$cmd += 'RMD5=$(md5sum ' + $remotePath + '); RMD5=${RMD5%% *}; '
$cmd += 'RSZ=$(wc -c < ' + $remotePath + '); '
$cmd += 'if [ "$RMD5" = "' + $md5 + '" ] && [ "$RSZ" = "' + $size + '" ]; then '
$cmd += 'echo "remote verified bytes=$RSZ md5=$RMD5"; '
$cmd += 'echo TRANSFER_VERIFIED; '
$cmd += 'else '
$cmd += 'echo "TRANSFER_MISMATCH got_md5=$RMD5 got_bytes=$RSZ want_md5=' + $md5 + ' want_bytes=' + $size + '" >&2; '
$cmd += 'exit 9; fi; '
if ($NoDelete) {
    $cmd += 'bash ' + $remotePath
} else {
    $cmd += 'bash ' + $remotePath + '; rc=$?; rm -f ' + $remotePath + '; exit $rc'
}

$out = & $plink -ssh -pw kali -batch -hostkey $hostKey "$sshUser@$sshHost" $cmd 2>&1
$rc = $LASTEXITCODE

foreach ($line in $out) { Write-Output ("  " + ($line -replace '\s+$', '')) }

# Decide based on what the remote actually said, not just on the exit code:
# a mismatch must be recognisable even if the surrounding plumbing also failed.
$joined = ($out | Out-String)
if ($joined -match 'TRANSFER_MISMATCH') {
    Write-Output 'RESULT=TRANSFER_MISMATCH (remote script NOT executed)'
    exit 9
}
if ($joined -notmatch 'TRANSFER_VERIFIED') {
    Write-Output 'RESULT=UNVERIFIED (no verification marker; remote script NOT executed)'
    exit 9
}
if ($rc -ne 0) {
    Write-Output "RESULT=REMOTE_SCRIPT_FAILED rc=$rc"
    exit $rc
}
Write-Output 'RESULT=OK'
exit 0