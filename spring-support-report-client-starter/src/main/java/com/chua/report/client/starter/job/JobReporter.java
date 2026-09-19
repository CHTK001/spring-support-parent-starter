package com.chua.report.client.starter.job;

import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.starter.sync.support.client.SyncClient;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 作业 结果上报器
 * <p>
 * 通过 同步协议 上报任务执行结果
 * </p>
 *
 * @版本 1.0.0
 * @since 2024/12/05
 * @author CH
 */
@Slf4j
public class JobReporter {


    /** 实例 */
    private static final JobReporter INSTANCE = new JobReporter();

    @Setter
    /** 同步客户端 */
    private SyncClient syncClient;

    /**
     * 获取Instance
     *
     * @return 获取instance的结果
     */
    public static JobReporter getInstance() {
        return INSTANCE;
    }

    /**
     * 上报任务结果
     * @param jobId 作业标识
     * @param logId 日志标识
     * @param status 状态
     * @param message 消息
     */
    public void report(int jobId, long logId, String status, String message) {
        if (syncClient == null || !syncClient.isConnected()) {
            log.warn("[JobReporter] SyncClient 未连接，无法上报结果: jobId={}", jobId);
            return;
        }

        try {
            Map<String, Object> result = Map.of(
                    "jobId", jobId,
                    "logId", logId,
                    "status", status,
                    "message", message != null ? message : "",
                    "clientId", syncClient.getClientId(),
                    "timestamp", System.currentTimeMillis()
            );
            syncClient.publish(MonitorTopics.JOB_RESULT, result);
            log.debug("[JobReporter] 任务结果已上报: jobId={}, status={}", jobId, status);
        } catch (Exception e) {
            log.error("[JobReporter] 上报任务结果失败: jobId={}", jobId, e);
        }
    }
}
