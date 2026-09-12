package com.chua.starter.sync.support.pojo;

import java.io.Serializable;

/**
 * 同步响应
 *
   * @版本 1.0.0
 * @since 2024/12/05
 * @author CH
 */
public class SyncResponse implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
      * 请求标识
     */
    private String requestId;

    /**
      * 客户端标识
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
      * 获取客户端标识
     *
     * @return 获取客户端id的结果
     */
    public String getClientId() {
        return clientId;
    }

    /**
      * 设置客户端标识
     *
     * @param clientId 客户端标识
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * 获取Topic
     *
     * @return 获取topic的结果
     */
    public String getTopic() {
        return topic;
    }

    /**
     * 设置Topic
     *
     * @param topic topic
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * 获取编码
     *
     * @return 获取编码的结果
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置编码
     *
     * @param code 编码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 获取消息
     *
     * @return 获取消息的结果
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置消息
     *
     * @param message 消息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取数据
     *
     * @return 获取数据的结果
     */
    public Object getData() {
        return data;
    }

    /**
     * 设置数据
     *
     * @param data 数据
     */
    public void setData(Object data) {
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
     * 获取持续时间
     *
     * @return 获取持续时间的结果
     */
    public long getDuration() {
        return duration;
    }

    /**
     * 设置持续时间
     *
     * @param duration 持续时间
     */
    public void setDuration(long duration) {
        this.duration = duration;
    }

    /**
     * 是否成功
     *
     * @return 是否成功的结果
     */
    public boolean isSuccess() {
        return code == 200;
    }

    /**
     * 成功
     *
     * @param requestId 请求标识
     * @param clientId 客户端标识
     * @param data 数据
     * @return 成功的结果
     */
    public static SyncResponse success(String requestId, String clientId, Object data) {
        /**
         * 构建器。
         * @return 构建器的结果
         */
        return new Builder()
                .requestId(requestId)
                .clientId(clientId)
                .code(200)
                .message("SUCCESS")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 失败
     *
     * @param requestId 请求标识
     * @param clientId 客户端标识
     * @param code 编码
     * @param message 消息
     * @return 失败的结果
     */
    public static SyncResponse fail(String requestId, String clientId, int code, String message) {
        /**
         * 构建器。
         * @return 构建器的结果
         */
        return new Builder()
                .requestId(requestId)
                .clientId(clientId)
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 记录错误
     *
     * @param requestId 请求标识
     * @param clientId 客户端标识
     * @param message 消息
     * @return 错误的结果
     */
    public static SyncResponse error(String requestId, String clientId, String message) {
        return fail(requestId, clientId, 500, message);
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
        /** 客户端标识 */
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
          * 客户端标识
         *
         * @param clientId 客户端标识
         * @return 客户端id的结果
         */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * Topic
         *
         * @param topic topic
         * @return topic的结果
         */
        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        /**
         * 编码
         *
         * @param code 编码
         * @return 编码的结果
         */
        public Builder code(int code) {
            this.code = code;
            return this;
        }

        /**
         * 消息
         *
         * @param message 消息
         * @return 消息的结果
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * 数据
         *
         * @param data 数据
         * @return 数据的结果
         */
        public Builder data(Object data) {
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
         * 持续时间
         *
         * @param duration 持续时间
         * @return 持续时间的结果
         */
        public Builder duration(long duration) {
            this.duration = duration;
            return this;
        }

        /**
         * 构建
         *
         * @return 构建的结果
         */
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
