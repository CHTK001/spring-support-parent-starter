package com.chua.starter.sync.data.support.service.sync;

import com.chua.common.support.text.json.Json;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.sync.data.support.entity.MonitorSyncTask;
import com.chua.starter.sync.data.support.entity.MonitorSyncTaskLog;
import com.chua.starter.sync.data.support.service.SyncMonitorService;
import com.chua.starter.sync.data.support.websocket.SyncProgressWebSocketHandler;
import com.chua.starter.sync.support.message.ServerWebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同步任务 WebSocket 推送服务
 * 用于推送同步任务的实时日志、状态变更、执行进度等
 *
 * @author CH
 * @since 2024/12/19
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SyncTaskWebSocketService {

    private final ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider;
    private final SyncProgressWebSocketHandler syncProgressWebSocketHandler;
    private final SyncMonitorService syncMonitorService;

    public static final String TOPIC_SYNC_TASK_STATUS = "monitor:sync:status";
    public static final String TOPIC_SYNC_TASK_LOG = "monitor:sync:log";
    public static final String TOPIC_SYNC_TASK_PROGRESS = "monitor:sync:progress";
    public static final String TOPIC_SYNC_TASK_METRICS = "monitor:sync:metrics";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 任务实时快照
     */
    private final Map<Long, Map<String, Object>> realtimeSnapshots = new ConcurrentHashMap<>();

    /**
     * 推送任务状态变更
     *
     * @param taskId   任务ID
     * @param taskName 任务名称
     * @param status   新状态 (RUNNING/STOPPED/ERROR)
     * @param message  状态消息
     */
    public void pushTaskStatus(Long taskId, String taskName, String status, String message) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);
            data.put("taskName", taskName);
            data.put("status", status);
            data.put("message", message);
            data.put("time", LocalDateTime.now().format(TIME_FORMATTER));

            Map<String, Object> payload = updateRealtimeSnapshot(taskId, data);
            syncRealtimeProgress(taskId, payload);
            sendTaskMessage(TOPIC_SYNC_TASK_STATUS, "sync_task_status", taskId, payload);
            log.debug("推送任务状态变更: taskId={}, status={}", taskId, status);

        } catch (Exception e) {
            log.error("推送任务状态失败: taskId={}", taskId, e);
        }
    }

    /**
     * 推送实时执行日志
     *
     * @param taskId  任务ID
     * @param logId   执行日志ID
     * @param level   日志级别 (DEBUG/INFO/WARN/ERROR)
     * @param nodeKey 相关节点Key (可为null)
     * @param message 日志消息
     */
    public void pushTaskLog(Long taskId, Long logId, String level, String nodeKey, String message) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);
            data.put("logId", logId);
            data.put("level", level);
            data.put("nodeKey", nodeKey);
            data.put("message", message);
            data.put("time", LocalDateTime.now().format(TIME_FORMATTER));

            sendTaskMessage(TOPIC_SYNC_TASK_LOG, "sync_task_log", taskId, data);
            log.trace("推送任务日志: taskId={}, level={}, message={}", taskId, level, message);

        } catch (Exception e) {
            log.error("推送任务日志失败: taskId={}", taskId, e);
        }
    }

    /**
     * 推送执行进度
     *
     * @param taskId       任务ID
     * @param logId        执行日志ID
     * @param readCount    读取数量
     * @param writeCount   写入数量
     * @param successCount 成功数量
     * @param failCount    失败数量
     * @param progress     进度百分比 (0-100)
     */
    public void pushTaskProgress(Long taskId, Long logId, long readCount, long writeCount,
                                 long successCount, long failCount, int progress) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);
            data.put("logId", logId);
            data.put("readCount", readCount);
            data.put("writeCount", writeCount);
            data.put("successCount", successCount);
            data.put("failCount", failCount);
            data.put("progress", progress);
            data.put("time", LocalDateTime.now().format(TIME_FORMATTER));

            Map<String, Object> payload = updateRealtimeSnapshot(taskId, data);
            syncRealtimeProgress(taskId, payload);
            sendTaskMessage(TOPIC_SYNC_TASK_PROGRESS, "sync_task_progress", taskId, payload);
            log.debug("推送任务进度: taskId={}, progress={}%", taskId, progress);

        } catch (Exception e) {
            log.error("推送任务进度失败: taskId={}", taskId, e);
        }
    }

    /**
     * 推送执行指标
     *
     * @param taskId         任务ID
     * @param logId          执行日志ID
     * @param throughput     吞吐量 (条/秒)
     * @param avgProcessTime 平均处理时间 (毫秒)
     * @param cpuUsage       CPU使用率
     * @param memoryUsage    内存使用率
     */
    public void pushTaskMetrics(Long taskId, Long logId, double throughput, double avgProcessTime,
                                double cpuUsage, double memoryUsage) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);
            data.put("logId", logId);
            data.put("throughput", throughput);
            data.put("avgProcessTime", avgProcessTime);
            data.put("cpuUsage", cpuUsage);
            data.put("memoryUsage", memoryUsage);
            data.put("time", LocalDateTime.now().format(TIME_FORMATTER));

            Map<String, Object> payload = updateRealtimeSnapshot(taskId, data);
            syncRealtimeProgress(taskId, payload);
            sendTaskMessage(TOPIC_SYNC_TASK_METRICS, "sync_task_metrics", taskId, payload);
            log.debug("推送任务指标: taskId={}, throughput={}/s", taskId, throughput);

        } catch (Exception e) {
            log.error("推送任务指标失败: taskId={}", taskId, e);
        }
    }

    /**
     * 推送任务执行开始
     *
     * @param task    任务实体
     * @param taskLog 执行日志
     */
    public void pushTaskStarted(MonitorSyncTask task, MonitorSyncTaskLog taskLog) {
        pushTaskStatus(task.getSyncTaskId(), task.getSyncTaskName(), "RUNNING", "任务开始执行");
        pushTaskProgress(task.getSyncTaskId(), taskLog.getSyncLogId(),
                resolveCount(taskLog.getSyncLogReadCount()),
                resolveCount(taskLog.getSyncLogWriteCount()),
                resolveCount(taskLog.getSyncLogSuccessCount()),
                resolveCount(taskLog.getSyncLogFailCount()),
                0);
        pushTaskLog(task.getSyncTaskId(), taskLog.getSyncLogId(), "INFO", null,
                "任务开始执行, 触发类型: " + taskLog.getSyncLogTriggerType());
    }

    /**
     * 推送任务执行完成
     *
     * @param task    任务实体
     * @param taskLog 执行日志
     */
    public void pushTaskCompleted(MonitorSyncTask task, MonitorSyncTaskLog taskLog) {
        pushTaskProgress(task.getSyncTaskId(), taskLog.getSyncLogId(),
                resolveCount(taskLog.getSyncLogReadCount()),
                resolveCount(taskLog.getSyncLogWriteCount()),
                resolveCount(taskLog.getSyncLogSuccessCount()),
                resolveCount(taskLog.getSyncLogFailCount()),
                "SUCCESS".equals(taskLog.getSyncLogStatus()) ? 100 : extractProgress(task.getSyncTaskId()));

        if (taskLog.getSyncLogThroughput() != null || taskLog.getSyncLogAvgProcessTime() != null) {
            pushTaskMetrics(task.getSyncTaskId(), taskLog.getSyncLogId(),
                    taskLog.getSyncLogThroughput() != null ? taskLog.getSyncLogThroughput() : 0D,
                    taskLog.getSyncLogAvgProcessTime() != null ? taskLog.getSyncLogAvgProcessTime() : 0D,
                    0D,
                    0D);
        }

        pushTaskStatus(task.getSyncTaskId(), task.getSyncTaskName(), "STOPPED", "任务执行完成");
        pushTaskLog(task.getSyncTaskId(), taskLog.getSyncLogId(), "INFO", null,
                "任务执行完成, 耗时: " + taskLog.getSyncLogCost() + "ms");
    }

    /**
     * 推送任务执行失败
     *
     * @param task    任务实体
     * @param taskLog 执行日志
     * @param error   错误信息
     */
    public void pushTaskError(MonitorSyncTask task, MonitorSyncTaskLog taskLog, String error) {
        pushTaskProgress(task.getSyncTaskId(), taskLog.getSyncLogId(),
                resolveCount(taskLog.getSyncLogReadCount()),
                resolveCount(taskLog.getSyncLogWriteCount()),
                resolveCount(taskLog.getSyncLogSuccessCount()),
                resolveCount(taskLog.getSyncLogFailCount()),
                extractProgress(task.getSyncTaskId()));
        pushTaskStatus(task.getSyncTaskId(), task.getSyncTaskName(), "ERROR", error);
        pushTaskLog(task.getSyncTaskId(), taskLog.getSyncLogId(), "ERROR", null,
                "任务执行失败: " + error);
    }

    public void pushError(Long taskId, Long logId, String nodeKey, String message, Exception error) {
        String errorMessage = message;
        if (error != null) {
            errorMessage = message + ": " + error.getMessage();
        }
        pushTaskStatus(taskId, null, "ERROR", errorMessage);
        pushTaskLog(taskId, logId, "ERROR", nodeKey, errorMessage);
    }

    private void sendTaskMessage(String topic, String messageType, Long taskId, Map<String, Object> data) {
        long timestamp = System.currentTimeMillis();
        ServerWebSocketMessage wsMessage = ServerWebSocketMessage.builder()
                .messageType(messageType)
                .dataId(String.valueOf(taskId))
                .data(data)
                .timestamp(timestamp)
                .build();

        SocketSessionTemplate socketSessionTemplate = socketSessionTemplateProvider.getIfAvailable();
        if (socketSessionTemplate != null) {
            socketSessionTemplate.send(topic, Json.toJson(wsMessage));
        }

        Map<String, Object> broadcastPayload = new HashMap<>(data);
        broadcastPayload.put("messageType", messageType);
        broadcastPayload.put("timestamp", timestamp);
        syncProgressWebSocketHandler.broadcastTaskMessage(taskId, broadcastPayload);
    }

    private Map<String, Object> updateRealtimeSnapshot(Long taskId, Map<String, Object> updates) {
        if (taskId == null) {
            return new HashMap<>(updates);
        }

        Map<String, Object> snapshot = realtimeSnapshots.computeIfAbsent(taskId, key -> new HashMap<>());
        synchronized (snapshot) {
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                if (entry.getValue() != null) {
                    snapshot.put(entry.getKey(), entry.getValue());
                }
            }
            snapshot.put("taskId", taskId);
            return new HashMap<>(snapshot);
        }
    }

    private void syncRealtimeProgress(Long taskId, Map<String, Object> payload) {
        if (taskId == null) {
            return;
        }

        long processedRecords = resolveProcessedRecords(payload);
        long totalRecords = resolveTotalRecords(payload, processedRecords);
        BigDecimal throughput = BigDecimal.valueOf(resolveDouble(payload.get("throughput")));
        syncMonitorService.recordProgress(taskId, processedRecords, totalRecords, throughput);
    }

    private long resolveProcessedRecords(Map<String, Object> payload) {
        long readCount = resolveLong(payload.get("readCount"));
        long writeCount = resolveLong(payload.get("writeCount"));
        long successCount = resolveLong(payload.get("successCount"));
        long failCount = resolveLong(payload.get("failCount"));
        return Math.max(Math.max(readCount, writeCount), successCount + failCount);
    }

    private long resolveTotalRecords(Map<String, Object> payload, long processedRecords) {
        long totalRecords = resolveLong(payload.get("totalRecords"));
        if (totalRecords > 0) {
            return totalRecords;
        }

        int progress = (int) resolveLong(payload.get("progress"));
        if (processedRecords > 0 && progress > 0 && progress < 100) {
            return Math.max(processedRecords, Math.round(processedRecords * 100.0 / progress));
        }

        if (progress >= 100) {
            return processedRecords;
        }

        return processedRecords;
    }

    private int extractProgress(Long taskId) {
        Map<String, Object> snapshot = realtimeSnapshots.get(taskId);
        return snapshot == null ? 0 : (int) resolveLong(snapshot.get("progress"));
    }

    private long resolveCount(Long value) {
        return value == null ? 0L : value;
    }

    private long resolveLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double resolveDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0D;
        }
    }
}
