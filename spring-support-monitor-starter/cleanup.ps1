Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 3
$left = Get-Process java -ErrorAction SilentlyContinue
if ($left) { Write-Output ("still alive: " + ($left.Id -join ',')) } else { Write-Output 'all java killed' }
Write-Output '--- ports ---'
netstat -ano | Select-String ':8081|:29181' | Select-String 'LISTENING'
