package com.chua.starter.scatter.support.configuration;

import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.scatter.Scatter;
import com.chua.common.support.scatter.ScatterBuilder;
import com.chua.common.support.scatter.ScatterServiceDiscovery;
import com.chua.starter.common.support.project.Project;
import com.chua.starter.scatter.support.filter.SelfBalanceFilter;
import com.chua.starter.scatter.support.properties.ScatterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

/**
 * scatter 组网自动装配。
 *
 * <p>装配内容：</p>
 * <ul>
 *   <li>{@link Scatter} — 基于 utils scatter 的去中心化组网（tcp/udp 控制面，无 HTTP），
 *       节点间互访与成员同步走组网端口；随容器启动，关闭前广播 LEAVE 尽快剔除本节点</li>
 *   <li>业务端点登记 — 以 {@code protocol=http}、{@code port=server.port} 另行注册本节点业务实例，
 *       serverId 与组网 self 条目区分开，避免 updateService 相互覆盖；metadata 携带 context-path</li>
 *   <li>{@link SelfBalanceFilter} — self-balance 反向代理 Filter，选中其它实例时转发，
 *       选中自己 / 池内仅自己 / 转发异常时本地放行兜底</li>
 * </ul>
 *
 * <p>条件装配：{@code scatter.enabled=true} 才生效；未配置时本 starter 零装配、零副作用。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@EnableConfigurationProperties(ScatterProperties.class)
@ConditionalOnProperty(prefix = "scatter", name = "enabled", havingValue = "true")
public class ScatterConfiguration {

    /**
     * 解析后的节点规格：把「配置覆盖 → 缺省回落」的推导结果集中为一个不可变值对象，
     * 供 Scatter 装配与 Filter 装配共享同一套 nodeId / serverId / 端口口径。
     *
     * @param nodeId           节点唯一标识（组网 self 条目 serverId 同值）
     * @param host             组网绑定地址
     * @param announceHost     对外通告地址
     * @param port             组网监听端口
     * @param servicePath      服务分组（发现路径）
     * @param group            组网范围（scatterId）
     * @param contextPath      业务上下文路径
     * @param serverPort       业务 HTTP 端口（server.port）
     * @param businessServerId 业务端点 serverId（= nodeId + ":http"，与组网 self 条目区分）
     */
    public record ScatterNodeSpec(String nodeId, String host, String announceHost, int port,
                                  String servicePath, String group, String contextPath,
                                  int serverPort, String businessServerId) {
    }

    /**
     * 解析节点规格：一级配置项可覆盖，未配置时缺省回落。
     *
     * @param properties  scatter 配置
     * @param environment Spring 环境
     * @return 节点规格
     */
    @Bean
    public ScatterNodeSpec scatterNodeSpec(ScatterProperties properties, Environment environment) {
        String appName = environment.getProperty("spring.application.name", "application");
        Project project = Project.getInstance();
        String host = firstNonBlank(properties.getHost(), project.getApplicationHost(), "127.0.0.1");
        int port = properties.getPort() > 0 ? properties.getPort() : findFreePort();
        String announceHost = firstNonBlank(properties.getAnnounceHost(), host);
        String nodeId = firstNonBlank(properties.getNodeId(), appName + "@" + announceHost + ":" + port);
        String servicePath = firstNonBlank(properties.getServicePath(), "/" + appName);
        if (!servicePath.startsWith("/")) {
            servicePath = "/" + servicePath;
        }
        String contextPath = firstNonBlank(properties.getContextPath(),
                environment.getProperty("server.servlet.context-path", ""));
        Integer projectPort = project.getApplicationPort();
        int serverPort = projectPort != null && projectPort > 0
                ? projectPort
                : Integer.parseInt(environment.getProperty("server.port", "8080"));
        return new ScatterNodeSpec(nodeId, host, announceHost, port, servicePath,
                properties.getGroup() == null || properties.getGroup().isBlank()
                        ? "default" : properties.getGroup(),
                contextPath, serverPort, nodeId + ":http");
    }

    /**
     * 创建并启动 scatter 组网，登记业务 HTTP 端点，返回带优雅离网语义的生命周期包装。
     *
     * @param spec       节点规格
     * @param properties scatter 配置
     * @return 已启动的 scatter
     * @throws Exception 启动失败
     */
    @Bean(destroyMethod = "close")
    public Scatter scatter(ScatterNodeSpec spec, ScatterProperties properties) throws Exception {
        ScatterBuilder<?> builder = "udp".equalsIgnoreCase(properties.getProtocol())
                ? ScatterBuilder.udp() : ScatterBuilder.tcp();
        builder.nodeId(spec.nodeId());
        builder.groupId(spec.group());
        builder.host(spec.host());
        builder.announceHost(spec.announceHost());
        builder.port(spec.port());
        builder.servicePath(spec.servicePath());
        builder.autoDiscovery(properties.isAutoDiscovery());
        builder.persistenceEnabled(properties.isPersistenceEnabled());
        if (notBlank(properties.getSubnet())) {
            builder.subnet(properties.getSubnet());
        }
        List<String> seeds = properties.getSeeds();
        if (seeds != null && !seeds.isEmpty()) {
            builder.seeds(seeds);
        }
        if (properties.getAutoDiscoveryInterval() > 0) {
            builder.autoDiscoveryInterval(properties.getAutoDiscoveryInterval());
        }
        if (properties.getHeartbeatInterval() > 0) {
            builder.heartbeatInterval(properties.getHeartbeatInterval());
        }
        if (properties.getFailRemoveCount() > 0) {
            builder.failRemoveCount(properties.getFailRemoveCount());
        }
        if (properties.getTimeoutMillis() > 0) {
            builder.timeoutMillis(properties.getTimeoutMillis());
        }
        if (notBlank(properties.getPersistenceFile())) {
            builder.persistenceFile(properties.getPersistenceFile());
        }

        Scatter delegate = builder.build();
        delegate.start();
        log.info("[Scatter] 组网已启动: nodeId={}, protocol={}, port={}, group={}, servicePath={}",
                spec.nodeId(), properties.getProtocol(), delegate.getPort(), spec.group(), spec.servicePath());

        // 业务端点另行登记：protocol=http、port=业务端口；serverId 与组网 self 条目（serverId=nodeId）区分开，
        // 否则 updateService 按 serverId 去重会把组网条目覆盖掉。self-balance Filter 只按 http 协议选实例。
        Discovery business = Discovery.builder()
                .id(spec.businessServerId())
                .serverId(spec.businessServerId())
                .scatterId(spec.group())
                .protocol("http")
                .host(spec.announceHost())
                .port(spec.serverPort())
                .uriSpec(spec.servicePath())
                .metadata(Map.of(
                        "context-path", spec.contextPath() == null ? "" : spec.contextPath(),
                        "node-id", spec.nodeId(),
                        "self-balance", String.valueOf(properties.isSelfBalance())))
                .build();
        delegate.discovery().registerService(spec.servicePath(), business);
        log.info("[Scatter] 业务端点已注册: http://{}:{}{} (serverId={})",
                spec.announceHost(), spec.serverPort(), spec.contextPath(), spec.businessServerId());

        return new ManagedScatter(delegate);
    }

    /**
     * 注册 self-balance Filter（{@code scatter.self-balance} 缺省开启）。
     *
     * @param scatter    scatter 聚合入口
     * @param spec       节点规格
     * @param properties scatter 配置
     * @return Filter 注册器
     */
    @Bean
    @ConditionalOnProperty(prefix = "scatter", name = "self-balance", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<SelfBalanceFilter> selfBalanceFilter(Scatter scatter, ScatterNodeSpec spec,
                                                                       ScatterProperties properties) {
        SelfBalanceFilter filter = new SelfBalanceFilter(scatter, spec.servicePath(),
                properties.getBalanceStrategy(), spec.businessServerId(), properties.getMaxForwardBodyBytes());
        FilterRegistrationBean<SelfBalanceFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("selfBalanceFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(properties.getFilterOrder());
        return registration;
    }

    /**
     * Scatter 生命周期包装：容器关闭时先向集群广播 LEAVE，让对端立即剔除本节点、不再向已停实例转发，
     * 然后再停组网。
     */
    static class ManagedScatter implements Scatter {

        /**
         * 委托的 scatter 实现。
         */
        private final Scatter delegate;

        /**
         * 构造方法。
         *
         * @param delegate 已启动的 scatter 实现
         */
        ManagedScatter(Scatter delegate) {
            this.delegate = delegate;
        }

        @Override
        public void start() {
            // 构造时已启动，无需重复启动
        }

        @Override
        public void stop() throws Exception {
            delegate.stop();
        }

        @Override
        public ScatterServiceDiscovery discovery() {
            return delegate.discovery();
        }

        @Override
        public int getPort() {
            return delegate.getPort();
        }

        @Override
        public void close() throws Exception {
            try {
                delegate.discovery().leave();
            } catch (Exception e) {
                log.debug("[Scatter] LEAVE 广播失败: {}", e.getMessage());
            }
            delegate.stop();
        }
    }

    /**
     * 挑选一个空闲端口（自动发现要求端口固定，宿主未显式配置时用本方法预占一个确定端口）。
     *
     * @return 空闲端口；获取失败返回 0（交给 utils 层自动分配）
     */
    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            log.warn("[Scatter] 挑选空闲端口失败，交由 scatter 自动分配: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 返回第一个非空白字符串。
     *
     * @param values 候选值
     * @return 第一个非空白值；全空白返回 {@code null}
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 判断字符串非空白。
     *
     * @param value 待判字符串
     * @return 是否非空白
     */
    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
