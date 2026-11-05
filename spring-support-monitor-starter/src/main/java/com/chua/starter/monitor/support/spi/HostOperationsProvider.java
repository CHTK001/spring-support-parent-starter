package com.chua.starter.monitor.support.spi;

import com.chua.starter.server.support.entity.MsServerHost;

/**
 * 主机平台能力集提供者（SPI）：按目标主机真实平台解析 {@link HostOperations}。
 *
 * <p>平台差异只有这一个入口。新增平台（Windows 之外的系统、或未来新增的国产系统）
 * 只需要写一个实现类并登记到
 * {@code META-INF/extensions/com.chua.starter.monitor.support.spi.HostOperationsProvider}，
 * 通道实现（Local / Agent / SSH / WinRM）与调用方一行都不用改，也不需要任何
 * {@code if / switch (platform)} 判断——那正是本接口存在的理由。</p>
 *
 * <p>解析口径：先按 {@link #platforms()} 精确匹配；都不匹配时交给
 * {@link #fallback()} 为 {@code true} 的实现（POSIX 兜底）。同一平台出现多个实现时，
 * 按 {@code @Spi#order} 由小到大取第一个，与扩展点的优先级语义一致。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface HostOperationsProvider {

    /**
     * 本实现负责的平台标识。
     *
     * <p>取值域与主机台账列 {@code ms_server_host.ms_server_host_os_type} 一致
     * （{@code WINDOWS} / {@code LINUX} / {@code MACOS} / {@code FREEBSD} / {@code AIX} / {@code SOLARIS}），
     * 判定时统一经 {@code Platforms.normalize} 归一，大小写与别名不影响匹配。</p>
     *
     * @return 平台标识数组，兜底实现返回空数组
     */
    String[] platforms();

    /**
     * 是否为兜底实现：所有没有被其它实现显式声明的平台都交给它。
     *
     * @return 兜底实现返回 true
     */
    default boolean fallback() {
        return false;
    }

    /**
     * 建出目标主机的平台能力集。
     *
     * @param channel 目标主机所在通道
     * @param host    目标主机
     * @return 平台能力集
     */
    HostOperations create(SystemInfoProvider channel, MsServerHost host);

    /**
     * 管控机本机执行命令时的 shell 首部（含方言修正）。
     *
     * <p>本机采集不做跨平台命令翻译，只解决「用哪个 shell 跑这条命令」，
     * 因此放在平台实现里而不是散落到各采集方法中。</p>
     *
     * @param cmd 原生命令
     * @return 可直接交给 {@link ProcessBuilder} 的 argv
     */
    default String[] localShell(String cmd) {
        return new String[]{"/bin/sh", "-c", cmd};
    }
}
