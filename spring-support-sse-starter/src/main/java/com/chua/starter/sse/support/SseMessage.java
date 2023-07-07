package com.chua.starter.sse.support;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 消息
 * @author CH
 */
@Data
@Builder
public class SseMessage {
    /**
     * 类型
     */
    private SseMessageType type;
    /**
     * 消息
     */
    private String message;
    /**
     * 事件
     */
    @Builder.Default
    private String event = "message";
    /**
     * 状态
     */
    private SseMessageStatus status;
    /**
     * 任务ID
     */
    private String tid;
    /**
     * 其它信息
     */
    private Map<String, Object> ext;
}
