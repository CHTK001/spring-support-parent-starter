package com.chua.starter.job.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Job配置属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Data
@ConfigurationProperties(prefix = JobProperties.PRE)
public class JobProperties {

    public static final String PRE = "plugin.job";

    /**
     * 是否启用
     */
    private boolean enable = true;

    /**
     * 线程池大小
     */
    private int poolSize = 10;

    /**
     * 日志路径
     */
    private String logPath = "/data/applogs/job/jobhandler";

    /**
     * 日志保留天数
     */
    private int logRetentionDays = 30;

    /**
     * 备份文件保留天数
     */
    private int backupRetentionDays = 90;

    /**
     * 是否启用自动备份
     */
    private boolean autoBackupEnabled = true;

    /**
     * 自动备份 CRON 表达式（默认每天凌晨3点）
     */
    private String autoBackupCron = "0 0 3 * * ?";

    /**
     * 触发池快速最大值
     */
    private int triggerPoolFastMax = 200;

    /**
     * 触发池慢速最大值
     */
    private int triggerPoolSlowMax = 100;
}
