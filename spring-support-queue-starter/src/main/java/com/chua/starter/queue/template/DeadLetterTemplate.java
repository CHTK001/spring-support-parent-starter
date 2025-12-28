package com.chua.starter.queue.template;

import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageHandler;
import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.SendResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
 * 死信队列模板
 * <p>
 * 实现 MessageTemplate 接口，提供死信队列功能。
 * 当消息处理失败达到最大重试次数后，将消息转移到死信队列。
 * 支持自动重试、延迟重试、死信回调等功能。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-25
 */
@Slf4j
public class DeadLetterTemplate implements MessageTemplate {

    /**
     * 死信队列前缀
     */
    private static final String DLQ_PREFIX = "dlq:";

    /**
     * 重试队列前缀
     */
    private static final String RETRY_PREFIX = "retry:";

    /**
     * 重试次数Header键
     */
    public static final String HEADER_RETRY_COUNT = "X-Retry-Count";

    /**
     * 原始目标Header键
     */
    public static final String HEADER_ORIGINAL_DESTINATION = "X-Original-Destination";

    /**
     * 失败原因Header键
     */
    public static final String HEADER_FAILURE_REASON = "X-Failure-Reason";

    /**
     * 失败时间Header键
     */
    public static final String HEADER_FAILURE_TIME = "X-Failure-Time";

    /**
     * 主消息模板
     */
    private final MessageTemplate messageTemplate;

    /**
     * 死信队列消息模板（可独立配置）
     */
    private final MessageTemplate dlqMessageTemplate;

    /**
     * 配置
     */
    private final DeadLetterConfig config;

    /**
     * 重试计数器
     */
    private final ConcurrentMap<String, AtomicInteger> retryCounters = new ConcurrentHashMap<>();

    /**
     * 死信回调
     */
    private final CopyOnWriteArrayList<BiConsumer<Message, Throwable>> deadLetterCallbacks = new CopyOnWriteArrayList<>();

    /**
     * 延迟调度器
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 构造函数（使用同一个消息模板）
     *
     * @param messageTemplate 消息模板
     * @param config          配置
     */
    public DeadLetterTemplate(MessageTemplate messageTemplate, DeadLetterConfig config) {
        this(messageTemplate, messageTemplate, config);
    }

