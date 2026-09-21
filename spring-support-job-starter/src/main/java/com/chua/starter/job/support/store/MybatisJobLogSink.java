package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

    /**
     * 日期_fmt
    */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    /**
     * 作业日志映射器
    */
    private final JobLogMapper jobLogMapper;
    /**
     * 作业映射器, 用于把日志行关联到任务台账 (可为空)
    */
    private final JobMapper jobMapper;
    /**
     * 文件日志是否启用
    */
    private final boolean fileLoggingEnabled;

    /**
     * 创建 MyBatis作业日志sink 实例
     * @param jobLogMapper 作业日志映射器
     */
    public MybatisJobLogSink(JobLogMapper jobLogMapper) {
        this(jobLogMapper, null, true);
    }

    /**
     * 创建 MyBatis作业日志sink 实例
     * @param jobLogMapper 作业日志映射器
     * @param fileLoggingEnabled 文件日志已启用
     */
    public MybatisJobLogSink(JobLogMapper jobLogMapper, boolean fileLoggingEnabled) {
        this(jobLogMapper, null, fileLoggingEnabled);
    }

    /**
     * 创建带台账关联的 MyBatis 作业日志 sink 实例。
     *
     * @param jobLogMapper       作业日志映射器
     * @param jobMapper          作业映射器, 为空则日志行不落任务编号
     * @param fileLoggingEnabled 文件日志已启用
     */
    public MybatisJobLogSink(JobLogMapper jobLogMapper, JobMapper jobMapper, boolean fileLoggingEnabled) {
        this.jobLogMapper = jobLogMapper;
        this.jobMapper = jobMapper;
        this.fileLoggingEnabled = fileLoggingEnabled;
    }

    @Override
    /**
     * 写入
    */
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
        attachLedger(entity, event.jobName());
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
     * 回查任务台账, 把任务主键与任务编号补到日志行。
     *
     * <p>派发事件只携带执行器 Bean 名, 台账名称与台账编号只能靠 {@code sys_job_execute_bean}
     * 反查。控制台的按分组过滤和「任务编号」列都读 {@code sys_job_no}, 不补这一列日志行就是孤儿行。</p>
     *
     * @param entity 日志实体
     * @param jobKey 执行器 Bean 名
     */
    private void attachLedger(JobLogEntity entity, String jobKey) {
        if (jobMapper == null || jobKey == null || jobKey.isEmpty()) {
            return;
        }
        try {
            JobEntity job = jobMapper.selectOne(Wrappers.<JobEntity>lambdaQuery()
                    .and(w -> w.eq(JobEntity::getSysJobExecuteBean, jobKey)
                            .or().eq(JobEntity::getSysJobName, jobKey))
                    .eq(JobEntity::getSysJobIsDeleted, 0)
                    .last("LIMIT 1"));
            if (job != null) {
                entity.setSysJobId(job.getSysJobId());
                entity.setSysJobNo(job.getSysJobNo());
            }
        } catch (Throwable ex) {
            log.debug("[job] 日志行关联台账失败 job={} cause={}", jobKey, ex.getMessage());
        }
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
