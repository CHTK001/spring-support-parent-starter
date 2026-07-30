package com.chua.starter.server.support.spi.process;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.spi.ServerProcessCollectorSpi;

/**
 * SSH 进程采集 SPI 实现。
 *
 * <p>通过 SSH 远程采集 Linux 进程信息。
 *
 * @author CH
 */
@Spi("ssh")
public class SshServerProcessCollectorSpi implements ServerProcessCollectorSpi {

    @Override
    public String buildListCommand(ServerHost host, int queryWindow) {
        return UnixProcessCommandSupport.unixListCommand(queryWindow);
    }

    @Override
    public String buildTerminateCommand(ServerHost host, Long pid, boolean force) {
        return UnixProcessCommandSupport.unixTerminateCommand(pid, force);
    }

    @Override
    public boolean windowsParser(ServerHost host) {
        return false;
    }
}
