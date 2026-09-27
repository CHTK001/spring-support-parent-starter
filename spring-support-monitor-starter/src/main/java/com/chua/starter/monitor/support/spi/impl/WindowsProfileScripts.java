package com.chua.starter.monitor.support.spi.impl;

/**
 * Windows 采集与动作通道共用的 PowerShell 脚本。
 *
 * <p>OpenSSH 与 WinRM 两条通道下发的是同一台 Windows 主机的同一份画像，脚本必须一字不差地一致，
 * 否则同一分区在两个通道上会给出不同字段。这里只放文本，不放解析逻辑。</p>
 *
 * <p>{@code static final} 字段必须在静态代码块里赋值：字面量拼接出来的字符串是编译期常量，
 * 取值会被固化进每个引用方 class，改这里的文本而引用方没重编，线上跑的还是旧脚本。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
final class WindowsProfileScripts {

    /**
     * 网卡清单：按 MAC 关联 {@code Win32_NetworkAdapter} 补链路速率与启用状态，MTU 取自 {@code Get-NetIPInterface}。
     *
     * <p>{@code IPAddress} 是字符串数组，直接 {@code ConvertTo-Csv} 只会写出
     * {@code System.String[]}，所以 IPv4/IPv6 必须在 PowerShell 侧拆好。</p>
     *
     * <p>MTU 的取值顺序是实测出来的：192.168.200.18（WS2012R2 + VirtIO）上
     * {@code Win32_NetworkAdapterConfiguration.MTU} 与 {@code Win32_NetworkAdapter.MaxMtu} 均为空，
     * 只有 {@code Get-NetIPInterface.NlMtu} 给出真实值（以太网 1500）；该 cmdlet 在更早的系统上不存在，
     * 故异常吞掉后回落到原来的两个来源。</p>
     */
    static final String NETWORK;

    /**
     * 进程属主：一条 {@code Get-Process -IncludeUserName} 建 pid 到「域\账号」的映射。
     *
     * <p>逐进程调 {@code Win32_Process.GetOwner} 实测 121 个进程要 6.7 秒，本写法实测 110 毫秒；
     * 低版本 PowerShell 不认该参数时输出为空，属主列留空。</p>
     */
    static final String PROCESS_OWNERS;

    /**
     * 进程清单：CPU 时间片用于折算占用率，线程数/可执行路径/创建时间补齐契约字段。
     */
    static final String PROCESSES;

    /**
     * 连接与监听清单：一次给出 TCP/UDP、v4/v6 与占用进程号。
     *
     * <p>不用 {@code Get-NetTCPConnection}：192.168.200.18（WS2012R2，管理员会话）实测 207 条连接的
     * {@code OwningProcess} 全为空，进程归属整列拿不到；且该 cmdlet 只有 TCP，UDP 端点会整块缺席。</p>
     */
    static final String NETSTAT;

    /**
     * 防火墙规则。
     *
     * <p>PowerShell 5.1 会把 CIM 枚举序列化成底层数字，故在脚本内投影为名字，
     * 避免页面出现 Direction=1、Action=2 这类无法解读的值。</p>
     */
    static final String FIREWALL;

    /**
     * 驱动清单。
     *
     * <p>属性名必须是 {@code Get-WindowsDriver} 真实输出的字段：Select-Object 取不存在的属性
     * 不会报错，只会静默少一列，页面表现为整列为空。</p>
     */
    static final String DRIVER;

    /**
     * 卷清单（磁盘分区）。
     */
    static final String VOLUMES;

    /**
     * 指定进程的线程清单。
     *
     * <p>CPU 占用必须两次采样相减：{@code ProcessThread} 只给累计 CPU 时间，单次取值拿不到百分比。</p>
     *
     * <p>枚举必须在脚本内投影为名字：{@code ConvertTo-Json} 把 {@code ThreadState}、
     * {@code WaitReason} 序列化成底层数字、{@code TotalProcessorTime} 序列化成 TimeSpan 对象，
     * 直接取用页面就出现状态 "5"、等待原因 "6"、CPU 时间是一段 JSON。</p>
     *
     * @param pid 进程 ID
     * @return PowerShell 脚本
     */
    static String threads(int pid) {
        return "$ProgressPreference='SilentlyContinue'; $p=Get-Process -Id " + pid + " -ErrorAction SilentlyContinue; "
                + "if($p){ $a=@{}; $w0=[int64]([DateTime]::Now.Ticks); "
                + "foreach($t in $p.Threads){ $a[[string][int64]$t.Id]=[int64]$t.TotalProcessorTime.Ticks }; "
                + "Start-Sleep -Milliseconds 500; $p2=Get-Process -Id " + pid + " -ErrorAction SilentlyContinue; "
                + "if($p2){ $w=([int64]([DateTime]::Now.Ticks)-$w0)/1e7; $p2.Threads | ForEach-Object { $t=$_; $prev=0; "
                + "$k=[string][int64]$t.Id; if($a.ContainsKey($k)){ $prev=$a[$k] }; $c=0; "
                + "if($w -gt 0){ $c=[math]::Round((([int64]$t.TotalProcessorTime.Ticks-$prev)/1e7)/$w*100,2) }; "
                + "[pscustomobject]@{ Id=[int64]$t.Id; ThreadState=[string]$t.ThreadState; WaitReason=[string]$t.WaitReason; "
                + "Priority=[string]$t.PriorityLevel; Cpu=$c; "
                + "CpuMs=[int64]([math]::Round($t.TotalProcessorTime.TotalMilliseconds)) } "
                + "} | ConvertTo-Json -Depth 3 } }";
    }

    static {
        NETWORK = "$ProgressPreference='SilentlyContinue'; $n=@{}; "
                + "try { foreach($i in (Get-NetIPInterface -AddressFamily IPv4 -ErrorAction Stop)){ $n[[string]$i.InterfaceAlias]=[int]$i.NlMtu } } catch { }; "
                + "$m=@{}; foreach($a in (Get-CimInstance Win32_NetworkAdapter | Where-Object {$_.MACAddress})){ $m[$a.MACAddress]=$a }; "
                + "Get-CimInstance Win32_NetworkAdapterConfiguration | Where-Object {$_.IPEnabled} | ForEach-Object { "
                + "$c=$_; $v4=@(); $v6=@(); foreach($i in $c.IPAddress){ if($i -match '^\\d+\\.\\d+\\.\\d+\\.\\d+$'){ $v4+=($i) } else { $v6+=($i) } }; "
                + "$mtu=$c.MTU; $speed=0; $up=$true; $a=$m[$c.MACAddress]; "
                + "if($a){ if($a.MaxMtu){ $mtu=$a.MaxMtu }; $cn=[string]$a.NetConnectionID; "
                + "if($n.ContainsKey($cn)){ $mtu=$n[$cn] }; $speed=$a.Speed; $up=$a.NetEnabled }; "
                + "[pscustomobject]@{ Description=$c.Description; MACAddress=$c.MACAddress; "
                + "IPv4=($v4 -join ';'); IPv6=($v6 -join ';'); MTU=$mtu; Speed=$speed; Up=[bool]$up } "
                + "} | ConvertTo-Csv -NoTypeInformation";
        PROCESS_OWNERS = "$ErrorActionPreference='SilentlyContinue'; "
                + "Get-Process -IncludeUserName | Select-Object Id,UserName | ConvertTo-Csv -NoTypeInformation";
        PROCESSES = "Get-CimInstance Win32_Process | Select ProcessId,ParentProcessId,"
                + "WorkingSetSize,KernelModeTime,UserModeTime,Name,CommandLine,ThreadCount,ExecutablePath,"
                + "@{N='Created';E={if($_.CreationDate){$_.CreationDate.ToString('yyyy-MM-dd HH:mm:ss')}else{''}}} "
                + "| ConvertTo-Csv -NoTypeInformation";
        NETSTAT = "netstat -ano";
        FIREWALL = "Get-NetFirewallRule | Select-Object DisplayName, "
                + "@{n='Direction';e={[string]$_.Direction}}, @{n='Action';e={[string]$_.Action}}, "
                + "@{n='Enabled';e={[string]$_.Enabled}}, @{n='Profile';e={[string]$_.Profile}} "
                + "| ConvertTo-Json -Depth 3";
        DRIVER = "Get-WindowsDriver -Online | Select-Object Driver, "
                + "OriginalFileName, ProviderName, Version, ClassName, BootCritical, "
                + "@{n='Date';e={[string]$_.Date}} | ConvertTo-Json -Depth 3";
        VOLUMES = "Get-Volume | Select-Object DriveLetter, FileSystemLabel, FileSystem, Size, SizeRemaining "
                + "| ConvertTo-Json -Depth 3";
    }

    /**
     * 工具类，禁止实例化。
     */
    private WindowsProfileScripts() {
    }
}
