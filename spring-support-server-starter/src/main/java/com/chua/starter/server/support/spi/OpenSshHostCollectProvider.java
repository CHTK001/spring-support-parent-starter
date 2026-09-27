package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.enums.HostCollectType;

/**
 * OpenSSH 采集 提供者（调度）
 * <p>
 * 显式选择 OpenSSH(22) 通道时使用，采集逻辑与 {@link ScheduledHostCollectProvider} 完全一致
 * （经 SSH 连接目标主机执行探测），仅以独立 {@link HostCollectType#OPENSSH} 注册，
 * 便于调度器按用户所选采集方式严格路由，避免与 AGENT 通道混用。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "openssh", order = 110)
public class OpenSshHostCollectProvider extends ScheduledHostCollectProvider {

    @Override
    public HostCollectType type() {
        return HostCollectType.OPENSSH;
    }
}
