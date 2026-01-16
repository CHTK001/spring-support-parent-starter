package com.chua.starter.redis.support.listener;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.Topic;

import java.util.Collection;

/**
 * redis侦听器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/05
 */
public interface RedisListener {
    /**
     * 在消息上
     *
     * @param message 消息
     * @param pattern 图案
     */
    void onMessage(Message message, byte[] pattern);

    /**
     * 获取主题
     *
     * @return {@link Collection}<{@link Topic}>
     */
    Collection<Topic> getTopics();
}
