package com.chua.starter.monitor.support.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.proxy.HttpProxyServer;
import com.chua.common.support.network.server.proxy.TcpProxyServer;
import com.chua.common.support.network.sip.SipConfig;
import com.chua.common.support.network.sip.SipServer;
import com.chua.starter.monitor.support.entity.MsServerNode;
import com.chua.starter.monitor.support.enums.ServerNodeProtocol;
import com.chua.starter.monitor.support.mapper.MsServerNodeMapper;
import com.chua.starter.monitor.support.server.filter.ServerFilterFactory;
import com.chua.starter.monitor.support.server.model.ServerFilterDescriptor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务管理 —— 协议服务器运行时注册表。
 *
 * <p>按 {@link MsServerNode} 定义拉起 common-starter {@link Server} 实例并托管生命周期：</p>
 * <ul>
 *   <li>HTTP：{@code ServerBuilder.type(engine)}（jdk/nio/aio），可挂 ServerFilter 插件</li>
 *   <li>HTTP_PROXY：{@code HttpProxyServer(setting, backend)}（backend 取 config.backendHost/backendPort）</li>
 *   <li>TCP_PROXY：{@code TcpProxyServer(setting, backend)}</li>
 *   <li>SIP：{@code SipServer(SipConfig)}（token 取 config.token）</li>
 * </ul>
 *
 * <p>应用就绪后自动拉起 {@code enabled=1} 的节点；单节点启动失败仅记录错误，不影响其他节点。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class ServerNodeRegistry {

    /**
     * HTTP 引擎白名单
     */
    private static final Set<String> HTTP_ENGINES = Set.of("jdk", "nio", "aio");

    /**
     * 运行中/曾拉起的节点: nodeId -> 运行时
     */
    private final Map<Long, RuntimeServer> runtimes = new ConcurrentHashMap<>();

    /**
     * 节点 Mapper
     */
    private final MsServerNodeMapper nodeMapper;
    /**
     * 插件工厂
     */
    private final ServerFilterFactory filterFactory;
    /**
     * JSON
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建注册表
     *
     * @param nodeMapper    节点 Mapper
     * @param filterFactory 插件工厂
     * @param objectMapper  JSON
     */
    @Autowired
    public ServerNodeRegistry(MsServerNodeMapper nodeMapper,
                              ServerFilterFactory filterFactory,
                              ObjectMapper objectMapper) {
        this.nodeMapper = nodeMapper;
        this.filterFactory = filterFactory;
        this.objectMapper = objectMapper;
    }

    /**
     * 应用就绪后自动拉起启用节点。
     *
     * @param event 就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void autoStart() {
        try {
            LambdaQueryWrapper<MsServerNode> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MsServerNode::getEnabled, 1);
            List<MsServerNode> nodes = nodeMapper.selectList(wrapper);
            if (nodes.isEmpty()) {
                return;
            }
            log.info("[ServerNodeRegistry] 自动拉起启用节点: {} 个", nodes.size());
            for (MsServerNode node : nodes) {
                try {
                    RuntimeServer runtime = start(node);
                    log.info("[ServerNodeRegistry] 节点已启动: {} [{}] {}",
                            node.getName(), node.getProtocol(), runtime.getServerUrl());
                } catch (Exception e) {
                    log.error("[ServerNodeRegistry] 节点自动启动失败: {} [{}] - {}",
                            node.getName(), node.getProtocol(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            // 数据库未就绪(ms_server_node 表缺失)等场景不应阻断应用启动
            log.warn("[ServerNodeRegistry] 自动拉起跳过: {}", e.getMessage());
        }
    }

    /**
     * 按定义构建并启动服务器。
     *
     * @param node 节点定义
     * @return 运行时
     * @throws Exception 启动失败时抛出
     */
    public RuntimeServer start(MsServerNode node) throws Exception {
        ServerNodeProtocol protocol = ServerNodeProtocol.of(node.getProtocol());
        if (protocol == null) {
            throw new IllegalArgumentException("未知协议类型: " + node.getProtocol());
        }
        RuntimeServer runtime = new RuntimeServer();
        runtime.setId(node.getId());
        runtime.setName(node.getName());
        runtime.setProtocol(node.getProtocol());
        try {
            Server server = buildServer(node, protocol);
            server.start();
            runtime.setServer(server);
            runtime.setServerUrl(buildServerUrl(node, protocol));
            runtimes.put(node.getId(), runtime);
            return runtime;
        } catch (Exception e) {
            runtime.setError(e.getMessage());
            runtimes.put(node.getId(), runtime);
            throw e;
        }
    }

    /**
     * 停止节点(定义保留)。
     *
     * @param nodeId 节点 ID
     */
    public void stop(Long nodeId) {
        RuntimeServer runtime = runtimes.remove(nodeId);
        if (runtime == null) {
            return;
        }
        Server server = runtime.getServer();
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                log.warn("[ServerNodeRegistry] 节点停止异常: {} - {}", runtime.getName(), e.getMessage());
            }
        }
        runtime.setServer(null);
        log.info("[ServerNodeRegistry] 节点已停止: {}", runtime.getName());
    }

    /**
     * 停止并移除节点。
     *
     * @param nodeId 节点 ID
     */
    public void remove(Long nodeId) {
        stop(nodeId);
    }

    /**
     * 查询节点运行时。
     *
     * @param nodeId 节点 ID
     * @return 运行时，未拉起返回 null
     */
    public RuntimeServer get(Long nodeId) {
        return runtimes.get(nodeId);
    }

    /**
     * 全部运行时快照。
     *
     * @return 不可变快照
     */
    public Collection<RuntimeServer> snapshot() {
        return Collections.unmodifiableCollection(runtimes.values());
    }

    /**
     * 按协议构建服务器实例。
     *
     * @param node     节点定义
     * @param protocol 协议枚举
     * @return 服务器实例（未启动）
     * @throws Exception 构建失败
     */
    private Server buildServer(MsServerNode node, ServerNodeProtocol protocol) throws Exception {
        Map<String, Object> config = parseConfig(node);
        ServerSetting setting = buildSetting(node, protocol, config);
        List<ServerFilterDescriptor> descriptors = parseFilters(node);
        return switch (protocol) {
            case HTTP -> buildHttpServer(node, setting, descriptors);
            case HTTP_PROXY -> buildProxyServer(new HttpProxyServer(setting, resolveBackend(config)), node, descriptors);
            case TCP_PROXY -> buildProxyServer(new TcpProxyServer(setting, resolveBackend(config)), node, descriptors);
            case SIP -> buildSipServer(node, config, descriptors);
        };
    }

    /**
     * 构建 HTTP 服务器并挂载插件。
     *
     * @param node        节点定义
     * @param setting     配置
     * @param descriptors 插件描述
     * @return 服务器实例
     */
    private Server buildHttpServer(MsServerNode node, ServerSetting setting, List<ServerFilterDescriptor> descriptors) {
        String engine = node.getEngine() == null || node.getEngine().isBlank()
                ? ServerNodeProtocol.HTTP.getDefaultEngine() : node.getEngine().trim().toLowerCase();
        if (!HTTP_ENGINES.contains(engine)) {
            throw new IllegalArgumentException("HTTP 服务器引擎仅支持 jdk/nio/aio: " + engine);
        }
        Server server = ServerBuilder.create()
                .type(engine)
                .host(setting.getHost())
                .port(setting.getPort())
                .build();
        for (com.chua.common.support.network.server.filter.ServerFilter filter : filterFactory.createFilters(descriptors, setting)) {
            server.addFilter(filter);
        }
        return server;
    }

    /**
     * 为代理服务器挂载插件。
     *
     * @param server      代理服务器
     * @param node        节点定义
     * @param descriptors 插件描述
     * @param <T>         代理类型
     * @return 服务器实例
     */
    @SuppressWarnings("unchecked")
    private <T extends com.chua.common.support.network.server.Server> Server buildProxyServer(
            T server, MsServerNode node, List<ServerFilterDescriptor> descriptors) {
        ServerSetting setting = server.getSetting();
        for (com.chua.common.support.network.server.filter.ServerFilter filter : filterFactory.createFilters(descriptors, setting)) {
            server.addFilter(filter);
        }
        return server;
    }

    /**
     * 构建 SIP 服务器并挂载插件。
     *
     * @param node        节点定义
     * @param config      协议配置
     * @param descriptors 插件描述
     * @return 服务器实例
     */
    private Server buildSipServer(MsServerNode node, Map<String, Object> config, List<ServerFilterDescriptor> descriptors) {
        SipConfig.Builder builder = SipConfig.builder()
                .host(node.getHost())
                .port(node.getPort());
        Object token = config.get("token");
        if (token != null && !String.valueOf(token).isBlank()) {
            builder.token(String.valueOf(token));
        }
        Object dataPlaneMode = config.get("dataPlaneMode");
        if (dataPlaneMode != null && !String.valueOf(dataPlaneMode).isBlank()) {
            builder.dataPlaneMode(String.valueOf(dataPlaneMode));
        }
        SipServer server = new SipServer(builder.build());
        ServerSetting setting = server.getSetting();
        for (com.chua.common.support.network.server.filter.ServerFilter filter : filterFactory.createFilters(descriptors, setting)) {
            server.addFilter(filter);
        }
        return server;
    }

    /**
     * 构建通用 ServerSetting。
     *
     * @param node     节点定义
     * @param protocol 协议枚举
     * @param config   协议配置
     * @return 配置实例
     */
    private ServerSetting buildSetting(MsServerNode node, ServerNodeProtocol protocol, Map<String, Object> config) {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost(node.getHost() == null || node.getHost().isBlank() ? "0.0.0.0" : node.getHost());
        setting.setPort(node.getPort() != null ? node.getPort() : protocol.getDefaultPort());
        setting.setProtocol(protocol.getAlias());
        applyCommonOptions(setting, config);
        return setting;
    }

    /**
     * 应用通用配置项（可选）。
     *
     * @param setting 配置实例
     * @param config  协议配置
     */
    private void applyCommonOptions(ServerSetting setting, Map<String, Object> config) {
        applyString(setting::setContextPath, config, "contextPath");
        applyString(setting::setCharset, config, "charset");
        applyInt(setting::setReadTimeout, config, "readTimeout");
        applyInt(setting::setWriteTimeout, config, "writeTimeout");
        applyInt(setting::setBacklog, config, "backlog");
        applyInt(setting::setMaxConnections, config, "maxConnections");
        applyInt(setting::setMaxConcurrency, config, "maxConcurrency");
        applyLong(setting::setMaxRequestSize, config, "maxRequestSize");
        applyLong(setting::setResponseTimeout, config, "responseTimeout");
        applyBoolean(setting::setGzipEnabled, config, "gzipEnabled");
        applyBoolean(setting::setReactor, config, "reactor");
    }

    /**
     * 解析代理后端地址（config.backendHost/backendPort 必填）。
     *
     * @param config 协议配置
     * @return 后端地址
     */
    private InetSocketAddress resolveBackend(Map<String, Object> config) {
        Object backendHost = config.get("backendHost");
        Object backendPort = config.get("backendPort");
        if (backendHost == null || String.valueOf(backendHost).isBlank()
                || backendPort == null || String.valueOf(backendPort).isBlank()) {
            throw new IllegalArgumentException("代理服务器必须在 config 中提供 backendHost 与 backendPort");
        }
        return new InetSocketAddress(String.valueOf(backendHost), Integer.parseInt(String.valueOf(backendPort).trim()));
    }

    /**
     * 解析节点 config JSON。
     *
     * @param node 节点定义
     * @return 配置 Map
     */
    public Map<String, Object> parseConfig(MsServerNode node) {
        String json = node.getConfig();
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("节点 [" + node.getName() + "] config JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析节点 filters JSON。
     *
     * @param node 节点定义
     * @return 插件描述列表
     */
    public List<ServerFilterDescriptor> parseFilters(MsServerNode node) {
        String json = node.getFilters();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ServerFilterDescriptor>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("节点 [" + node.getName() + "] filters JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建访问地址。
     *
     * @param node     节点定义
     * @param protocol 协议枚举
     * @return 地址字符串
     */
    private String buildServerUrl(MsServerNode node, ServerNodeProtocol protocol) {
        String scheme = switch (protocol) {
            case HTTP, HTTP_PROXY -> "http";
            case TCP_PROXY -> "tcp";
            case SIP -> "sip";
        };
        return scheme + "://" + (node.getHost() == null || node.getHost().isBlank() ? "0.0.0.0" : node.getHost())
                + ":" + (node.getPort() != null ? node.getPort() : protocol.getDefaultPort()) + "/";
    }

    private void applyString(java.util.function.Consumer<String> setter, Map<String, Object> config, String key) {
        Object v = config.get(key);
        if (v != null && !String.valueOf(v).isBlank()) {
            setter.accept(String.valueOf(v));
        }
    }

    private void applyInt(java.util.function.IntConsumer setter, Map<String, Object> config, String key) {
        Object v = config.get(key);
        if (v != null && !String.valueOf(v).isBlank()) {
            setter.accept(Integer.parseInt(String.valueOf(v).trim()));
        }
    }

    private void applyLong(java.util.function.LongConsumer setter, Map<String, Object> config, String key) {
        Object v = config.get(key);
        if (v != null && !String.valueOf(v).isBlank()) {
            setter.accept(Long.parseLong(String.valueOf(v).trim()));
        }
    }

    private void applyBoolean(java.util.function.Consumer<Boolean> setter, Map<String, Object> config, String key) {
        Object v = config.get(key);
        if (v != null && !String.valueOf(v).isBlank()) {
            String s = String.valueOf(v).trim().toLowerCase();
            setter.accept("true".equals(s) || "1".equals(s));
        }
    }

    /**
     * 节点运行时。
     */
    @Getter
    public static class RuntimeServer {

        /**
         * 节点 ID
         */
        private Long id;
        /**
         * 节点名称
         */
        private String name;
        /**
         * 协议编码
         */
        private String protocol;
        /**
         * 服务器实例(停止后置空)
         */
        private volatile Server server;
        /**
         * 访问地址
         */
        private volatile String serverUrl;
        /**
         * 最近错误
         */
        private volatile String error;

        /**
         * 设置节点 ID
         *
         * @param id 节点 ID
         */
        public void setId(Long id) {
            this.id = id;
        }

        /**
         * 设置节点名称
         *
         * @param name 节点名称
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * 设置协议编码
         *
         * @param protocol 协议编码
         */
        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        /**
         * 设置服务器实例
         *
         * @param server 服务器实例
         */
        public void setServer(Server server) {
            this.server = server;
        }

        /**
         * 设置访问地址
         *
         * @param serverUrl 访问地址
         */
        public void setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        /**
         * 设置最近错误
         *
         * @param error 错误信息
         */
        public void setError(String error) {
            this.error = error;
        }

        /**
         * 运行状态。
         *
         * @return RUNNING / STOPPED / ERROR
         */
        public String status() {
            Server s = this.server;
            if (s != null && s.isRunning()) {
                return "RUNNING";
            }
            if (s != null) {
                return "STOPPED";
            }
            return error != null ? "ERROR" : "STOPPED";
        }
    }

}
