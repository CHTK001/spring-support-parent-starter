package com.chua.starter.server.support.provider.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.server.support.connection.PowerShellCommand;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.spi.HostDetectProvider;
import com.chua.starter.server.support.util.SshClients;
import lombok.extern.slf4j.Slf4j;

/**
 * Windows 主机硬件探测提供者（WinRM 采集）
 * <p>
 * 通过 WinRM（Windows Remote Management，Windows 远程管理）连接目标 Windows 主机，
 * 使用 PowerShell 单条命令批量采集 CPU/内存/磁盘/系统版本/架构/内网 IP 并回写资产字段。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "windows", order = 100)
public class WindowsHostDetectProvider implements HostDetectProvider {

    /**
    /**
     * OpenSSH 默认端口（未配置时使用）
     */
    private static final int DEFAULT_SSH_PORT = 22;

    @Override
    public String osType() {
        return "WINDOWS";
    }

    @Override
    public ReturnResult<MsServerHost> detect(MsServerHost host, SshCredential credential) {
        // OpenSSH 端口：优先主机"登录端口"，否则回退 22
        int sshPort = host.getServerHostPort() != null && host.getServerHostPort() > 0
                ? host.getServerHostPort() : DEFAULT_SSH_PORT;
        try (SshClients.Handle handle = SshClients.open(host.getServerHostAddress(), sshPort, credential)) {
            handle.client().connect();
            // 用 -EncodedCommand（UTF-16LE Base64）彻底规避引号/反斜杠/折行/特殊字符问题；
            // 脚本末尾 Write-Output 单行、以 | 分隔 k=v
            String command = PowerShellCommand.encoded(buildDetectScript());
            String output = handle.client().exec().command(command).execute().stdout();
            log.info("[detect] OpenSSH raw output len={}: [{}]", output == null ? -1 : output.length(), output);
            // 解析并回写硬件字段
            parseWinrmOutput(output, host);
            return ReturnResult.ok(host, "OpenSSH 硬件探测成功");
        } catch (Exception e) {
            log.warn("[detect] OpenSSH 探测失败 hostId={} address={}: {}",
                    host.getId(), host.getServerHostAddress(), e.getMessage());
            return ReturnResult.error("OpenSSH 硬件探测失败: " + e.getMessage());
        }
    }

    /**
     * 构建 Windows 硬件/包管理器探测 PowerShell 脚本。
     * <p>
     * 脚本先逐项计算为标量变量，最后 {@code Write-Output} 单行、以 | 分隔 k=v。
     * 该脚本经 UTF-16LE Base64 编码后由 {@code powershell -EncodedCommand} 执行。
     * </p>
     *
     * @return PowerShell 脚本文本
     */
    private String buildDetectScript() {
        return """
                $ErrorActionPreference='SilentlyContinue'
                $cpu=(Get-CimInstance Win32_Processor | Measure-Object -Property NumberOfCores -Sum).Sum
                $cores=(Get-CimInstance Win32_Processor | Measure-Object -Property NumberOfLogicalProcessors -Sum).Sum
                $mem=[math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1MB)
                $disk=[math]::Round((Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=3' | Measure-Object -Property Size -Sum).Sum/1GB)
                $os=(Get-CimInstance Win32_OperatingSystem).Caption
                $arch=$env:PROCESSOR_ARCHITECTURE
                $ip=((Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -notlike '127.*'} | Select-Object -First 1).IPAddress)
                $gpun=@(Get-CimInstance Win32_VideoController).Count
                $gpum=((Get-CimInstance Win32_VideoController | Select-Object -First 1).Name)
                $net=((Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -notlike '127.*'} | ForEach-Object {$_.InterfaceAlias+':'+$_.IPAddress}) -join ',')
                $cu=[math]::Round((Get-Counter '\\Processor(_Total)\\% Processor Time' -SampleInterval 1 -MaxSamples 1).CounterSamples.CookedValue,2)
                $mu=[math]::Round(((Get-Counter '\\Memory\\Available MBytes').CounterSamples.CookedValue)/((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1MB)*100,2)
                $du=[math]::Round(((Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=3' | Measure-Object FreeSpace -Sum).Sum)/((Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=3' | Measure-Object Size -Sum).Sum)*100,2)
                $dio=Get-Counter '\\PhysicalDisk(_Total)\\Disk Read Bytes/sec','\\PhysicalDisk(_Total)\\Disk Write Bytes/sec' -SampleInterval 1 -MaxSamples 1
                $dr=[math]::Round((($dio.CounterSamples | Where-Object {$_.Path -like '*read*'}).CookedValue)/1KB,2)
                $dw=[math]::Round((($dio.CounterSamples | Where-Object {$_.Path -like '*write*'}).CookedValue)/1KB,2)
                $pm=$(if(Test-Path 'C:\\ProgramData\\chocolatey\\bin\\choco.exe'){'choco'}elseif(Get-Command winget -ErrorAction SilentlyContinue){'winget'}else{''})
                Write-Output "CPU=$cpu|MEM=$mem|DISK=$disk|OS=$os|ARCH=$arch|IP=$ip|GPUCOUNT=$gpun|GPUMODEL=$gpum|NET=$net|CPUUSAGE=$cu|MEMUSAGE=$mu|DISKUSAGE=$du|DISKREAD=$dr|DISKWRITE=$dw|PM=$pm"
                $names=@('java','javaw','redis-server','redis-sentinel','mysqld','mariadbd','nginx','node','postgres','mongod','dockerd','containerd','memcached','etcd','minio')
                Get-Process -Name $names -ErrorAction SilentlyContinue | ForEach-Object {
                  $up=[int]((Get-Date)-$_.StartTime).TotalSeconds
                  $cp=if($up -gt 0 -and $cores -gt 0 -and $_.CPU){[math]::Round(($_.CPU/$up/$cores)*100,1)}else{0}
                  $mm=[math]::Round($_.WS/1MB,1)
                  $pn=$_.ProcessName
                  $pt=if($_.Path){$_.Path -replace "[`t`r`n]",' '}else{''}
                  Write-Output ("SOFT="+$_.Id+[char]9+$cp+[char]9+$mm+[char]9+$up+[char]9+$pn+[char]9+$pt)
                }
                """;
    }

    /**
     * 解析 WinRM PowerShell 输出为硬件字段（| 分隔的 k=v 段）
     *
     * @param output 原始输出，可为 null
     * @param host   待回写的主机，不能为 null
     */
    private void parseWinrmOutput(String output, MsServerHost host) {
        if (StringUtils.isBlank(output)) {
            return;
        }
        // 第一行是 | 分隔的 k=v 主行；其余以 SOFT= 开头的是软件实例（tab 分隔）
        String[] lines = output.split("\\r?\\n");
        String mainLine = lines[0];
        for (String seg : mainLine.split("\\|")) {
            int eq = seg.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = seg.substring(0, eq).trim().toLowerCase();
            String value = seg.substring(eq + 1).trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                switch (key) {
                    case "cpu" -> {
                        int cores = Integer.parseInt(value);
                        if (cores > 0) {
                            host.setServerHostCpuCores(cores);
                        }
                    }
                    case "mem" -> {
                        long memMb = Long.parseLong(value);
                        if (memMb > 0) {
                            host.setServerHostMemoryMb(memMb);
                        }
                    }
                    case "disk" -> {
                        long diskGb = (long) Math.floor(Double.parseDouble(value));
                        if (diskGb > 0) {
                            host.setServerHostDiskGb(diskGb);
                        }
                    }
                    case "os" -> host.setServerHostOsVersion(value);
                    case "arch" -> host.setServerHostOsArch(value);
                    case "ip" -> {
                        if (value.contains(".") || value.contains(":")) {
                            host.setServerHostPrivateIp(value);
                        }
                    }
                    case "gpucount" -> {
                        int gpu = Integer.parseInt(value);
                        if (gpu > 0) {
                            host.setServerHostGpuCount(gpu);
                        }
                    }
                    case "gpumodel" -> {
                        if (StringUtils.isNotBlank(value)) {
                            host.setServerHostGpuModel(value);
                        }
                    }
                    case "net" -> {
                        if (value.contains(":")) {
                            // 网卡别名:IP 逗号拼接，转 JSON 数组
                            host.setServerHostNetworks(toNetworksJson(value));
                        }
                    }
                    case "cpuusage" -> {
                        double usage = parseRatio(value);
                        if (usage >= 0) {
                            host.setServerHostCpuUsage(usage);
                        }
                    }
                    case "memusage" -> {
                        double usage = parseRatio(value);
                        if (usage >= 0) {
                            host.setServerHostMemUsage(usage);
                        }
                    }
                    case "diskusage" -> {
                        double usage = parseRatio(value);
                        if (usage >= 0) {
                            host.setServerHostDiskUsage(usage);
                        }
                    }
                    case "diskread" -> host.setServerHostDiskReadKbs(parseKbs(value));
                    case "diskwrite" -> host.setServerHostDiskWriteKbs(parseKbs(value));
                    case "pm" -> {
                        // 实际探测到的包管理器（choco / winget）；为空则保持不变，不强制默认
                        if (StringUtils.isNotBlank(value)) {
                            host.setServerHostPackageManager(value);
                        }
                    }
                    default -> {
                        // 忽略未知字段
                    }
                }
            } catch (NumberFormatException nfe) {
                log.debug("[detect] WinRM 字段解析失败 key={}: {}", key, value);
            }
        }
        // 软件实例：SOFT=<pid>\t<cpu>\t<memMb>\t<uptime>\t<name>\t<path>
        parseSoftwareLines(lines, host);
        if (StringUtils.isBlank(host.getServerHostOsVersion())) {
            host.setServerHostOsVersion("Windows");
        }
        // 包管理器由 PowerShell 探测段（PM=）按实际可用性回写（choco/winget），不再强制默认 winget
    }

    /**
     * 解析软件实例行并按软件特征识别后落 JSON。
     *
     * @param lines 全部输出行
     * @param host  主机
     */
    private void parseSoftwareLines(String[] lines, MsServerHost host) {
        java.util.List<com.chua.starter.server.support.software.SoftwareInstanceVO> list =
                new java.util.ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.startsWith("SOFT=")) {
                continue;
            }
            String[] f = line.substring(5).split("\\t", -1);
            if (f.length < 5 || !com.chua.common.support.utils.NumberUtils.isNumber(f[0])) {
                continue;
            }
            int pid = com.chua.common.support.utils.NumberUtils.toInt(f[0]);
            double cpu = com.chua.common.support.utils.NumberUtils.isDecimals(f[1])
                    ? com.chua.common.support.utils.NumberUtils.toDouble(f[1]) : 0;
            double memMb = com.chua.common.support.utils.NumberUtils.isDecimals(f[2])
                    ? com.chua.common.support.utils.NumberUtils.toDouble(f[2]) : 0;
            long up = com.chua.common.support.utils.NumberUtils.isNumber(f[3])
                    ? com.chua.common.support.utils.NumberUtils.toLong(f[3]) : 0;
            String name = f[4];
            String path = f.length > 5 ? f[5] : "";
            String type = com.chua.starter.server.support.software.SoftwareCatalog.match(name,
                    path.isBlank() ? name : path);
            if (type != null) {
                list.add(com.chua.starter.server.support.software.SoftwareCatalog
                        .instance(type, pid, cpu, memMb, up, path.isBlank() ? name : path));
            }
        }
        if (!list.isEmpty()) {
            try {
                host.setServerHostSoftware(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(list));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignore) {
                // 序列化失败不阻断
            }
        }
    }

    /**
     * 解析速率（KB/s）字段，非法返回 null。
     *
     * @param value 数值文本
     * @return KB/s，非法为 null
     */
    private Double parseKbs(String value) {
        try {
            double d = Double.parseDouble(value.trim());
            return d < 0 ? null : Math.round(d * 100.0) / 100.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 将 WinRM 网卡字符串（别名:IP 逗号拼接）转为 JSON 数组
     *
     * @param value 网卡字符串，如 "以太网:10.0.0.3,WLAN:192.168.1.5"
     * @return JSON 数组字符串，解析失败返回 null
     */
    private String toNetworksJson(String value) {
        try {
            StringBuilder sb = new StringBuilder("[");
            String[] parts = value.split(",");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                int colon = part.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String name = part.substring(0, colon).trim();
                String ip = part.substring(colon + 1).trim();
                if (ip.isEmpty()) {
                    continue;
                }
                if (sb.length() > 1) {
                    sb.append(",");
                }
                sb.append("{\"name\":\"").append(escapeJson(name)).append("\",\"ip\":\"").append(ip).append("\"}");
            }
            sb.append("]");
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 转义 JSON 字符串中的双引号与反斜杠
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 解析百分比字符串为 Double（保留 1 位小数），非法返回 -1
     *
     * @param value 百分比字符串
     * @return Double，非法返回 -1
     */
    private double parseRatio(String value) {
        try {
            double d = Double.parseDouble(value.trim());
            return d >= 0 && d <= 100 ? Math.round(d * 10.0) / 10.0 : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
