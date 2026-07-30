package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.server.support.entity.ServerHost;

/**
 * 服务发现 SPI 管理器。
 *
 * <p>依据主机平台将服务发现委派给 windows/unix 实现，便于后续扩展 mac。
 *
 * @author CH
 */
public class ServerServiceDiscoveryManager {

    private final ServerHostProtocolManager protocolManager;
    private final ServiceProvider<ServerServiceDiscoverySpi> provider =
            ServiceProvider.of(ServerServiceDiscoverySpi.class);

    public ServerServiceDiscoveryManager(ServerHostProtocolManager protocolManager) {
        this.protocolManager = protocolManager;
    }

    /**
     * 解析主机对应的服务发现 SPI。
     *
     * @param host 服务器主机
     * @return 服务发现 SPI
     */
    public ServerServiceDiscoverySpi resolve(ServerHost host) {
        String key = protocolManager.isWindows(host) ? "windows" : "unix";
        ServerServiceDiscoverySpi spi = provider.getExtension(key);
        if (spi != null) {
            return spi;
        }
        ServerServiceDiscoverySpi fallback = provider.getExtension("unix");
        if (fallback == null) {
            throw new IllegalStateException("未找到服务发现 SPI: " + key);
        }
        return fallback;
    }
}

