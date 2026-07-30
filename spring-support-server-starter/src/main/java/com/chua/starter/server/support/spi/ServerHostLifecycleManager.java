package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.server.support.entity.ServerHost;

/**
 * 服务器生命周期 SPI 管理器。
 *
 * <p>按 serverType 分发到具体生命周期实现，并提供 local 回落能力。
 *
 * @author CH
 */
public class ServerHostLifecycleManager {

    /**
     * 生命周期 SPI 提供器，负责按协议加载扩展实现。
     */
    private final com.chua.common.support.spi.ServiceProvider<ServerHostLifecycleSpi> provider =
            ServiceProvider.of(ServerHostLifecycleSpi.class);

    /**
     * 归一化主机配置，保存前可补齐默认值与规范字段。
     *
     * @param host 服务器主机对象
     */
    public void normalize(ServerHost host) {
        resolve(host).normalizeBeforeSave(host);
    }

    /**
     * 校验主机配置，校验失败由具体 SPI 抛出异常。
     *
     * @param host 服务器主机对象
     */
    public void validate(ServerHost host) {
        resolve(host).validateForSave(host);
    }

    /**
     * 按协议解析生命周期 SPI，未命中时回落 local。
     *
     * @param host 服务器主机对象
     * @return 生命周期 SPI 实现
     */
    private ServerHostLifecycleSpi resolve(ServerHost host) {
        String type = ServerTypeResolver.resolve(host);
        ServerHostLifecycleSpi spi = provider.getExtension(type);
        if (spi != null) {
            return spi;
        }
        ServerHostLifecycleSpi local = provider.getExtension("local");
        if (local == null) {
            throw new IllegalStateException("未找到服务器生命周期 SPI: local");
        }
        return local;
    }
}
