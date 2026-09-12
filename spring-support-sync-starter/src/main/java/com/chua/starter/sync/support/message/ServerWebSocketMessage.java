package com.chua.starter.sync.support.message;

import java.util.Map;

/**
   * web套接字 推送消息体
 * 用于在同步任务执行过程中向前端推送实时状态、日志、进度等信息
 *
 * @since 2024/12/19
   * @版本 1.0.0
 * @author CH
 */
public class ServerWebSocketMessage {

    /**
      * 消息类型 (同步_任务_状态/同步_任务_日志/同步_任务_进步/同步_任务_指标)
     */
    private String messageType;

    /**
      * 数据标识 (任务标识)
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

    /**
     * 获取消息类型
     *
     * @return 获取消息类型的结果
     */
    public String getMessageType() {
        return messageType;
    }

    /**
     * 设置消息类型
     *
     * @param messageType 消息类型
     */
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    /**
      * 获取数据标识
     *
     * @return 获取数据id的结果
     */
    public String getDataId() {
        return dataId;
    }

    /**
      * 设置数据标识
     *
     * @param dataId 数据标识
     */
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    /**
     * 获取数据
     *
     * @return 获取数据的结果
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * 设置数据
     *
     * @param data 数据
     */
    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    /**
     * 获取时间戳
     *
     * @return 获取时间戳的结果
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 设置时间戳
     *
     * @param timestamp 时间戳
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 构建器
     *
     * @return 构建器的结果
     */
    public static Builder builder() {
        /**
         * 构建器。
         * @return 构建器的结果
         */
        return new Builder();
    }

    /**
     * 构建器类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Builder {
        /** 消息类型 */
        private String messageType;
        /** 数据标识 */
        private String dataId;
        private Map<String, Object> data; // 数据
        /** 时间戳 */
        private long timestamp;

        /**
         * 消息类型
         *
         * @param messageType 消息类型
         * @return 消息类型的结果
         */
        public Builder messageType(String messageType) {
            this.messageType = messageType;
            return this;
        }

        /**
          * 数据标识
         *
         * @param dataId 数据标识
         * @return 数据id的结果
         */
        public Builder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        /**
         * 数据
         *
         * @param data 数据
         * @return 数据的结果
         */
        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        /**
         * 时间戳
         *
         * @param timestamp 时间戳
         * @return 时间戳的结果
         */
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * 构建
         *
         * @return 构建的结果
         */
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
