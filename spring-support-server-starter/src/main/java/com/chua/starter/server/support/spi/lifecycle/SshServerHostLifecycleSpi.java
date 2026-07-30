package com.chua.starter.server.support.spi.lifecycle;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.spi.ServerHostLifecycleSpi;
import org.springframework.util.StringUtils;

/**
 * SSH 生命周期 SPI。
 *
 * <p>约束 SSH 连接至少提供 host 与 username，并在端口缺失时补齐 22。
 *
 * @author CH
 */
@Spi({"ssh", "SSH"})
public class SshServerHostLifecycleSpi implements ServerHostLifecycleSpi {

    /**
     * 归一化 SSH 配置。
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
        if (host.getPort() == null || host.getPort() <= 0) {
            host.setPort(22);
        }
    }

    /**
     * 校验 SSH 配置。
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
        if (!StringUtils.hasText(host.getHost())) {
            throw new IllegalStateException("SSH 主机地址不能为空");
        }
        if (!StringUtils.hasText(host.getUsername())) {
            throw new IllegalStateException("SSH 用户名不能为空");
        }
    }
}

