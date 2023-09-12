package com.chua.starter.common.support.websocket;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 字符消息
 *
 * @author CH
 * @since 2023/09/12
 */
@Data
@Accessors(fluent = true)
@Builder
public class ChatMessage {

    /**
     * 事件
     */
    private String event;
}
