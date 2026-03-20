package com.chua.starter.queue.filter;

import com.chua.starter.queue.Message;

/**
 * 消息过滤器接口
 * <p>
 * 用于在消息处理前进行过滤，支持：
 * - 消息去重
 * - 消息验证
 * - 消息路由
 * - 消息丢弃
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@FunctionalInterface
public interface MessageFilter {

    /**
     * 过滤消息
     *
     * @param message 消息
     * @return true表示通过过滤，false表示拒绝该消息
     */
    boolean filter(Message message);

    /**
     * 过滤器优先级
     * <p>
     * 数值越小优先级越高，默认为0
     * </p>
     *
     * @return 优先级
     */
    default int getOrder() {
        return 0;
    }
}
