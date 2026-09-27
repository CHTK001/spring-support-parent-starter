package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.enums.ShellDialect;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ProcessSummaryVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.spi.Platforms;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;

/**
 * 统一 SSH 系统信息 Provider：一个 SPI 实现同时覆盖 Linux / macOS / Windows。
 *
 * <p>协议层（SSH/SFTP、22 端口、认证）三端完全一致，差异仅在连上之后的默认外壳：
 * POSIX 走 bash/sh，Windows 显式拉 PowerShell。本门面按主机平台选择内部委托，
 * 路由层只看到一个 {@code ssh} 通道，新增平台只需扩展委托。</p>
 *
 * <ul>
 *   <li>{@link PosixSshDelegate}：Linux / macOS（bash 命令集）；</li>
 *   <li>{@link WindowsSshDelegate}：Windows（PowerShell EncodedCommand + SFTP）。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "ssh", order = 200)
public class SshProvider implements SystemInfoProvider {

    /**
     * POSIX（Linux/macOS）采集委托
     */
    private final PosixSshDelegate posix = new PosixSshDelegate();

    /**
     * Windows 采集委托
     */
    private final WindowsSshDelegate windows = new WindowsSshDelegate();

    /**
     * 按主机平台选择采集委托。
     *
     * @param host 目标主机
     * @return Windows 主机返回 Windows 委托，其余返回 POSIX 委托
     */
    private SystemInfoProvider delegate(MsServerHost host) {
        return windows.supports(host) ? windows : posix;
    }

    @Override
    public boolean supports(MsServerHost host) {
        if (host == null) {
            return false;
        }
        // 采集通道严格跟随用户所选方式：仅 OpenSSH / 定时SSH 才走 SSH，
        // 选了 AGENT（未装）/PROMETHEUS/NONE 时不再静默回退到 SSH
        String mode = host.getServerHostCollectMode();
        boolean sshMode = "OPENSSH".equalsIgnoreCase(mode) || "SCHEDULED".equalsIgnoreCase(mode);
        return sshMode && (posix.supports(host) || windows.supports(host));
    }

    @Override
    public String platform(MsServerHost host) {
        return delegate(host).platform(host);
    }

    @Override
    public HostInfoVO hostInfo(MsServerHost host) {
        return delegate(host).hostInfo(host);
    }

    @Override
    public List<ProcessRowVO> processes(MsServerHost host, int page, int pageSize) {
        return delegate(host).processes(host, page, pageSize);
    }

    @Override
    public ProcessSummaryVO processSummary(MsServerHost host) {
        return delegate(host).processSummary(host);
    }

    @Override
    public List<ServiceRowVO> services(MsServerHost host, int page, int pageSize) {
        return delegate(host).services(host, page, pageSize);
    }

    @Override
    public List<DiskRowVO> disks(MsServerHost host) {
        return delegate(host).disks(host);
    }

    @Override
    public List<NetworkInterfaceRowVO> networkInterfaces(MsServerHost host) {
        return delegate(host).networkInterfaces(host);
    }

    @Override
    public List<NetProcessRowVO> netProcesses(MsServerHost host) {
        return delegate(host).netProcesses(host);
    }

    @Override
    public List<FileEntryVO> files(MsServerHost host, String path) {
        return delegate(host).files(host, path);
    }

    @Override
    public boolean uploadFile(MsServerHost host, String remotePath, InputStream data, long size) {
        return delegate(host).uploadFile(host, remotePath, data, size);
    }

    @Override
    public InputStream downloadFile(MsServerHost host, String remotePath) {
        return delegate(host).downloadFile(host, remotePath);
    }

    @Override
    public String exec(MsServerHost host, String cmd) {
        return delegate(host).exec(host, cmd);
    }

    @Override
    public ShellDialect shellDialect(MsServerHost host) {
        // WindowsSshDelegate 用 powershell -EncodedCommand 承载命令，不是 cmd.exe
        return Platforms.isWindows(platform(host))
                ? ShellDialect.POWERSHELL
                : SystemInfoProvider.super.shellDialect(host);
    }

    @Override
    public List<EnvVarVO> env(MsServerHost host) {
        return delegate(host).env(host);
    }

    @Override
    public boolean mkdir(MsServerHost host, String path) {
        return delegate(host).mkdir(host, path);
    }

    @Override
    public boolean delete(MsServerHost host, String path) {
        return delegate(host).delete(host, path);
    }

    @Override
    public boolean rename(MsServerHost host, String from, String to) {
        return delegate(host).rename(host, from, to);
    }
}
