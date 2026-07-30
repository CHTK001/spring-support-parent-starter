package com.chua.starter.server.support.spi.process;

/**
 * Windows 进程命令支持工具类。
 *
 * <p>提供 Windows 系统下进程列表和终止命令的构建。
 *
 * @author CH
 */
public final class WindowsProcessCommandSupport {

    private WindowsProcessCommandSupport() {
    }

    /**
     * 构建 Windows 本机进程列表命令。
     *
     * @param queryWindow 查询窗口
     * @return 命令字符串
     */
    public static String windowsListCommand(int queryWindow) {
        return "$os=Get-CimInstance Win32_OperatingSystem;"
                + "$total=[double]($os.TotalVisibleMemorySize)*1024;"
                + "$stats=Get-CimInstance Win32_PerfFormattedData_PerfProc_Process "
                + "| Where-Object { $_.IDProcess -gt 0 -and $_.Name -ne '_Total' -and $_.Name -ne 'Idle' } "
                + "| Sort-Object @{Expression='PercentProcessorTime';Descending=$true},@{Expression='WorkingSetPrivate';Descending=$true} "
                + "| Select-Object -First " + queryWindow + ";"
                + "$procMap=@{};"
                + "$selected=@{};"
                + "$stats | ForEach-Object {$selected[[int]$_.IDProcess]=$true};"
                + "Get-CimInstance Win32_Process | Where-Object {$selected.ContainsKey([int]$_.ProcessId)} | ForEach-Object {$procMap[[int]$_.ProcessId]=$_};"
                + "$items=$stats | ForEach-Object {"
                + "$stat=$_;"
                + "$proc=$procMap[[int]$stat.IDProcess];"
                + "$workingSet=if($null -ne $stat.WorkingSetPrivate){[int64]$stat.WorkingSetPrivate}elseif($proc -and $null -ne $proc.WorkingSetSize){[int64]$proc.WorkingSetSize}else{$null};"
                + "$created=$null;"
                + "if($proc -and $proc.CreationDate){"
                + "try{$created=([System.Management.ManagementDateTimeConverter]::ToDateTime([string]$proc.CreationDate)).ToString('o')}catch{"
                + "try{$created=([datetime]::Parse([string]$proc.CreationDate)).ToString('o')}catch{$created=$null}"
                + "}"
                + "};"
                + "[pscustomobject]@{"
                + "ProcessId=[int64]$stat.IDProcess;"
                + "ParentProcessId=if($proc){[int64]$proc.ParentProcessId}else{$null};"
                + "Name=if($proc -and $proc.Name){$proc.Name}else{$stat.Name};"
                + "CommandLine=if($proc){$proc.CommandLine}else{$null};"
                + "UserName=$null;"
                + "State='Running';"
                + "CpuPercent=if($null -ne $stat.PercentProcessorTime){[double]$stat.PercentProcessorTime}else{$null};"
                + "MemoryPercent=if($total -gt 0 -and $workingSet -gt 0){[math]::Round(($workingSet / $total) * 100, 2)}else{$null};"
                + "WorkingSetSize=$workingSet;"
                + "HandleCount=if($null -ne $stat.HandleCount){[int64]$stat.HandleCount}else{$null};"
                + "ThreadCount=if($null -ne $stat.ThreadCount){[int]$stat.ThreadCount}else{$null};"
                + "Elapsed=if($null -ne $stat.ElapsedTime){[string]$stat.ElapsedTime}else{$null};"
                + "CreationDate=$created"
                + "}"
                + "};"
                + "$items | ConvertTo-Json -Depth 4 -Compress";
    }

    /**
     * 构建 WinRM 进程列表命令。
     *
     * @param queryWindow 查询窗口
     * @return 命令字符串
     */
    public static String winrmListCommand(int queryWindow) {
        return "powershell -NoProfile -Command \"" + windowsListCommand(queryWindow).replace("\"", "\\\"") + "\"";
    }

    /**
     * 构建 Windows 终止进程命令。
     *
     * @param pid 进程 ID
     * @param force 是否强制终止
     * @return 命令字符串
     */
    public static String windowsTerminateCommand(Long pid, boolean force) {
        return force
                ? "Stop-Process -Id %d -Force -ErrorAction Stop; Write-Output 'Process %d terminated'".formatted(pid, pid)
                : "Stop-Process -Id %d -ErrorAction Stop; Write-Output 'Process %d terminated'".formatted(pid, pid);
    }
}
