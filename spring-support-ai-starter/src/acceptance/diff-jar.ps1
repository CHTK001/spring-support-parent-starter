# Compare two fat jars entry by entry (name -> size) and print every difference.
#
# Companion to build-fat-jar.ps1: run the build WITHOUT -RefJar, then use this to
# review the delta against the reference jar before deploying. Every changed entry
# must be explainable by the change set; anything else means the rebuild dragged
# in unrelated work.
#
# ASCII only (repo rule: PowerShell 5.1 reads a BOM-less .ps1 as ANSI and mangles CJK).
#
# USAGE
#   pwsh -File diff-jar.ps1 -Ref 'D:\ch\project\.tmp\ref-app.jar' -New 'target\xxx-exec.jar'
param(
  [Parameter(Mandatory = $true)][string]$Ref,
  [Parameter(Mandatory = $true)][string]$New
)

$ErrorActionPreference = 'Stop'

function Get-EntryMap([string]$path) {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
  $map = @{}
  foreach ($e in $zip.Entries) { $map[$e.FullName] = $e.Length }
  $zip.Dispose()
  return $map
}

if (-not (Test-Path $Ref)) { throw "reference jar not found: $Ref" }
if (-not (Test-Path $New)) { throw "new jar not found: $New" }

$r = Get-EntryMap $Ref
$n = Get-EntryMap $New

$added = @($n.Keys | Where-Object { -not $r.ContainsKey($_) } | Sort-Object)
$removed = @($r.Keys | Where-Object { -not $n.ContainsKey($_) } | Sort-Object)
$changed = @($n.Keys | Where-Object { $r.ContainsKey($_) -and $r[$_] -ne $n[$_] } | Sort-Object)

Write-Host ("ref entries = " + $r.Count + "   new entries = " + $n.Count)
Write-Host ("ADDED   (" + $added.Count + ")")
foreach ($k in $added) { Write-Host ("  + " + $k + "  " + $n[$k]) }
Write-Host ("REMOVED (" + $removed.Count + ")")
foreach ($k in $removed) { Write-Host ("  - " + $k + "  " + $r[$k]) }
Write-Host ("CHANGED (" + $changed.Count + ")")
foreach ($k in $changed) { Write-Host ("  ~ " + $k + "  ref=" + $r[$k] + " new=" + $n[$k]) }

$total = $added.Count + $removed.Count + $changed.Count
Write-Host ("TOTAL_DIFF " + $total)
if ($total -eq 0) { Write-Host 'DIFF_NONE' }
