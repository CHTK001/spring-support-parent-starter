package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.impl.JdkHttpServer;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.storage.FileStorage;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.server.filter.FilterChain;
import com.chua.starter.filesystem.support.server.filter.LoggingFilter;
import com.chua.starter.filesystem.support.server.filter.ServerFilter;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import static com.chua.starter.common.support.logger.ModuleLog.*;

/**
 * 文件服务器管理器。
 *
 * <p>根据 {@link FileStorageProperties#getServers()} 启动 HTTP 文件服务器，
 * 提供按 bucket 读取存储对象的文件访问能力。</p>
 *
 * @since 4.0.0
 * @author CH
 */
@Slf4j
public class FileServerManager implements AutoCloseable {

    /**
     * 启动成功的服务器映射
     */
    @Getter
    private final Map<String, Server> serverMap = new ConcurrentHashMap<>(); // 服务端映射

    /**
     * 文件存储配置
     */
    private final FileStorageProperties properties;

    /**
     * 文件存储模板
     */
    private final FileStorageTemplate storageTemplate;

    /**
     * Spring 应用上下文
     */
    private final ApplicationContext applicationContext;

    /**
     * 创建 文件服务端管理器 实例
     * @param properties 属性
     * @param storageTemplate storagetemplate
     * @param applicationContext application上下文
     */
    public FileServerManager(FileStorageProperties properties,
                             FileStorageTemplate storageTemplate,
                             ApplicationContext applicationContext) {
        this.properties = properties;
        this.storageTemplate = storageTemplate;
        this.applicationContext = applicationContext;
    }

    /**
     * 启动所有配置的文件服务器。
     */
    public void startAll() {
        List<FileStorageProperties.ServerConfig> servers = properties.getServers();
        if (servers == null || servers.isEmpty()) {
            log.info("未配置文件服务器，跳过启动");
            return;
        }

        log.info("══════════════════════════════════════════════");
        log.info("文件服务器配置");
        log.info("├─ 服务器数量: {}", highlight(servers.size()));

        for (FileStorageProperties.ServerConfig serverConfig : servers) {
            if (!serverConfig.isEnable()) {
                log.info("├─ 跳过禁用的服务器: {}", serverConfig.getName());
                continue;
            }
            try {
                /**
                 * 启动服务端。
                 * @param serverConfig 服务端配置
                 */
                startServer(serverConfig);
            } catch (Exception e) {
                log.error("启动服务器失败: {} - {}", serverConfig.getName(), e.getMessage(), e);
            }
        }

        log.info("══════════════════════════════════════════════");
    }

    /**
     * 启动单个文件服务器。
     *
     * @param config 服务器配置
     * @throws Exception 启动失败时抛出
     */
    private void startServer(FileStorageProperties.ServerConfig config) throws Exception {
        String serverName = config.getName();
        String host = config.getHost();
        int port = config.getPort();
        String type = config.getType();
        String contextPath = config.getContextPath() != null ? config.getContextPath() : "/";

        ServerSetting setting = ServerSetting.defaults();
        setting.setHost(host);
        setting.setPort(port);
 // 映射挑选的 服务端setting 配置项
        setting.setContextPath(contextPath);
        setting.setMaxRequestSize(config.getMaxRequestSize());
        setting.setMaxConnections(config.getMaxConnections());
        setting.setWorkerThreads(config.getWorkerThreads());
        setting.setResponseTimeout(config.getResponseTimeoutMillis());
        setting.setContentType(config.getContentType());
        setting.setGzipEnabled(config.isGzipEnabled());
        setting.setGzipMinSize(config.getGzipMinSize());
        setting.setCharset(config.getCharset());
        setting.setReadTimeout((int) config.getReadTimeoutMillis());

        Server server = createServer(type, setting);
        server.start();

        if (server instanceof JdkHttpServer) {
            HttpServer httpServer = (HttpServer) ReflectUtils.getField(server, "server");
            if (httpServer == null) {
                log.warn("无法访问 JdkHttpServer.server 字段");
            }
            if (httpServer != null) {
 // JDK http服务端 不允许 上下文="/" (根路径)，至少需要 /xxx
                String effectiveContext = "/".equals(contextPath) ? "/files" : contextPath;
 // 先移除 jdkhttp服务端 内部注册的同路径 上下文，避免冲突
                try {
                    httpServer.removeContext(effectiveContext);
                } catch (Exception e) {
                    log.warn("移除内置 context 失败(可忽略): {}", e.getMessage());
                }
                httpServer.createContext(effectiveContext, createFileHandler(config, effectiveContext));
            }
        } else {
            log.warn("Server type {} does not support custom handler registration, file serving unavailable", type);
        }

        serverMap.put(serverName, server);
        log.info("├─ 启动服务器: {} -> {}:{} [{}]",
                /**
                 * highlight。
                 * @param serverName 服务端名称
                 */
                highlight(serverName),
                host, port,
                success());
    }

