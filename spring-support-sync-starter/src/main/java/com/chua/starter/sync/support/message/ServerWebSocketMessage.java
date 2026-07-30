package com.chua.starter.sync.support.message;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * WebSocket 推送消息体
 * 用于在同步任务执行过程中向前端推送实时状态、日志、进度等信息
 *
 * @author CH
 * @since 2024/12/19
 * @version 1.0.0
 */
@Data
@Builder
public class ServerWebSocketMessage {

    /**
     * 消息类型 (sync_task_status/sync_task_log/sync_task_progress/sync_task_metrics)
     */
    private String messageType;

    /**
     * 数据ID (任务ID)
     */
    private String dataId;

    /**
     * 消息数据
     */
    private Map<String, Object> data;

    /**
     * 时间戳 (毫秒)
     */
    private long timestamp;
}
