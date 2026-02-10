package com.chua.sync.data.support.properties;

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
}
