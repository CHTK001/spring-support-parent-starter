package com.chua.starter.server.support.spi.protocol;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.network.protocol.ClientSetting;
import com.chua.common.support.network.protocol.client.FileClient;
import com.chua.ssh.support.client.SftpClient;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.spi.ServerHostProtocolSpi;
import org.springframework.util.StringUtils;

/**
 * SSH 协议 SPI。
 */
@Spi({"ssh", "SSH"})
public class SshServerHostProtocolSpi implements ServerHostProtocolSpi {

    @Override
    public int getDefaultPort() {
        return 22;
    }

    @Override
    public String getDefaultHost(ServerHost host) {
        return null;
    }

    @Override
    public boolean isWindows(ServerHost host) {
        return host != null
                && StringUtils.hasText(host.getOsType())
                && host.getOsType().trim().toLowerCase().contains("win");
    }

    @Override
    public FileClient createFileClient(ServerHost host) {
        return new SftpClient(ClientSetting.builder()
                .host(host.getHost())
                .port(host.getPort() == null ? getDefaultPort() : host.getPort())
                .username(host.getUsername())
                .password(host.getPassword())
                .build());
    }
}
