package com.chua.starter.queue.template;

import static com.chua.starter.common.support.text.json.Json.toJson;

import com.chua.starter.queue.Acknowledgment;
import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageHandler;
import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.SendResult;
import com.chua.starter.queue.properties.QueueProperties;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
* 基于Guava 事件bus的本地消息队列模板
* <p>
* 使用 {@link EventBus} 和 {@link AsyncEventBus} 实现的本地消息队列，
* 适用于单机环境或测试场景，支持同步和异步消息传递。
* </p>
*
* @版本 1.0.0
* @since 2025-01-02
* @author CH
 */
@Slf4j
public class LocalMessageTemplate implements MessageTemplate {

    /** 类型 */
    private static final String TYPE = "local";

    /**
    * 事件bus容器，每个目标对应一个事件bus
    */
    private final ConcurrentHashMap<String, EventBus> eventBuses = new ConcurrentHashMap<>();

    /**
    * 异步事件bus容器，每个目标对应一个异步事件bus
    */
    private final ConcurrentHashMap<String, AsyncEventBus> asyncEventBuses = new ConcurrentHashMap<>();

    /**
    * 异步事件bus使用的线程池容器，用于关闭时清理资源
    */
    private final ConcurrentHashMap<String, ExecutorService> asyncExecutors = new ConcurrentHashMap<>();

    /**
    * 订阅者容器，每个目标对应一组处理器
    */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<MessageHandler>> subscribers = new ConcurrentHashMap<>();

    /**
    * 事件bus订阅者容器，用于管理订阅者对象
    */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<EventBusSubscriber>> eventBusSubscribers = new ConcurrentHashMap<>();

    /**
    * 是否运行中
    */
    private volatile boolean running = true;

    /**
    * 配置
    */
    private final QueueProperties.LocalConfig config;

    /**
    * 延迟消息调度器（使用平台线程，因为虚拟线程不适合精确的定时调度）
    */
    private final ScheduledExecutorService delayScheduler;

    /**
    * 虚拟线程执行器，用于执行延迟消息的实际发送
    */
    private final ExecutorService virtualExecutor;

