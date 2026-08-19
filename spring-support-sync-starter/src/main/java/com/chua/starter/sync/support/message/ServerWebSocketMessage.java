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

    /** 获取MessageType */
    public String getMessageType() {
        return messageType;
    }

    /** 设置MessageType */
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    /** 获取DataId */
    public String getDataId() {
        return dataId;
    }

    /** 设置DataId */
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    /** 获取Data */
    public Map<String, Object> getData() {
        return data;
    }

    /** 设置Data */
    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    /** 获取Timestamp */
    public long getTimestamp() {
        return timestamp;
    }

    /** 设置Timestamp */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /** Builder */
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

        /** MessageType */
        public Builder messageType(String messageType) {
            this.messageType = messageType;
            return this;
        }

        /** DataId */
        public Builder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        /** Data */
        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        /** Timestamp */
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /** 构建 */
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
