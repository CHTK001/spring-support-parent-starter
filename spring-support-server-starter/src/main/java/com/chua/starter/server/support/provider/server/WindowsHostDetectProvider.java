package com.chua.starter.server.support.provider.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.spi.HostDetectProvider;
import com.chua.winrm.support.client.WinRmExecClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
@Component
public class WindowsHostDetectProvider implements HostDetectProvider {

    /** WinRM HTTP 默认端口（未配置时使用） */
    private static final int DEFAULT_WINRM_PORT = 5985;

    @Override
    public String osType() {
        return "WINDOWS";
    }

    @Override
    public ReturnResult<MsServerHost> detect(MsServerHost host, SshCredential credential) {
        // WinRM 端口：优先主机"登录端口"，否则回退 5985（HTTP）
        int winrmPort = host.getServerHostPort() != null && host.getServerHostPort() > 0
                ? host.getServerHostPort() : DEFAULT_WINRM_PORT;
        try (WinRmExecClient winrm = WinRmExecClient.builder()
                .host(host.getServerHostAddress())
                .port(winrmPort)
                .username(credential.username())
                .password(credential.password())
                .build()) {
            winrm.connect();
            // 单条 PowerShell 一次性输出全部硬件字段（以 | 分隔 k=v 段，避免多次握手）
            String ps = "('CPU='+((Get-CimInstance Win32_Processor).NumberOfCores)) "
                    + "+('|MEM='+[math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1MB)) "
                    + "+('|DISK='+[math]::Round((Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=3' | Measure-Object -Property Size -Sum).Sum/1GB)) "
                    + "+('|OS='+(Get-CimInstance Win32_OperatingSystem).Caption) "
                    + "+('|ARCH='+$env:PROCESSOR_ARCHITECTURE) "
                    + "+('|IP='+((Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -notlike '127.*'} | Select-Object -First 1).IPAddress)) "
                    + "+('|GPUCOUNT='+@(Get-CimInstance Win32_VideoController).Count) "
                    + "+('|GPUMODEL='+((Get-CimInstance Win32_VideoController | Select-Object -First 1).Name)) "
                    + "+('|NET='+((Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -notlike '127.*'} | ForEach-Object {$_.InterfaceAlias+':'+$_.IPAddress}) -join ',')) "
                    + "+('|CPUUSAGE='+[math]::Round((Get-Counter '\\Processor(_Total)\\% Processor Time' -SampleInterval 1 -MaxSamples 1).CounterSamples.CookedValue,2)) "
                    + "+('|MEMUSAGE='+[math]::Round((((Get-Counter '\\Memory\\Available MBytes').CounterSamples.CookedValue))/((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1MB)*100,2)) "
                    + "+('|DISKUSAGE='+[math]::Round((((Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=3' | Measure-Object -Property FreeSpace -Sum).Sum)/((Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=3' | Measure-Object -Property Size -Sum).Sum))*100,2))";
            String output = winrm.exec().command(ps).executeAndGetOutput();
            // 解析并回写硬件字段
            parseWinrmOutput(output, host);
            return ReturnResult.ok(host, "WinRM 硬件探测成功");
        } catch (Exception e) {
            log.warn("[detect] WinRM 探测失败 hostId={} address={}: {}",
                    host.getId(), host.getServerHostAddress(), e.getMessage());
            return ReturnResult.error("WinRM 硬件探测失败: " + e.getMessage());
        }
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
        for (String seg : output.split("\\|")) {
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
                    default -> {
                        // 忽略未知字段
                    }
                }
            } catch (NumberFormatException nfe) {
                log.debug("[detect] WinRM 字段解析失败 key={}: {}", key, value);
            }
        }
        if (StringUtils.isBlank(host.getServerHostOsVersion())) {
            host.setServerHostOsVersion("Windows");
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
    * 解析百分比字符串为 Double，非法返回 -1
    *
    * @param value 百分比字符串
    * @return Double，非法返回 -1
    */
    private double parseRatio(String value) {
        try {
            double d = Double.parseDouble(value.trim());
            return d >= 0 && d <= 100 ? d : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