    /**
    * 创建 本地消息template 实例
    * @param config 配置
    */
    public LocalMessageTemplate(QueueProperties.LocalConfig config) {
        this.config = config;
        // 延迟消息调度器使用平台线程（虚拟线程不适合精确的定时调度）
        this.delayScheduler = Executors.newScheduledThreadPool(
                config != null && config.getDelayThreads() > 0 ? config.getDelayThreads() : 2,
                r -> {
                    Thread t = new Thread(r, "local-queue-delay");
                    t.setDaemon(true);
                    return t;
                });
        // 虚拟线程执行器，用于执行延迟消息的实际发送和异步消息处理
        this.virtualExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("local-queue-", 0).factory());
        log.info("[Queue] 本地消息队列初始化完成 (基于Guava EventBus，使用Java 21虚拟线程)");
    }

    @Override
    /** 发送 */
    public SendResult send(String destination, Object payload) {
        return send(destination, payload, null);
    }

    @Override
    /** 发送异步 */
    public CompletableFuture<SendResult> sendAsync(String destination, Object payload) {
        return sendAsync(destination, payload, null);
    }

    /**
    * 异步发送带头部的消息
    */
    @Override
    public CompletableFuture<SendResult> sendAsync(String destination, Object payload, Map<String, Object> headers) {
        String messageId = UUID.randomUUID().toString();
        CompletableFuture<SendResult> future = new CompletableFuture<>();
        
        // 检查是否已关闭
        if (!running) {
            future.complete(SendResult.failure(destination, new IllegalStateException("MessageTemplate is closed")));
            return future;
        }
        
        try {
            byte[] bytes = serializePayload(payload);
            var message = new Message();
            message.setId(messageId);
            message.setDestination(destination);
            message.setPayload(bytes);
            message.setHeaders(headers != null ? headers : new ConcurrentHashMap<>());
            message.setTimestamp(System.currentTimeMillis());
            message.setType(TYPE);

 // 使用异步事件bus发送消息
            AsyncEventBus asyncEventBus = getOrCreateAsyncEventBus(destination);
 // Async事件bus.post() 是异步的，不会抛出异常，所以这里直接完成期货
 // 如果消息处理失败，会在处理器中记录日志，但不会影响发送异步的返回值
            asyncEventBus.post(message);

            log.debug("Message sent asynchronously to destination: {}, messageId: {}", destination, messageId);
            future.complete(SendResult.success(messageId, destination));
        } catch (Exception e) {
            log.error("Async send failed for destination: {}", destination, e);
            future.complete(SendResult.failure(destination, e));
        }
        
        return future;
    }

    @Override
    /** 发送 */
    public SendResult send(String destination, Object payload, Map<String, Object> headers) {
        // 检查是否已关闭
        if (!running) {
            return SendResult.failure(destination, new IllegalStateException("MessageTemplate is closed"));
        }
        
        String messageId = UUID.randomUUID().toString();
        try {
            byte[] bytes = serializePayload(payload);
            var message = new Message();
            message.setId(messageId);
            message.setDestination(destination);
            message.setPayload(bytes);
            message.setHeaders(headers != null ? headers : new ConcurrentHashMap<>());
            message.setTimestamp(System.currentTimeMillis());
            message.setType(TYPE);

 // 使用同步事件bus发送消息
            EventBus eventBus = getOrCreateEventBus(destination);
            eventBus.post(message);

            log.debug("Message sent to destination: {}, messageId: {}", destination, messageId);
            return SendResult.success(messageId, destination);
        } catch (Exception e) {
            log.error("Send failed for destination: {}", destination, e);
            return SendResult.failure(destination, e);
        }
    }

    @Override
    /** 发送延迟 */
    public SendResult sendDelayed(String destination, Object payload, Duration delay) {
        // 检查是否已关闭
        if (!running) {
            return SendResult.failure(destination, new IllegalStateException("MessageTemplate is closed"));
        }
        
        String messageId = UUID.randomUUID().toString();
        try {
            byte[] bytes = serializePayload(payload);
            var message = new Message();
            message.setId(messageId);
            message.setDestination(destination);
            message.setPayload(bytes);
            message.setTimestamp(System.currentTimeMillis());
            message.setType(TYPE);

            // 使用平台线程调度，虚拟线程执行
            delayScheduler.schedule(() -> {
                // 检查是否已关闭，避免在关闭后发送消息
                if (running) {
                    virtualExecutor.execute(() -> {
                        if (running) {
                            EventBus eventBus = getOrCreateEventBus(destination);
                            eventBus.post(message);
                        }
                    });
                }
            }, delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);

            log.debug("Delayed message scheduled for destination: {}, delay: {}", destination, delay);
            return SendResult.success(messageId, destination);
        } catch (Exception e) {
            log.error("Send delayed failed for destination: {}", destination, e);
            return SendResult.failure(destination, e);
        }
    }

    @Override
    /** 订阅 */
    public void subscribe(String destination, MessageHandler handler, boolean autoAck) {
        CopyOnWriteArrayList<MessageHandler> handlers = subscribers.computeIfAbsent(
                destination, k -> new CopyOnWriteArrayList<>());
        handlers.add(handler);

 // 获取或创建事件bus
        EventBus eventBus = getOrCreateEventBus(destination);

 // 创建并注册订阅者到事件bus
        EventBusSubscriber subscriber = new EventBusSubscriber(destination, handler, autoAck);
        eventBus.register(subscriber);

        // 记录订阅者对象
        CopyOnWriteArrayList<EventBusSubscriber> subscribers = eventBusSubscribers.computeIfAbsent(
                destination, k -> new CopyOnWriteArrayList<>());
        subscribers.add(subscriber);

        log.info("[Queue] 订阅目标: {} (autoAck: {})", highlight(destination), highlight(String.valueOf(autoAck)));
    }

    @Override
    /** 订阅 */
    public void subscribe(String destination, String group, MessageHandler handler, boolean autoAck) {
        // 本地队列不支持消费组，直接订阅
        /**
    * 订阅。
    * @param destination 目标
    * @param handler 处理器
    * @param autoAck autoACK
    */
        subscribe(destination, handler, autoAck);
    }

    @Override
    /** 订阅 */
    public void subscribe(String destination, MessageHandler handler, boolean autoAck, int concurrency) {
        if (concurrency <= 1) {
            /**
    * 订阅。
    * @param destination 目标
    * @param handler 处理器
    * @param autoAck autoACK
    */
            subscribe(destination, handler, autoAck);
            return;
        }
        
        // 对于并发处理，使用 Semaphore 限制并发数，使用虚拟线程池执行
        // 创建固定大小的虚拟线程池执行器（实际使用虚拟线程，可以支持大量并发）
        ExecutorService concurrentExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("local-queue-concurrent-" + destination + "-", 0).factory());
        java.util.concurrent.Semaphore semaphore = new java.util.concurrent.Semaphore(concurrency);
        
 // 包装处理器，使用信号量控制并发数
        MessageHandler concurrentHandler = (message, ack) -> {
            try {
                // 获取信号量许可
                semaphore.acquire();
                concurrentExecutor.execute(() -> {
                    try {
                        handler.handle(message, ack);
                    } catch (Exception e) {
                        log.error("Error in concurrent message handler for destination: {}", destination, e);
                        // 异常时也要释放信号量
                    } finally {
                        semaphore.release();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while acquiring semaphore for destination: {}", destination, e);
                semaphore.release();
            }
        };
        
 // 注册包装后的处理器到同步事件bus（因为并发控制已经在处理器内部）
        CopyOnWriteArrayList<MessageHandler> handlers = subscribers.computeIfAbsent(
                destination, k -> new CopyOnWriteArrayList<>());
        handlers.add(concurrentHandler);

 // 获取或创建事件bus
        EventBus eventBus = getOrCreateEventBus(destination);

 // 创建并注册订阅者到事件bus
        EventBusSubscriber subscriber = new EventBusSubscriber(destination, concurrentHandler, autoAck);
        eventBus.register(subscriber);

        // 记录订阅者对象
        CopyOnWriteArrayList<EventBusSubscriber> eventBusSubs = eventBusSubscribers.computeIfAbsent(
                destination, k -> new CopyOnWriteArrayList<>());
        eventBusSubs.add(subscriber);
        
        // 记录并发执行器，用于关闭时清理
        asyncExecutors.put(destination + "-concurrent", concurrentExecutor);
        
        log.info("[Queue] 订阅目标: {} (autoAck: {}, concurrency: {})", 
                highlight(destination), highlight(String.valueOf(autoAck)), highlight(String.valueOf(concurrency)));
    }

    @Override
    /** 订阅 */
    public void subscribe(String destination, String group, MessageHandler handler, boolean autoAck, int concurrency) {
        // 本地队列不支持消费组，直接订阅
        /**
    * 订阅。
    * @param destination 目标
    * @param handler 处理器
    * @param autoAck autoACK
    * @param concurrency concurrency
    */
        subscribe(destination, handler, autoAck, concurrency);
    }

    @Override
    /** 取消订阅 */
    public void unsubscribe(String destination) {
        // 先检查是否还有订阅者，再决定是否清理资源
        CopyOnWriteArrayList<MessageHandler> remainingHandlers = this.subscribers.get(destination);
        
 // 获取该目标的所有订阅者
        CopyOnWriteArrayList<EventBusSubscriber> subscribers = eventBusSubscribers.remove(destination);
        if (subscribers != null && !subscribers.isEmpty()) {
 // 获取对应的事件bus，取消注册所有订阅者
            EventBus eventBus = eventBuses.get(destination);
            if (eventBus != null) {
                for (EventBusSubscriber subscriber : subscribers) {
                    try {
                        eventBus.unregister(subscriber);
                    } catch (IllegalArgumentException e) {
                        // 订阅者可能已经被取消注册，忽略
                        log.debug("Subscriber already unregistered for destination: {}", destination);
                    }
                }
            }
        }
        
        // 移除订阅者列表
        this.subscribers.remove(destination);
        
 // 检查是否还有同步订阅者，如果没有则移除同步事件bus
        if (remainingHandlers == null || remainingHandlers.isEmpty()) {
            eventBuses.remove(destination);
            log.debug("EventBus removed for destination: {} (no more subscribers)", destination);
            
 // 如果没有同步订阅者，也移除异步事件bus并关闭其线程池
 // 注意：异步事件bus主要用于异步发送，但如果没有订阅者，也可以清理
            AsyncEventBus asyncEventBus = asyncEventBuses.remove(destination);
            if (asyncEventBus != null) {
                log.debug("AsyncEventBus removed for destination: {}", destination);
            }
            
            // 关闭并移除对应的线程池（包括异步和并发执行器）
            ExecutorService executor = asyncExecutors.remove(destination);
            if (executor != null) {
                /**
    * 关闭执行器。
    * @param executor 执行器
    * @param destination 目标
    */
                shutdownExecutor(executor, destination);
            }
            
            // 关闭并发执行器（如果存在）
            ExecutorService concurrentExecutor = asyncExecutors.remove(destination + "-concurrent");
            if (concurrentExecutor != null) {
                /**
                * 关闭执行器。
                * @param concurrentExecutor 并发执行器
                * @param "-concurrent" "-并发"
                */
                shutdownExecutor(concurrentExecutor, destination + "-concurrent");
            }
        }
        
        log.info("[Queue] 取消订阅: {}", highlight(destination));
    }

    @Override
    /** 获取类型 */
    public String getType() {
        return TYPE;
    }

    @Override
    /** 是否连接 */
    public boolean isConnected() {
        return running;
    }

    @Override
    /** 关闭 */
    public void close() {
        running = false;

        // 关闭延迟消息调度器
        delayScheduler.shutdown();
        try {
            if (!delayScheduler.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                delayScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            delayScheduler.shutdownNow();
        }

        // 关闭虚拟线程执行器
        virtualExecutor.shutdown();
        try {
            if (!virtualExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                virtualExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            virtualExecutor.shutdownNow();
        }

 // 关闭所有异步事件bus和并发执行器使用的线程池
        for (Map.Entry<String, ExecutorService> entry : asyncExecutors.entrySet()) {
            /**
    * 关闭执行器。
    * @param entry.getValue( entry.获取值(
    */
            shutdownExecutor(entry.getValue(), entry.getKey());
        }

 // 清空所有事件bus和线程池
        eventBuses.clear();
        asyncEventBuses.clear();
        asyncExecutors.clear();
        subscribers.clear();
        eventBusSubscribers.clear();

        log.info("[Queue] 本地消息队列已关闭");
    }

    /**
    * 安全关闭执行器
    * @param executor 执行器
    * @param name 名称
    */
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        log.debug("ExecutorService closed: {}", name);
    }

    /**
    * 获取或创建同步事件bus
    * @param destination 目标
    * @return 获取or创建事件bus的结果
    */
    private EventBus getOrCreateEventBus(String destination) {
        return eventBuses.computeIfAbsent(destination, k -> new EventBus(destination));
    }

    /**
    * 获取或创建异步事件bus（使用虚拟线程）
    * @param destination 目标
    * @return 获取or创建Async事件bus的结果
    */
    private AsyncEventBus getOrCreateAsyncEventBus(String destination) {
        return asyncEventBuses.computeIfAbsent(destination, k -> {
 // 为每个目标创建独立的虚拟线程执行器
            ExecutorService executor = Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("local-queue-async-" + destination + "-", 0).factory());
            asyncExecutors.put(destination, executor);
            /**
            * 异步事件bus。
            * @param destination 目标
            * @param executor 执行器
            * @return Async事件bus的结果
            */
            return new AsyncEventBus(destination, executor);
        });
    }

    /**
    * 事件bus订阅者包装类
    * @author CH
    * @since 4.0.0
    */
    private class EventBusSubscriber {
        /** 日志 */
        private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EventBusSubscriber.class);
        /** 目的地 */
        private final String destination;
        /** 处理器 */
        private final MessageHandler handler;
        /** autoACK */
        private final boolean autoAck;

        /**
        * 事件bussubscriber。
        * @param destination 目标
        * @param handler 处理器
        * @param autoAck autoACK
        */
        EventBusSubscriber(String destination, MessageHandler handler, boolean autoAck) {
            this.destination = destination;
            this.handler = handler;
            this.autoAck = autoAck;
        }

        @Subscribe
        /**
        * 处理消息
        *
        * @param message 消息
        */
        public void handleMessage(Message message) {
            try {
 // 创建承认对象
                LocalAcknowledgment ack = new LocalAcknowledgment();
                if (message.getAcknowledgment() == null) {
                    message.setAcknowledgment(ack);
                }

                // 调用处理器
                handler.handle(message, message.getAcknowledgment());

                // 如果是自动确认模式，自动确认消息
                if (autoAck && !ack.isAcknowledged()) {
                    ack.acknowledge();
                }
            } catch (Exception e) {
                log.error("Handler error for destination: {}", destination, e);
                // 在自动确认模式下，即使异常也确认消息（因为无法重试）
                if (autoAck && message.getAcknowledgment() != null) {
                    message.getAcknowledgment().acknowledge();
                }
            }
        }
    }

    /**
    * 本地队列的ACK实现
    * @author CH
    * @since 4.0.0
    */
    private static class LocalAcknowledgment implements Acknowledgment {
        private volatile boolean acknowledged = false; // acknowledged

        @Override
        /** Acknowledge */
        public void acknowledge() {
            acknowledged = true;
        }

        @Override
        /** Nack */
        public void nack(boolean requeue) {
            // 本地队列不支持requeue，nack等同于丢弃
            acknowledged = false;
        }

        @Override
        /** 是否Acknowledged */
        public boolean isAcknowledged() {
            return acknowledged;
        }
    }

    /**
        * 序列化消息内容
        * @param payload payload
        * @return serializePayload的结果
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
        return toJson(payload).getBytes(StandardCharsets.UTF_8);
    }
}

