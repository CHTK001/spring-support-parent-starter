package com.chua.starter.sse.support;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 消息
 * @author CH
 * @since 4.0.0
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
    /** 事件 */
    private String event = "message";
    /**
     * 状态
     */
    private SseMessageStatus status;
    /**
      * 任务标识
     */
    private String tid;
    /**
     * 其它信息
     */
    private Map<String, Object> ext;

    /**
     * 获取事件名称
     *
     * @return 事件名称
     */
    public String getEvent() {
        return event;
    }
}
