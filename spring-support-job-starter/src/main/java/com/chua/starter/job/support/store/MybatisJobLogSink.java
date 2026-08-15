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
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class MybatisJobLogSink implements JobLogSink {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final JobLogMapper jobLogMapper;
    private final boolean fileLoggingEnabled;

    public MybatisJobLogSink(JobLogMapper jobLogMapper) {
        this(jobLogMapper, true);
    }

    public MybatisJobLogSink(JobLogMapper jobLogMapper, boolean fileLoggingEnabled) {
        this.jobLogMapper = jobLogMapper;
        this.fileLoggingEnabled = fileLoggingEnabled;
    }

    @Override
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
     * 事件转实体 (字段均按 sys_job_log_* 前缀)。
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

    private String resolveLogFilePath(JobLogEvent event) {
        String dateDir = DATE_FMT.format(Instant.now());
        String jobName = sanitize(event.jobName() != null ? event.jobName() : "unknown");
        return dateDir + "/" + jobName + "/" + event.executedAtMs() + ".log";
    }

    private static String sanitize(String name) {
        return name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }
}