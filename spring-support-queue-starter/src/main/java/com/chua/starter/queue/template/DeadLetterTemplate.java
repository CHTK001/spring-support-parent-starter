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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeadLetterTemplate.class);

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
     * 原始消息ID Header键（用于重试时跟踪消息）
     */
    public static final String HEADER_ORIGINAL_MESSAGE_ID = "X-Original-Message-Id";

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
        if (messageTemplate == null) {
            throw new IllegalArgumentException("MessageTemplate cannot be null");
        }
        if (dlqMessageTemplate == null) {
            throw new IllegalArgumentException("DLQ MessageTemplate cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("DeadLetterConfig cannot be null");
        }
        
        // 验证配置参数
        validateConfig(config);
        
        this.messageTemplate = messageTemplate;
        this.dlqMessageTemplate = dlqMessageTemplate;
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "dlq-scheduler");
            t.setDaemon(true);
            return t;
        });
        log.info("[Queue] 死信队列初始化完成, 最大重试: {}, 重试延迟: {}ms, 类型: {}",
                config.getMaxRetries(), config.getRetryDelay().toMillis(), dlqMessageTemplate.getType());
    }
    
    /**
     * 验证配置参数
     */
    private void validateConfig(DeadLetterConfig config) {
        if (config.getMaxRetries() <= 0) {
            throw new IllegalArgumentException("maxRetries must be greater than 0, got: " + config.getMaxRetries());
        }
        if (config.getRetryDelay() == null || config.getRetryDelay().isNegative() || config.getRetryDelay().isZero()) {
            throw new IllegalArgumentException("retryDelay must be positive, got: " + config.getRetryDelay());
        }
        if (config.getMaxRetryDelay() != null && config.getMaxRetryDelay().isNegative()) {
            throw new IllegalArgumentException("maxRetryDelay must be non-negative, got: " + config.getMaxRetryDelay());
        }
        if (config.getBackoffMultiplier() <= 0) {
            throw new IllegalArgumentException("backoffMultiplier must be greater than 0, got: " + config.getBackoffMultiplier());
        }
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
    public void subscribe(String destination, MessageHandler handler, boolean autoAck) {
        dlqMessageTemplate.subscribe(getDlqDestination(destination), handler, autoAck);
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler) {
        dlqMessageTemplate.subscribe(getDlqDestination(destination), group, handler);
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler, boolean autoAck) {
        dlqMessageTemplate.subscribe(getDlqDestination(destination), group, handler, autoAck);
    }

    @Override
    public void unsubscribe(String destination) {
        dlqMessageTemplate.unsubscribe(getDlqDestination(destination));
    }

    @Override
    public void subscribe(String destination, MessageHandler handler, boolean autoAck, int concurrency) {
        dlqMessageTemplate.subscribe(getDlqDestination(destination), handler, autoAck, concurrency);
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler, boolean autoAck, int concurrency) {
        dlqMessageTemplate.subscribe(getDlqDestination(destination), group, handler, autoAck, concurrency);
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
        // 先停止接受新任务
        scheduler.shutdown();
        
        // 清理所有待执行的重试任务对应的计数器
        // 注意：这里无法直接获取待执行的任务，所以只能清理已完成的
        // 未执行的任务在 scheduler 关闭后会被取消，对应的计数器会在下次重试时被清理
        retryCounters.clear();
        
        try {
            // 等待正在执行的任务完成，最多等待 5 秒
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[Queue] 死信队列调度器未能在 5 秒内关闭，强制关闭");
                scheduler.shutdownNow();
                // 再次等待，确保任务被取消
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.error("[Queue] 死信队列调度器无法关闭");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Queue] 等待死信队列调度器关闭时被中断");
            scheduler.shutdownNow();
        }
        
        // 关闭死信队列底层存储（如果是独立创建的，需要关闭）
        // 注意：如果 dlqMessageTemplate 是 Spring Bean，Spring 会自动关闭，这里不需要关闭
        // 但如果是 createDlqStorage() 创建的独立实例，需要手动关闭
        if (dlqMessageTemplate != messageTemplate && dlqMessageTemplate != null) {
            try {
                dlqMessageTemplate.close();
            } catch (Exception e) {
                log.warn("Error closing DLQ message template", e);
            }
        }
        
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
        if (destination == null || handler == null) {
            throw new IllegalArgumentException("Destination and handler cannot be null");
        }
        
        messageTemplate.subscribe(destination, (message, ack) -> {
            if (message == null || ack == null) {
                log.error("[Queue] 收到空消息或空确认对象, 目标: {}", destination);
                return;
            }
            
            // 优先使用header中的原始消息ID（重试消息），否则使用消息ID，最后生成UUID
            String originalMessageId = message.getHeaderAsString(HEADER_ORIGINAL_MESSAGE_ID);
            String messageId = originalMessageId != null ? originalMessageId 
                    : (message.getId() != null ? message.getId() : UUID.randomUUID().toString());
            AtomicInteger counter = retryCounters.computeIfAbsent(messageId, k -> new AtomicInteger(0));

            try {
                handler.handle(message, ack);
                // 处理成功，清除重试计数（使用 remove 而不是直接删除，避免并发问题）
                AtomicInteger removed = retryCounters.remove(messageId);
                if (removed != null && removed.get() > 0) {
                    log.debug("[Queue] 消息处理成功，清除重试计数, 目标: {}, 消息ID: {}, 最终重试次数: {}",
                            destination, messageId, removed.get());
                }
            } catch (Exception e) {
                int retryCount = counter.incrementAndGet();
                log.warn("[Queue] 消息处理失败, 目标: {}, 消息ID: {}, 重试次数: {}/{}",
                        destination, messageId, retryCount, config.getMaxRetries(), e);

                if (retryCount < config.getMaxRetries()) {
                    // 延迟重试
                    scheduleRetry(destination, message, messageId, retryCount, e);
                    // 重试时确认原消息，避免重复消费
                    try {
                        ack.acknowledge();
                    } catch (Exception ackException) {
                        log.warn("Failed to acknowledge message for retry: {}", messageId, ackException);
                    }
                } else {
                    // 转移到死信队列
                    SendResult dlqResult = moveToDeadLetter(destination, message, e);
                    retryCounters.remove(messageId);
                    
                    // 只有在成功转移到死信队列后才确认原消息，避免消息丢失
                    if (dlqResult.isSuccess()) {
                        try {
                            ack.acknowledge();
                        } catch (Exception ackException) {
                            log.warn("Failed to acknowledge message after moving to DLQ: {}", messageId, ackException);
                        }
                    } else {
                        // 死信队列发送失败，不确认原消息，让消息重新投递
                        log.error("[Queue] 死信队列发送失败，不确认原消息以保留消息, 目标: {}, 消息ID: {}",
                                destination, messageId);
                    }
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
        if (destination == null || handler == null) {
            throw new IllegalArgumentException("Destination and handler cannot be null");
        }
        if (group == null) {
            throw new IllegalArgumentException("Group cannot be null");
        }
        
        messageTemplate.subscribe(destination, group, (message, ack) -> {
            if (message == null || ack == null) {
                log.error("[Queue] 收到空消息或空确认对象, 目标: {}, 组: {}", destination, group);
                return;
            }
            
            // 优先使用header中的原始消息ID（重试消息），否则使用消息ID，最后生成UUID
            String originalMessageId = message.getHeaderAsString(HEADER_ORIGINAL_MESSAGE_ID);
            String messageId = originalMessageId != null ? originalMessageId 
                    : (message.getId() != null ? message.getId() : UUID.randomUUID().toString());
            AtomicInteger counter = retryCounters.computeIfAbsent(messageId, k -> new AtomicInteger(0));

            try {
                handler.handle(message, ack);
                // 处理成功，清除重试计数
                AtomicInteger removed = retryCounters.remove(messageId);
                if (removed != null && removed.get() > 0) {
                    log.debug("[Queue] 消息处理成功，清除重试计数, 目标: {}, 组: {}, 消息ID: {}, 最终重试次数: {}",
                            destination, group, messageId, removed.get());
                }
            } catch (Exception e) {
                int retryCount = counter.incrementAndGet();
                log.warn("[Queue] 消息处理失败, 目标: {}, 组: {}, 消息ID: {}, 重试次数: {}/{}",
                        destination, group, messageId, retryCount, config.getMaxRetries(), e);

                if (retryCount < config.getMaxRetries()) {
                    scheduleRetry(destination, message, messageId, retryCount, e);
                    // 重试时确认原消息，避免重复消费
                    try {
                        ack.acknowledge();
                    } catch (Exception ackException) {
                        log.warn("Failed to acknowledge message for retry: {}", messageId, ackException);
                    }
                } else {
                    // 转移到死信队列
                    SendResult dlqResult = moveToDeadLetter(destination, message, e);
                    retryCounters.remove(messageId);
                    
                    // 只有在成功转移到死信队列后才确认原消息，避免消息丢失
                    if (dlqResult.isSuccess()) {
                        try {
                            ack.acknowledge();
                        } catch (Exception ackException) {
                            log.warn("Failed to acknowledge message after moving to DLQ: {}", messageId, ackException);
                        }
                    } else {
                        // 死信队列发送失败，不确认原消息，让消息重新投递
                        log.error("[Queue] 死信队列发送失败，不确认原消息以保留消息, 目标: {}, 组: {}, 消息ID: {}",
                                destination, group, messageId);
                    }
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
        if (originalDestination == null) {
            throw new IllegalArgumentException("Original destination cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        String dlqDestination = getDlqDestination(originalDestination);

        Map<String, Object> headers = new ConcurrentHashMap<>(message.getHeaders() != null ? message.getHeaders() : Map.of());
        headers.put(HEADER_ORIGINAL_DESTINATION, originalDestination);
        headers.put(HEADER_FAILURE_REASON, reason != null ? reason : "Unknown reason");
        headers.put(HEADER_FAILURE_TIME, LocalDateTime.now().toString());

        SendResult result = dlqMessageTemplate.send(dlqDestination, message.getPayload(), headers);
        if (result.isSuccess()) {
            log.info("[Queue] 消息已转移到死信队列: {}, 消息ID: {}, 类型: {}", 
                    dlqDestination, message.getId(), dlqMessageTemplate.getType());
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
        if (originalDestination == null) {
            throw new IllegalArgumentException("Original destination cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        
        String dlqDestination = getDlqDestination(originalDestination);
        dlqMessageTemplate.subscribe(dlqDestination, handler);
        log.info("[Queue] 订阅死信队列: {}, 类型: {}", dlqDestination, dlqMessageTemplate.getType());
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
        if (originalDestination == null) {
            throw new IllegalArgumentException("Original destination cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        // 重置重试计数
        Map<String, Object> headers = new ConcurrentHashMap<>(message.getHeaders() != null ? message.getHeaders() : Map.of());
        headers.put(HEADER_RETRY_COUNT, 0);
        headers.remove(HEADER_FAILURE_REASON);
        headers.remove(HEADER_FAILURE_TIME);

        SendResult result = messageTemplate.send(originalDestination, message.getPayload(), headers);
        if (result.isSuccess()) {
            log.info("[Queue] 死信消息已重新处理: {}, 消息ID: {}", originalDestination, message.getId());
        }
        return result;
    }

    /**
     * 批量重新处理死信消息
     * <p>
     * 注意：此方法会创建一个临时订阅，处理完 maxCount 条消息后自动取消订阅。
     * 如果多次调用此方法，每次都会创建新的订阅，建议使用唯一的目标地址。
     * </p>
     *
     * @param originalDestination 原始目标地址
     * @param maxCount            最大处理数量
     * @param handler             处理器，返回true表示重新发送，false表示丢弃
     */
    public void reprocessDeadLetters(String originalDestination, int maxCount,
                                     java.util.function.Predicate<Message> handler) {
        if (originalDestination == null) {
            throw new IllegalArgumentException("Original destination cannot be null");
        }
        if (maxCount <= 0) {
            throw new IllegalArgumentException("Max count must be greater than 0, got: " + maxCount);
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        
        String dlqDestination = getDlqDestination(originalDestination);
        AtomicInteger processed = new AtomicInteger(0);
        
        // 创建临时订阅，处理完指定数量后停止处理
        // 注意：不自动取消订阅，避免影响其他订阅；如需取消，请手动调用 unsubscribe
        MessageHandler messageHandler = (message, ack) -> {
            // 使用原子操作确保不会超过 maxCount（竞态条件修复）
            int currentCount = processed.get();
            if (currentCount >= maxCount) {
                // 已达到最大处理数量，确认消息但不处理
                try {
                    ack.acknowledge();
                } catch (Exception e) {
                    log.warn("Failed to acknowledge message after reaching max count: {}", message.getId(), e);
                }
                return;
            }

            // 使用 compareAndSet 确保原子性，避免竞态条件
            int newCount;
            do {
                currentCount = processed.get();
                if (currentCount >= maxCount) {
                    // 在检查和增加之间，其他线程已经达到 maxCount
                    try {
                        ack.acknowledge();
                    } catch (Exception e) {
                        log.warn("Failed to acknowledge message after concurrent max count: {}", message.getId(), e);
                    }
                    return;
                }
                newCount = currentCount + 1;
            } while (!processed.compareAndSet(currentCount, newCount));

            try {
                if (handler.test(message)) {
                    reprocessDeadLetter(originalDestination, message);
                } else {
                    log.info("[Queue] 死信消息已丢弃: {}", message.getId());
                }
                
                // 确认消息已处理
                try {
                    ack.acknowledge();
                } catch (Exception e) {
                    log.warn("Failed to acknowledge reprocessed message: {}", message.getId(), e);
                }
                
                if (newCount >= maxCount) {
                    log.info("[Queue] 批量重新处理完成，已处理 {} 条消息: {} (订阅仍存在，如需取消请手动调用 unsubscribe)", 
                            newCount, dlqDestination);
                }
            } catch (Exception e) {
                log.error("Error reprocessing dead letter: {}", message.getId(), e);
                // 处理失败时也确认消息，避免重复处理
                try {
                    ack.acknowledge();
                } catch (Exception ackException) {
                    log.warn("Failed to acknowledge failed reprocess message: {}", message.getId(), ackException);
                }
            }
        };
        
        dlqMessageTemplate.subscribe(dlqDestination, messageHandler);
        log.info("[Queue] 开始批量重新处理死信消息: {}, 最大数量: {}", dlqDestination, maxCount);
    }

    /**
     * 注册死信回调
     *
     * @param callback 回调函数（消息, 异常）
     */
    public void onDeadLetter(BiConsumer<Message, Throwable> callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null");
        }
        deadLetterCallbacks.add(callback);
    }

    /**
     * 获取死信队列名称
     *
     * @param originalDestination 原始目标地址
     * @return 死信队列名称
     */
    public String getDlqDestination(String originalDestination) {
        if (originalDestination == null) {
            throw new IllegalArgumentException("Original destination cannot be null");
        }
        return DLQ_PREFIX + originalDestination;
    }

    /**
     * 获取重试队列名称
     *
     * @param originalDestination 原始目标地址
     * @return 重试队列名称
     */
    public String getRetryDestination(String originalDestination) {
        if (originalDestination == null) {
            throw new IllegalArgumentException("Original destination cannot be null");
        }
        return RETRY_PREFIX + originalDestination;
    }

    /**
     * 调度重试
     */
    private void scheduleRetry(String destination, Message message, String originalMessageId, int retryCount, Exception cause) {
        Duration delay = calculateRetryDelay(retryCount);

        try {
            scheduler.schedule(() -> {
                try {
                    Map<String, Object> headers = new ConcurrentHashMap<>(message.getHeaders() != null ? message.getHeaders() : Map.of());
                    headers.put(HEADER_RETRY_COUNT, retryCount);
                    // 保留原始消息ID，确保重试计数正确跟踪
                    headers.put(HEADER_ORIGINAL_MESSAGE_ID, originalMessageId);

                    SendResult result = messageTemplate.send(destination, message.getPayload(), headers);
                    if (result.isSuccess()) {
                        log.debug("Retry scheduled for destination: {}, originalMessageId: {}, retryCount: {}, delay: {}ms",
                                destination, originalMessageId, retryCount, delay.toMillis());
                    } else {
                        String errorMsg = result.getError() != null ? result.getError().getMessage() : "Unknown error";
                        log.error("[Queue] 重试消息发送失败, 目标: {}, 消息ID: {}, 重试次数: {}, 错误: {}",
                                destination, originalMessageId, retryCount, errorMsg);
                        // 重试发送失败，直接转移到死信队列
                        moveToDeadLetter(destination, message, new RuntimeException("Retry send failed: " + errorMsg, 
                                result.getError() != null ? result.getError() : cause));
                        retryCounters.remove(originalMessageId);
                    }
                } catch (Exception e) {
                    log.error("[Queue] 重试消息发送异常, 目标: {}, 消息ID: {}, 重试次数: {}", 
                            destination, originalMessageId, retryCount, e);
                    // 重试发送异常，直接转移到死信队列
                    moveToDeadLetter(destination, message, e);
                    retryCounters.remove(originalMessageId);
                }
            }, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // scheduler 已关闭，无法调度重试任务，直接转移到死信队列
            log.warn("[Queue] 无法调度重试任务（调度器已关闭）, 目标: {}, 消息ID: {}, 直接转移到死信队列", 
                    destination, originalMessageId);
            moveToDeadLetter(destination, message, new RuntimeException("Scheduler is shutdown", cause));
            retryCounters.remove(originalMessageId);
        }
    }

    /**
     * 计算重试延迟（指数退避）
     */
    private Duration calculateRetryDelay(int retryCount) {
        if (!config.isExponentialBackoff()) {
            return config.getRetryDelay();
        }

        // retryCount 从 1 开始，第一次重试时 retryCount=1，延迟应该是 baseDelay
        // 第二次重试时 retryCount=2，延迟应该是 baseDelay * multiplier
        // 所以指数应该是 retryCount - 1，但需要确保至少为 0
        int exponent = Math.max(0, retryCount - 1);
        long baseDelayMs = config.getRetryDelay().toMillis();
        
        // 防止溢出：如果指数太大，直接使用最大延迟
        double multiplier = config.getBackoffMultiplier();
        long delayMs;
        if (exponent > 50 || multiplier <= 0) {
            // 指数太大或乘数异常，使用最大延迟
            delayMs = Long.MAX_VALUE;
        } else {
            double powResult = Math.pow(multiplier, exponent);
            // 检查是否会溢出
            if (powResult > Long.MAX_VALUE / (double) baseDelayMs) {
                delayMs = Long.MAX_VALUE;
            } else {
                delayMs = (long) (baseDelayMs * powResult);
            }
        }
        
        // 如果 maxRetryDelay 为 null，使用计算出的延迟（但限制在合理范围内）
        Duration maxRetryDelay = config.getMaxRetryDelay();
        if (maxRetryDelay == null) {
            // 如果没有设置最大延迟，限制在 1 小时内
            long maxDelayMs = Math.min(delayMs, Duration.ofHours(1).toMillis());
            return Duration.ofMillis(maxDelayMs);
        }
        
        long maxDelayMs = maxRetryDelay.toMillis();
        return Duration.ofMillis(Math.min(delayMs, maxDelayMs));
    }

    /**
     * 转移到死信队列
     * 
     * @return 发送结果，用于判断是否成功
     */
    private SendResult moveToDeadLetter(String destination, Message message, Exception cause) {
        String dlqDestination = getDlqDestination(destination);

        Map<String, Object> headers = new ConcurrentHashMap<>(message.getHeaders() != null ? message.getHeaders() : Map.of());
        headers.put(HEADER_ORIGINAL_DESTINATION, destination);
        headers.put(HEADER_FAILURE_REASON, cause != null && cause.getMessage() != null ? cause.getMessage() : "Unknown error");
        headers.put(HEADER_FAILURE_TIME, LocalDateTime.now().toString());
        headers.put(HEADER_RETRY_COUNT, config.getMaxRetries());

        SendResult result = dlqMessageTemplate.send(dlqDestination, message.getPayload(), headers);

        if (result.isSuccess()) {
            log.warn("Message moved to dead letter queue: {}, messageId: {}, reason: {}, dlqType: {}",
                    dlqDestination, message.getId(), cause != null ? cause.getMessage() : "Unknown", dlqMessageTemplate.getType());
        } else {
            String errorMsg = result.getError() != null ? result.getError().getMessage() : "Unknown error";
            log.error("Failed to move message to dead letter queue: {}, messageId: {}, dlqType: {}, error: {}",
                    dlqDestination, message.getId(), dlqMessageTemplate.getType(), errorMsg);
            // 死信队列发送失败，不确认原消息，让消息重新投递（由调用方处理）
        }

        // 触发回调（无论成功与否都触发）
        for (BiConsumer<Message, Throwable> callback : deadLetterCallbacks) {
            try {
                callback.accept(message, cause);
            } catch (Exception e) {
                log.error("Dead letter callback error", e);
            }
        }
        
        return result;
    }

    /**
     * 死信队列配置
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DeadLetterConfig {
        /**
         * 最大重试次数
         */
        private int maxRetries = 3;

        /**
         * 重试延迟
         */
        private Duration retryDelay = Duration.ofSeconds(5);

        /**
         * 最大重试延迟
         */
        private Duration maxRetryDelay = Duration.ofMinutes(5);

        /**
         * 是否启用指数退避
         */
        private boolean exponentialBackoff = true;

        /**
         * 退避乘数
         */
        private double backoffMultiplier = 2.0;

        // Lombok 注解处理器未运行时的手动构造函数
        public DeadLetterConfig() {
        }

        public DeadLetterConfig(int maxRetries, Duration retryDelay, Duration maxRetryDelay, boolean exponentialBackoff, double backoffMultiplier) {
            this.maxRetries = maxRetries;
            this.retryDelay = retryDelay;
            this.maxRetryDelay = maxRetryDelay;
            this.exponentialBackoff = exponentialBackoff;
            this.backoffMultiplier = backoffMultiplier;
        }

        // Lombok 注解处理器未运行时的手动 getter 方法
        public int getMaxRetries() {
            return maxRetries;
        }

        public Duration getRetryDelay() {
            return retryDelay;
        }

        public Duration getMaxRetryDelay() {
            return maxRetryDelay;
        }

        public boolean isExponentialBackoff() {
            return exponentialBackoff;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }
    }
}
