package com.chua.starter.sse.support;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

/**
 * 消息
 * @author CH
 */
@Getter
public enum SseMessageType {
    /**
     * 通知
     */
    NOTIFY,
    /**
     * 通知
     */
    NOTIFY_HTML,
    /**
     * 进度
     */
    PROCESS,
    /**
     * 完成
     */
    FINISH
}
