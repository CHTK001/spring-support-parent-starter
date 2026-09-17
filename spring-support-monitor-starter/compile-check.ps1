$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$m = 'D:\ch\project\spring-support-parent-starter\spring-support-monitor-starter'
$src = "$m\src\main\java"
$out = "$m\target\verify-out"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$cp = [IO.File]::ReadAllText("$m\cp-full.txt").Trim()
$files = Get-ChildItem -LiteralPath $src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$argfile = "$out\args.txt"
$lines = @("-d", $out, "-cp", $cp, "-sourcepath", $src)
$lines += $files
Set-Content -LiteralPath $argfile -Value $lines -Encoding ascii
$bat = "$out\run.bat"
Set-Content -LiteralPath $bat -Value ("javac `"@{0}`" > `{1}err.txt` 2>&1" -f $argfile, $out) -Encoding ascii
& cmd /c $bat | Out-Null
$errf = "$out\err.txt"
if (-not (Test-Path $errf)) { Write-Output 'NO_ERR_FILE'; exit 1 }
$err = [IO.File]::ReadAllLines($errf, [System.Text.Encoding]::Default)
$totalErr = ($err | Where-Object { $_ -match '错误:|error:' }).Count
Write-Output ("总错误行: " + $totalErr)
# 按文件分组
$byFile = $err | Where-Object { $_ -match '\.java:\d+:' } | ForEach-Object { ($_ -split ':')[0] } | Group-Object | Sort-Object Count -Descending
Write-Output '=== 按文件错误分布(前20) ==='
$byFile | Select-Object -First 20 | ForEach-Object { Write-Output ("{0}  错误{1}" -f $_.Name, $_.Count) }
# 重点看我的4个文件
Write-Output '=== 我改动的4个文件相关错误 ==='
$mine = $err | Where-Object { $_ -match 'MonitorController|DataSourceConfig|DataSourceStoreService|JdbcAdminService' }
if ($mine) { $mine | Select-Object -First 20 | ForEach-Object { Write-Output $_ } } else { Write-Output '(无)' }
