package com.chua.starter.sync.support.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据同步消息信封。
 *
 * @author CH
 * @since 2026/03/23
 */
public class SyncDataEnvelope implements Serializable {

    /**
     * 请求ID
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
     * 源客户端ID
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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSourceClientId() {
        return sourceClientId;
    }

    public void setSourceClientId(String sourceClientId) {
        this.sourceClientId = sourceClientId;
    }

    public String getTargetAppName() {
        return targetAppName;
    }

    public void setTargetAppName(String targetAppName) {
        this.targetAppName = targetAppName;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
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
        private String requestId;
        private String channel;
        private String eventType;
        private String sourceClientId;
        private String targetAppName;
        private Object payload;
        private Map<String, Object> metadata = new HashMap<>();
        private long timestamp;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder sourceClientId(String sourceClientId) {
            this.sourceClientId = sourceClientId;
            return this;
        }

        public Builder targetAppName(String targetAppName) {
            this.targetAppName = targetAppName;
            return this;
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

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
