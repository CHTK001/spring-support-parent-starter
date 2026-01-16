package com.chua.starter.queue;

import com.chua.common.support.json.Json;
import lombok.Builder;
import lombok.Data;

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
     * 创建消息构建器
     *
     * @param destination 目标地址
     * @param payload     消息内容
     * @return 构建器
     */
    public static MessageBuilder of(String destination, Object payload) {
        byte[] bytes;
        if (payload instanceof byte[]) {
            bytes = (byte[]) payload;
        } else if (payload instanceof String) {
            bytes = ((String) payload).getBytes(StandardCharsets.UTF_8);
        } else {
            bytes = Json.toJson(payload).getBytes(StandardCharsets.UTF_8);
        }

        return Message.builder()
                .destination(destination)
                .payload(bytes)
                .timestamp(System.currentTimeMillis());
    }
}
