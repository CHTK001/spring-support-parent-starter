package com.chua.starter.sync.support.pojo;

import java.io.Serializable;

/**
 * 同步响应
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 */
public class SyncResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 主题
     */
    private String topic;

    /**
     * 状态码 (200-成功, 400-参数错误, 500-内部错误)
     */
    private int code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private Object data;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 耗时(毫秒)
     */
    private long duration;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public boolean isSuccess() {
        return code == 200;
    }

    public static SyncResponse success(String requestId, String clientId, Object data) {
        return new Builder()
                .requestId(requestId)
                .clientId(clientId)
                .code(200)
                .message("SUCCESS")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static SyncResponse fail(String requestId, String clientId, int code, String message) {
        return new Builder()
                .requestId(requestId)
                .clientId(clientId)
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static SyncResponse error(String requestId, String clientId, String message) {
        return fail(requestId, clientId, 500, message);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String requestId;
        private String clientId;
        private String topic;
        private int code;
        private String message;
        private Object data;
        private long timestamp;
        private long duration;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder code(int code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder duration(long duration) {
            this.duration = duration;
            return this;
        }

        public SyncResponse build() {
            SyncResponse response = new SyncResponse();
            response.setRequestId(requestId);
            response.setClientId(clientId);
            response.setTopic(topic);
            response.setCode(code);
            response.setMessage(message);
            response.setData(data);
            response.setTimestamp(timestamp);
            response.setDuration(duration);
            return response;
        }
    }
}