    /**
     * 构造函数（支持独立的死信队列消息模板）
     *
     * @param messageTemplate    主消息模板
     * @param dlqMessageTemplate 死信队列消息模板
     * @param config             配置
     */
    public DeadLetterTemplate(MessageTemplate messageTemplate, MessageTemplate dlqMessageTemplate, DeadLetterConfig config) {
        this.messageTemplate = messageTemplate;
        this.dlqMessageTemplate = dlqMessageTemplate;
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "dlq-scheduler");
            t.setDaemon(true);
            return t;
        });
        log.info("[Queue] 死信队列初始化完成, 最大重试: {}, 重试延迟: {}ms, 类型: {}",
                highlight(config.getMaxRetries()), highlight(config.getRetryDelay().toMillis()), highlight(dlqMessageTemplate.getType()));
    }

    // ==================== MessageTemplate 接口实现 ====================

    @Override
    public SendResult send(String destination, Object payload) {
        return dlqMessageTemplate.send(getDlqDestination(destination), payload);
    }

    @Override
    public CompletableFuture<SendResult> sendAsync(String destination, Object payload) {
        return dlqMessageTemplate.sendAsync(getDlqDestination(destination), payload);
    }

    @Override
    public SendResult send(String destination, Object payload, Map<String, Object> headers) {
        Map<String, Object> dlqHeaders = new ConcurrentHashMap<>(headers != null ? headers : Map.of());
        dlqHeaders.put(HEADER_ORIGINAL_DESTINATION, destination);
        dlqHeaders.put(HEADER_FAILURE_TIME, LocalDateTime.now().toString());
        return dlqMessageTemplate.send(getDlqDestination(destination), payload, dlqHeaders);
    }

    @Override
    public SendResult sendDelayed(String destination, Object payload, Duration delay) {
        return dlqMessageTemplate.sendDelayed(getDlqDestination(destination), payload, delay);
    }

    @Override
    public void subscribe(String destination, MessageHandler handler) {
        dlqMessageTemplate.subscribe(getDlqDestination(destination), handler);
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler) {
        dlqMessageTemplate.subscribe(getDlqDestination(destination), group, handler);
    }

    @Override
    public void unsubscribe(String destination) {
        dlqMessageTemplate.unsubscribe(getDlqDestination(destination));
    }

    @Override
    public String getType() {
        return "dead-letter";
    }

    @Override
    public boolean isConnected() {
        return dlqMessageTemplate.isConnected();
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        retryCounters.clear();
        log.info("[Queue] 死信队列已关闭");
    }

    // ==================== 死信队列特有方法 ====================

    /**
     * 带重试的消息订阅
     * <p>
     * 当消息处理失败时自动重试，达到最大重试次数后转移到死信队列。
     * </p>
     *
     * @param destination 目标地址
     * @param handler     消息处理器
     */
    public void subscribeWithRetry(String destination, MessageHandler handler) {
        messageTemplate.subscribe(destination, message -> {
            String messageId = message.getId() != null ? message.getId() : UUID.randomUUID().toString();
            AtomicInteger counter = retryCounters.computeIfAbsent(messageId, k -> new AtomicInteger(0));

            try {
                handler.handle(message);
                // 处理成功，清除重试计数
                retryCounters.remove(messageId);
            } catch (Exception e) {
                int retryCount = counter.incrementAndGet();
                log.warn("[Queue] 消息处理失败, 目标: {}, 消息ID: {}, 重试次数: {}/{}",
                        destination, messageId, retryCount, config.getMaxRetries(), e);

                if (retryCount < config.getMaxRetries()) {
                    // 延迟重试
                    scheduleRetry(destination, message, retryCount, e);
                } else {
                    // 转移到死信队列
                    moveToDeadLetter(destination, message, e);
                    retryCounters.remove(messageId);
                }
            }
        });
    }

    /**
     * 带重试的消息订阅（指定消费组）
     *
     * @param destination 目标地址
     * @param group       消费组
     * @param handler     消息处理器
     */
    public void subscribeWithRetry(String destination, String group, MessageHandler handler) {
        messageTemplate.subscribe(destination, group, message -> {
            String messageId = message.getId() != null ? message.getId() : UUID.randomUUID().toString();
            AtomicInteger counter = retryCounters.computeIfAbsent(messageId, k -> new AtomicInteger(0));

            try {
                handler.handle(message);
                retryCounters.remove(messageId);
            } catch (Exception e) {
                int retryCount = counter.incrementAndGet();
                log.warn("[Queue] 消息处理失败, 目标: {}, 组: {}, 消息ID: {}, 重试次数: {}/{}",
                        destination, group, messageId, retryCount, config.getMaxRetries(), e);

                if (retryCount < config.getMaxRetries()) {
                    scheduleRetry(destination, message, retryCount, e);
                } else {
                    moveToDeadLetter(destination, message, e);
                    retryCounters.remove(messageId);
                }
            }
        });
    }

    /**
     * 发送消息到死信队列
     *
     * @param originalDestination 原始目标地址
     * @param message             消息
     * @param reason              失败原因
     * @return 发送结果
     */
    public SendResult sendToDeadLetter(String originalDestination, Message message, String reason) {
        String dlqDestination = getDlqDestination(originalDestination);

        Map<String, Object> headers = new ConcurrentHashMap<>(message.getHeaders() != null ? message.getHeaders() : Map.of());
        headers.put(HEADER_ORIGINAL_DESTINATION, originalDestination);
        headers.put(HEADER_FAILURE_REASON, reason);
        headers.put(HEADER_FAILURE_TIME, LocalDateTime.now().toString());

        SendResult result = dlqMessageTemplate.send(dlqDestination, message.getPayload(), headers);
        if (result.isSuccess()) {
            log.info("[Queue] 消息已转移到死信队列: {}, 消息ID: {}, 类型: {}", 
                    highlight(dlqDestination), message.getId(), highlight(dlqMessageTemplate.getType()));
        }
        return result;
    }

    /**
     * 订阅死信队列
     *
     * @param originalDestination 原始目标地址
     * @param handler             死信处理器
     */
    public void subscribeDeadLetter(String originalDestination, MessageHandler handler) {
        String dlqDestination = getDlqDestination(originalDestination);
        dlqMessageTemplate.subscribe(dlqDestination, handler);
        log.info("[Queue] 订阅死信队列: {}, 类型: {}", highlight(dlqDestination), highlight(dlqMessageTemplate.getType()));
    }

    /**
     * 重新处理死信消息
     * <p>
     * 将死信队列中的消息重新发送到原始队列进行处理。
     * </p>
     *
     * @param originalDestination 原始目标地址
     * @param message             死信消息
     * @return 发送结果
     */
    public SendResult reprocessDeadLetter(String originalDestination, Message message) {
        // 重置重试计数
        Map<String, Object> headers = new ConcurrentHashMap<>(message.getHeaders() != null ? message.getHeaders() : Map.of());
        headers.put(HEADER_RETRY_COUNT, 0);
        headers.remove(HEADER_FAILURE_REASON);
        headers.remove(HEADER_FAILURE_TIME);

        SendResult result = messageTemplate.send(originalDestination, message.getPayload(), headers);
        if (result.isSuccess()) {
            log.info("[Queue] 死信消息已重新处理: {}, 消息ID: {}", highlight(originalDestination), message.getId());
        }
        return result;
    }

    /**
     * 批量重新处理死信消息
     *
     * @param originalDestination 原始目标地址
     * @param maxCount            最大处理数量
     * @param handler             处理器，返回true表示重新发送，false表示丢弃
     */
    public void reprocessDeadLetters(String originalDestination, int maxCount,
                                     java.util.function.Predicate<Message> handler) {
        String dlqDestination = getDlqDestination(originalDestination);
        AtomicInteger processed = new AtomicInteger(0);

        dlqMessageTemplate.subscribe(dlqDestination, message -> {
            if (processed.get() >= maxCount) {
                return;
            }

            try {
                if (handler.test(message)) {
                    reprocessDeadLetter(originalDestination, message);
                } else {
                    log.info("[Queue] 死信消息已丢弃: {}", message.getId());
                }
                processed.incrementAndGet();
            } catch (Exception e) {
                log.error("Error reprocessing dead letter: {}", message.getId(), e);
            }
        });
    }

    /**
     * 注册死信回调
     *
     * @param callback 回调函数（消息, 异常）
     */
    public void onDeadLetter(BiConsumer<Message, Throwable> callback) {
        deadLetterCallbacks.add(callback);
    }

    /**
     * 获取死信队列名称
     *
     * @param originalDestination 原始目标地址
     * @return 死信队列名称
     */
    public String getDlqDestination(String originalDestination) {
        return DLQ_PREFIX + originalDestination;
    }

    /**
     * 获取重试队列名称
     *
     * @param originalDestination 原始目标地址
     * @return 重试队列名称
     */
    public String getRetryDestination(String originalDestination) {
        return RETRY_PREFIX + originalDestination;
    }

    /**
     * 调度重试
     */
    private void scheduleRetry(String destination, Message message, int retryCount, Exception cause) {
        Duration delay = calculateRetryDelay(retryCount);

        scheduler.schedule(() -> {
            Map<String, Object> headers = new ConcurrentHashMap<>(message.getHeaders() != null ? message.getHeaders() : Map.of());
            headers.put(HEADER_RETRY_COUNT, retryCount);

            messageTemplate.send(destination, message.getPayload(), headers);
            log.debug("Retry scheduled for destination: {}, messageId: {}, delay: {}ms",
                    destination, message.getId(), delay.toMillis());
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 计算重试延迟（指数退避）
     */
    private Duration calculateRetryDelay(int retryCount) {
        if (!config.isExponentialBackoff()) {
            return config.getRetryDelay();
        }

        long delayMs = (long) (config.getRetryDelay().toMillis() * Math.pow(config.getBackoffMultiplier(), retryCount - 1));
        long maxDelayMs = config.getMaxRetryDelay().toMillis();
        return Duration.ofMillis(Math.min(delayMs, maxDelayMs));
    }

    /**
     * 转移到死信队列
     */
    private void moveToDeadLetter(String destination, Message message, Exception cause) {
        String dlqDestination = getDlqDestination(destination);

        Map<String, Object> headers = new ConcurrentHashMap<>(message.getHeaders() != null ? message.getHeaders() : Map.of());
        headers.put(HEADER_ORIGINAL_DESTINATION, destination);
        headers.put(HEADER_FAILURE_REASON, cause.getMessage());
        headers.put(HEADER_FAILURE_TIME, LocalDateTime.now().toString());
        headers.put(HEADER_RETRY_COUNT, config.getMaxRetries());

        SendResult result = dlqMessageTemplate.send(dlqDestination, message.getPayload(), headers);

        if (result.isSuccess()) {
            log.warn("Message moved to dead letter queue: {}, messageId: {}, reason: {}, dlqType: {}",
                    dlqDestination, message.getId(), cause.getMessage(), dlqMessageTemplate.getType());
        } else {
            log.error("Failed to move message to dead letter queue: {}, messageId: {}, dlqType: {}",
                    dlqDestination, message.getId(), dlqMessageTemplate.getType());
        }

        // 触发回调
        for (BiConsumer<Message, Throwable> callback : deadLetterCallbacks) {
            try {
                callback.accept(message, cause);
            } catch (Exception e) {
                log.error("Dead letter callback error", e);
            }
        }
    }

    /**
     * 死信队列配置
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DeadLetterConfig {
        /**
         * 最大重试次数
         */
        @lombok.Builder.Default
        private int maxRetries = 3;

        /**
         * 重试延迟
         */
        @lombok.Builder.Default
        private Duration retryDelay = Duration.ofSeconds(5);

        /**
         * 最大重试延迟
         */
        @lombok.Builder.Default
        private Duration maxRetryDelay = Duration.ofMinutes(5);

        /**
         * 是否启用指数退避
         */
        @lombok.Builder.Default
        private boolean exponentialBackoff = true;

        /**
         * 退避乘数
         */
        @lombok.Builder.Default
        private double backoffMultiplier = 2.0;
    }
}
