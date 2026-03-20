package com.chua.starter.sync.data.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sync 与 Job 调度集成配置
 *
 * @author CH
 * @since 2026/03/19
 */
@Data
@ConfigurationProperties(prefix = SyncJobIntegrationProperties.PRE)
public class SyncJobIntegrationProperties {

    public static final String PRE = SyncProperties.PRE + ".job-integration";

    /**
     * 是否启用 Job 集成
     */
    private boolean enabled = false;

    /**
     * 是否同步任务状态
     */
    private boolean syncStatus = true;

    /**
     * 是否同步写入 Job 日志
     */
    private boolean dualLog = true;

    /**
     * Job 名称前缀
     */
    private String jobNamePrefix = "SYNC_TASK_";

    /**
     * 是否使用 Job 模块重试配置
     */
    private boolean useJobRetry = true;
}
