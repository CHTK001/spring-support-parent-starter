package com.chua.starter.queue;

/**
 * 消息处理器接口
 *
 * @author CH
 * @since 2025-12-25
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * 处理消息
     *
     * @param message 消息
     */
    void handle(Message message);
}
