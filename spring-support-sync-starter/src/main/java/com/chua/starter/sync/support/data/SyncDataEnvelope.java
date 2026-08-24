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

    private static final long serialVersionUID = 1L;

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

    /** 获取RequestId */
    public String getRequestId() {
        return requestId;
    }

    /** 设置RequestId */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /** 获取Channel */
    public String getChannel() {
        return channel;
    }

    /** 设置Channel */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    /** 获取EventType */
    public String getEventType() {
        return eventType;
    }

    /** 设置EventType */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /** 获取SourceClientId */
    public String getSourceClientId() {
        return sourceClientId;
    }

    /** 设置SourceClientId */
    public void setSourceClientId(String sourceClientId) {
        this.sourceClientId = sourceClientId;
    }

    /** 获取TargetAppName */
    public String getTargetAppName() {
        return targetAppName;
    }

    /** 设置TargetAppName */
    public void setTargetAppName(String targetAppName) {
        this.targetAppName = targetAppName;
    }

    /** 获取Payload */
    public Object getPayload() {
        return payload;
    }

    /** 设置Payload */
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    /** 获取Metadata */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /** 设置Metadata */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
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
        /** 请求ID */
        private String requestId;
        /** 通道 */
        private String channel;
        /** 事件类型 */
        private String eventType;
        /** 来源客户端ID */
        private String sourceClientId;
        /** 目标APP名称 */
        private String targetAppName;
        /** Payload */
        private Object payload;
        private Map<String, Object> metadata = new HashMap<>();
        /** 时间戳 */
        private long timestamp;

        /** RequestId */
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        /** Channel */
        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        /** EventType */
        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        /** SourceClientId */
        public Builder sourceClientId(String sourceClientId) {
            this.sourceClientId = sourceClientId;
            return this;
        }

        /** TargetAppName */
        public Builder targetAppName(String targetAppName) {
            this.targetAppName = targetAppName;
            return this;
        }

        /** Payload */
        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        /** Metadata */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /** Timestamp */
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /** 构建 */
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
