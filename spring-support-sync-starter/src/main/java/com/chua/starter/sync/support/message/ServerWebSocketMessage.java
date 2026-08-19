package com.chua.starter.sync.support.message;

import java.util.Map;

/**
 * WebSocket 推送消息体
 * 用于在同步任务执行过程中向前端推送实时状态、日志、进度等信息
 *
 * @since 2024/12/19
 * @version 1.0.0
 * @author CH
 */
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

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        /** 消息类型 */
        private String messageType;
        /** 数据ID */
        private String dataId;
        private Map<String, Object> data;
        /** 时间戳 */
        private long timestamp;

        public Builder messageType(String messageType) {
            this.messageType = messageType;
            return this;
        }

        public Builder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ServerWebSocketMessage build() {
            ServerWebSocketMessage message = new ServerWebSocketMessage();
            message.setMessageType(messageType);
            message.setDataId(dataId);
            message.setData(data);
            message.setTimestamp(timestamp);
            return message;
        }
    }
}
