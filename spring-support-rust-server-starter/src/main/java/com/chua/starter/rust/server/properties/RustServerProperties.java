package com.chua.starter.rust.server.properties;

import lombok.Data;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;

/**
 * Rust HTTP Server 配置属性
 * <p>
 * 支持 Spring Boot 原生 server.* 配置，同时提供 Rust Server 特有的扩展配置。
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = RustServerProperties.PREFIX)
public class RustServerProperties {

    public static final String PREFIX = "plugin.rust-server";

    /**
     * 是否启用 Rust HTTP Server
     */
    private boolean enabled = false;

    // ==================== 连接管理 ====================

    /**
     * 最大连接数 (高并发优化: 100K+)
     */
    private int maxConnections = 100000;

    /**
     * 连接超时时间
     */
    private Duration connectionTimeout = Duration.ofSeconds(10);

    /**
     * Keep-Alive 超时时间 (HTTP/1.1 连接复用)
     */
    private Duration keepAliveTimeout = Duration.ofSeconds(60);

    /**
     * 单个连接最大请求数 (缓解连接耗尽)
     */
    private int keepAliveMaxRequests = 10000;

    /**
     * 空闲连接超时
     */
    private Duration idleTimeout = Duration.ofSeconds(30);

    // ==================== 请求限制 ====================

    /**
     * 最大请求体大小 (字节)
     */
    private long maxRequestSize = 100 * 1024 * 1024; // 100MB

    /**
     * 最大请求头大小 (字节)
     */
    private int maxHeaderSize = 16 * 1024; // 16KB

    /**
     * 最大 URI 长度
     */
    private int maxUriLength = 16384;

    /**
     * 请求超时时间
     */
    private Duration requestTimeout = Duration.ofSeconds(60);

    // ==================== 线程池配置 ====================

    /**
     * Rust 工作线程数，0 表示使用 CPU 核心数
     */
    private int workers = 0;

    /**
     * 分发器线程池配置
     */
    @NestedConfigurationProperty
    private DispatcherConfig dispatcher = new DispatcherConfig();

    // ==================== 限流配置 ====================

    /**
     * 限流配置
     */
    @NestedConfigurationProperty
    private RateLimitConfig rateLimit = new RateLimitConfig();

    // ==================== HTTP/2 配置 ====================

    /**
     * HTTP/2 配置
     */
    @NestedConfigurationProperty
    private Http2Config http2 = new Http2Config();

    // ==================== IPC 配置 ====================

    /**
     * IPC 配置
     */
    @NestedConfigurationProperty
    private IpcConfig ipc = new IpcConfig();

    // ==================== 健康检查 ====================

    /**
     * 健康检查配置
     */
    @NestedConfigurationProperty
    private HealthConfig health = new HealthConfig();

    // ==================== 访问日志 ====================

    /**
     * 访问日志配置
     */
    @NestedConfigurationProperty
    private AccessLogConfig accessLog = new AccessLogConfig();

    /**
     * 自定义 Rust 可执行文件路径
     */
    private String executablePath;

    // ==================== 内部配置类 ====================

    @Data
    public static class DispatcherConfig {
        /**
         * 核心线程池大小 (传统线程模式)
         */
        private int corePoolSize = 500;

        /**
         * 最大线程池大小 (传统线程模式)
         */
        private int maxPoolSize = 2000;

        /**
         * 队列容量
         */
        private int queueCapacity = 50000;

        /**
         * 是否使用虚拟线程 (Java 21+, 默认开启, 无限制并发)
         */
        private boolean useVirtualThreads = true;
    }

    @Data
    public static class RateLimitConfig {
        /**
         * 是否启用限流 (默认关闭, 按需开启)
         */
        private boolean enabled = false;

        /**
         * 全局每秒请求数限制
         */
        private int requestsPerSecond = 100000;

        /**
         * 突发容量
         */
        private int burstSize = 10000;

        /**
         * 是否按 IP 限流
         */
        private boolean perIp = false;

        /**
         * 单 IP 每秒请求数限制
         */
        private int perIpRequestsPerSecond = 100;

        /**
         * 限流响应状态码
         */
        private int responseStatus = 429;

        /**
         * 限流响应消息
         */
        private String responseMessage = "Too Many Requests";
    }

    @Data
    public static class Http2Config {
        /**
         * 是否启用 HTTP/2
         */
        private boolean enabled = true;

        /**
         * 最大并发流数 (每个 HTTP/2 连接)
         */
        private int maxConcurrentStreams = 1000;

        /**
         * 初始窗口大小 (1MB)
         */
        private int initialWindowSize = 1048576;

        /**
         * 最大帧大小 (1MB)
         */
        private int maxFrameSize = 1048576;
    }

    @Data
    public static class IpcConfig {
        /**
         * IPC 类型: auto, unix-socket, named-pipe, tcp
         */
        private String type = "auto";

        /**
         * IPC 缓冲区大小 (256KB, 优化大请求体)
         */
        private int bufferSize = 262144;

        /**
         * IPC 连接池大小
         */
        private int poolSize = 100;
    }

    @Data
    public static class HealthConfig {
        /**
         * 是否启用健康检查端点
         */
        private boolean enabled = true;

        /**
         * 健康检查路径
         */
        private String path = "/health";
    }

    @Data
    public static class AccessLogConfig {
        /**
         * 是否启用访问日志
         */
        private boolean enabled = true;

        /**
         * 日志格式: combined, common, json
         */
        private String format = "combined";

        /**
         * 日志文件路径
         */
        private String path = "logs/access.log";
    }
}
