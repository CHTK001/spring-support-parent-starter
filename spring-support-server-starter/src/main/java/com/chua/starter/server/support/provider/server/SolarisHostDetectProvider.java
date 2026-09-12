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
* Solaris 主机硬件探测提供者（SSH 采集）
* <p>
* 通过 SSH 连接目标 Solaris 主机，使用 Solaris 特有命令
* （psrinfo / prtconf / prstat / ifconfig -a）采集资产字段。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolarisHostDetectProvider implements HostDetectProvider {

    /** SSH 连接池管理器 */
    private final ConnectionManager connectionManager;

    @Override
    public String osType() {
        return "SOLARIS";
    }

    @Override
    public ReturnResult<MsServerHost> detect(MsServerHost host, SshCredential credential) {
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
            // CPU 核心数（psrinfo -p 显示物理处理器数）
            String cpu = execQuiet(handle, "psrinfo -p");
            if (NumberUtils.isNumber(cpu)) {
                host.setServerHostCpuCores(NumberUtils.toInt(cpu));
            }
            // 内存(MB)（prtconf Memory size 行，单位 MB）
            String mem = execQuiet(handle, "prtconf 2>/dev/null | grep 'Memory size' | awk '{print $3}'");
            if (NumberUtils.isNumber(mem)) {
                host.setServerHostMemoryMb(NumberUtils.toLong(mem));
            }
            // 磁盘(GB, 根分区)，df -k 容量换算
            String disk = execQuiet(handle, "df -k / | awk 'NR==2{printf \"%.0f\", $2/1024/1024}'");
            if (NumberUtils.isNumber(disk)) {
                host.setServerHostDiskGb(NumberUtils.toLong(disk));
            }
            // 网卡信息（ifconfig -a 全局 IPv4，Solaris inet 行含 netmask）
            String networks = execQuiet(handle, "ifconfig -a 2>/dev/null | awk '/^[a-z]/{n=$1; sub(\":\",\"\",n)} /inet /{split($2,a,\".\"); if(a[1]!=\"127\" && a[1]!=\"169\"){printf \"{\\\"name\\\":\\\"\"n\"\\\",\\\"ip\\\":\\\"\"$2\"\\\"} \"}}' | tr ' ' ',' | sed 's/^,/[/;s/,$/]/'");
            if (StringUtils.isNotBlank(networks) && networks.startsWith("[")) {
                host.setServerHostNetworks(networks);
            }
            // CPU 使用率（%）（prstat 汇总行的 CPU 列）
            String cpuUsage = execQuiet(handle, "prstat -1 1 2>/dev/null | grep 'Total' | awk '{print 100-$6}'");
            if (StringUtils.isNotBlank(cpuUsage) && isRatio(cpuUsage)) {
                host.setServerHostCpuUsage(toRatio(cpuUsage));
            }
            // 内存使用率（%）（prstat -s rss 汇总：已用/总容量）
            String memUsage = execQuiet(handle, "prstat -1 -s rss 1 1 2>/dev/null | grep 'Total' | awk '{printf \"%.2f\", $6/$5*100}'");
            if (StringUtils.isNotBlank(memUsage) && isRatio(memUsage)) {
                host.setServerHostMemUsage(toRatio(memUsage));
            }
            // 磁盘使用率（%）（df 根分区已用/总容量）
            String diskUsage = execQuiet(handle, "df -k / | awk 'NR==2{printf \"%.2f\", $3/$2*100}'");
            if (StringUtils.isNotBlank(diskUsage) && isRatio(diskUsage)) {
                host.setServerHostDiskUsage(toRatio(diskUsage));
            }
            // 系统版本（/etc/release 首行）
            String os = execQuiet(handle, "head -1 /etc/release");
            if (StringUtils.isNotBlank(os)) {
                host.setServerHostOsVersion(os.trim());
            }
            // 架构（uname -p）
            String arch = execQuiet(handle, "uname -p");
            if (StringUtils.isNotBlank(arch)) {
                host.setServerHostOsArch(arch.trim());
            }
            // 内网 IP（ifconfig -a 首个全局 IPv4）
            String ip = execQuiet(handle, "ifconfig -a 2>/dev/null | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | head -1");
            if (StringUtils.isNotBlank(ip)) {
                host.setServerHostPrivateIp(ip.trim());
            }
            return ReturnResult.ok(host, "Solaris 硬件探测成功");
        } catch (Exception e) {
            log.warn("[detect] Solaris 探测失败 hostId={} address={}: {}",
                    host.getId(), host.getServerHostAddress(), e.getMessage());
            return ReturnResult.error("Solaris 硬件探测失败: " + e.getMessage());
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
        }
    }

    /**
    * 判断字符串是否为可解析的百分比数字（非空且为数字或小数）
    *
    * @param value 待判断字符串
    * @return 是否可解析
     */
    private boolean isRatio(String value) {
        return StringUtils.isNotBlank(value) && NumberUtils.isNumber(value);
    }

    /**
    * 将百分比字符串转为 Double（四舍五入保留两位小数），解析失败返回 null
    *
    * @param value 百分比字符串
    * @return Double 或 null
     */
    private Double toRatio(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
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
            log.debug("[detect] Solaris 命令执行失败 {}: {}", command, e.getMessage());
            return null;
        }
    }
}
