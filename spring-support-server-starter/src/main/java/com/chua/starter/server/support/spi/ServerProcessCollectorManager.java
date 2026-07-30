package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.server.support.entity.ServerHost;

/**
 * 进程采集 SPI 管理器。
 *
 * <p>根据 serverType 定位对应采集实现，缺省回落 local。
 *
 * @author CH
 */
public class ServerProcessCollectorManager {

    private final ServiceProvider<ServerProcessCollectorSpi> provider =
            ServiceProvider.of(ServerProcessCollectorSpi.class);

    /**
     * 解析主机对应的进程采集 SPI。
     *
     * @param host 服务器主机
     * @return 进程采集 SPI
     */
    public ServerProcessCollectorSpi resolve(ServerHost host) {
        String type = ServerTypeResolver.resolve(host);
        ServerProcessCollectorSpi spi = provider.getExtension(type);
        if (spi != null) {
            return spi;
        }
        ServerProcessCollectorSpi local = provider.getExtension("local");
        if (local == null) {
            throw new IllegalStateException("未找到进程采集 SPI: local");
        }
        return local;
    }
}

