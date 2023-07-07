package com.chua.starter.sse.support;

import lombok.Getter;

/**
 * 消息
 * @author CH
 */
@Getter
public enum SseMessageStatus {
    /**
     * 成功
     */
    SUCCESS,
    /**
     * 异常
     */
    ERROR,
    /**
     * 警告
     */
    WARN
}
