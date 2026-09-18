package com.chua.starter.queue;

import static com.chua.starter.common.support.text.json.Json.fromJson;
import static com.chua.starter.common.support.text.json.Json.toJson;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
* 统一消息模型
*
* @since 2025-12-25
* @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /**
    * 消息标识
    */
    private String id;

    /**
    * 目标地址（topic/队列）
    */
    private String destination;

    /**
    * 消息内容（原始字节）
    */
    private byte[] payload;

    /**
    * 消息头
    */
    @Builder.Default
    private Map<String, Object> headers = new HashMap<>(); // 头部

    /**
    * 消息时间戳
    */
    private long timestamp;

    /**
    * 消息类型（MQTT/Kafka/RabbitMQ/RocketMQ）
    */
    private String type;

    /**
    * 消费组（如果有）
    */
    private String group;

    /**
    * 消息确认对象（用于手动确认）
    * <p>
    * 当 autoACK=false 时，此对象可用于手动确认或拒绝消息。
    * </p>
    */
    private Acknowledgment acknowledgment;

    /**
    * 原始消息对象（用于获取底层队列的原始消息，如 RabbitMQ 通道、Kafka consumerrecord 等）
    */
    private Object originalMessage;

    /**
    * 获取字符串格式的消息内容
    *
    * @return 字符串内容
    */
    public String getPayloadAsString() {
        return payload != null ? new String(payload, StandardCharsets.UTF_8) : null;
    }

    /**
    * 获取原始字节数组格式的消息内容
    *
    * @return 字节数组
    */
    public byte[] getPayload() {
        return payload;
    }

    /**
    * 获取指定类型的消息内容
    *
    * @param clazz 目标类型
    * @param <T>   类型参数
    * @return 反序列化后的对象
    */
    public <T> T getPayload(Class<T> clazz) {
        if (payload == null) {
            return null;
        }
        String json = getPayloadAsString();
        return fromJson(json, clazz);
    }

    /**
    * 获取消息头中的值
    *
    * @param key 键
    * @return 值
    */
    public Object getHeader(String key) {
        return headers != null ? headers.get(key) : null;
    }

    /**
    * 获取消息头中的字符串值
    *
    * @param key 键
    * @return 字符串值
    */
    public String getHeaderAsString(String key) {
        Object value = getHeader(key);
        return value != null ? value.toString() : null;
    }

    /**
    * 创建消息
    *
    * @param destination 目标地址
    * @param payload     消息内容
    * @return 消息对象
    */
    public static Message of(String destination, Object payload) {
        byte[] bytes;
        if (payload instanceof byte[]) {
            bytes = (byte[]) payload;
        } else if (payload instanceof String) {
            bytes = ((String) payload).getBytes(StandardCharsets.UTF_8);
        } else {
            bytes = toJson(payload).getBytes(StandardCharsets.UTF_8);
        }

        var message = new Message();
        message.destination = destination;
        message.payload = bytes;
        message.timestamp = System.currentTimeMillis();
        message.headers = new HashMap<>();
        return message;
    }

    // Lombok 注解处理器未运行时的手动 getter/setter 方法
    /**
    * 获取标识
    *
    * @return 获取id的结果
    */
    public String getId() {
        return id;
    }

    /**
    * 设置标识
    *
    * @param id 标识
    */
    public void setId(String id) {
        this.id = id;
    }

    /**
    * 获取头部
    *
    * @return 获取头部的结果
    */
    public Map<String, Object> getHeaders() {
        return headers;
    }

    /**
    * 设置头部
    *
    * @param headers 头部
    */
    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    /**
    * 获取目标
    *
    * @return 获取目标的结果
    */
    public String getDestination() {
        return destination;
    }

    /**
    * 设置目标
    *
    * @param destination 目标
    */
    public void setDestination(String destination) {
        this.destination = destination;
    }

    /**
    * 设置Payload
    *
    * @param payload payload
    */
    public void setPayload(byte[] payload) {
        this.payload = payload;
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
    * 设置类型
    *
    * @param type 类型
    */
    public void setType(String type) {
        this.type = type;
    }

    /**
    * 获取承认
    *
    * @return 获取承认的结果
    */
    public Acknowledgment getAcknowledgment() {
        return acknowledgment;
    }

    /**
    * 设置承认
    *
    * @param acknowledgment 承认
    */
    public void setAcknowledgment(Acknowledgment acknowledgment) {
        this.acknowledgment = acknowledgment;
    }

    /**
    * 获取原始消息
    *
    * @return 获取原始消息的结果
    */
    public Object getOriginalMessage() {
        return originalMessage;
    }

    /**
    * 设置原始消息
    *
    * @param originalMessage 原始消息
    */
    public void setOriginalMessage(Object originalMessage) {
        this.originalMessage = originalMessage;
    }

    /**
    * 获取类型
    *
    * @return 获取类型的结果
    */
    public String getType() {
        return type;
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
    * 获取分组
    *
    * @return 获取群体的结果
    */
    public String getGroup() {
        return group;
    }

    /**
    * 设置分组
    *
    * @param group 群体
    */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
    * 创建 消息构建器 实例
    *
    * @return MessageBuilder 实例
    */
    public static MessageBuilder builder() {
        /**
        * 消息构建器。
        * @return 消息构建器的结果
        */
        return new MessageBuilder();
    }

    /**
    * 消息 构建器 类
    * @author CH
    * @since 4.0.0
    */
    public static class MessageBuilder {
        /** 标识 */
        private String id;
        /** 目的地 */
        private String destination;
        /** Payload */
        private byte[] payload;
        private Map<String, Object> headers = new HashMap<>(); // 头部
        /** 时间戳 */
        private long timestamp;
        /** 类型 */
        private String type;
        /** 分组 */
        private String group;
        /** 承认 */
        private Acknowledgment acknowledgment;
        /** 原始消息 */
        private Object originalMessage;

        /**
        * 标识
        *
        * @param id 标识
        * @return id的结果
        */
        public MessageBuilder id(String id) {
            this.id = id;
            return this;
        }

        /**
        * 目标
        *
        * @param destination 目标
        * @return 目标的结果
        */
        public MessageBuilder destination(String destination) {
            this.destination = destination;
            return this;
        }

        /**
        * Payload
        *
        * @param payload payload
        * @return payload的结果
        */
        public MessageBuilder payload(byte[] payload) {
            this.payload = payload;
            return this;
        }

        /**
        * 头部
        *
        * @param headers 头部
        * @return 头部的结果
        */
        public MessageBuilder headers(Map<String, Object> headers) {
            this.headers = headers;
            return this;
        }

        /**
        * 时间戳
        *
        * @param timestamp 时间戳
        * @return 时间戳的结果
        */
        public MessageBuilder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
        * 类型
        *
        * @param type 类型
        * @return 类型的结果
        */
        public MessageBuilder type(String type) {
            this.type = type;
            return this;
        }

        /**
        * 分组
        *
        * @param group 群体
        * @return 群体的结果
        */
        public MessageBuilder group(String group) {
            this.group = group;
            return this;
        }

        /**
        * 承认
        *
        * @param acknowledgment 承认
        * @return 承认的结果
        */
        public MessageBuilder acknowledgment(Acknowledgment acknowledgment) {
            this.acknowledgment = acknowledgment;
            return this;
        }

        /**
        * 原始消息
        *
        * @param originalMessage 原始消息
        * @return 原始消息的结果
        */
        public MessageBuilder originalMessage(Object originalMessage) {
            this.originalMessage = originalMessage;
            return this;
        }

        /**
        * 构建
        *
        * @return 构建的结果
        */
        public Message build() {
            var message = new Message();
            message.id = this.id;
            message.destination = this.destination;
            message.payload = this.payload;
            message.headers = this.headers != null ? this.headers : new HashMap<>();
            message.timestamp = this.timestamp;
            message.type = this.type;
            message.group = this.group;
            message.acknowledgment = this.acknowledgment;
            message.originalMessage = this.originalMessage;
            return message;
        }
    }
}
