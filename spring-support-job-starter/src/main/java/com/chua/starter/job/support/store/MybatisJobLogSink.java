package com.chua.starter.job.support.store;

import com.chua.starter.job.support.log.JobLogEvent;
import com.chua.starter.job.support.log.JobLogSink;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * MyBatis-Plus 版 {@link JobLogSink}, 写入 {@code sys_job_log} 表。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class MybatisJobLogSink implements JobLogSink {

    /** 日期_fmt */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    /** 作业日志映射器 */
    private final JobLogMapper jobLogMapper;
    /** 文件日志是否启用 */
    private final boolean fileLoggingEnabled;

    /**
     * 创建 MyBatis作业日志sink 实例
     * @param jobLogMapper 作业日志映射器
     */
    public MybatisJobLogSink(JobLogMapper jobLogMapper) {
        /**
         * this。
         * @param jobLogMapper 作业日志映射器
         * @param true true
         */
        this(jobLogMapper, true);
    }

    /**
     * 创建 MyBatis作业日志sink 实例
     * @param jobLogMapper 作业日志映射器
     * @param fileLoggingEnabled 布尔值
     * @param fileLoggingEnabled 文件日志已启用
     */
    public MybatisJobLogSink(JobLogMapper jobLogMapper, boolean fileLoggingEnabled) {
        this.jobLogMapper = jobLogMapper;
        this.fileLoggingEnabled = fileLoggingEnabled;
    }

    @Override
    /** 写入 */
    public void write(JobLogEvent event) {
        if (event == null) {
            return;
        }
        try {
            JobLogEntity entity = toEntity(event);
            jobLogMapper.insert(entity);
        } catch (Throwable ex) {
            log.warn("[job] 写入 sys_job_log 失败 cause={}", ex.getMessage(), ex);
        }
    }

    /**
     * 事件转实体 (字段均按 sys_作业_日志_* 前缀)。
     *
     * @param event 事件
     * @return 实体
     */
    private JobLogEntity toEntity(JobLogEvent event) {
        JobLogEntity entity = new JobLogEntity();
        entity.setSysJobLogNo("JOBLOG" + System.currentTimeMillis());
        entity.setSysJobLogApp("scheduler");
        entity.setSysJobLogTriggerBean(event.jobName());
        entity.setSysJobLogTriggerType("CRON");
        entity.setSysJobLogTriggerCode(event.status().name());
        entity.setSysJobLogExecuteCode(event.status().name());
        entity.setSysJobLogCost(BigDecimal.valueOf(event.costMs()));
        entity.setSysJobLogTriggerMsg(event.exception() == null ? "ok" : event.exception());
        entity.setSysJobLogNodeId(event.nodeId());
        Date executedAt = new Date(event.executedAtMs());
        entity.setSysJobLogTriggerTime(executedAt);
        entity.setSysJobLogTriggerDate(executedAt);
        if (fileLoggingEnabled) {
            entity.setSysJobLogFilePath(resolveLogFilePath(event));
        }
        entity.setSysJobLogIsDeleted(0);
        return entity;
    }

    /**
     * 解析记录日志文件路径
     *
     * @param event 事件
     * @return resolve日志文件路径的结果
     */
    private String resolveLogFilePath(JobLogEvent event) {
        String dateDir = DATE_FMT.format(Instant.now());
        String jobName = sanitize(event.jobName() != null ? event.jobName() : "unknown");
        return dateDir + "/" + jobName + "/" + event.executedAtMs() + ".log";
    }

    /**
     * Sanitize
     *
     * @param name 名称
     * @return sanitize的结果
     */
    private static String sanitize(String name) {
        return name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }
}
