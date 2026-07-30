package com.chua.starter.server.support.spi.lifecycle;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.spi.ServerHostLifecycleSpi;
import org.springframework.util.StringUtils;

/**
 * WinRM 生命周期 SPI。
 *
 * <p>约束 WinRM 连接提供 host/username/password，并在端口缺失时补齐 5985。
 *
 * @author CH
 */
@Spi({"winrm", "WINRM"})
public class WinRmServerHostLifecycleSpi implements ServerHostLifecycleSpi {

    /**
     * 归一化 WinRM 配置。
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
            host.setPort(5985);
        }
        // WinRM 不依赖 SSH 私钥，统一清空避免脏数据干扰。
        host.setPrivateKey(null);
    }

    /**
     * 校验 WinRM 配置。
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
            throw new IllegalStateException("WinRM 主机地址不能为空");
        }
        if (!StringUtils.hasText(host.getUsername())) {
            throw new IllegalStateException("WinRM 用户名不能为空");
        }
        if (!StringUtils.hasText(host.getPassword())) {
            throw new IllegalStateException("WinRM 密码不能为空");
        }
    }
}

