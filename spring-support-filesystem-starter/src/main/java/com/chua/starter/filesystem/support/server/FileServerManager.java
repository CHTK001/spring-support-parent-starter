package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.impl.JdkHttpServer;
import com.chua.common.support.network.server.ServerSetting;
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

@Slf4j
public class FileServerManager implements AutoCloseable {

    @Getter
    private final Map<String, Server> serverMap = new ConcurrentHashMap<>();

    private final FileStorageProperties properties;
    private final FileStorageTemplate storageTemplate;
    private final ApplicationContext applicationContext;

    public FileServerManager(FileStorageProperties properties, FileStorageTemplate storageTemplate, ApplicationContext applicationContext) {
        this.properties = properties;
        this.storageTemplate = storageTemplate;
        this.applicationContext = applicationContext;
    }

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
                startServer(serverConfig);
            } catch (Exception e) {
                log.error("启动服务器失败: {} - {}", serverConfig.getName(), e.getMessage(), e);
            }
        }

        log.info("══════════════════════════════════════════════");
    }

    private void startServer(FileStorageProperties.ServerConfig config) throws Exception {
        String serverName = config.getName();
        String host = config.getHost();
        int port = config.getPort();
        String type = config.getType();

        ServerSetting setting = ServerSetting.defaults();
        setting.setHost(host);
        setting.setPort(port);

        Server server = createServer(type, setting);
        server.start();

        if (server instanceof JdkHttpServer) {
            HttpServer httpServer = ((JdkHttpServer) server).getHttpServer();
            String contextPath = config.getContextPath() != null ? config.getContextPath() : "/";
            httpServer.createContext(contextPath, createFileHandler(config));
        } else {
            log.warn("Server type {} does not support custom handler registration, file serving unavailable", type);
        }

        serverMap.put(serverName, server);
        log.info("├─ 启动服务器: {} -> {}:{} [{}]",
                highlight(serverName),
                host, port,
                success());
    }

    private Server createServer(String type, ServerSetting setting) throws Exception {
        if (type == null || type.isEmpty() || "jdk".equalsIgnoreCase(type)) {
            return new JdkHttpServer(setting);
        }
        for (Server s : ServiceLoader.load(Server.class)) {
            if (s.getClass().getSimpleName().toLowerCase().contains(type.toLowerCase())) {
                return s;
            }
        }
        try {
            Class<?> clazz = Class.forName(type);
            return (Server) clazz.getDeclaredConstructor(ServerSetting.class).newInstance(setting);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unknown server type: " + type + ", fallback to JDK", e);
        }
    }

    private HttpHandler createFileHandler(FileStorageProperties.ServerConfig config) {
        List<ServerFilter> filters = buildFilters(config);
        ServerFilter finalHandler = this::handleFileRequest;
        FilterChain chain = FilterChain.build(filters, finalHandler);
        return exchange -> {
            try {
                chain.doFilter(exchange);
            } catch (Exception e) {
                log.error("Error processing request", e);
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, -1);
            } finally {
                exchange.close();
            }
        };
    }

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

    private ServerFilter getFilterInstance(String filterName) {
        if (applicationContext != null) {
            try {
                return applicationContext.getBean(filterName, ServerFilter.class);
            } catch (Exception ignored) {}
        }
        try {
            Class<?> clazz = Class.forName(filterName);
            return (ServerFilter) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.warn("Failed to instantiate filter: {}", filterName, e);
            return null;
        }
    }

    private void handleFileRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_METHOD, -1);
            return;
        }
        String[] segments = path.split("/");
        if (segments.length < 3) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, -1);
            return;
        }
        String bucket = URLDecoder.decode(segments[1], StandardCharsets.UTF_8.name());
        String key = path.substring(segments[1].length() + 1);
        try {
            byte[] data = storageTemplate.getDefaultStorage().read(bucket, key);
            if (data == null || data.length == 0) {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1);
                return;
            }
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        } catch (Exception e) {
            log.error("File read error: {}", e.getMessage());
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, -1);
        }
    }

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

    public Server getServer(String name) {
        return serverMap.get(name);
    }

    public boolean isRunning(String name) {
        Server server = serverMap.get(name);
        return server != null && server.isRunning();
    }

    @Override
    public void close() {
        stopAll();
    }
}