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
     * 已解析的运行时（<strong>不含凭据</strong>）：平台 + 命令文本 + 通道。
     *
     * <p>缓存的理由：一次请求里 {@code commands(ds)} 与 {@code exec(ds,…)} 都会调
     * {@link #resolve}，若每次都重新探测平台，不可达主机上每个调用点都要白等一次连接超时。
     * 只缓存「解析结果」而把 {@link NginxExecutor.Target}（含口令）每次新建，
     * 是为了避免口令在缓存里滞留、改了口令却不生效。</p>
     */
    private record Resolved(String platform, NginxCommandProvider commands, NginxExecutor executor) {
    }

    /**
     * 平台解析结果缓存（仅缓存<strong>成功</strong>解析）。
     */
    private final Map<String, Resolved> resolvedCache = new ConcurrentHashMap<>();

    /**
     * 平台探测失败的冷却截止时刻。
     *
     * <p>与成功缓存分开：连接不上时探测必然失败，但「连不上」是可恢复的，
     * 不该像成功的平台结论那样长期钉住。冷却期内直接用兜底平台、不再探测。</p>
     */
    private final Map<String, Long> probeFailUntil = new ConcurrentHashMap<>();

    /**
     * 探测失败冷却时长（毫秒）。与 {@code NginxManageServiceImpl} 的断路器同一量级，
     * 保证「一次请求内 + 紧随其后的请求」都不会重复探测。</p>
     */
    private static final long PROBE_FAIL_COOLDOWN_MILLIS = 120_000L;

    /**
     * 取指定平台对应的命令文本提供者。
     *
     * <p>用于「命令文本天然属于某个平台」的场景：例如 systemd 只存在于 POSIX，
     * 构造 systemctl 命令时就不该按数据源平台去猜——按数据源猜会在 Windows 目标上
     * 取到 sc，而调用方压根不会在 Windows 上走 systemd 分支。</p>
     *
     * @param platform 平台标识
     * @return 命令文本提供者
     */
    public NginxCommandProvider commandsOf(String platform) {
        return pickCommands(platform);
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
        // Target 每次新建（含口令），不进缓存
        NginxExecutor.Target target = new NginxExecutor.Target(
                datasource.host(), port, user, datasource.password(), local);
        String key = cacheKey(datasource);

        Resolved cached = resolvedCache.get(key);
        if (cached != null) {
            return new Runtime(cached.platform(), cached.commands(), cached.executor(), target);
        }

        NginxExecutor executor = pickExecutor(target);
        String platform;
        if (local) {
            // 本机执行：控制台自身 OS 就是权威答案，不探测
            platform = com.chua.starter.monitor.support.nginx.spi.impl.LocalNginxExecutor.localPlatform();
        } else {
            Long failUntil = probeFailUntil.get(key);
            if (failUntil != null && System.currentTimeMillis() < failUntil) {
                // 最近探测失败，冷却期内直接兜底，不再白等一次连接超时
                platform = Platforms.LINUX;
            } else {
                platform = detectPlatform(datasource, executor, target);
                if (platform == null || platform.isEmpty()) {
                    probeFailUntil.put(key, System.currentTimeMillis() + PROBE_FAIL_COOLDOWN_MILLIS);
                    platform = Platforms.LINUX;
                }
            }
        }
        NginxCommandProvider commands = pickCommands(platform);
        // 只有成功判定出平台才进长期缓存；兜底结果按失败处理，到期后重新探测以自愈
        if (local || !Platforms.LINUX.equals(platform) || probeFailUntil.get(key) == null) {
            resolvedCache.put(key, new Resolved(platform, commands, executor));
        }
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
     * @return 探测到的平台；**探测失败返回空串**（由调用方决定兜底与负缓存）
     */
    private String detectPlatform(DataSourceVO datasource, NginxExecutor executor, NginxExecutor.Target target) {
        for (NginxCommandProvider provider : orderedCommands()) {
            String probe = provider.probePlatform();
            if (probe == null || probe.isBlank()) {
                continue;
            }
            try {
                NginxExecutor.ExecResult result = executor.exec(target, probe, 15);
                String platform = provider.parsePlatform(result.output());
                if (platform != null && !platform.isEmpty()) {
                    log.info("[nginx] 平台探测命中 {} -> {}（数据源 {}）", provider.name(), platform, datasource.name());
                    return platform;
                }
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                // 连接类失败要**立即跳出**：连接都断了，再试下一个实现的自证命令
                // 只会再白等一次连接超时（实测两个实现各 10s → 一次探测 20s）。
                // 这不是「命令写错了」，换命令也救不回来。
                if (msg.contains("连接失败") || msg.contains("Timed out") || msg.contains("refused")
                        || msg.contains("不可达") || msg.contains("No route")) {
                    log.warn("[nginx] 平台探测因连接失败中止 provider={} 主机={} err={}",
                            provider.name(), datasource.host(), msg);
                    return "";
                }
                log.debug("[nginx] 平台探测失败 provider={} err={}", provider.name(), msg);
            }
        }
        log.warn("[nginx] 平台探测未命中（数据源 {}，主机 {}）。若目标是 Windows，"
                + "请确认本机执行模式或 SSH 可达性。", datasource.name(), datasource.host());
        return "";
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
     * 使某数据源的解析缓存失效（改配置 / 改口令后调用）。
     *
     * <p>三处都要清：成功解析缓存、探测失败冷却。只清前者会让「刚改完配置仍被判成
     * 不可达」一直持续到冷却自然过期。</p>
     *
     * @param datasource 数据源
     */
    public void invalidate(DataSourceVO datasource) {
        if (datasource != null) {
            String key = cacheKey(datasource);
            resolvedCache.remove(key);
            probeFailUntil.remove(key);
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
