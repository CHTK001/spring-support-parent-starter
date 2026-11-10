package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasourcehub.support.spi.Platforms;
import com.chua.starter.monitor.support.spi.HostOperations;
import com.chua.starter.monitor.support.spi.HostOperationsProvider;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.server.support.entity.MsServerHost;

/**
 * macOS（Darwin）平台能力集提供者。
 *
 * <p>为什么必须单独登记：macOS 曾经落在 {@link PosixHostOperationsProvider} 上，
 * 而那个实现是 Linux 命令口径（systemd / NetworkManager / iptables / {@code /proc}），
 * 在 macOS 上要么必失败、要么「写了文件但不生效还报成功」。
 * 精确匹配的优先级高于兜底实现（见 {@code HostOperationsRegistry}），
 * 因此这里登记后不再需要任何平台分支判断。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi(value = "macos", order = 20)
public class MacHostOperationsProvider implements HostOperationsProvider {

    @Override
    public String[] platforms() {
        return new String[]{Platforms.MACOS};
    }

    @Override
    public HostOperations create(SystemInfoProvider channel, MsServerHost host) {
        return new MacHostOperations(channel, host);
    }
}
