package com.chua.starter.server.support.spi.lifecycle;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.spi.ServerHostLifecycleSpi;
import org.springframework.util.StringUtils;

/**
 * LOCAL 生命周期 SPI。
 *
 * <p>本机模式不依赖账号密码与私钥，保存时统一清空认证字段。
 *
 * @author CH
 */
@Spi({"local", "LOCAL"})
public class LocalServerHostLifecycleSpi implements ServerHostLifecycleSpi {

    /**
     * 归一化本机配置。
     *
     * @param host 服务器主机对象
     */
    @Override
    /**
     *
     */
    public void normalizeBeforeSave(ServerHost host) {
        if (host == null) {
            return;
        }
        host.setUsername(null);
        host.setPassword(null);
        host.setPrivateKey(null);
        if (!StringUtils.hasText(host.getHost())) {
            host.setHost("127.0.0.1");
        }
        if (host.getPort() == null) {
            host.setPort(0);
        }
    }

    /**
     * 校验本机配置。
     *
     * @param host 服务器主机对象
     */
    @Override
    /**
     *
     */
    public void validateForSave(ServerHost host) {
        if (host == null || !StringUtils.hasText(host.getServerName())) {
            throw new IllegalStateException("服务器名称不能为空");
        }
    }
}

