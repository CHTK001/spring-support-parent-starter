package com.chua.starter.sync.data.support.service.impl;

import com.chua.common.support.text.json.Json;
import com.chua.starter.job.support.entity.SysJobLog;
import com.chua.starter.job.support.mapper.SysJobLogMapper;
import com.chua.starter.job.support.thread.JobContext;
import com.chua.starter.sync.data.support.entity.MonitorSyncTask;
import com.chua.starter.sync.data.support.entity.MonitorSyncTaskLog;
import com.chua.starter.sync.data.support.properties.SyncJobIntegrationProperties;
import com.chua.starter.sync.data.support.service.sync.SyncJobIntegrationService;
import com.chua.starter.sync.data.support.service.sync.SyncTaskLogAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sync 日志适配器实现
 *
 * @author CH
 * @since 2026/03/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(SysJobLogMapper.class)
@ConditionalOnProperty(prefix = SyncJobIntegrationProperties.PRE, name = "enabled", havingValue = "true")
public class SyncTaskLogAdapterImpl implements SyncTaskLogAdapter {

    private final SysJobLogMapper sysJobLogMapper;
    private final SyncJobIntegrationService syncJobIntegrationService;
    private final SyncJobIntegrationProperties properties;

    private final Map<Long, Integer> syncLogJobMapping = new ConcurrentHashMap<>();

    @Override
    public Integer createJobLog(MonitorSyncTask task, MonitorSyncTaskLog syncLog) {
        Integer currentJobLogId = resolveCurrentJobLogId();
        if (currentJobLogId != null) {
            syncLogJobMapping.put(syncLog.getSyncLogId(), currentJobLogId);
            updateRunningJobLog(currentJobLogId, task, syncLog);
            return currentJobLogId;
        }

        if (!properties.isDualLog()) {
            return null;
        }

        Integer jobId = syncJobIntegrationService.getJobId(task.getSyncTaskId());
        if (jobId == null) {
            return null;
        }

        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobLogApp("sync");
        jobLog.setJobLogTriggerBean("syncTaskJobHandler");
        jobLog.setJobLogTriggerType(resolveJobTriggerType(syncLog.getSyncLogTriggerType()));
        jobLog.setJobLogTriggerTime(new Date());
        jobLog.setJobLogTriggerDate(LocalDate.now());
        jobLog.setJobLogExecuteCode("PADDING");
        jobLog.setJobLogTriggerParam(buildExecuteParam(task.getSyncTaskId()));
        jobLog.setJobLogTriggerMsg("同步任务开始执行: " + task.getSyncTaskName());
        sysJobLogMapper.insert(jobLog);

        syncLogJobMapping.put(syncLog.getSyncLogId(), jobLog.getJobLogId());
        return jobLog.getJobLogId();
    }

    @Override
    public void updateJobLog(MonitorSyncTask task, MonitorSyncTaskLog syncLog, Integer jobLogId) {
        Integer finalJobLogId = jobLogId != null ? jobLogId : syncLogJobMapping.remove(syncLog.getSyncLogId());
        if (finalJobLogId == null) {
            return;
        }

        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobLogId(finalJobLogId);
        jobLog.setJobLogTriggerBean("syncTaskJobHandler");
        jobLog.setJobLogTriggerType(resolveJobTriggerType(syncLog.getSyncLogTriggerType()));
        jobLog.setJobLogTriggerParam(buildExecuteParam(task.getSyncTaskId()));
        jobLog.setJobLogExecuteCode(mapExecuteCode(syncLog.getSyncLogStatus()));
        if (syncLog.getSyncLogCost() != null) {
            jobLog.setJobLogCost(BigDecimal.valueOf(syncLog.getSyncLogCost()));
        }
        jobLog.setJobLogTriggerMsg(buildSummaryMessage(task, syncLog));
        sysJobLogMapper.updateById(jobLog);
        syncLogJobMapping.remove(syncLog.getSyncLogId());
    }

    private void updateRunningJobLog(Integer jobLogId, MonitorSyncTask task, MonitorSyncTaskLog syncLog) {
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobLogId(jobLogId);
        jobLog.setJobLogTriggerBean("syncTaskJobHandler");
        jobLog.setJobLogTriggerType(resolveJobTriggerType(syncLog.getSyncLogTriggerType()));
        jobLog.setJobLogTriggerParam(buildExecuteParam(task.getSyncTaskId()));
        jobLog.setJobLogExecuteCode("PADDING");
        jobLog.setJobLogTriggerMsg("同步任务开始执行: " + task.getSyncTaskName());
        sysJobLogMapper.updateById(jobLog);
    }

    private Integer resolveCurrentJobLogId() {
        long currentJobLogId = JobContext.getCurrentJobLogId();
        return currentJobLogId > 0 ? (int) currentJobLogId : null;
    }

    private String mapExecuteCode(String syncStatus) {
        if ("SUCCESS".equalsIgnoreCase(syncStatus)) {
            return "SUCCESS";
        }
        if ("RUNNING".equalsIgnoreCase(syncStatus)) {
            return "PADDING";
        }
        return "FAILURE";
    }

    private String buildSummaryMessage(MonitorSyncTask task, MonitorSyncTaskLog syncLog) {
        return String.format(
                "同步任务[%s]执行%s, 读取:%d, 写入:%d, 成功:%d, 失败:%d, 耗时:%dms, 消息:%s",
                task.getSyncTaskName(),
                "SUCCESS".equalsIgnoreCase(syncLog.getSyncLogStatus()) ? "成功" : "失败",
                defaultLong(syncLog.getSyncLogReadCount()),
                defaultLong(syncLog.getSyncLogWriteCount()),
                defaultLong(syncLog.getSyncLogSuccessCount()),
                defaultLong(syncLog.getSyncLogFailCount()),
                defaultLong(syncLog.getSyncLogCost()),
                syncLog.getSyncLogMessage()
        );
    }

    private long defaultLong(Long value) {
        return value != null ? value : 0L;
    }

    private String resolveJobTriggerType(String syncTriggerType) {
        if ("API".equalsIgnoreCase(syncTriggerType)) {
            return "API";
        }
        if ("MANUAL".equalsIgnoreCase(syncTriggerType)) {
            return "API";
        }
        return "CRON";
    }

    private String buildExecuteParam(Long taskId) {
        Map<String, Object> param = new HashMap<>();
        param.put("taskId", taskId);
        return Json.toJson(param);
    }
}
