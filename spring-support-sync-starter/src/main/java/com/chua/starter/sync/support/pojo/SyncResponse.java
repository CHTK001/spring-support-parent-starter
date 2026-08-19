package com.chua.starter.sync.support.pojo;

import java.io.Serializable;

/**
 * 同步响应
 *
 * @version 1.0.0
 * @since 2024/12/05
 * @author CH
 */
public class SyncResponse implements Serializable {

    /** Serial版本UID */
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

    /** 获取RequestId */
    public String getRequestId() {
        return requestId;
    }

    /** 设置RequestId */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /** 获取ClientId */
    public String getClientId() {
        return clientId;
    }

    /** 设置ClientId */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /** 获取Topic */
    public String getTopic() {
        return topic;
    }

    /** 设置Topic */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /** 获取Code */
    public int getCode() {
        return code;
    }

    /** 设置Code */
    public void setCode(int code) {
        this.code = code;
    }

    /** 获取Message */
    public String getMessage() {
        return message;
    }

    /** 设置Message */
    public void setMessage(String message) {
        this.message = message;
    }

    /** 获取Data */
    public Object getData() {
        return data;
    }

    /** 设置Data */
    public void setData(Object data) {
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

    /** 获取Duration */
    public long getDuration() {
        return duration;
    }

    /** 设置Duration */
    public void setDuration(long duration) {
        this.duration = duration;
    }

    /** 是否Success */
    public boolean isSuccess() {
        return code == 200;
    }

    /** Success */
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

    /** Fail */
    public static SyncResponse fail(String requestId, String clientId, int code, String message) {
        return new Builder()
                .requestId(requestId)
                .clientId(clientId)
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /** 记录错误 */
    public static SyncResponse error(String requestId, String clientId, String message) {
        return fail(requestId, clientId, 500, message);
    }

    /** Builder */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        /** 请求ID */
        private String requestId;
        /** 客户端ID */
        private String clientId;
        /** Topic */
        private String topic;
        /** 代码 */
        private int code;
        /** 消息 */
        private String message;
        /** 数据 */
        private Object data;
        /** 时间戳 */
        private long timestamp;
        /** 持续时间 */
        private long duration;

        /** RequestId */
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        /** ClientId */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /** Topic */
        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        /** Code */
        public Builder code(int code) {
            this.code = code;
            return this;
        }

        /** Message */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /** Data */
        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        /** Timestamp */
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /** Duration */
        public Builder duration(long duration) {
            this.duration = duration;
            return this;
        }

        /** 构建 */
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
