package com.chua.starter.sync.data.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 同步任务配置属性
 *
 * @author CH
 * @since 2024/12/19
 */
@Data
@ConfigurationProperties(prefix = SyncProperties.PRE)
public class SyncProperties {

    public static final String PRE = "plugin.sync";

    /**
     * 是否启用同步功能
     */
    private boolean enabled = true;

    /**
     * 是否自动创建同步相关表
     * <p>
     * 启用后，应用启动时会自动执行DDL创建 monitor_sync_* 相关表
     * </p>
     */
    private boolean autoCreateTable = false;

    /**
     * 是否启用WebSocket实时推送
     */
    private boolean websocketEnabled = true;

    /**
     * 是否启用定时任务调度集成
     */
    private boolean schedulerEnabled = true;

    /**
     * 默认批处理大小
     */
    private int defaultBatchSize = 1000;

    /**
     * 默认消费超时时间(ms)
     */
    private int defaultConsumeTimeout = 30000;

    /**
     * 默认重试次数
     */
    private int defaultRetryCount = 3;

    /**
     * 默认重试间隔(ms)
     */
    private int defaultRetryInterval = 1000;

    /**
     * 任务执行日志保留天数
     */
    private int logRetentionDays = 30;

    /**
     * 是否启用任务执行日志清理
     */
    private boolean logCleanupEnabled = true;

    /**
     * 默认最大内存限制(MB)
     */
    private int defaultMaxMemoryMb = 512;

    /**
     * 默认线程池大小
     */
    private int defaultThreadPoolSize = 5;

    /**
     * 内存使用率告警阈值（0.0-1.0）
     */
    private double memoryThreshold = 0.85;

    /**
     * 是否启用动态批次大小调整
     */
    private boolean adaptiveBatchSizeEnabled = true;

    /**
     * 是否启用流式处理
     */
    private boolean streamingEnabled = true;

    /**
     * 是否启用对象池
     */
    private boolean objectPoolEnabled = true;

    /**
     * 对象池最大对象数
     */
    private int objectPoolMaxTotal = 1000;

    /**
     * 对象池最大空闲对象数
     */
    private int objectPoolMaxIdle = 100;

    /**
     * 是否启用限流保护
     */
    private boolean rateLimitEnabled = true;

    /**
     * 默认限流速率（每秒允许的请求数）
     */
    private double defaultRateLimit = 100.0;

    /**
     * 是否启用连接健康检查
     */
    private boolean healthCheckEnabled = true;

    /**
     * 连接健康检查间隔(ms)
     */
    private long healthCheckInterval = 60000;

    /**
     * 是否启用监控统计
     */
    private boolean monitoringEnabled = true;

    /**
     * 统计数据保留天数
     */
    private int statisticsRetentionDays = 90;

    /**
     * 是否启用告警功能
     */
    private boolean alertEnabled = true;

    /**
     * 告警数据保留天数
     */
    private int alertRetentionDays = 30;

    /**
     * 前端认证配置
     */
    private WebAuthConfig webAuth = new WebAuthConfig();

    /**
     * 前端认证配置类
     */
    @Data
    public static class WebAuthConfig {
        /**
         * 认证模式：embedded（嵌入式账号密码）、none（无认证，业务方自己处理）
         */
        private String mode = "embedded";
        
        /**
         * 嵌入式认证的默认用户名
         */
        private String username = "admin";
        
        /**
         * 嵌入式认证的默认密码（建议生产环境修改）
         */
        private String password = "admin123";
        
        /**
         * Session超时时间（秒）
         */
        private int sessionTimeout = 3600;
        
        /**
         * 是否启用记住我功能
         */
        private boolean rememberMeEnabled = true;
        
        /**
         * 记住我有效期（秒）
         */
        private int rememberMeDuration = 604800; // 7天
    }
}
