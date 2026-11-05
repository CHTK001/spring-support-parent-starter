package com.chua.starter.monitor.support.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.datasourcehub.support.spi.Platforms;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 平台能力集路由器：把「目标主机真实平台」解析成 {@link HostOperations} 实现。
 *
 * <p>本类只做路由，不写任何平台命令，也不出现平台分支。解析顺序：
 * 精确匹配的平台实现优先，其次是 {@code fallback()} 为 true 的兜底实现；
 * 两者都没有时抛错而不是悄悄用错平台（用错平台的代价是「命令看起来执行了但没生效」）。</p>
 *
 * <p>扩展点实例用 {@link ServiceProvider#collect()} 取，不依赖 Spring 容器：
 * 本类会被 {@link SystemInfoProvider} 的默认动作方法静态调用，那些默认方法可能在
 * 容器装配期之外被执行（单元测试、探针）</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public final class HostOperationsRegistry {

    /**
     * 平台能力集扩展点
     */
    private static final ServiceProvider<HostOperationsProvider> EXTENSIONS =
            ServiceProvider.of(HostOperationsProvider.class);

    /**
     * 扩展点实例缓存，首次解析时构建
     */
    private static volatile List<HostOperationsProvider> providers;

    /**
     * 工具类，禁止实例化
     */
    private HostOperationsRegistry() {
    }

    /**
     * 解析目标主机的平台能力集。
     *
     * @param channel 目标主机所在通道
     * @param host    目标主机
     * @return 平台能力集，永不为 null
     * @throws IllegalStateException 扩展点里既没有匹配实现也没有兜底实现
     */
    public static HostOperations resolve(SystemInfoProvider channel, MsServerHost host) {
        String platform = platformOf(channel, host);
        HostOperationsProvider fallback = null;
        for (HostOperationsProvider provider : providers()) {
            if (provider.fallback()) {
                if (fallback == null) {
                    fallback = provider;
                }
                continue;
            }
            for (String candidate : provider.platforms()) {
                if (platform.equals(Platforms.normalize(candidate))) {
                    log.debug("[host-ops] 平台 {} 命中实现 {}", platform, provider.getClass().getSimpleName());
                    return provider.create(channel, host);
                }
            }
        }
        if (fallback != null) {
            log.debug("[host-ops] 平台 {} 无精确实现，交给兜底 {}", platform, fallback.getClass().getSimpleName());
            return fallback.create(channel, host);
        }
        throw new IllegalStateException("没有可用的主机平台能力集实现，平台=" + platform);
    }

    /**
     * 管控机本机执行命令的 shell argv。
     *
     * @param platform 本机平台标识
     * @param cmd      原生命令
     * @return 可直接交给 {@link ProcessBuilder} 的 argv
     */
    public static String[] localShell(String platform, String cmd) {
        HostOperationsProvider provider = providerOf(platform);
        return provider == null ? new String[]{"/bin/sh", "-c", cmd} : provider.localShell(cmd);
    }

    /**
     * 目标主机路径分隔符。
     *
     * @param platform 目标主机平台标识
     * @return 路径分隔符
     */
    public static char pathSeparator(String platform) {
        HostOperationsProvider provider = providerOf(platform);
        return provider == null ? '/' : provider.pathSeparator();
    }

    /**
     * 目标主机 shell 方言。
     *
     * @param platform 目标主机平台标识
     * @return shell 方言，未命中时按 POSIX sh
     */
    public static com.chua.starter.monitor.support.enums.ShellDialect shellDialect(String platform) {
        HostOperationsProvider provider = providerOf(platform);
        return provider == null
                ? com.chua.starter.monitor.support.enums.ShellDialect.SH
                : provider.shellDialect();
    }

    /**
     * 本机平台的专属服务枚举（Windows 走 {@code Win32_Service}，POSIX 为 null 由 OSHI 兜底）。
     *
     * @return 服务行，本平台不提供时为 null
     */
    public static java.util.List<com.chua.starter.monitor.support.model.ServiceRowVO> localServices() {
        HostOperationsProvider provider = local();
        return provider == null ? null : provider.localServices();
    }

    /**
     * 本机平台的专属端口占用采集。
     *
     * @return 端口占用行，本平台不提供时为 null
     */
    public static java.util.List<com.chua.starter.monitor.support.model.NetProcessRowVO> localNetConnections() {
        HostOperationsProvider provider = local();
        return provider == null ? null : provider.localNetConnections();
    }

    /**
     * 管控机本机平台对应的实现。
     *
     * @return 命中的实现，未登记任何实现时为 null
     */
    private static HostOperationsProvider local() {
        return providerOf(Platforms.normalize(System.getProperty("os.name")));
    }

    /**
     * 按平台标识取实现：精确匹配优先，否则取兜底实现。
     *
     * @param platform 平台标识，可为 null
     * @return 命中的实现，两者都没有时为 null
     */
    private static HostOperationsProvider providerOf(String platform) {
        String key = Platforms.normalize(platform);
        HostOperationsProvider fallback = null;
        for (HostOperationsProvider provider : providers()) {
            if (provider.fallback()) {
                if (fallback == null) {
                    fallback = provider;
                }
                continue;
            }
            for (String candidate : provider.platforms()) {
                if (key.equals(Platforms.normalize(candidate))) {
                    return provider;
                }
            }
        }
        return fallback;
    }

    /**
     * 目标主机真实平台：台账填了用台账，没填按管控机自身平台。
     *
     * @param channel 目标主机所在通道
     * @param host    目标主机
     * @return 归一后的平台标识，可能是空串（未知平台，走兜底实现）
     */
    public static String platformOf(SystemInfoProvider channel, MsServerHost host) {
        String platform = channel == null ? "" : channel.platform(host);
        if (platform == null || platform.isEmpty()) {
            platform = Platforms.normalize(System.getProperty("os.name"));
        }
        return platform;
    }

    /**
     * 取扩展点实例，必要时构建。
     *
     * @return 不可变实例列表
     */
    private static List<HostOperationsProvider> providers() {
        List<HostOperationsProvider> current = providers;
        if (current != null) {
            return current;
        }
        synchronized (HostOperationsRegistry.class) {
            if (providers == null) {
                List<HostOperationsProvider> list = new ArrayList<>(EXTENSIONS.collect());
                if (list.isEmpty()) {
                    log.warn("[host-ops] HostOperationsProvider 扩展点无任何实现");
                }
                providers = Collections.unmodifiableList(list);
            }
            return providers;
        }
    }
}
