package com.chua.starter.job.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Job调度模块配置属性
 * <p>
 * 通过 {@code plugin.job.*} 前缀配置。
 * </p>
 * 
 * <h3>配置示例:</h3>
 * <pre>
 * # application.yml
 * plugin:
 *   job:
 *     enable: true                    # 启用Job模块
 *     pool-size: 10                   # 调度线程池大小
 *     log-path: /data/applogs/job     # 日志存储路径
 *     log-retention-days: 30          # 日志保留天数
 *     backup-retention-days: 90       # 备份保留天数
 *     auto-backup-enabled: true       # 启用自动备份
 *     auto-backup-cron: 0 0 3 * * ?   # 自动备仼CRON
 *     trigger-pool-fast-max: 200      # 快速线程池最大线程数
 *     trigger-pool-slow-max: 100      # 慢速线程池最大线程数
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see JobConfiguration
 */
@Data
@ConfigurationProperties(prefix = JobProperties.PRE)
public class JobProperties {

    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.job";

    /**
     * 是否启用Job调度模块
     * <p>默认: true</p>
     */
    private boolean enable = true;

    /**
     * 调度线程池核心大小
     * <p>默认: 10</p>
     */
    private int poolSize = 10;

    /**
     * 日志文件存储路径
     * <p>默认: /data/applogs/job/jobhandler</p>
     */
    private String logPath = "/data/applogs/job/jobhandler";

    /**
     * 日志文件保留天数
     * <p>超过此天数的日志将被自动备份并清理。默认: 30天</p>
     */
    private int logRetentionDays = 30;

    /**
     * 备份文件保留天数
     * <p>超过此天数的备份文件将被删除。默认: 90天</p>
     */
    private int backupRetentionDays = 90;

    /**
     * 是否启用自动备份
     * <p>启用后将按照autoBackupCron定时执行日志备份。默认: true</p>
     */
    private boolean autoBackupEnabled = true;

    /**
     * 自动备份CRON表达式
     * <p>默认: 每天凌晨3点执行 (0 0 3 * * ?)</p>
     */
    private String autoBackupCron = "0 0 3 * * ?";

    /**
     * 快速触发线程池最大线程数
     * <p>用于执行正常任务（执行时间<500ms）。默认: 200</p>
     */
    private int triggerPoolFastMax = 200;

    /**
     * 慢速触发线程池最大线程数
     * <p>用于执行慢任务（连续10次执行时间>500ms的任务）。默认: 100</p>
     */
    private int triggerPoolSlowMax = 100;
}
