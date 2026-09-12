package com.chua.starter.server.support.provider.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.spi.HostDetectProvider;
import com.chua.starter.server.support.util.SshClients;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Linux 主机硬件探测提供者（SSH 采集）
 * <p>
 * 通过 SSH 连接目标 Linux 主机，执行 nproc / free -m / df -k / os-release /
 * uname -m / hostname -I 等命令采集 CPU/内存/磁盘/系统版本/架构/内网 IP。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinuxHostDetectProvider implements HostDetectProvider {

    /** SSH 连接池管理器 */
    private final ConnectionManager connectionManager;

    @Override
    public String osType() {
        return "LINUX";
    }

    @Override
    public ReturnResult<MsServerHost> detect(MsServerHost host, SshCredential credential) {
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
            // CPU 核心
            String cpu = execQuiet(handle, "nproc");
            if (isNumeric(cpu)) {
                host.setServerHostCpuCores(Integer.valueOf(cpu.trim()));
            }
            // 内存(MB)
            String mem = execQuiet(handle, "free -m | awk '/^Mem:/{print $2}'");
            if (isNumeric(mem)) {
                host.setServerHostMemoryMb(Long.valueOf(mem.trim()));
            }
            // 磁盘(GB, 根分区)
            String disk = execQuiet(handle, "df -k / | awk 'NR==2{printf \"%.0f\", $2/1024/1024}'");
            if (isNumeric(disk)) {
                host.setServerHostDiskGb(Long.valueOf(disk.trim()));
            }
            // 系统版本
            String os = execQuiet(handle, "cat /etc/os-release 2>/dev/null | grep -m1 PRETTY_NAME | cut -d'=' -f2 | tr -d '\"'");
            if (isBlank(os)) {
                os = execQuiet(handle, "uname -sr");
            }
            if (!isBlank(os)) {
                host.setServerHostOsVersion(os.trim());
            }
            // 架构
            String arch = execQuiet(handle, "uname -m");
            if (!isBlank(arch)) {
                host.setServerHostOsArch(arch.trim());
            }
            // 内网 IP
            String ip = execQuiet(handle, "hostname -I 2>/dev/null | awk '{print $1}'");
            if (!isBlank(ip)) {
                host.setServerHostPrivateIp(ip.trim());
            }
            return ReturnResult.ok(host, "Linux 硬件探测成功");
        } catch (Exception e) {
            log.warn("[detect] Linux 探测失败 hostId={} address={}: {}",
                    host.getId(), host.getServerHostAddress(), e.getMessage());
            return ReturnResult.error("Linux 硬件探测失败: " + e.getMessage());
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
        }
    }

    /**
     * 静默执行命令并返回输出（失败返回 null）
     *
     * @param handle SSH 连接句柄
     * @param command 命令
     * @return 输出或 null
     */
    private String execQuiet(SshClients.Handle handle, String command) {
        try {
            return handle.exec(command);
        } catch (Exception e) {
            log.debug("[detect] Linux 命令执行失败 {}: {}", command, e.getMessage());
            return null;
        }
    }

    private boolean isNumeric(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}