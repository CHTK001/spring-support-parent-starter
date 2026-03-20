package com.chua.starter.queue.interceptor;

import com.chua.starter.queue.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控拦截器
 * <p>
 * 统计消息发送和接收的性能指标
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
@Component
public class MetricsMessageInterceptor implements MessageInterceptor {

    private final ConcurrentHashMap<String, AtomicLong> sendCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> receiveCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> sendFailureCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> receiveFailureCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sendTimestamps = new ConcurrentHashMap<>();

    @Override
    public Message beforeSend(Message message) {
        sendTimestamps.put(message.getId(), System.currentTimeMillis());
        return message;
    }

    @Override
    public void afterSend(Message message, boolean success, Throwable error) {
        String destination = message.getDestination();
        if (success) {
            sendCounters.computeIfAbsent(destination, k -> new AtomicLong()).incrementAndGet();

            Long startTime = sendTimestamps.remove(message.getId());
            if (startTime != null) {
                long duration = System.currentTimeMillis() - startTime;
                if (duration > 1000) {
                    log.warn("[Queue] 消息发送耗时过长: destination={}, duration={}ms",
                        destination, duration);
                }
            }
        } else {
            sendFailureCounters.computeIfAbsent(destination, k -> new AtomicLong()).incrementAndGet();
        }
    }

    @Override
    public Message beforeReceive(Message message) {
        message.getHeaders().put("receiveStartTime", System.currentTimeMillis());
        return message;
    }

    @Override
    public void afterReceive(Message message, boolean success, Throwable error) {
        String destination = message.getDestination();
        if (success) {
            receiveCounters.computeIfAbsent(destination, k -> new AtomicLong()).incrementAndGet();

            Object startTime = message.getHeaders().get("receiveStartTime");
            if (startTime instanceof Long) {
                long duration = System.currentTimeMillis() - (Long) startTime;
                if (duration > 5000) {
                    log.warn("[Queue] 消息处理耗时过长: destination={}, duration={}ms",
                        destination, duration);
                }
            }
        } else {
            receiveFailureCounters.computeIfAbsent(destination, k -> new AtomicLong()).incrementAndGet();
        }
    }

    /**
     * 获取发送计数
     */
    public long getSendCount(String destination) {
        AtomicLong counter = sendCounters.get(destination);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取接收计数
     */
    public long getReceiveCount(String destination) {
        AtomicLong counter = receiveCounters.get(destination);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取发送失败计数
     */
    public long getSendFailureCount(String destination) {
        AtomicLong counter = sendFailureCounters.get(destination);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取接收失败计数
     */
    public long getReceiveFailureCount(String destination) {
        AtomicLong counter = receiveFailureCounters.get(destination);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 重置统计
     */
    public void reset() {
        sendCounters.clear();
        receiveCounters.clear();
        sendFailureCounters.clear();
        receiveFailureCounters.clear();
        sendTimestamps.clear();
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级
    }
}
