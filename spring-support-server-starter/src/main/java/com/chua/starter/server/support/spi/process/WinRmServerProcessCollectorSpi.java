package com.chua.starter.server.support.spi.process;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.spi.ServerProcessCollectorSpi;

/**
 * WinRM 进程采集 SPI 实现。
 *
 * <p>通过 WinRM 远程采集 Windows 进程信息。
 *
 * @author CH
 */
@Spi("winrm")
public class WinRmServerProcessCollectorSpi implements ServerProcessCollectorSpi {

    @Override
    public String buildListCommand(ServerHost host, int queryWindow) {
        return WindowsProcessCommandSupport.winrmListCommand(queryWindow);
    }

    @Override
    public String buildTerminateCommand(ServerHost host, Long pid, boolean force) {
        return WindowsProcessCommandSupport.windowsTerminateCommand(pid, force);
    }

    @Override
    public boolean windowsParser(ServerHost host) {
        return true;
    }
}