    /**
     * 创建 服务端 实例。
     *
     * @param type    服务端 类型（jdk 或 SPI 名称）
     * @param setting 服务配置
     * @return Server 实例
     * @throws Exception 创建失败时抛出
     */
    private Server createServer(String type, ServerSetting setting) throws Exception {
        if (type == null || type.isEmpty() || "jdk".equalsIgnoreCase(type)) {
            /**
             * jdkhttp服务端。
             * @param setting setting
             * @return jdkhttp服务端的结果
             */
            return new JdkHttpServer(setting);
        }
        for (Server s : ServiceLoader.load(Server.class)) {
            if (s.getClass().getSimpleName().toLowerCase().contains(type.toLowerCase())) {
                return s;
            }
        }
        Class<?> clazz = ReflectUtils.forName(type);
        if (clazz == null) {
            /**
             * IllegalArgumentException。
             * @param " "
             * @param JDK" JDK"
             * @return IllegalArgumentException的结果
             */
            throw new IllegalArgumentException("Unknown server type: " + type + ", fallback to JDK");
        }
        Server created = (Server) ReflectUtils.instantiate(clazz, setting);
        if (created == null) {
            /**
             * IllegalArgumentException。
             * @param " "
             * @param JDK" JDK"
             * @return IllegalArgumentException的结果
             */
            throw new IllegalArgumentException("Unknown server type: " + type + ", fallback to JDK");
        }
        return created;
    }

