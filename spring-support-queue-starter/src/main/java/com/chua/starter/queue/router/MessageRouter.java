package com.chua.starter.queue.router;

import com.chua.starter.queue.Message;

/**
 * 消息路由器接口
 * <p>
 * 根据消息内容动态路由到不同的目标队列
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@FunctionalInterface
public interface MessageRouter {

    /**
     * 路由消息
     * <p>
     * 根据消息内容返回目标队列地址
     * </p>
     *
     * @param message 消息
     * @return 目标队列地址，返回null表示使用原始目标
     */
    String route(Message message);

    /**
     * 是否支持该消息
     *
     * @param message 消息
     * @return 是否支持
     */
    default boolean supports(Message message) {
        return true;
    }

    /**
     * 路由器优先级
     *
     * @return 优先级
     */
    default int getOrder() {
        return 0;
    }
}
