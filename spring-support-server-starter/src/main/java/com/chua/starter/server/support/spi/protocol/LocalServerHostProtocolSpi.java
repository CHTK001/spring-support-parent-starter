package com.chua.starter.server.support.spi.protocol;

import com.chua.common.support.core.annotation.Spi;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.spi.ServerHostProtocolSpi;
import org.springframework.util.StringUtils;

/**
 * 本机协议 SPI。
 */
@Spi({"local", "LOCAL"})
public class LocalServerHostProtocolSpi implements ServerHostProtocolSpi {

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public int getDefaultPort() {
        return 0;
    }

    @Override
    public String getDefaultHost(ServerHost host) {
        return "127.0.0.1";
    }

    @Override
    public boolean isWindows(ServerHost host) {
        return host != null
                && StringUtils.hasText(host.getOsType())
                && host.getOsType().trim().toLowerCase().contains("win");
    }
}
