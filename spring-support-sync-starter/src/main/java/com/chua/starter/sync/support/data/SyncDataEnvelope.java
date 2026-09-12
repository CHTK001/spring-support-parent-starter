package com.chua.starter.sync.support.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
* 数据同步消息信封。
*
* @since 2026/03/23
* @author CH
 */
public class SyncDataEnvelope implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
    * 请求标识
     */
    private String requestId;

    /**
    * 逻辑通道
     */
    private String channel;

    /**
    * 事件类型
     */
    private String eventType;

    /**
    * 源客户端标识
     */
    private String sourceClientId;

    /**
    * 目标应用名
     */
    private String targetAppName;

    /**
    * 负载数据
     */
    private Object payload;

    /**
    * 扩展元数据
     */
    private Map<String, Object> metadata = new HashMap<>();

    /**
    * 发送时间戳
     */
    private long timestamp;

    /**
    * 获取请求标识
    *
    * @return 获取请求id的结果
     */
    public String getRequestId() {
        return requestId;
    }

    /**
    * 设置请求标识
    *
    * @param requestId 请求标识
     */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
    * 获取通道
    *
    * @return 获取通道的结果
     */
    public String getChannel() {
        return channel;
    }

    /**
    * 设置通道
    *
    * @param channel 通道
     */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    /**
    * 获取事件类型
    *
    * @return 获取事件类型的结果
     */
    public String getEventType() {
        return eventType;
    }

    /**
    * 设置事件类型
    *
    * @param eventType 事件类型
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
    * 获取源客户端标识
    *
    * @return 获取源客户端id的结果
     */
    public String getSourceClientId() {
        return sourceClientId;
    }

    /**
    * 设置源客户端标识
    *
    * @param sourceClientId 源客户端标识
     */
    public void setSourceClientId(String sourceClientId) {
        this.sourceClientId = sourceClientId;
    }

    /**
    * 获取Targetapp名称
    *
    * @return 获取Targetapp名称的结果
     */
    public String getTargetAppName() {
        return targetAppName;
    }

    /**
    * 设置Targetapp名称
    *
    * @param targetAppName Targetapp名称
     */
    public void setTargetAppName(String targetAppName) {
        this.targetAppName = targetAppName;
    }

    /**
    * 获取Payload
    *
    * @return 获取payload的结果
     */
    public Object getPayload() {
        return payload;
    }

    /**
    * 设置Payload
    *
    * @param payload payload
     */
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    /**
    * 获取Metadata
    *
    * @return 获取metadata的结果
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
    * 设置Metadata
    *
    * @param metadata metadata
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
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
        /** 请求标识 */
        private String requestId;
        /** 通道 */
        private String channel;
        /** 事件类型 */
        private String eventType;
        /** 来源客户端标识 */
        private String sourceClientId;
        /** 目标APP名称 */
        private String targetAppName;
        /** Payload */
        private Object payload;
        private Map<String, Object> metadata = new HashMap<>(); // metadata
        /** 时间戳 */
        private long timestamp;

        /**
        * 请求标识
        *
        * @param requestId 请求标识
        * @return 请求id的结果
         */
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        /**
        * 通道
        *
        * @param channel 通道
        * @return 通道的结果
         */
        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        /**
        * 事件类型
        *
        * @param eventType 事件类型
        * @return 事件类型的结果
         */
        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        /**
        * 源客户端标识
        *
        * @param sourceClientId 源客户端标识
        * @return 源客户端id的结果
         */
        public Builder sourceClientId(String sourceClientId) {
            this.sourceClientId = sourceClientId;
            return this;
        }

        /**
        * Targetapp名称
        *
        * @param targetAppName Targetapp名称
        * @return Targetapp名称的结果
         */
        public Builder targetAppName(String targetAppName) {
            this.targetAppName = targetAppName;
            return this;
        }

        /**
        * Payload
        *
        * @param payload payload
        * @return payload的结果
         */
        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        /**
        * Metadata
        *
        * @param metadata metadata
        * @return metadata的结果
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
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
        public SyncDataEnvelope build() {
            SyncDataEnvelope envelope = new SyncDataEnvelope();
            envelope.setRequestId(requestId);
            envelope.setChannel(channel);
            envelope.setEventType(eventType);
            envelope.setSourceClientId(sourceClientId);
            envelope.setTargetAppName(targetAppName);
            envelope.setPayload(payload);
            envelope.setMetadata(metadata);
            envelope.setTimestamp(timestamp);
            return envelope;
        }
    }
}
