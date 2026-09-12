package com.chua.starter.server.support.provider.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
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
 * 通过 SSH 连接目标 Linux 主机，执行系统命令采集 CPU/内存/磁盘/系统版本/架构/内网 IP，
 * 探测结果回写主机资产生成 {@link MsServerHost} 资产字段。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinuxHostDetectProvider implements HostDetectProvider {

    /**
     * SSH 连接池管理器（负责借取/释放 SSH 连接）
     */
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
            // CPU 核心数（nproc 命令）
            String cpu = execQuiet(handle, "nproc");
            if (NumberUtils.isNumber(cpu)) {
                host.setServerHostCpuCores(NumberUtils.toInt(cpu));
            }
            // 内存(MB)，取自 free -m 第二行 Mem 列
            String mem = execQuiet(handle, "free -m | awk '/^Mem:/{print $2}'");
            if (NumberUtils.isNumber(mem)) {
                host.setServerHostMemoryMb(NumberUtils.toLong(mem));
            }
            // 磁盘(GB, 根分区)，df -k 第二行容量换算
            String disk = execQuiet(handle, "df -k / | awk 'NR==2{printf \"%.0f\", $2/1024/1024}'");
            if (NumberUtils.isNumber(disk)) {
                host.setServerHostDiskGb(NumberUtils.toLong(disk));
            }
            // 系统版本（os-release 优先，回退 uname -sr）
            String os = execQuiet(handle,
                    "cat /etc/os-release 2>/dev/null | grep -m1 PRETTY_NAME | cut -d'=' -f2 | tr -d '\"'");
            if (StringUtils.isBlank(os)) {
                os = execQuiet(handle, "uname -sr");
            }
            if (StringUtils.isNotBlank(os)) {
                host.setServerHostOsVersion(os.trim());
            }
            // 架构（uname -m）
            String arch = execQuiet(handle, "uname -m");
            if (StringUtils.isNotBlank(arch)) {
                host.setServerHostOsArch(arch.trim());
            }
            // 内网 IP（hostname -I 取第一个）
            String ip = execQuiet(handle, "hostname -I 2>/dev/null | awk '{print $1}'");
            if (StringUtils.isNotBlank(ip)) {
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
     * 静默执行 SSH 命令并返回输出（失败返回 null，不抛出异常）
     *
     * @param handle  SSH 连接句柄，不能为 null
     * @param command 待执行命令，不能为 null 或空
     * @return 命令标准输出；执行失败返回 null
     */
    private String execQuiet(SshClients.Handle handle, String command) {
        try {
            return handle.exec(command);
        } catch (Exception e) {
            log.debug("[detect] Linux 命令执行失败 {}: {}", command, e.getMessage());
            return null;
        }
    }
}