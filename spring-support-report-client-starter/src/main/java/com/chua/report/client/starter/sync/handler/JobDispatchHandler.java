package com.chua.report.client.starter.sync.handler;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.protocol.request.HttpServletRequest;
import com.chua.common.support.protocol.request.ServletResponse;
import com.chua.report.client.starter.job.TriggerParam;
import com.chua.report.client.starter.job.execute.DefaultJobExecute;
import com.chua.report.client.starter.job.execute.JobExecute;
import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.sync.support.spi.SyncMessageHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * XXL-Job 任务下发处理器
 * <p>
 * 接收服务端下发的任务，走 XXL-Job 原有执行逻辑
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 */
@Slf4j
@Spi("jobDispatchHandler")
public class JobDispatchHandler implements SyncMessageHandler {

    private final JobExecute jobExecute = new DefaultJobExecute();

    @Override
    public String getName() {
        return "jobDispatchHandler";
    }

    @Override
    public boolean supports(String topic) {
        return MonitorTopics.JOB_DISPATCH.equals(topic) || MonitorTopics.JOB_CANCEL.equals(topic);
    }

    @Override
    public Object handle(String topic, String sessionId, Map<String, Object> data) {
        if (MonitorTopics.JOB_DISPATCH.equals(topic)) {
            return handleDispatch(data);
        } else if (MonitorTopics.JOB_CANCEL.equals(topic)) {
            return handleCancel(data);
        }
        return Map.of("code", 404, "message", "Unknown topic");
    }

    /**
     * 处理任务下发
     */
    private Object handleDispatch(Map<String, Object> data) {
        log.info("[JobHandler] 收到任务下发: jobId={}, handler={}", 
                data.get("jobId"), data.get("executorHandler"));
        try {
            TriggerParam param = buildTriggerParam(data);
            
            // 调用作业执行逻辑
            ServletResponse response = jobExecute.run(HttpServletRequest.builder().build(), param);
            
            if (response.isSuccess()) {
                log.info("[JobHandler] 任务接收成功: jobId={}", param.getJobId());
                return Map.of("code", 200, "message", "SUCCESS", "jobId", param.getJobId());
            } else {
                String errorMsg = response.getErrorMessage() != null ? response.getErrorMessage() : response.getStatusMessage();
                log.warn("[JobHandler] 任务接收失败: jobId={}, msg={}", param.getJobId(), errorMsg);
                return Map.of("code", 500, "message", errorMsg != null ? errorMsg : "任务执行失败", "jobId", param.getJobId());
            }
        } catch (Exception e) {
            log.error("[JobHandler] 处理任务下发异常", e);
            return Map.of("code", 500, "message", e.getMessage());
        }
    }

    /**
     * 处理任务取消
     */
    private Object handleCancel(Map<String, Object> data) {
        int jobId = getInt(data, "jobId");
        log.info("[JobHandler] 收到任务取消: jobId={}", jobId);
        // TODO: 实现任务取消逻辑
        return Map.of("code", 200, "message", "cancelled", "jobId", jobId);
    }

    /**
     * 构建触发参数
     */
    private TriggerParam buildTriggerParam(Map<String, Object> data) {
        TriggerParam param = new TriggerParam();
        param.setJobId(getInt(data, "jobId"));
        param.setProfile(getString(data, "profile"));
        param.setExecutorHandler(getString(data, "executorHandler"));
        param.setExecutorParams(getString(data, "executorParams"));
        param.setExecutorBlockStrategy(getString(data, "executorBlockStrategy"));
        param.setExecutorTimeout(getInt(data, "executorTimeout"));
        param.setLogId(getLong(data, "logId"));
        param.setLogDateTime(getLong(data, "logDateTime"));
        param.setGlueType(getString(data, "glueType"));
        param.setGlueSource(getString(data, "glueSource"));
        param.setGlueUpdatetime(getLong(data, "glueUpdatetime"));
        param.setBroadcastIndex(getInt(data, "broadcastIndex"));
        param.setBroadcastTotal(getInt(data, "broadcastTotal"));
        return param;
    }

    private String getString(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v != null ? v.toString() : null;
    }

    private int getInt(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v != null) return Integer.parseInt(v.toString());
        return 0;
    }

    private long getLong(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v != null) return Long.parseLong(v.toString());
        return 0L;
    }
}
