package com.chua.starter.strategy.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 基于 Spring Data Redis 的策略 Redis 支撑实现。
 *
 * @author CH
 * @since 2026-03-26
 */
@RequiredArgsConstructor
public class SpringDataRedisStrategyRedisSupport implements StrategyRedisSupport {

    private static final String DELETE_IF_MATCH_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    @Override
    public boolean isAvailable() {
        return stringRedisTemplate != null;
    }

    @Override
    public boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        if (stringRedisTemplate == null) {
            return false;
        }
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void delete(String key) {
        if (stringRedisTemplate != null) {
            stringRedisTemplate.delete(key);
        }
    }

    @Override
    public long deleteIfValueMatches(String key, String expectedValue) {
        if (stringRedisTemplate == null) {
            return 0L;
        }

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(DELETE_IF_MATCH_SCRIPT);
        script.setResultType(Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(key), expectedValue);
        return result == null ? 0L : result;
    }

    @Override
    public void publish(String channel, String payload) {
        if (stringRedisTemplate != null) {
            stringRedisTemplate.convertAndSend(channel, payload);
        }
    }

    @Override
    public boolean subscribe(String channel, Consumer<String> consumer) {
        if (redisMessageListenerContainer == null) {
            return false;
        }
        redisMessageListenerContainer.addMessageListener(
                (message, pattern) -> consumer.accept(new String(message.getBody())),
                new ChannelTopic(channel)
        );
        return true;
    }
}
