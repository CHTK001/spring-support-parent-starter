package com.chua.starter.server.support.spi.process;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.spi.ServerProcessCollectorSpi;
import com.chua.starter.server.support.util.ServerCommandSupport;
import org.springframework.util.StringUtils;

/**
 * 本机进程采集 SPI 实现。
 *
 * <p>根据本机操作系统类型选择对应的命令构建方式。
 *
 * @author CH
 */
@Spi("local")
public class LocalServerProcessCollectorSpi implements ServerProcessCollectorSpi {

    @Override
    public String buildListCommand(ServerHost host, int queryWindow) {
        if (windowsParser(host)) {
            return WindowsProcessCommandSupport.windowsListCommand(queryWindow);
        }
        return UnixProcessCommandSupport.unixListCommand(queryWindow);
    }

    @Override
    public String buildTerminateCommand(ServerHost host, Long pid, boolean force) {
        if (windowsParser(host)) {
            return WindowsProcessCommandSupport.windowsTerminateCommand(pid, force);
        }
        return UnixProcessCommandSupport.unixTerminateCommand(pid, force);
    }

    @Override
    public boolean windowsParser(ServerHost host) {
        String osType = host == null || !StringUtils.hasText(host.getOsType())
                ? System.getProperty("os.name")
                : host.getOsType();
        return ServerCommandSupport.isWindows(osType);
    }

    @Override
    public boolean supportsLocalFallback(ServerHost host) {
        return windowsParser(host);
    }
}
