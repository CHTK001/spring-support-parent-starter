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
* macOS 主机硬件探测提供者（SSH 采集）
* <p>
* 通过 SSH 连接目标 macOS 主机，使用 BSD 系命令（sysctl / sw_vers / ipconfig getifaddr）
* 采集 CPU/内存/系统版本/内网 IP 等资产字段。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MacosHostDetectProvider implements HostDetectProvider {

    /** SSH 连接池管理器 */
    private final ConnectionManager connectionManager;

    @Override
    public String osType() {
        return "MACOS";
    }

    @Override
    public ReturnResult<MsServerHost> detect(MsServerHost host, SshCredential credential) {
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
            // CPU 核心数（macOS 无 nproc，使用 sysctl hw.ncpu）
            String cpu = execQuiet(handle, "sysctl -n hw.ncpu");
            if (NumberUtils.isNumber(cpu)) {
                host.setServerHostCpuCores(NumberUtils.toInt(cpu));
            }
            // 内存(MB)（sysctl hw.memsize 字节换算）
            String mem = execQuiet(handle, "echo $(( $(sysctl -n hw.memsize) / 1024 / 1024 ))");
            if (NumberUtils.isNumber(mem)) {
                host.setServerHostMemoryMb(NumberUtils.toLong(mem));
            }
            // 磁盘(GB, 根分区)，df -k 容量换算
            String disk = execQuiet(handle, "df -k / | awk 'NR==2{printf \"%.0f\", $2/1024/1024}'");
            if (NumberUtils.isNumber(disk)) {
                host.setServerHostDiskGb(NumberUtils.toLong(disk));
            }
            // 系统版本（sw_vers 优先，回退 uname -sr）
            String os = execQuiet(handle, "sw_vers -productVersion 2>/dev/null");
            if (StringUtils.isBlank(os)) {
                os = execQuiet(handle, "uname -sr");
            }
            if (StringUtils.isNotBlank(os)) {
                host.setServerHostOsVersion(os.trim());
            }
            // 架构
            String arch = execQuiet(handle, "uname -m");
            if (StringUtils.isNotBlank(arch)) {
                host.setServerHostOsArch(arch.trim());
            }
            // 内网 IP（macOS 用 ipconfig getifaddr）
            String ip = execQuiet(handle, "ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null");
            if (StringUtils.isNotBlank(ip)) {
                host.setServerHostPrivateIp(ip.trim());
            }
            return ReturnResult.ok(host, "macOS 硬件探测成功");
        } catch (Exception e) {
            log.warn("[detect] macOS 探测失败 hostId={} address={}: {}",
                    host.getId(), host.getServerHostAddress(), e.getMessage());
            return ReturnResult.error("macOS 硬件探测失败: " + e.getMessage());
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
            return handle.client().exec().command(command).executeAndGetOutput();
        } catch (Exception e) {
            log.debug("[detect] macOS 命令执行失败 {}: {}", command, e.getMessage());
            return null;
        }
    }
}