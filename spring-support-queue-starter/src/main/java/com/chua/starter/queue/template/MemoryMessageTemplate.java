package com.chua.starter.queue.template;

import com.chua.common.support.json.Json;
import com.chua.starter.queue.Acknowledgment;
import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageHandler;
import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.SendResult;
import com.chua.starter.queue.properties.QueueProperties;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
 * 基于JDK的内存消息队列模板
 * <p>
 * 使用 {@link LinkedBlockingQueue} 实现的本地内存消息队列，
 * 适用于单机环境或测试场景。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-25
 */
@Slf4j
public class MemoryMessageTemplate implements MessageTemplate {

    private static final String TYPE = "memory";

    /**
     * 队列容器，每个destination对应一个阻塞队列
     */
    private final ConcurrentMap<String, BlockingQueue<Message>> queues = new ConcurrentHashMap<>();

    /**
     * 订阅者容器，每个destination对应一组处理器
     */
    private final ConcurrentMap<String, CopyOnWriteArrayList<MessageHandler>> subscribers = new ConcurrentHashMap<>();

    /**
     * 消费者线程池
     */
    private final ExecutorService consumerExecutor;

    /**
     * 延迟消息调度器
     */
    private final ScheduledExecutorService delayScheduler;

    /**
     * 消费者线程管理
     */
    private final ConcurrentMap<String, Future<?>> consumerTasks = new ConcurrentHashMap<>();

    /**
     * 是否运行中
     */
    private volatile boolean running = true;

    /**
     * 配置
     */
    private final QueueProperties.MemoryConfig config;