    /**
     * 创建文件访问 http处理器。
     *
     * @param config      服务器配置
     * @param contextPath 上下文 前缀
     * @return HttpHandler
     */
    private HttpHandler createFileHandler(FileStorageProperties.ServerConfig config, String contextPath) {
        List<ServerFilter> filters = buildFilters(config);
        ServerFilter finalHandler = (exchange, chain) -> handleFileRequest(exchange, contextPath);
        FilterChain filterChain = FilterChain.build(filters, finalHandler);
        return exchange -> {
            try {
                filterChain.doFilter(exchange);
            } catch (Exception e) {
                log.error("Error processing request", e);
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, -1);
            } finally {
                exchange.close();
            }
        };
    }

    /**
     * 构建过滤器链。
     *
     * @param config 服务器配置
     * @return 过滤器列表
     */
    private List<ServerFilter> buildFilters(FileStorageProperties.ServerConfig config) {
        List<ServerFilter> filters = new ArrayList<>();
        for (String filterName : config.getFilters()) {
            ServerFilter filter = getFilterInstance(filterName);
            if (filter != null) {
                filters.add(filter);
            }
        }
        if (filters.stream().noneMatch(f -> f instanceof LoggingFilter)) {
            filters.add(new LoggingFilter());
        }
        return filters;
    }

    /**
     * 实例化过滤器。
     *
     * @param filterName 过滤器类名
     * @return 过滤器实例
     */
    private ServerFilter getFilterInstance(String filterName) {
        if (applicationContext != null) {
            try {
                return applicationContext.getBean(filterName, ServerFilter.class);
            } catch (Exception ignored) {}
        }
        Class<?> clazz = ReflectUtils.forName(filterName);
        ServerFilter filter = clazz == null ? null : (ServerFilter) ReflectUtils.instantiate(clazz);
        if (filter == null) {
            log.warn("Failed to instantiate filter: {}", filterName);
            return null;
        }
        return filter;
    }

    /**
     * 处理文件访问请求。
     *
     * <p>路径格式: {context}/{bucket}/{key} 或 {context}/view/{bucket}/{key} 或 {context}/download/{bucket}/{key}。
     * 访问模式由 属性 的 mode 配置决定：前端请求意图与 mode 取交集，冲突时降级到 mode 允许的行为。</p>
     *
     * @param exchange    httpexchange
     * @param contextPath 上下文 前缀
     * @throws IOException IO 异常
     */
    private void handleFileRequest(HttpExchange exchange, String contextPath) throws IOException {
        String fullPath = exchange.getRequestURI().getPath();
        log.info("[FileServer] 收到请求: {} (context={})", fullPath, contextPath);
 // 去掉 上下文 前缀
        String path = fullPath;
        if (contextPath != null && !"/".equals(contextPath) && fullPath.startsWith(contextPath)) {
            path = fullPath.substring(contextPath.length());
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
        }
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_METHOD, -1);
            return;
        }
        String[] segments = path.split("/");
        if (segments.length < 3) {
            log.warn("[FileServer] 路径格式错误: {}", path);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, -1);
            return;
        }
        // 第一段可为 view/download 前缀，用于指定前端访问意图
        boolean forceDownload = false;
        List<String> parts = new ArrayList<>(List.of(segments));
        if (parts.size() > 1 && "view".equalsIgnoreCase(parts.get(1))) {
            parts.remove(1);
        } else if (parts.size() > 1 && "download".equalsIgnoreCase(parts.get(1))) {
            forceDownload = true;
            parts.remove(1);
        }
 // parts[0] 为空（前导斜杠），真实段从 索引=1 开始: [bucket, Key...]
        if (parts.size() < 3) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, -1);
            return;
        }
        String bucket = URLDecoder.decode(parts.get(1), StandardCharsets.UTF_8.name());
 // Key = 去掉 bucket 段后的路径
        String key = String.join("/", parts.subList(2, parts.size()));
        // 规范化，防目录穿越
        key = FilenameUtils.normalize(key, true);
        if (key == null) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, -1);
            return;
        }
        // 根据配置的访问模式决定最终响应行为（前端意图与 mode 取交集，冲突时降级到 mode 允许的行为）
        FileStorageProperties.AccessMode mode = properties.getMode();
        boolean download = switch (mode) {
            case VIEW -> false;         // 仅预览：即使前端请求下载，也降级为内联预览
            case DOWNLOAD -> true;      // 仅下载：即使前端请求预览，也降级为附件下载
            case BOTH -> forceDownload; // 两者皆可：按前端前缀决定
        };
        log.info("[FileServer] bucket={}, key={}, forceDownload={}, mode={}, download={}",
                bucket, key, forceDownload, mode, download);
        try {
 // 优先按 bucket 查找对应 文件storage，找不到再回退到默认存储
            FileStorage storage = storageTemplate.getStorage(bucket);
            if (storage == null) {
                storage = storageTemplate.getDefaultStorage();
            }
            if (storage == null) {
                log.warn("[FileServer] 存储不存在: bucket={}", bucket);
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1);
                return;
            }
            com.chua.common.support.storage.result.GetObjectResult getResult =
                    storage.getObject(key);
            log.info("[FileServer] getObject result: {}", getResult == null ? "null" : getResult.getResultCode());
            byte[] data = null;
            if (getResult != null && getResult.getInputStream() != null) {
                /**
                 * 尝试。
                 * @param getResult.getInputStream( 获取结果.获取输入流(
                 */
                try (java.io.InputStream in = getResult.getInputStream()) {
                    data = in.readAllBytes();
                }
            }
            if (data == null || data.length == 0) {
                log.warn("[FileServer] 文件不存在或为空: {}", key);
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1);
                return;
            }
            log.info("[FileServer] 返回文件: {} size={}", key, data.length);
 // 按最终收敛的 download 状态设置 内容-Disposition 头
            if (download) {
                String fileName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
                exchange.getResponseHeaders().set("Content-Disposition",
                        "attachment; filename=\"" + fileName + "\"");
            }
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, data.length);
            /**
             * 尝试。
             * @param exchange.getResponseBody( exchange.获取响应主体(
             */
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        } catch (Exception e) {
            log.error("File read error: {}", e.getMessage());
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, -1);
        }
    }

    /**
     * 停止所有服务器。
     */
    public void stopAll() {
        for (Map.Entry<String, Server> entry : serverMap.entrySet()) {
            try {
                entry.getValue().stop();
                log.info("停止服务器: {} [{}]", entry.getKey(), success());
            } catch (Exception e) {
                log.error("停止服务器失败: {} [{}]", entry.getKey(), failed(), e);
            }
        }
        serverMap.clear();
    }

    /**
     * 获取服务器实例。
     *
     * @param name 服务器名称
     * @return Server 实例
     */
    public Server getServer(String name) {
        return serverMap.get(name);
    }

    /**
     * 判断服务器是否运行中。
     *
     * @param name 服务器名称
     * @return true 表示运行中
     */
    public boolean isRunning(String name) {
        Server server = serverMap.get(name);
        return server != null && server.isRunning();
    }

    @Override
    /** 关闭 */
    public void close() {
        /**
         * 停止全部。
         */
        stopAll();
    }
}
