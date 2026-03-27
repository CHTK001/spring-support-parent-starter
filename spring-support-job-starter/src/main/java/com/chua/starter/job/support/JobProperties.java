package com.chua.starter.job.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Job调度模块配置属性
 * <p>
 * 通过 {@code plugin.job.*} 前缀配置，统一描述任务扫描、轮询执行和物理表初始化能力。
 * </p>
 * 
 * <h3>配置示例:</h3>
 * <pre>
 * # application.yml
 * plugin:
 *   job:
 *     enable: true                    # 启用Job模块
 *     config-table-enabled: true      # 启用数据库配置表调度
 *     pool-size: 10                   # 调度线程池大小
 *     log-path: /data/applogs/job     # 日志存储路径
 *     log-retention-days: 30          # 日志保留天数
 *     backup-retention-days: 90       # 备份保留天数
 *     auto-backup-enabled: true       # 启用自动备份
 *     auto-backup-cron: 0 0 3 * * ?   # 自动备仼CRON
 *     trigger-pool-fast-max: 200      # 快速线程池最大线程数
 *     trigger-pool-slow-max: 100      # 慢速线程池最大线程数
 *     job-annotation-sync-mode: UPDATE # 自动解析 @Job 到配置表
 *     scheduled-annotation-sync-mode: CREATE # 自动解析 @Scheduled 到配置表
 *     table-init-mode: UPDATE         # 使用 JdbcEngine 初始化/校验表结构
 *     remote-executor:
 *       enabled: true                 # 暴露远程执行器下发入口
 *       access-token: job-secret      # 平台与执行器之间的简单鉴权口令
 *       dispatch-path: /v1/job-executor/dispatch
 *     table:
 *       prefix: payment               # 生成 payment_sys_job 等物理表
 *       job: payment_sys_job          # 显式表名优先级高于 prefix
 *       log: payment_sys_job_log
 *       log-detail: payment_sys_job_log_detail
 *       log-backup: payment_sys_job_log_backup
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
     * <p>默认: false</p>
     */
    private boolean enable = false;

    /**
     * 是否启用数据库配置表调度。
     * <p>
     * 关闭后不会启动基于 sys_job 的本地轮询调度器。
     * </p>
     */
    private boolean configTableEnabled = true;

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

    /**
     * @Job 注解自动同步到配置表的策略。
     */
    private AnnotationSyncMode jobAnnotationSyncMode = AnnotationSyncMode.NONE;

    /**
     * @Scheduled 注解自动同步到配置表的策略。
     */
    private AnnotationSyncMode scheduledAnnotationSyncMode = AnnotationSyncMode.NONE;

    /**
     * Job 物理表初始化策略。
     * <p>
     * 默认为 NONE，保留现有脚本建表方式；配置为 CREATE / UPDATE / DROP_CREATE
     * 时会使用 JdbcEngine 根据当前解析后的物理表名执行 DDL。
     * </p>
     */
    private JobSchemaInitMode tableInitMode = JobSchemaInitMode.NONE;

    /**
     * 远程执行器配置。
     * <p>
     * 开启后，业务服务会额外暴露一个轻量下发入口，允许调度中心像 XXL-Job
     * 那样把任务推送到当前进程执行。未开启时仍保持现有本地表轮询模式。
     * </p>
     */
    private RemoteExecutor remoteExecutor = new RemoteExecutor();

    /**
     * Job 相关表配置。
     * <p>
     * 支持直接指定完整表名，也支持通过 {@link Table#prefix} 统一派生物理表。
     * </p>
     */
    private Table table = new Table();

    @Data
    public static class Table {

        private static final String DEFAULT_JOB = "sys_job";
        private static final String DEFAULT_LOG = "sys_job_log";
        private static final String DEFAULT_LOG_DETAIL = "sys_job_log_detail";
        private static final String DEFAULT_LOG_BACKUP = "sys_job_log_backup";

        /**
         * 物理表名前缀。
         * <p>
         * 例如配置为 {@code payment} 时，会得到
         * {@code payment_sys_job / payment_sys_job_log ...}。
         * </p>
         */
        private String prefix;

        /**
         * 任务表
         */
        private String job;

        /**
         * 任务日志表
         */
        private String log;

        /**
         * 任务日志详情表
         */
        private String logDetail;

        /**
         * 任务日志备份表
         */
        private String logBackup;

        public String getPrefix() {
            if (!StringUtils.hasText(prefix)) {
                return null;
            }
            String normalized = prefix.trim();
            return normalized.endsWith("_") ? normalized : normalized + "_";
        }

        public String getJob() {
            return resolveValue(job, DEFAULT_JOB);
        }

        public String getLog() {
            return resolveValue(log, DEFAULT_LOG);
        }

        public String getLogDetail() {
            return resolveValue(logDetail, DEFAULT_LOG_DETAIL);
        }

        public String getLogBackup() {
            return resolveValue(logBackup, DEFAULT_LOG_BACKUP);
        }

        /**
         * 将逻辑表名解析为当前配置下的物理表名。
         */
        public String resolve(String logicalTableName) {
            if (!StringUtils.hasText(logicalTableName)) {
                return logicalTableName;
            }
            return switch (logicalTableName.trim().toLowerCase()) {
                case DEFAULT_JOB -> getJob();
                case DEFAULT_LOG -> getLog();
                case DEFAULT_LOG_DETAIL -> getLogDetail();
                case DEFAULT_LOG_BACKUP -> getLogBackup();
                default -> logicalTableName;
            };
        }

        /**
         * 复制一份已完成物理表名解析的配置，适合传递给 ThreadLocal 上下文。
         */
        public Table resolvedCopy() {
            Table target = new Table();
            target.setPrefix(getPrefix());
            target.setJob(getJob());
            target.setLog(getLog());
            target.setLogDetail(getLogDetail());
            target.setLogBackup(getLogBackup());
            return target;
        }

        private String resolveValue(String configuredValue, String defaultValue) {
            if (StringUtils.hasText(configuredValue)) {
                return configuredValue.trim();
            }
            String currentPrefix = getPrefix();
            return currentPrefix == null ? defaultValue : currentPrefix + defaultValue;
        }
    }

    @Data
    public static class RemoteExecutor {

        /**
         * 是否暴露远程执行器入口。
         */
        private boolean enabled = false;

        /**
         * 平台调用远程执行器时使用的访问口令。
         * <p>
         * 为空时表示不校验自定义口令，适合仅内网联调场景。
         * </p>
         */
        private String accessToken;

        /**
         * 远程执行器下发路径。
         */
        private String dispatchPath = "/v1/job-executor/dispatch";
    }
}
