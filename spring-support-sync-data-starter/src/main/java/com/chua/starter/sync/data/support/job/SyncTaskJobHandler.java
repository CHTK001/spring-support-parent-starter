package com.chua.starter.sync.data.support.job;

import com.chua.common.support.text.json.Json;
import com.chua.starter.job.support.annotation.Job;
import com.chua.starter.job.support.entity.SysJobLog;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.mapper.SysJobLogMapper;
import com.chua.starter.job.support.thread.JobContext;
import com.chua.starter.sync.data.support.service.sync.MonitorSyncTaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 同步任务 JobHandler
 * <p>
 * 集成 spring-job 的任务处理器，支持通过定时任务触发同步任务执行。
 * 可以在 monitor_job 表中配置：
 * - job_execute_bean: syncTaskJobHandler
 * - job_execute_param: 同步任务ID（如: 1 或 1,2,3 多个任务）
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 * @version 1.0.0
 */
@Slf4j
@Component("syncTaskJobHandler")
public class SyncTaskJobHandler implements JobHandler {

    @Autowired
    private MonitorSyncTaskExecutor syncTaskExecutor;

    @Autowired(required = false)
    private SysJobLogMapper sysJobLogMapper;

    @Override
    @Job(value = "syncTaskJobHandler", init = "init", destroy = "destroy")
    public void execute() throws Exception {
        String param = JobContext.getJobParam();
        if (param == null || param.trim().isEmpty()) {
            JobContext context = JobContext.getJobContext();
            if (context != null) {
                context.setFail("同步任务参数为空");
            }
            log.warn("同步任务参数为空，请在 job_execute_param 中配置任务ID");
            return;
        }

        log.info("开始执行同步任务, 参数: {}", param);

        List<Long> taskIds = resolveTaskIds(param);
        String triggerType = resolveTriggerType();

        for (Long taskId : taskIds) {
            try {
                Long logId = syncTaskExecutor.executeOnce(taskId, triggerType);
                log.info("同步任务执行完成, taskId: {}, logId: {}", taskId, logId);
            } catch (Exception e) {
                JobContext context = JobContext.getJobContext();
                if (context != null) {
                    context.setFail("同步任务执行失败: " + e.getMessage());
                }
                log.error("同步任务执行失败, taskId: {}", taskId, e);
                throw e;
            }
        }

        JobContext context = JobContext.getJobContext();
        if (context != null) {
            context.setSuccess("同步任务执行完成");
        }
    }

    @Override
    public void init() throws Exception {
        log.debug("SyncTaskJobHandler 初始化");
    }

    @Override
    public void destroy() throws Exception {
        log.debug("SyncTaskJobHandler 销毁");
    }

    private List<Long> resolveTaskIds(String param) {
        List<Long> taskIds = new ArrayList<>();
        String trimmed = param.trim();

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            Map<String, Object> config = Json.fromJson(trimmed, Map.class);
            Object taskId = config.get("taskId");
            if (taskId instanceof Number number) {
                taskIds.add(number.longValue());
            } else if (taskId != null) {
                taskIds.add(Long.parseLong(String.valueOf(taskId)));
            }

            Object taskIdList = config.get("taskIds");
            if (taskIdList instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    taskIds.add(Long.parseLong(String.valueOf(item)));
                }
            }
            return taskIds;
        }

        String[] taskIdArray = trimmed.split(",");
        for (String taskIdStr : taskIdArray) {
            taskIds.add(Long.parseLong(taskIdStr.trim()));
        }
        return taskIds;
    }

    private String resolveTriggerType() {
        long currentJobLogId = JobContext.getCurrentJobLogId();
        if (currentJobLogId > 0 && sysJobLogMapper != null) {
            SysJobLog jobLog = sysJobLogMapper.selectById((int) currentJobLogId);
            if (jobLog != null && "API".equalsIgnoreCase(jobLog.getJobLogTriggerType())) {
                return "API";
            }
        }

        if (JobContext.getCurrentJobId() > 0) {
            return "SCHEDULE";
        }
        return "MANUAL";
    }
}
