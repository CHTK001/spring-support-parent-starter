# Agent 自动部署脚本 (Windows)
# 用法: powershell -ExecutionPolicy Bypass -File deploy-windows.ps1 <server-url> <host-id> <agent-key>
param(
    [string]$ServerUrl = "http://124.221.230.112:8081",
    [string]$HostId = "1",
    [string]$AgentKey = "unknown"
)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $dir

$yml = @"
server:
  url: $ServerUrl
host:
  id: $HostId
  key: $AgentKey
agent:
  heartbeat: 10s
"@
$yml | Out-File agent.yml -Encoding utf8

# 注册为 Windows 服务
$serviceName = "MonitorAgent"
sc.exe create $serviceName binPath= "java -jar `"$dir\agent.jar`" --spring.config.additional-location=file:`"$dir\agent.yml`"" start= auto
sc.exe description $serviceName "Monitor Agent Service"
sc.exe start $serviceName
Write-Host "Agent deployed and started. Check: Get-Service $serviceName"
