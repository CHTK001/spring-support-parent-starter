package com.chua.starter.monitor.support.nginx.spi;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.monitor.support.spi.Platforms;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nginx 运行时路由器：把「一个 Nginx 数据源」解析成
 * <strong>平台 + 命令提供者 + 执行通道</strong> 三者一致的组合。
 *
 * <p><strong>为什么需要它</strong>：本仓库既有的通道扩展点
 * （{@code SystemInfoProvider}）是以主机台账 {@code MsServerHost} 为键、凭据取自台账的；
 * 而 Nginx 数据源是独立的一条线（自带 host/port/username/password，且没有平台字段）。
 * 直接复用通道会要求先把数据源登记成主机，属于另一件事。所以这里单列一层适配：
 * 通道与命令文本仍走 SPI，只是解析键换成数据源。</p>
 *
 * <p><strong>平台怎么定</strong>：数据源没有平台字段，只能探测；但本机执行时不需要探测
 * ——控制台自己的 OS 就是答案（{@code LocalNginxExecutor.localPlatform()}），且这是
 * 权威答案，不该被探测结果覆盖。探测结果按 (host,port,user) 缓存，避免每次请求多一个
 * SSH 来回。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class NginxRuntimeRouter {

    /**
     * 命令文本扩展点
     */
    private final ServiceProvider<NginxCommandProvider> commandExtensions =
            ServiceProvider.of(NginxCommandProvider.class);

    /**
     * 执行通道扩展点
     */
    private final ServiceProvider<NginxExecutor> executorExtensions =
            ServiceProvider.of(NginxExecutor.class);

    /**
     * 平台探测缓存：键为 {@code host:port:user}。
     *
     * <p>只缓存「探测成功」的结果。探测失败不缓存——否则一次网络抖动会把整台机器
     * 永久钉在错误的平台上，而用户改完网络也不会自愈。</p>
     */
    private final Map<String, String> platformCache = new ConcurrentHashMap<>();

    /**
     * 一次执行所需的全部上下文。
     *
     * @param platform 平台标识
     * @param commands 命令文本提供者
     * @param executor 执行通道
     * @param target   执行目标（命令要送到哪里）
     */
    public record Runtime(String platform, NginxCommandProvider commands, NginxExecutor executor,
                          NginxExecutor.Target target) {

        /**
         * 用本运行时执行一条命令。
         *
         * @param command 完整命令行（由 {@link #commands} 拼出）
         * @param seconds 超时秒数
         * @return 执行结果
         */
        public NginxExecutor.ExecResult exec(String command, long seconds) {
            return executor.exec(target, command, seconds);
        }

        /**
         * 是否 Windows。
         *
         * @return 是返回 true
         */
        public boolean windows() {
            return Platforms.WINDOWS.equals(platform);
        }
    }

    /**
     * 解析数据源对应的运行时。
     *
     * @param datasource 数据源
     * @return 平台 + 命令提供者 + 执行通道
     */
    public Runtime resolve(DataSourceVO datasource) {
        if (datasource == null) {
            throw new IllegalStateException("Nginx 数据源不存在");
        }
        boolean local = NginxExecutor.Target.isLocalMarker(datasource.host());
        int port = datasource.port() == null ? 0 : datasource.port();
        String user = datasource.username() == null ? "" : datasource.username();
        NginxExecutor.Target target = new NginxExecutor.Target(
                datasource.host(), port, user, datasource.password(), local);
        NginxExecutor executor = pickExecutor(target);
        // 本机执行时不探测：控制台自身的 OS 就是权威答案，探测结果反而可能被 SSH 误判覆盖。
        // 非本机才需要探测，因为数据源没有平台字段。
        String platform = local
                ? com.chua.starter.monitor.support.nginx.spi.impl.LocalNginxExecutor.localPlatform()
                : detectPlatform(datasource, executor, target);
        // pickCommands 在平台无实现时会抛异常，这本身就是一道一致性校验：
        // 命令文本与执行通道是两个独立扩展点，配错不会编译失败，只会在运行时报语法错。
        NginxCommandProvider commands = pickCommands(platform);
        return new Runtime(platform, commands, executor, target);
    }

    /**
     * 选择执行通道：按 order 降序取第一个支持该目标的实现。
     *
     * @param target 目标
     * @return 通道
     * @throws IllegalStateException 无可用通道
     */
    private NginxExecutor pickExecutor(NginxExecutor.Target target) {
        List<String> tried = new ArrayList<>();
        for (NginxExecutor executor : orderedExecutors()) {
            try {
                if (executor.supports(target)) {
                    return executor;
                }
            } catch (Exception ignored) {
                // 单个通道判定异常不影响后续匹配
            }
            tried.add(executor.getClass().getSimpleName());
        }
        throw new IllegalStateException("无可用 Nginx 执行通道 for " + target + ", 已尝试: " + tried);
    }

    /**
     * 选择命令文本提供者：按平台匹配。
     *
     * @param platform 平台
     * @return 提供者
     * @throws IllegalStateException 该平台无实现
     */
    private NginxCommandProvider pickCommands(String platform) {
        for (NginxCommandProvider provider : orderedCommands()) {
            try {
                if (provider.supports(platform)) {
                    return provider;
                }
            } catch (Exception ignored) {
                // 忽略单个实现的判定异常
            }
        }
        throw new IllegalStateException("平台 [" + platform + "] 没有 Nginx 命令文本实现，"
                + "请在 META-INF/extensions/" + NginxCommandProvider.SPI_NAME + " 注册");
    }

    /**
     * 探测远端平台。
     *
     * <p>依次尝试各实现的自证命令，第一条能判定平台的就采纳。探测全部失败时退回
     * LINUX 并告警：既有数据源全是 Linux，退回比直接抛异常更能保住可用性，但必须
     * 留下日志，否则 Windows 连不上时会表现成「命令莫名其妙语法错」。</p>
     *
     * @param datasource 数据源
     * @param executor   通道
     * @param target     执行目标（探测命令也要送到同一个目标上）
     * @return 平台
     */
    private String detectPlatform(DataSourceVO datasource, NginxExecutor executor, NginxExecutor.Target target) {
        String key = cacheKey(datasource);
        String cached = platformCache.get(key);
        if (cached != null) {
            return cached;
        }
        for (NginxCommandProvider provider : orderedCommands()) {
            String probe = provider.probePlatform();
            if (probe == null || probe.isBlank()) {
                continue;
            }
            try {
                NginxExecutor.ExecResult result = executor.exec(target, probe, 15);
                String platform = provider.parsePlatform(result.output());
                if (platform != null && !platform.isEmpty()) {
                    platformCache.put(key, platform);
                    log.info("[nginx] 平台探测命中 {} -> {}（数据源 {}）", provider.name(), platform, datasource.name());
                    return platform;
                }
            } catch (Exception e) {
                log.debug("[nginx] 平台探测失败 provider={} err={}", provider.name(), e.getMessage());
            }
        }
        log.warn("[nginx] 平台探测全部失败，按 LINUX 处理（数据源 {}，主机 {}）。"
                + "若目标是 Windows，请确认本机执行模式或 SSH 可达性。", datasource.name(), datasource.host());
        return Platforms.LINUX;
    }

    /**
     * 平台缓存键。
     *
     * @param datasource 数据源
     * @return 键
     */
    private static String cacheKey(DataSourceVO datasource) {
        return (datasource.host() == null ? "" : datasource.host())
                + ":" + (datasource.port() == null ? 0 : datasource.port())
                + ":" + (datasource.username() == null ? "" : datasource.username());
    }

    /**
     * 使某数据源的平台缓存失效（改配置后调用）。
     *
     * @param datasource 数据源
     */
    public void invalidate(DataSourceVO datasource) {
        if (datasource != null) {
            platformCache.remove(cacheKey(datasource));
        }
    }

    /**
     * 按优先级取回全部命令文本实现。
     *
     * @return 列表
     */
    private List<NginxCommandProvider> orderedCommands() {
        List<NginxCommandProvider> providers = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : commandExtensions.getPriorityServiceDefinitions()) {
            NginxCommandProvider provider = definition.getObj(commandExtensions.getServiceAutowire());
            if (provider != null && seen.add(provider.getClass())) {
                providers.add(provider);
            }
        }
        if (providers.isEmpty()) {
            log.warn("NginxCommandProvider 扩展点无任何可用实现");
        }
        return providers;
    }

    /**
     * 按优先级取回全部执行通道实现。
     *
     * @return 列表
     */
    private List<NginxExecutor> orderedExecutors() {
        List<NginxExecutor> executors = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        for (ServiceDefinition definition : executorExtensions.getPriorityServiceDefinitions()) {
            NginxExecutor executor = definition.getObj(executorExtensions.getServiceAutowire());
            if (executor != null && seen.add(executor.getClass())) {
                executors.add(executor);
            }
        }
        if (executors.isEmpty()) {
            log.warn("NginxExecutor 扩展点无任何可用实现");
        }
        return executors;
    }
}
