// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.queue.filter;

import com.chua.starter.queue.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 消息去重过滤器
 * <p>
 * 基于消息标识进行去重，防止重复消息处理
 * </p>
 *
 * @since 2025-03-20
 * @author CH
 */
@Slf4j
@Component
public class DuplicateMessageFilter implements MessageFilter {

    private final ConcurrentHashMap<String, Long> processedMessages = new ConcurrentHashMap<>(); // 处理消息
    /**
     * 默认_ttl
    */
    private static final long DEFAULT_TTL = TimeUnit.MINUTES.toMillis(5);

    @Override
    /**
     * 过滤
     * @param message 消息
     * @return 返回值
    */
    public boolean filter(Message message) {
        String messageId = message.getId();
        if (messageId == null || messageId.isEmpty()) {
            return true; // 没有标识的消息直接通过
        }

        long now = System.currentTimeMillis();
        Long lastProcessTime = processedMessages.putIfAbsent(messageId, now);

        if (lastProcessTime != null) {
            // 消息已处理过
            if (now - lastProcessTime < DEFAULT_TTL) {
                log.warn("[Queue] 检测到重复消息: destination={}, id={}",
                    message.getDestination(), messageId);
                return false;
            } else {
                // 超过TTL，允许重新处理
                processedMessages.put(messageId, now);
                return true;
            }
        }

        // 清理过期记录
        /**
         * cleanupexpiredrecords。
         * @param now now
         */
        cleanupExpiredRecords(now);

        return true;
    }

    /**
     * 清理过期记录
     * @param now now
     */
    private void cleanupExpiredRecords(long now) {
        if (processedMessages.size() > 10000) {
            processedMessages.entrySet().removeIf(entry ->
                now - entry.getValue() > DEFAULT_TTL);
        }
    }

    /**
     * 清空所有记录
     */
    public void clear() {
        processedMessages.clear();
    }

    @Override
    /**
     * 获取订单
     * @return 返回值
    */
    public int getOrder() {
        return -1000; // 最高优先级，最先执行
    }
}