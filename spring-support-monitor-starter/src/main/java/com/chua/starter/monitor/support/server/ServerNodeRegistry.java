package com.chua.starter.monitor.support.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.sip.SipConfig;
import com.chua.common.support.network.sip.SipServer;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.monitor.support.entity.MsServerNode;
import com.chua.starter.monitor.support.enums.ServerNodeProtocol;
import com.chua.starter.monitor.support.mapper.MsServerNodeMapper;
import com.chua.starter.monitor.support.server.filter.DelegatingServerFilter;
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
import java.util.ArrayList;
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
     * 文件存储网关过滤器类型别名（与 {@code ServerFilterFactory} 预设一致）
     */
    private static final String FILE_STORAGE_ALIAS = "file-storage";

    /**
     * 文件存储网关过滤器全限定类名（file-storage 预设实例化的目标类）
     */
    private static final String FILE_STORAGE_FILTER_CLASS =
            "com.chua.starter.filesystem.support.server.FileStorageServerFilter";

    /**
     * OAuth 鉴权过滤器全限定类名。
     * <p>oauth-client-starter 为可选依赖，保持反射装载的插件语义：类缺失时文件服务器按公共桶运行。</p>
     */
    private static final String OAUTH_FILTER_CLASS =
            "com.chua.starter.oauth.client.support.filter.OAuthServerFilter";

    /**
     * OAuth 鉴权过滤器类型别名（节点 filters 显式声明时不再自动挂载）
     */
    private static final String OAUTH_FILTER_ALIAS = "oauth";

    /**
     * 运行中/曾拉起的节点: 节点标识 -> 运行时
     */
    private final Map<Long, RuntimeServer> runtimes = new ConcurrentHashMap<>();

    /**
     * 节点 映射器
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
     * @param nodeMapper    节点 映射器
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
 // 数据库未就绪(ms_服务端_节点 表缺失)等场景不应阻断应用启动
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
        if (protocol == null && !ServiceProvider.of(Server.class).isSupport(node.getProtocol())) {
            throw new IllegalArgumentException("未知协议类型: " + node.getProtocol());
        }
 // 幂等: 已存在且运行中的实例直接复用, 避免重复 bind 导致 bind异常 / 端口泄漏
        RuntimeServer existing = runtimes.get(node.getId());
        if (existing != null && existing.isRunning()) {
            return existing;
        }
 // 若残留未运行或错误实例, 先清理, 避免旧 服务端 占着端口
        if (existing != null) {
            /**
             * 停止。
             * @param node.getId( 节点.获取标识(
             */
            stop(node.getId());
        }
        RuntimeServer runtime = new RuntimeServer();
        runtime.setId(node.getId());
        runtime.setName(node.getName());
        runtime.setProtocol(node.getProtocol());
        List<ServerFilter> managed = new ArrayList<>();
        try {
            Server server = buildServer(node, protocol, managed);
            server.start();
            runtime.setServer(server);
            runtime.setManagedFilters(managed);
            runtime.setServerUrl(protocol == null ? server.getServerUrl() : buildServerUrl(node, protocol));
            runtimes.put(node.getId(), runtime);
            return runtime;
        } catch (Exception e) {
            runtime.setError(e.getMessage());
            runtimes.put(node.getId(), runtime);
            throw e;
        }
    }

    /**
     * 热更新过滤器链（不重启服务器）。
     *
     * <p>仅替换本注册表托管的过滤器实例：逐个从运行中的 {@link Server} 上摘除并销毁旧实例，
     * 再按新的 {@code filters} 描述重建挂载。服务器内置过滤器（Gzip/Tracing/AccessLog/Cors）
     * 不受影响。节点未运行时不产生副作用，等待下次启动生效。</p>
     *
     * @param node 节点定义（filters 已是最新）
     * @return 运行时快照
     */
    public RuntimeServer hotUpdateFilters(MsServerNode node) {
        RuntimeServer runtime = runtimes.get(node.getId());
        if (runtime == null || !runtime.isRunning()) {
            return runtime;
        }
        Server server = runtime.getServer();
        if (server == null) {
            return runtime;
        }
        List<ServerFilterDescriptor> descriptors = parseFilters(node);
        List<ServerFilter> created = createManagedFilters(node, descriptors, server.getSetting());
        // 先建后删：新插件构建失败时保持原链路可用，避免把服务打成无过滤器状态
        for (ServerFilter old : runtime.getManagedFilters()) {
            try {
                server.removeFilter(old);
            } catch (Exception e) {
                log.warn("[ServerNodeRegistry] 摘除过滤器失败: {} - {}", old.getFilterId(), e.getMessage());
            }
            try {
                old.destroy();
            } catch (Exception e) {
                log.warn("[ServerNodeRegistry] 销毁过滤器失败: {} - {}", old.getFilterId(), e.getMessage());
            }
        }
        for (ServerFilter filter : created) {
            server.addFilter(filter);
        }
        runtime.setManagedFilters(new ArrayList<>(created));
        log.info("[ServerNodeRegistry] 过滤器链已热更新: node={} filters={}",
                node.getName(), runtime.getManagedFilters().size());
        return runtime;
    }

    /**
     * 热更新单个过滤器的参数（不重启服务器、不重建实例）。
     *
     * <p>定位到托管链路上第 {@code index} 个过滤器，调用其
     * {@code updateConfig(params)}；实现未覆写该方法时为无害空操作。</p>
     *
     * @param node   节点定义（用于报错定位）
     * @param index  过滤器下标（与 {@code filters} 数组一致）
     * @param params 新的参数键值
     * @return 运行时快照
     */
    public RuntimeServer hotUpdateFilterParams(MsServerNode node, int index, Map<String, Object> params) {
        RuntimeServer runtime = runtimes.get(node.getId());
        if (runtime == null || !runtime.isRunning()) {
            return runtime;
        }
        ServerFilter target = resolveManagedFilter(runtime, index);
        if (target == null) {
            throw new IllegalArgumentException("过滤器下标越界: " + index);
        }
        target.updateConfig(params);
        log.info("[ServerNodeRegistry] 过滤器参数已热更新: node={} index={} params={}",
                node.getName(), index, params);
        return runtime;
    }

    /**
     * 热切换单个过滤器的启用状态（不重启服务器、不重建实例）。
     *
     * @param node    节点定义（用于报错定位）
     * @param index   过滤器下标
     * @param enabled 是否启用
     * @return 运行时快照
     */
    public RuntimeServer hotToggleFilter(MsServerNode node, int index, boolean enabled) {
        RuntimeServer runtime = runtimes.get(node.getId());
        if (runtime == null || !runtime.isRunning()) {
            return runtime;
        }
        ServerFilter target = resolveManagedFilter(runtime, index);
        if (target == null) {
            throw new IllegalArgumentException("过滤器下标越界: " + index);
        }
        if (target instanceof DelegatingServerFilter delegating) {
            delegating.setEnabled(enabled);
        } else {
            throw new IllegalStateException("过滤器不支持热切换启用状态: " + target.getFilterId());
        }
        log.info("[ServerNodeRegistry] 过滤器启停已热切换: node={} index={} enabled={}",
                node.getName(), index, enabled);
        return runtime;
    }

    /**
     * 按下标定位托管链路上的过滤器。
     *
     * @param runtime 运行时
     * @param index   下标
     * @return 过滤器实例，越界返回 空
     */
    private ServerFilter resolveManagedFilter(RuntimeServer runtime, int index) {
        List<ServerFilter> filters = runtime.getManagedFilters();
        if (index < 0 || index >= filters.size()) {
            return null;
        }
        return filters.get(index);
    }

    /**
     * 停止节点(定义保留)。
     *
     * @param nodeId 节点 标识
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
     * @param nodeId 节点 标识
     */
    public void remove(Long nodeId) {
        /**
         * 停止。
         * @param nodeId 节点标识
         */
        stop(nodeId);
    }

    /**
     * 查询节点运行时。
     *
     * @param nodeId 节点 标识
     * @return 运行时，未拉起返回 空
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
     * @param managed  收集本注册表托管的过滤器实例（用于热更新时精准摘除）
     * @return 服务器实例（未启动）
     * @throws Exception 构建失败
     */
    private Server buildServer(MsServerNode node, ServerNodeProtocol protocol, List<ServerFilter> managed) throws Exception {
        Map<String, Object> config = parseConfig(node);
        ServerSetting setting = buildSetting(node, protocol, config);
        List<ServerFilterDescriptor> descriptors = parseFilters(node);
        if (protocol == null) {
            // SPI 动态协议：直接按 SPI 名称实例化
            return buildSpiServer(node, setting, descriptors, managed);
        }
        return switch (protocol) {
            case HTTP -> buildHttpServer(node, setting, descriptors, managed);
            case HTTP_PROXY -> buildProxyServer(createProxyServer("http-proxy", setting, resolveBackend(config)), node, descriptors, managed);
            case TCP_PROXY -> buildProxyServer(createProxyServer("tcp-proxy", setting, resolveBackend(config)), node, descriptors, managed);
            case SIP -> buildSipServer(node, config, descriptors, managed);
        };
    }

    /**
     * 通过 服务提供者 SPI 创建代理服务器。
     *
     * @param spiName  SPI 扩展名（http-代理 / tcp-代理）
     * @param setting 服务器配置
     * @param backend 后端地址
     * @return 代理服务器实例
     */
    private Server createProxyServer(String spiName, ServerSetting setting, InetSocketAddress backend) {
        Server server = ServiceProvider.of(Server.class).getNewExtension(spiName, setting, backend);
        if (server == null) {
            /**
             * illegal状态异常。
             * @param spiName spi名称
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("代理服务器 SPI 未注册: " + spiName);
        }
        return server;
    }

    /**
     * 构建 HTTP 服务器并挂载插件。
     *
     * @param node        节点定义
     * @param setting     配置
     * @param descriptors 插件描述
     * @param managed     托管过滤器收集器
     * @return 服务器实例
     */
    private Server buildHttpServer(MsServerNode node, ServerSetting setting,
                                   List<ServerFilterDescriptor> descriptors, List<ServerFilter> managed) {
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
        // 文件存储节点：预览统一走 iframe，关闭 X-Frame-Options 并用 CSP frame-ancestors 允许被嵌入
        if (containsFileStorage(descriptors)) {
            ServerSetting.SecurityHeadersConfig security = server.getSetting().getSecurityHeaders();
            if (security != null) {
                security.setFrameOptions("");
                security.setFrameAncestors("*");
            }
        }
        attachFilters(server, node, descriptors, setting, managed);
        return server;
    }

    /**
     * 为代理服务器挂载插件。
     *
     * @param server      代理服务器
     * @param node        节点定义
     * @param descriptors 插件描述
     * @param managed     托管过滤器收集器
     * @param <T>         代理类型
     * @return 服务器实例
     */
    private <T extends com.chua.common.support.network.server.Server> Server buildProxyServer(
            T server, MsServerNode node, List<ServerFilterDescriptor> descriptors, List<ServerFilter> managed) {
        attachFilters(server, node, descriptors, server.getSetting(), managed);
        return server;
    }

    /**
     * 构建 SIP 服务器并挂载插件。
     *
     * @param node        节点定义
     * @param config      协议配置
     * @param descriptors 插件描述
     * @param managed     托管过滤器收集器
     * @return 服务器实例
     */
    private Server buildSipServer(MsServerNode node, Map<String, Object> config,
                                  List<ServerFilterDescriptor> descriptors, List<ServerFilter> managed) {
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
        attachFilters(server, node, descriptors, server.getSetting(), managed);
        return server;
    }

    /**
     * 通过 SPI 名称构建动态协议服务器。
     *
     * @param node        节点定义
     * @param setting     通用配置
     * @param descriptors 插件描述
     * @param managed     托管过滤器收集器
     * @return 服务器实例
     */
    private Server buildSpiServer(MsServerNode node, ServerSetting setting,
                                  List<ServerFilterDescriptor> descriptors, List<ServerFilter> managed) {
        Server server = ServiceProvider.of(Server.class).getNewExtension(node.getProtocol(), setting);
        if (server == null) {
            throw new IllegalStateException("Server SPI 未注册: " + node.getProtocol());
        }
        attachFilters(server, node, descriptors, setting, managed);
        return server;
    }

    /**
     * 按描述构建过滤器并挂载到服务器，同时记录到托管集合。
     *
     * @param server      目标服务器
     * @param descriptors 插件描述
     * @param setting     服务器配置
     * @param managed     托管过滤器收集器
     */
    private void attachFilters(Server server, MsServerNode node, List<ServerFilterDescriptor> descriptors,
                               ServerSetting setting, List<ServerFilter> managed) {
        for (ServerFilter filter : createManagedFilters(node, descriptors, setting)) {
            server.addFilter(filter);
            managed.add(filter);
        }
    }

    /**
     * 构建节点托管过滤器链（含按需自动挂载的附加过滤器）。
     *
     * <p>启动（{@link #attachFilters}）与热更新（{@link #hotUpdateFilters}）必须走同一入口，
     * 保证自动挂载的过滤器在两条路径上行为一致。</p>
     *
     * @param node        节点定义
     * @param descriptors 插件描述
     * @param setting     服务器配置
     * @return 过滤器列表（顺序由各自 {@code getOrder()} 在链上合并时决定）
     */
    private List<ServerFilter> createManagedFilters(MsServerNode node, List<ServerFilterDescriptor> descriptors,
                                                    ServerSetting setting) {
        List<ServerFilter> created = filterFactory.createFilters(descriptors, setting);
        attachOAuthFilter(node, descriptors, created);
        return created;
    }

    /**
     * 文件服务器节点自动挂载 OAuth 鉴权过滤器。
     *
     * <p>命中条件：节点过滤器链含 {@code file-storage} 且未显式声明 {@code oauth}；
     * 过滤器经 {@link ReflectUtils} 反射装载（oauth-client-starter 可选存在），
     * 私有桶取自节点 config JSON 的 {@code privateBuckets}（缺省回退环境配置，
     * 见 {@code plugin.oauth.file-storage.private-buckets}）。过滤器自身在
     * {@code plugin.oauth.enable=false} 或未配置私有桶时整体放行，不影响公共桶。</p>
     *
     * @param node        节点定义
     * @param descriptors 插件描述
     * @param filters     已构建的过滤器列表（原地追加）
     */
    private void attachOAuthFilter(MsServerNode node, List<ServerFilterDescriptor> descriptors,
                                   List<ServerFilter> filters) {
        if (!containsFileStorage(descriptors) || containsOAuthFilter(descriptors)) {
            return;
        }
        ServerFilter oauth = createOAuthFilter();
        if (oauth == null) {
            log.debug("[ServerNodeRegistry] OAuth 鉴权过滤器不可用（oauth-client-starter 未装配），文件服务器按公共桶运行");
            return;
        }
        Object privateBuckets = parseConfig(node).get("privateBuckets");
        if (privateBuckets != null && !String.valueOf(privateBuckets).isBlank()) {
            oauth.updateConfig(Map.of("privateBuckets", String.valueOf(privateBuckets).trim()));
        }
        filters.add(oauth);
        // 私有桶热更新联动：文件存储网关 setConfig 后，把最新私有桶同步给 OAuth 鉴权过滤器
        bindFileStorageConfigListener(filters, oauth);
        log.info("[ServerNodeRegistry] 自动挂载 OAuth 鉴权过滤器: node={} order={} filters={} privateBuckets={}",
                node.getName(), oauth.getOrder(), filters.size(),
                privateBuckets == null ? "" : String.valueOf(privateBuckets).trim());
    }

    /**
     * 将文件存储网关的配置热更新回调绑定到 OAuth 鉴权过滤器。
     *
     * <p>文件存储网关（{@code FileStorageServerFilter}）提供 {@code setConfigListener(Consumer)}，
     * 管理员 {@code setConfig} 变更私有桶后回调；此处经动态代理桥接到
     * {@link ServerFilter#updateConfig(Map)}，使运行期"公共/私有"切换即时生效。</p>
     *
     * @param filters 已构建的过滤器列表
     * @param oauth   OAuth 鉴权过滤器
     */
    private void bindFileStorageConfigListener(List<ServerFilter> filters, ServerFilter oauth) {
        ServerFilter fileStorage = null;
        for (ServerFilter filter : filters) {
            if (FILE_STORAGE_FILTER_CLASS.equals(filter.getClass().getName())) {
                fileStorage = filter;
                break;
            }
        }
        if (fileStorage == null) {
            return;
        }
        try {
            // [P3C 1.10 豁免] 跨模块反射桥接 Consumer<FileStorageSetting> → updateConfig，第三方签名保持原生反射
            java.util.function.Consumer<Object> consumer = setting -> {
                try {
                    Object buckets = ReflectUtils.invoke(setting, "getPrivateBuckets", java.util.Set.class);
                    String csv = buckets == null ? "" : joinCsv((java.util.Set<?>) buckets);
                    oauth.updateConfig(Map.of("privateBuckets", csv));
                } catch (Exception ignored) {
                    // 同步失败不影响 setConfig 结果
                }
            };
            fileStorage.getClass()
                    .getMethod("setConfigListener", java.util.function.Consumer.class)
                    .invoke(fileStorage, consumer);
        } catch (Exception e) {
            log.debug("[ServerNodeRegistry] 绑定文件存储配置热更新回调失败(可忽略): {}", e.getMessage());
        }
    }

    /**
     * 集合转逗号分隔文本。
     *
     * @param values 集合
     * @return CSV
     */
    private String joinCsv(java.util.Set<?> values) {
        StringBuilder sb = new StringBuilder();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(text);
        }
        return sb.toString();
    }

    /**
     * 判断过滤器描述中是否含文件存储网关。
     *
     * @param descriptors 插件描述
     * @return true 表示含 file-storage
     */
    private boolean containsFileStorage(List<ServerFilterDescriptor> descriptors) {
        for (ServerFilterDescriptor descriptor : descriptors) {
            String type = descriptor.resolveType();
            if (FILE_STORAGE_ALIAS.equalsIgnoreCase(type) || FILE_STORAGE_FILTER_CLASS.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断过滤器描述中是否已显式声明 OAuth 鉴权过滤器（避免重复挂载）。
     *
     * @param descriptors 插件描述
     * @return true 表示已声明
     */
    private boolean containsOAuthFilter(List<ServerFilterDescriptor> descriptors) {
        for (ServerFilterDescriptor descriptor : descriptors) {
            String type = descriptor.resolveType();
            if (OAUTH_FILTER_ALIAS.equalsIgnoreCase(type) || OAUTH_FILTER_CLASS.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 反射创建 OAuth 鉴权过滤器（类缺失时返回 空）。
     *
     * @return 过滤器实例，不可用时返回 空
     */
    private ServerFilter createOAuthFilter() {
        try {
            Class<?> filterClass = ReflectUtils.forName(OAUTH_FILTER_CLASS);
            if (filterClass == null) {
                return null;
            }
            return (ServerFilter) ReflectUtils.instantiate(filterClass);
        } catch (Exception e) {
            log.warn("[ServerNodeRegistry] OAuth 鉴权过滤器实例化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建通用 服务端setting。
     *
     * @param node     节点定义
     * @param protocol 协议枚举（SPI 动态协议为 空）
     * @param config   协议配置
     * @return 配置实例
     */
    private ServerSetting buildSetting(MsServerNode node, ServerNodeProtocol protocol, Map<String, Object> config) {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost(node.getHost() == null || node.getHost().isBlank() ? "0.0.0.0" : node.getHost());
        setting.setPort(node.getPort() != null ? node.getPort()
                : (protocol == null ? 0 : protocol.getDefaultPort()));
        setting.setProtocol(protocol == null ? node.getProtocol() : protocol.getAlias());
        /**
         * apply通用期权。
         * @param setting setting
         * @param config 配置
         */
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
     * 解析代理后端地址（配置.backend主机/backend端口 必填）。
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
        /**
         * inetSocket地址。
         * @param String.valueOf(backendHost 字符串.valueof(backend主机
         * @return inetSocket地址的结果
         */
        return new InetSocketAddress(String.valueOf(backendHost), Integer.parseInt(String.valueOf(backendPort).trim()));
    }

    /**
     * 解析节点 配置 JSON。
     *
     * @param node 节点定义
     * @return 配置 映射
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
     * 解析节点 过滤器 JSON。
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

    /**
     * apply字符串。
     * @param setter setter
     * @param config 配置
     * @param key 键
     */
    private void applyString(java.util.function.Consumer<String> setter, Map<String, Object> config, String key) {
        Object v = config.get(key);
        if (v != null && !String.valueOf(v).isBlank()) {
            setter.accept(String.valueOf(v));
        }
    }

    /**
     * applyint。
     * @param setter setter
     * @param config 配置
     * @param key 键
     */
    private void applyInt(java.util.function.IntConsumer setter, Map<String, Object> config, String key) {
        Object v = config.get(key);
        if (v != null && !String.valueOf(v).isBlank()) {
            setter.accept(Integer.parseInt(String.valueOf(v).trim()));
        }
    }

    /**
     * applylong。
     * @param setter setter
     * @param config 配置
     * @param key 键
     */
    private void applyLong(java.util.function.LongConsumer setter, Map<String, Object> config, String key) {
        Object v = config.get(key);
        if (v != null && !String.valueOf(v).isBlank()) {
            setter.accept(Long.parseLong(String.valueOf(v).trim()));
        }
    }

    /**
     * apply布尔值。
     * @param setter setter
     * @param config 配置
     * @param key 键
     */
    private void applyBoolean(java.util.function.Consumer<Boolean> setter, Map<String, Object> config, String key) {
        Object v = config.get(key);
        if (v != null && !String.valueOf(v).isBlank()) {
            String s = String.valueOf(v).trim().toLowerCase();
            setter.accept("true".equals(s) || "1".equals(s));
        }
    }

    /**
     * 节点运行时。
     * @author CH
     * @since 4.0.0
     */
    @Getter
    public static class RuntimeServer {

        /**
         * 节点 标识
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
         * 本注册表托管的过滤器实例（热更新时精准摘除，不影响服务器内置过滤器）
         */
        private volatile List<ServerFilter> managedFilters = new ArrayList<>();

        /**
         * 设置托管的过滤器实例集合。
         *
         * @param managedFilters 过滤器实例列表
         */
        public void setManagedFilters(List<ServerFilter> managedFilters) {
            this.managedFilters = managedFilters == null ? new ArrayList<>() : managedFilters;
        }

        /**
         * 设置节点 标识
         *
         * @param id 节点 标识
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
 * @return RUNNING / STOPPED / 错误
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

    /**
     * 是否处于运行中(服务端 非空且底层服务器正在运行)。
     *
     * @return true=运行中
     */
    public boolean isRunning() {
        return server != null && server.isRunning();
    }
}

}
