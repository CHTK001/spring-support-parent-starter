package com.chua.starter.queue.trace;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息链路追踪上下文
 * <p>
 * 记录消息在系统中的流转路径和处理信息
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Data
public class MessageTraceContext {

    /**
     * 追踪ID
     */
    private String traceId;

    /**
     * 跨度ID
     */
    private String spanId;

    /**
     * 父跨度ID
     */
    private String parentSpanId;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 目标地址
     */
    private String destination;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 接收时间
     */
    private LocalDateTime receiveTime;

    /**
     * 处理开始时间
     */
    private LocalDateTime processStartTime;

    /**
     * 处理结束时间
     */
    private LocalDateTime processEndTime;

    /**
     * 处理状态
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 自定义标签
     */
    private Map<String, String> tags = new HashMap<>();

    /**
     * 添加标签
     */
    public void addTag(String key, String value) {
        tags.put(key, value);
    }

    /**
     * 计算处理耗时（毫秒）
     */
    public long getProcessDuration() {
        if (processStartTime == null || processEndTime == null) {
            return 0;
        }
        return java.time.Duration.between(processStartTime, processEndTime).toMillis();
    }

    /**
     * 计算总耗时（毫秒）
     */
    public long getTotalDuration() {
        if (sendTime == null || processEndTime == null) {
            return 0;
        }
        return java.time.Duration.between(sendTime, processEndTime).toMillis();
    }
}
