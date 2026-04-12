package com.chua.starter.server.support.spi.protocol;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.network.protocol.ClientSetting;
import com.chua.common.support.network.protocol.client.FileClient;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.spi.ServerHostProtocolSpi;
import com.chua.winrm.support.client.WinRmFileClient;

/**
 * WinRM 协议 SPI。
 */
@Spi({"winrm", "WINRM"})
public class WinRmServerHostProtocolSpi implements ServerHostProtocolSpi {

    @Override
    public int getDefaultPort() {
        return 5985;
    }

    @Override
    public String getDefaultHost(ServerHost host) {
        return null;
    }

    @Override
    public boolean isWindows(ServerHost host) {
        return true;
    }

    @Override
    public FileClient createFileClient(ServerHost host) {
        return new WinRmFileClient(ClientSetting.builder()
                .host(host.getHost())
                .port(host.getPort() == null ? getDefaultPort() : host.getPort())
                .username(host.getUsername())
                .password(host.getPassword())
                .build());
    }
}
