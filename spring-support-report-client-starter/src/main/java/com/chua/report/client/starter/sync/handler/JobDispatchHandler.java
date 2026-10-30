package com.chua.report.client.starter.sync.handler;

import com.chua.report.client.starter.job.TriggerParam;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.report.client.starter.job.execute.DefaultJobExecute;
import com.chua.report.client.starter.job.execute.JobExecute;
import com.chua.report.client.starter.job.thread.JobThread;
import com.chua.report.client.starter.job.thread.JobThreadFactory;
import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.starter.common.support.protocol.request.ServletRequest;
import com.chua.starter.common.support.protocol.request.ServletResponse;
import com.chua.starter.sync.support.spi.SyncMessageHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * XXL-作业 任务下发处理器
 * <p>
 * 接收服务端下发的任务，走 XXL-作业 原有执行逻辑
 * </p>
 *
 * @since 2024/12/05
 * @author CH
 */
@Slf4j
@Spi("jobDispatchHandler")
public class JobDispatchHandler implements SyncMessageHandler {


    /**
     * 作业执行
    */
    private final JobExecute jobExecute = new DefaultJobExecute();

    @Override
    /**
     * 获取名称
     * @return 返回值
    */
    public String getName() {
        return "jobDispatchHandler";
    }

    @Override
    /**
     * 支持
     * @param topic 字符串
     * @return 返回值
    */
    public boolean supports(String topic) {
        return MonitorTopics.JOB_DISPATCH.equals(topic) || MonitorTopics.JOB_CANCEL.equals(topic);
    }

    @Override
    /**
     * 处理
     * @param Map<String 参数
     * @param data 参数
     * @param sessionId 字符串
     * @param topic 字符串
     * @return 返回值
    */
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
     * @param data 数据
     * @return 处理dispatch的结果
     */
    private Object handleDispatch(Map<String, Object> data) {
        log.info("[JobHandler] 收到任务下发: jobId={}, handler={}", 
                data.get("jobId"), data.get("executorHandler"));
        try {
            TriggerParam param = buildTriggerParam(data);
            
            // 调用作业执行逻辑
            ServletResponse response = jobExecute.run(new ServletRequest(), param);
            
            if (response.success()) {
                log.info("[JobHandler] 任务接收成功: jobId={}", param.jobId());
                return Map.of("code", 200, "message", "SUCCESS", "jobId", param.jobId());
            } else {
                String errorMsg = response.message();
                log.warn("[JobHandler] 任务接收失败: jobId={}, msg={}", param.jobId(), errorMsg);
                return Map.of("code", 500, "message", errorMsg != null ? errorMsg : "任务执行失败", "jobId", param.jobId());
            }
        } catch (Exception e) {
            log.error("[JobHandler] 处理任务下发异常", e);
            return Map.of("code", 500, "message", e.getMessage());
        }
    }

    /**
     * 处理任务取消
     * @param data 数据
     * @return 处理cancel的结果
     */
    private Object handleCancel(Map<String, Object> data) {
        int jobId = getInt(data, "jobId");
        log.info("[JobHandler] 收到任务取消: jobId={}", jobId);
        JobThread jobThread = JobThreadFactory.removeJobThread(jobId, "任务被取消");
        if (jobThread == null) {
            return Map.of("code", 404, "message", "任务不存在或已结束", "jobId", jobId);
        }
        return Map.of("code", 200, "message", "cancelled", "jobId", jobId);
    }

    /**
     * 构建触发参数
     * @param data 数据
     * @return 构建trigger参数的结果
     */
    private TriggerParam buildTriggerParam(Map<String, Object> data) {
        return new TriggerParam(
                getInt(data, "jobId"),
                getString(data, "profile"),
                getString(data, "executorHandler"),
                getString(data, "executorParams"),
                getString(data, "executorBlockStrategy"),
                getString(data, "executorRouter"),
                getInt(data, "executorTimeout"),
                getLong(data, "logId"),
                getLong(data, "logDateTime"),
                getString(data, "glueType"),
                getString(data, "glueSource"),
                getLong(data, "glueUpdatetime"),
                getInt(data, "broadcastIndex"),
                getInt(data, "broadcastTotal"));
    }

    /**
     * 获取字符串
     *
     * @param data 数据
     * @param key 键
     * @return 获取字符串的结果
     */
    private String getString(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * 获取Int
     *
     * @param data 数据
     * @param key 键
     * @return 获取int的结果
     */
    private int getInt(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v != null) {
            return Integer.parseInt(v.toString());
        }
        return 0;
    }

    /**
     * 获取Long
     *
     * @param data 数据
     * @param key 键
     * @return 获取long的结果
     */
    private long getLong(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        if (v != null) {
            return Long.parseLong(v.toString());
        }
        return 0L;
    }
}