    public MemoryMessageTemplate(QueueProperties.MemoryConfig config) {
        this.config = config;
        this.consumerExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "memory-queue-consumer");
            t.setDaemon(true);
            return t;
        });
        this.delayScheduler = Executors.newScheduledThreadPool(config.getDelayThreads(), r -> {
            Thread t = new Thread(r, "memory-queue-delay");
            t.setDaemon(true);
            return t;
        });
        log.info("[Queue] 内存消息队列初始化完成, 容量: {}", highlight(config.getQueueCapacity()));
    }

    @Override
    public SendResult send(String destination, Object payload) {
        return send(destination, payload, null);
    }

    @Override
    public CompletableFuture<SendResult> sendAsync(String destination, Object payload) {
        return CompletableFuture.supplyAsync(() -> send(destination, payload), consumerExecutor);
    }

    @Override
    public SendResult send(String destination, Object payload, Map<String, Object> headers) {
        String messageId = UUID.randomUUID().toString();
        try {
            byte[] bytes = serializePayload(payload);
            Message message = Message.builder()
                    .id(messageId)
                    .destination(destination)
                    .payload(bytes)
                    .headers(headers != null ? headers : new ConcurrentHashMap<>())
                    .timestamp(System.currentTimeMillis())
                    .type(TYPE)
                    .build();

            BlockingQueue<Message> queue = getOrCreateQueue(destination);

            // 使用offer而非put，避免无限阻塞
            boolean offered = queue.offer(message, config.getSendTimeout(), TimeUnit.MILLISECONDS);
            if (offered) {
                log.debug("Message sent to destination: {}, messageId: {}", destination, messageId);
                return SendResult.success(messageId, destination);
            } else {
                log.warn("Queue full for destination: {}", destination);
                return SendResult.failure(destination, new RuntimeException("Queue is full"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Send interrupted for destination: {}", destination, e);
            return SendResult.failure(destination, e);
        } catch (Exception e) {
            log.error("Send failed for destination: {}", destination, e);
            return SendResult.failure(destination, e);
        }
    }

    @Override
    public SendResult sendDelayed(String destination, Object payload, Duration delay) {
        String messageId = UUID.randomUUID().toString();
        try {
            byte[] bytes = serializePayload(payload);
            Message message = Message.builder()
                    .id(messageId)
                    .destination(destination)
                    .payload(bytes)
                    .timestamp(System.currentTimeMillis())
                    .type(TYPE)
                    .build();

            delayScheduler.schedule(() -> {
                BlockingQueue<Message> queue = getOrCreateQueue(destination);
                try {
                    queue.offer(message, config.getSendTimeout(), TimeUnit.MILLISECONDS);
                    log.debug("Delayed message delivered to destination: {}, messageId: {}", destination, messageId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Delayed message delivery interrupted: {}", messageId, e);
                }
            }, delay.toMillis(), TimeUnit.MILLISECONDS);

            log.debug("Delayed message scheduled for destination: {}, delay: {}", destination, delay);
            return SendResult.success(messageId, destination);
        } catch (Exception e) {
            log.error("Send delayed failed for destination: {}", destination, e);
            return SendResult.failure(destination, e);
        }
    }

    @Override
    public void subscribe(String destination, MessageHandler handler, boolean autoAck) {
        CopyOnWriteArrayList<MessageHandler> handlers = subscribers.computeIfAbsent(
                destination, k -> new CopyOnWriteArrayList<>());
        handlers.add(handler);

        // 确保队列存在
        getOrCreateQueue(destination);

        // 启动消费者线程（如果尚未启动）
        consumerTasks.computeIfAbsent(destination, this::startConsumer);

        log.info("[Queue] 订阅目标: {} (autoAck: {})", highlight(destination), highlight(String.valueOf(autoAck)));
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler, boolean autoAck) {
        // 内存队列不支持消费组，直接订阅
        subscribe(destination, handler, autoAck);
    }

    @Override
    public void unsubscribe(String destination) {
        subscribers.remove(destination);
        Future<?> task = consumerTasks.remove(destination);
        if (task != null) {
            task.cancel(true);
        }
        log.info("[Queue] 取消订阅: {}", highlight(destination));
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isConnected() {
        return running;
    }

    @Override
    public void close() {
        running = false;

        // 取消所有消费者任务
        consumerTasks.values().forEach(task -> task.cancel(true));
        consumerTasks.clear();

        // 关闭线程池
        consumerExecutor.shutdown();
        delayScheduler.shutdown();

        try {
            if (!consumerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                consumerExecutor.shutdownNow();
            }
            if (!delayScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                delayScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            consumerExecutor.shutdownNow();
            delayScheduler.shutdownNow();
        }

        // 清空队列
        queues.clear();
        subscribers.clear();

        log.info("[Queue] 内存消息队列已关闭");
    }

    /**
     * 获取队列大小
     *
     * @param destination 目标地址
     * @return 队列中的消息数量
     */
    public int getQueueSize(String destination) {
        BlockingQueue<Message> queue = queues.get(destination);
        return queue != null ? queue.size() : 0;
    }

    /**
     * 获取所有队列名称
     *
     * @return 队列名称集合
     */
    public java.util.Set<String> getDestinations() {
        return queues.keySet();
    }

    /**
     * 清空指定队列
     *
     * @param destination 目标地址
     */
    public void clear(String destination) {
        BlockingQueue<Message> queue = queues.get(destination);
        if (queue != null) {
            queue.clear();
            log.info("[Queue] 队列已清空: {}", highlight(destination));
        }
    }

    /**
     * 清空所有队列
     */
    public void clearAll() {
        queues.values().forEach(BlockingQueue::clear);
        log.info("[Queue] 所有队列已清空");
    }

    /**
     * 获取或创建队列
     */
    private BlockingQueue<Message> getOrCreateQueue(String destination) {
        return queues.computeIfAbsent(destination,
                k -> new LinkedBlockingQueue<>(config.getQueueCapacity()));
    }

    /**
     * 启动消费者线程
     */
    private Future<?> startConsumer(String destination) {
        return consumerExecutor.submit(() -> {
            BlockingQueue<Message> queue = getOrCreateQueue(destination);
            log.debug("Consumer started for destination: {}", destination);

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Message message = queue.poll(1, TimeUnit.SECONDS);
                    if (message != null) {
                        dispatchMessage(destination, message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error processing message from destination: {}", destination, e);
                }
            }

            log.debug("Consumer stopped for destination: {}", destination);
        });
    }

    /**
     * 分发消息给所有订阅者
     */
    private void dispatchMessage(String destination, Message message) {
        CopyOnWriteArrayList<MessageHandler> handlers = subscribers.get(destination);
        if (handlers == null || handlers.isEmpty()) {
            log.warn("No handlers for destination: {}, message discarded", destination);
            return;
        }

        // 内存队列的 ACK 实现（虽然不需要真正的 ack，但保持接口一致）
        MemoryAcknowledgment ack = new MemoryAcknowledgment();
        if (message.getAcknowledgment() == null) {
            message.setAcknowledgment(ack);
        }

        for (MessageHandler handler : handlers) {
            try {
                handler.handle(message, message.getAcknowledgment());
            } catch (Exception e) {
                log.error("Handler error for destination: {}", destination, e);
            }
        }
    }

    /**
     * 内存队列的 ACK 实现（用于保持接口一致性）
     */
    private static class MemoryAcknowledgment implements Acknowledgment {
        @Override
        public void acknowledge() {
            // 内存队列中，消息从队列取出即视为已确认
        }

        @Override
        public void nack(boolean requeue) {
            // 内存队列不支持 requeue，nack 等同于丢弃
            // 如果需要 requeue，需要在业务层实现
        }
    }

    /**
     * 序列化消息内容
     */
    private byte[] serializePayload(Object payload) {
        if (payload == null) {
            return new byte[0];
        }
        if (payload instanceof byte[]) {
            return (byte[]) payload;
        }
        if (payload instanceof String) {
            return ((String) payload).getBytes(StandardCharsets.UTF_8);
        }
        return Json.toJson(payload).getBytes(StandardCharsets.UTF_8);
    }
}
