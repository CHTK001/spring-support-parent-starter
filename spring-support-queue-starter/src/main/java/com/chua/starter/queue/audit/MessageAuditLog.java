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
 * @author CH
 * @since 2025-03-20
 */
@Data
public class MessageAuditLog {

    /**
     * 日志ID
     */
    private String id;

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
     * 操作类型（SEND/RECEIVE/PROCESS/RETRY/DEAD_LETTER）
     */
    private String operation;

    /**
     * 操作时间
     */
    private LocalDateTime operationTime;

    /**
     * 操作状态（SUCCESS/FAILURE）
     */
    private String status;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 用户ID
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
     */
    public void addExtra(String key, Object value) {
        extras.put(key, value);
    }
}
