Set-Location 'D:\ch\project\spring-support-parent-starter'
& 'D:\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd' compile -pl spring-support-monitor-starter -DskipTests 2>&1 | Select-Object -Last 4
Write-Output "BACKEND_EXIT:$LASTEXITCODE"
Set-Location 'D:\ch\project\vue-support-parent-starter\apps\vue-support-monitor-starter'
npx vue-tsc --noEmit 2>&1 | Select-String -Pattern 'HostProfileView|HostDetailView|zip\.ts|monitorApi' | Select-Object -First 15
Write-Output "VUE_EXIT:$LASTEXITCODE"
