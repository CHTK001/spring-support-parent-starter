package com.chua.starter.job.support.log;

import com.chua.starter.job.support.entity.SysJobLogDetail;
import com.chua.starter.job.support.mapper.SysJobLogDetailMapper;
import com.chua.starter.job.support.thread.JobContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 任务日志详情服务
 * <p>
 * 提供详细日志的记录、查询和文件存储功能
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLogDetailService {

    private final SysJobLogDetailMapper jobLogDetailMapper;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 记录日志详情
     *
     * @param jobContext 任务上下文
     * @param level      日志级别
     * @param content    日志内容
     * @param phase      执行阶段
     * @param progress   执行进度
     * @return 日志详情
     */
    public SysJobLogDetail log(JobContext jobContext, String level, String content, String phase, Integer progress) {
        if (jobContext == null) {
            throw new IllegalArgumentException("任务上下文不能为空");
        }
        SysJobLogDetail detail = new SysJobLogDetail();
        if (jobContext.getJobLogId() > 0) {
            detail.setJobLogId((int) jobContext.getJobLogId());
        }
        if (jobContext.getJobId() > 0) {
            detail.setJobId((int) jobContext.getJobId());
        }
        detail.setJobLogNo(jobContext.getJobLogNo());
        detail.setJobNo(jobContext.getJobNo());
        detail.setJobLogDetailLevel(StringUtils.hasText(level) ? level.trim().toUpperCase() : "INFO");
        detail.setJobLogDetailContent(content);
        detail.setJobLogDetailPhase(phase);
        detail.setJobLogDetailProgress(progress);
        detail.setJobLogDetailTime(LocalDateTime.now());
        detail.setJobLogDetailFilePath(jobContext.getJobLogFileName());
        detail.setJobLogDetailHandler(stringAttribute(jobContext, "handler"));
        detail.setJobLogDetailProfile(stringAttribute(jobContext, "profile"));
        detail.setJobLogDetailAddress(stringAttribute(jobContext, "address"));
        detail.setCreateTime(LocalDateTime.now());
        jobLogDetailMapper.insert(detail);
        appendLogFile(detail, jobContext.getJobLogFileName());
        return detail;
    }

    /**
     * 记录日志详情
     *
     * @param jobLogId 任务日志ID
     * @param jobId    任务ID
     * @param level    日志级别
     * @param content  日志内容
     * @return 日志详情
     */
    public SysJobLogDetail log(Integer jobLogId, Integer jobId, String level, String content) {
        return log(jobLogId, jobId, level, content, null, null);
    }

    /**
     * 记录日志详情
     *
     * @param jobLogId 任务日志ID
     * @param jobId    任务ID
     * @param level    日志级别
     * @param content  日志内容
     * @param phase    执行阶段
     * @param progress 执行进度
     * @return 日志详情
     */
    public SysJobLogDetail log(Integer jobLogId, Integer jobId, String level, String content, 
                                    String phase, Integer progress) {
        SysJobLogDetail detail = new SysJobLogDetail();
        detail.setJobLogId(jobLogId);
        detail.setJobId(jobId);
        detail.setJobLogDetailLevel(level);
        detail.setJobLogDetailContent(content);
        detail.setJobLogDetailPhase(phase);
        detail.setJobLogDetailProgress(progress);
        detail.setJobLogDetailTime(LocalDateTime.now());
        detail.setCreateTime(LocalDateTime.now());
        jobLogDetailMapper.insert(detail);
        appendLogFile(detail, JobFileAppender.makeLogFileName(
                java.sql.Timestamp.valueOf(LocalDateTime.now()), jobLogId));

        return detail;
    }

    public SysJobLogDetail info(JobContext jobContext, String content) {
        return log(jobContext, "INFO", content, null, null);
    }

    public SysJobLogDetail info(JobContext jobContext, String content, String phase, Integer progress) {
        return log(jobContext, "INFO", content, phase, progress);
    }

    public SysJobLogDetail warn(JobContext jobContext, String content) {
        return log(jobContext, "WARN", content, null, null);
    }

    public SysJobLogDetail error(JobContext jobContext, String content) {
        return log(jobContext, "ERROR", content, null, null);
    }

    public SysJobLogDetail error(JobContext jobContext, String content, Throwable t) {
        StringBuilder sb = new StringBuilder(content);
        if (t != null) {
            sb.append("\n").append(t.getClass().getName()).append(": ").append(t.getMessage());
            for (StackTraceElement element : t.getStackTrace()) {
                sb.append("\n\tat ").append(element);
                if (sb.length() > 4000) {
                    sb.append("\n\t...(truncated)");
                    break;
                }
            }
        }
        return log(jobContext, "ERROR", sb.toString(), null, null);
    }

    /**
     * 记录 INFO 级别日志
     */
    public SysJobLogDetail info(Integer jobLogId, Integer jobId, String content) {
        return log(jobLogId, jobId, "INFO", content);
    }

    /**
     * 记录 INFO 级别日志（带阶段和进度）
     */
    public SysJobLogDetail info(Integer jobLogId, Integer jobId, String content, String phase, Integer progress) {
        return log(jobLogId, jobId, "INFO", content, phase, progress);
    }

    /**
     * 记录 WARN 级别日志
     */
    public SysJobLogDetail warn(Integer jobLogId, Integer jobId, String content) {
        return log(jobLogId, jobId, "WARN", content);
    }

    /**
     * 记录 ERROR 级别日志
     */
    public SysJobLogDetail error(Integer jobLogId, Integer jobId, String content) {
        return log(jobLogId, jobId, "ERROR", content);
    }

    /**
     * 记录 ERROR 级别日志（带异常）
     */
    public SysJobLogDetail error(Integer jobLogId, Integer jobId, String content, Throwable t) {
        StringBuilder sb = new StringBuilder(content);
        if (t != null) {
            sb.append("\n").append(t.getClass().getName()).append(": ").append(t.getMessage());
            for (StackTraceElement element : t.getStackTrace()) {
                sb.append("\n\tat ").append(element.toString());
                if (sb.length() > 4000) {
                    sb.append("\n\t...(truncated)");
                    break;
                }
            }
        }
        return log(jobLogId, jobId, "ERROR", sb.toString());
    }

    /**
     * 记录 DEBUG 级别日志
     */
    public SysJobLogDetail debug(Integer jobLogId, Integer jobId, String content) {
        return log(jobLogId, jobId, "DEBUG", content);
    }

    /**
     * 批量保存日志详情
     *
     * @param details 日志详情列表
     * @return 保存数量
     */
    public int batchSave(List<SysJobLogDetail> details) {
        if (details == null || details.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (SysJobLogDetail detail : details) {
            detail.setCreateTime(LocalDateTime.now());
            if (detail.getJobLogDetailTime() == null) {
                detail.setJobLogDetailTime(LocalDateTime.now());
            }
            jobLogDetailMapper.insert(detail);
            count++;
        }
        return count;
    }

    /**
     * 根据任务日志ID查询详情列表
     *
     * @param jobLogId 任务日志ID
     * @return 详情列表
     */
    public List<SysJobLogDetail> getByJobLogId(Integer jobLogId) {
        return jobLogDetailMapper.selectByJobLogId(jobLogId);
    }

    /**
     * 根据任务ID查询最近的日志详情
     *
     * @param jobId 任务ID
     * @return 详情列表
     */
    public List<SysJobLogDetail> getRecentByJobId(Integer jobId) {
        return jobLogDetailMapper.selectByJobId(jobId);
    }

    /**
     * 清理指定时间之前的日志详情
     *
     * @param beforeTime 截止时间
     * @return 清理数量
     */
    public int cleanBeforeTime(LocalDateTime beforeTime) {
        int count = jobLogDetailMapper.deleteBeforeTime(beforeTime);
        log.info("清理 {} 之前的日志详情, 共 {} 条", beforeTime, count);
        return count;
    }

    /**
     * 删除指定任务日志的所有详情
     *
     * @param jobLogId 任务日志ID
     * @return 删除数量
     */
    public int deleteByJobLogId(Integer jobLogId) {
        return jobLogDetailMapper.deleteByJobLogId(jobLogId);
    }

    /**
     * 格式化日志行
     */
    private String formatLogLine(SysJobLogDetail detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(detail.getJobLogDetailTime().format(TIME_FORMATTER)).append("]");
        sb.append(" [").append(String.format("%-5s", detail.getJobLogDetailLevel())).append("]");
        if (detail.getJobLogDetailPhase() != null) {
            sb.append(" [").append(detail.getJobLogDetailPhase()).append("]");
        }
        if (detail.getJobLogDetailProgress() != null) {
            sb.append(" [").append(detail.getJobLogDetailProgress()).append("%]");
        }
        sb.append(" ").append(detail.getJobLogDetailContent());
        return sb.toString();
    }

    /**
     * 读取日志文件内容
     *
     * @param jobLogId 任务日志ID
     * @param fromLine 起始行
     * @return 日志结果
     */
    public LogResult readLogFile(Integer jobLogId, int fromLine) {
        String logFileName = JobFileAppender.makeLogFileName(
                java.sql.Timestamp.valueOf(LocalDateTime.now()), jobLogId);
        return JobFileAppender.readLog(logFileName, fromLine);
    }

    public LogResult readLogFile(String logFileName, int fromLine) {
        return JobFileAppender.readLog(logFileName, fromLine);
    }

    private void appendLogFile(SysJobLogDetail detail, String logFileName) {
        if (!StringUtils.hasText(logFileName)) {
            return;
        }
        JobFileAppender.appendLog(logFileName, formatLogLine(detail));
    }

    private String stringAttribute(JobContext jobContext, String key) {
        if (jobContext.getAttributes() == null) {
            return null;
        }
        Object value = jobContext.getAttributes().get(key);
        return value == null ? null : String.valueOf(value);
    }
}
