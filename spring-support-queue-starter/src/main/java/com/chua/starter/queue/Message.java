package com.chua.starter.queue;

import com.chua.starter.queue.util.Json;
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
 * @author CH
 * @since 2025-12-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /**
     * 消息ID
     */
    private String id;

    /**
     * 目标地址（topic/queue）
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
    private Map<String, Object> headers = new HashMap<>();

    /**
     * 消息时间戳
     */
    private long timestamp;

    /**
     * 消息类型（mqtt/kafka/rabbitmq/rocketmq）
     */
    private String type;

    /**
     * 消费组（如果有）
     */
    private String group;

    /**
     * 消息确认对象（用于手动确认）
     * <p>
     * 当 autoAck=false 时，此对象可用于手动确认或拒绝消息。
     * </p>
     */
    private Acknowledgment acknowledgment;

    /**
     * 原始消息对象（用于获取底层队列的原始消息，如 RabbitMQ Channel、Kafka ConsumerRecord 等）
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
        return Json.fromJson(json, clazz);
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
            bytes = Json.toJson(payload).getBytes(StandardCharsets.UTF_8);
        }

        var message = new Message();
        message.destination = destination;
        message.payload = bytes;
        message.timestamp = System.currentTimeMillis();
        message.headers = new HashMap<>();
        return message;
    }

    // Lombok 注解处理器未运行时的手动 getter/setter 方法
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Acknowledgment getAcknowledgment() {
        return acknowledgment;
    }

    public void setAcknowledgment(Acknowledgment acknowledgment) {
        this.acknowledgment = acknowledgment;
    }

    public Object getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(Object originalMessage) {
        this.originalMessage = originalMessage;
    }

    public String getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * 创建 MessageBuilder 实例
     *
     * @return MessageBuilder 实例
     */
    public static MessageBuilder builder() {
        return new MessageBuilder();
    }

    /**
     * Message Builder 类
     */
    public static class MessageBuilder {
        private String id;
        private String destination;
        private byte[] payload;
        private Map<String, Object> headers = new HashMap<>();
        private long timestamp;
        private String type;
        private String group;
        private Acknowledgment acknowledgment;
        private Object originalMessage;

        public MessageBuilder id(String id) {
            this.id = id;
            return this;
        }

        public MessageBuilder destination(String destination) {
            this.destination = destination;
            return this;
        }

        public MessageBuilder payload(byte[] payload) {
            this.payload = payload;
            return this;
        }

        public MessageBuilder headers(Map<String, Object> headers) {
            this.headers = headers;
            return this;
        }

        public MessageBuilder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public MessageBuilder type(String type) {
            this.type = type;
            return this;
        }

        public MessageBuilder group(String group) {
            this.group = group;
            return this;
        }

        public MessageBuilder acknowledgment(Acknowledgment acknowledgment) {
            this.acknowledgment = acknowledgment;
            return this;
        }

        public MessageBuilder originalMessage(Object originalMessage) {
            this.originalMessage = originalMessage;
            return this;
        }

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
