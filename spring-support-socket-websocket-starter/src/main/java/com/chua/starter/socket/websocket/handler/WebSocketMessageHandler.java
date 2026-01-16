package com.chua.starter.socket.websocket.handler;

import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket 消息处理器接口
 * <p>
 * 实现此接口以处理 WebSocket 消息和事件
 * </p>
 *
 * @author CH
 * @since 2025-12-25
 */
public interface WebSocketMessageHandler {

    /**
     * 连接建立时调用
     *
     * @param session 会话
     */
    default void onConnect(WebSocketSession session) {
    }

    /**
     * 收到消息时调用
     *
     * @param session 会话
     * @param message 消息内容
     */
    void onMessage(WebSocketSession session, String message);

    /**
     * 连接关闭时调用
     *
     * @param session 会话
     * @param code    关闭码
     * @param reason  关闭原因
     */
    default void onClose(WebSocketSession session, int code, String reason) {
    }

    /**
     * 发生错误时调用
     *
     * @param session   会话
     * @param throwable 异常
     */
    default void onError(WebSocketSession session, Throwable throwable) {
    }
}
