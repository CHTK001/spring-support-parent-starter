package com.chua.starter.queue.audit;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息审计日志
 * <p>
 * 记录消息的完整生命周期信息，用于审计和问题排查
 * </p>
 *
 * @since 2025-03-20
 * @author CH
 */
@Data
public class MessageAuditLog {

    /**
     * 日志标识
     */
    private String id;

    /**
     * 消息标识
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
     * 操作类型（发送/接收/处理/重试/DEAD_LETTER）
     */
    private String operation;

    /**
     * 操作时间
     */
    private LocalDateTime operationTime;

    /**
     * 操作状态（成功/失败）
     */
    private String status;

    /**
     * 租户标识
     */
    private Long tenantId;

    /**
     * 用户标识
     */
    private Long userId;

    /**
     * 消息大小（字节）
     */
    private Integer messageSize;

    /**
     * 处理耗时（毫秒）
     */
    private Long duration;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 错误堆栈
     */
    private String errorStack;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 消息头
     */
    private Map<String, Object> headers = new HashMap<>();

    /**
     * 消息内容摘要（前100字符）
     */
    private String payloadSummary;

    /**
     * 扩展信息
     */
    private Map<String, Object> extras = new HashMap<>();

    /**
     * 添加扩展信息
     * @param key Key
     * @param value 值
     */
    public void addExtra(String key, Object value) {
        extras.put(key, value);
    }
}
