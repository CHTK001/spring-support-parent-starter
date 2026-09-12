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
                    + "+('|IP='+((Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -notlike '127.*'} | Select-Object -First 1).IPAddress))";
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
}