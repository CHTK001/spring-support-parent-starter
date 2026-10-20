Set-Location 'D:\ch\project\vue-support-parent-starter\apps\vue-support-monitor-starter'
npx vue-tsc --noEmit 2>&1 | Select-String -Pattern 'HostProfileView|HostDetailView|HostFileManager|monitorApi|zip\.ts' | Select-Object -First 20
Write-Output "VUE_EXIT:$LASTEXITCODE"
