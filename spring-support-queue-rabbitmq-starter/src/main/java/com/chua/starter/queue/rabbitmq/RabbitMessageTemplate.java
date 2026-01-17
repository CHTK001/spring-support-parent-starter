package com.chua.starter.queue.rabbitmq;

import com.chua.starter.queue.Acknowledgment;
import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageHandler;
import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.SendResult;
import com.chua.starter.queue.properties.QueueProperties;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
public class RabbitMessageTemplate implements MessageTemplate {

    private static final Logger log = LoggerFactory.getLogger(RabbitMessageTemplate.class);
    private final AmqpTemplate amqpTemplate;
    private final ConnectionFactory connectionFactory;
    private final QueueProperties props;
    private final Map<String, SimpleMessageListenerContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> autoAckMap = new ConcurrentHashMap<>();
    private final Executor virtualExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("rabbitmq-send-", 0).factory());

    public RabbitMessageTemplate(AmqpTemplate amqpTemplate, ConnectionFactory connectionFactory, QueueProperties props) {
        this.amqpTemplate = amqpTemplate;
        this.connectionFactory = connectionFactory;
        this.props = props;
    }

    @Override
    public SendResult send(String destination, Object payload) {
        try {
            if (amqpTemplate instanceof RabbitTemplate rt) {
                rt.convertAndSend(destination, payload);
            } else {
                amqpTemplate.convertAndSend(destination, payload);
            }
            return SendResult.success(null, destination);
        } catch (Exception e) {
            return SendResult.failure(destination, e);
        }
    }

    @Override
    public CompletableFuture<SendResult> sendAsync(String destination, Object payload) {
        return CompletableFuture.supplyAsync(() -> send(destination, payload), virtualExecutor);
    }

    @Override
    public SendResult send(String destination, Object payload, Map<String, Object> headers) {
        try {
            org.springframework.amqp.core.Message message = MessageBuilder.withBody(toBytes(payload))
                    .andProperties(new MessageProperties())
                    .build();
            if (headers != null && !headers.isEmpty()) {
                headers.forEach((k, v) -> message.getMessageProperties().setHeader(k, v));
            }
            if (amqpTemplate instanceof RabbitTemplate rt) {
                rt.send(destination, message);
            } else {
                amqpTemplate.convertAndSend(destination, message);
            }
            return SendResult.success(null, destination);
        } catch (Exception e) {
            return SendResult.failure(destination, e);
        }
    }

    @Override
    public void subscribe(String destination, MessageHandler handler, boolean autoAck) {
        subscribe(destination, handler, autoAck, 1);
    }

    @Override
    public void subscribe(String destination, MessageHandler handler, boolean autoAck, int concurrency) {
        subscribe(destination, null, handler, autoAck, concurrency);
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler, boolean autoAck, int concurrency) {
        // RabbitMQ 的 group 参数主要用于消费组标识，不影响实际订阅逻辑
        // 但为了支持同一个 destination 的不同配置，key 应该包含 group、autoAck 和 concurrency
        // 注意：RabbitMQ 的队列名就是 destination，同一个队列通常只需要一个容器
        // 但如果需要不同的并发设置，可以使用不同的 group
        String actualGroup = group == null || group.isEmpty() ? "default" : group;
        String key = destination + "|" + actualGroup + "|" + autoAck + "|" + concurrency;
        autoAckMap.put(key, autoAck);
        containers.computeIfAbsent(key, k -> {
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
            container.setQueueNames(destination);
            container.setAcknowledgeMode(autoAck ? AcknowledgeMode.AUTO : AcknowledgeMode.MANUAL);
            
            // 设置并发消费者数量
            if (concurrency > 1) {
                container.setConcurrentConsumers(concurrency);
                container.setMaxConcurrentConsumers(concurrency);
            }
            
            if (autoAck) {
                // 自动确认模式
                container.setMessageListener((org.springframework.amqp.core.Message msg) -> {
                    Map<String, Object> headers = new HashMap<>();
                    if (msg.getMessageProperties() != null && msg.getMessageProperties().getHeaders() != null) {
                        msg.getMessageProperties().getHeaders().forEach((headerKey, headerValue) -> {
                            if (headerValue != null) {
                                headers.put(headerKey, headerValue);
                            }
                        });
                    }
                    Message m = Message.builder()
                            .destination(destination)
                            .payload(msg.getBody())
                            .headers(headers)
                            .timestamp(System.currentTimeMillis())
                            .type(getType())
                            .originalMessage(msg)
                            .build();
                    try {
                        handler.handle(m, new AutoAcknowledgment());
                    } catch (Exception e) {
                        // 自动确认模式下，即使异常也记录日志（消息已自动确认，无法重试）
                        log.error("Error handling message in auto-ack mode, queue: {}, messageId: {}", 
                                destination, msg.getMessageProperties().getMessageId(), e);
                    }
                });
            } else {
                // 手动确认模式
                container.setMessageListener((ChannelAwareMessageListener) (msg, channel) -> {
                    long deliveryTag = msg.getMessageProperties().getDeliveryTag();
                    RabbitAcknowledgment ack = new RabbitAcknowledgment(channel, deliveryTag);
                    
                    Map<String, Object> headers = new HashMap<>();
                    if (msg.getMessageProperties() != null && msg.getMessageProperties().getHeaders() != null) {
                        msg.getMessageProperties().getHeaders().forEach((headerKey2, headerValue2) -> {
                            if (headerValue2 != null) {
                                headers.put(headerKey2, headerValue2);
                            }
                        });
                    }
                    Message m = Message.builder()
                            .destination(destination)
                            .payload(msg.getBody())
                            .headers(headers)
                            .timestamp(System.currentTimeMillis())
                            .type(getType())
                            .originalMessage(msg)
                            .acknowledgment(ack)
                            .build();
                    try {
                        handler.handle(m, ack);
                        // 如果 handler 没有确认也没有 nack，消息会保持未确认状态
                        // Spring AMQP 会根据配置决定是否重新入队
                    } catch (Exception e) {
                        // 处理异常，记录日志
                        log.error("Error handling message from queue: {}, deliveryTag: {}", 
                                destination, deliveryTag, e);
                        // 如果消息未被确认，RabbitMQ 会根据配置决定是否重新入队
                        // 这里不抛出异常，避免 Spring AMQP 拒绝消息
                        // 如果 handler 没有确认消息，消息会保持未确认状态，可以重新入队
                        // 注意：如果消息一直失败，会导致消息堆积，需要监控和处理
                        // 如果需要拒绝消息，应该在 handler 中调用 ack.nack(false)
                        // 如果需要重新入队，应该在 handler 中调用 ack.nack(true) 或不调用任何确认方法
                    }
                });
            }
            
            container.start();
            return container;
        });
    }

    /**
     * RabbitMQ 手动确认实现
     */
    @Slf4j
    private static class RabbitAcknowledgment implements Acknowledgment {
        private static final Logger log = LoggerFactory.getLogger(RabbitAcknowledgment.class);
        private final Channel channel;
        private final long deliveryTag;
        private volatile boolean acknowledged = false;
        private String deadLetterQueue;
        private String deadLetterReason;

        RabbitAcknowledgment(Channel channel, long deliveryTag) {
            this.channel = channel;
            this.deliveryTag = deliveryTag;
        }

        @Override
        public void acknowledge() {
            if (!acknowledged) {
                try {
                    channel.basicAck(deliveryTag, false);
                    acknowledged = true;
                } catch (Exception e) {
                    // 确认失败时，记录日志但不抛出异常，避免影响消息处理流程
                    log.warn("Failed to acknowledge message, deliveryTag: {}", deliveryTag, e);
                    // 如果 channel 已关闭，RabbitMQ 会自动处理未确认的消息
                    // 抛出异常可能导致消息重复处理
                    // 注意：这可能导致消息重复处理，但比抛出异常导致整个处理失败要好
                    // 理想情况下，应该检查 channel 状态，但这里简化处理
                    acknowledged = false; // 标记为未确认，因为确认操作失败
                    // 不抛出异常，避免影响消息处理流程
                    // 如果确实需要严格处理，可以考虑使用回调或异步确认
                }
            }
        }

        @Override
        public void nack(boolean requeue) {
            if (!acknowledged) {
                try {
                    channel.basicNack(deliveryTag, false, requeue);
                    acknowledged = true;
                } catch (Exception e) {
                    // nack 失败时，记录日志但不抛出异常，避免影响消息处理流程
                    log.warn("Failed to nack message, deliveryTag: {}, requeue: {}", deliveryTag, requeue, e);
                    // 如果 channel 已关闭，RabbitMQ 会自动处理未确认的消息
                    acknowledged = false; // 标记为未确认，因为 nack 操作失败
                    // 不抛出异常，避免影响消息处理流程
                }
            }
        }

        @Override
        public void nackToDeadLetter(String deadLetterQueue, String reason) {
            if (!acknowledged) {
                // 记录死信队列信息
                this.deadLetterQueue = deadLetterQueue;
                this.deadLetterReason = reason;
                // RabbitMQ 的 nack(requeue=false) 会将消息发送到死信队列（如果配置了）
                // 如果需要发送到指定的死信队列，需要外部支持（如 DeadLetterTemplate）
                try {
                    channel.basicNack(deliveryTag, false, false);
                    acknowledged = true;
                } catch (Exception e) {
                    // nack 失败时，记录日志但不抛出异常，避免影响消息处理流程
                    log.error("Failed to nack message to dead letter queue, deliveryTag: {}, deadLetterQueue: {}", 
                            deliveryTag, deadLetterQueue, e);
                    // 标记为未确认，因为 nack 操作失败
                    acknowledged = false;
                    // 不抛出异常，避免影响消息处理流程
                }
            }
        }

        @Override
        public boolean isAcknowledged() {
            return acknowledged;
        }

        public String getDeadLetterQueue() {
            return deadLetterQueue;
        }

        public String getDeadLetterReason() {
            return deadLetterReason;
        }
    }

    /**
     * 自动确认实现（用于 autoAck=true 的情况）
     */
    private static class AutoAcknowledgment implements Acknowledgment {
        @Override
        public void acknowledge() {
            // 自动确认模式下，无需操作
        }

        @Override
        public void nack(boolean requeue) {
            // 自动确认模式下，nack 等同于 ack
            acknowledge();
        }

        @Override
        public boolean isAcknowledged() {
            return true; // 自动确认模式下，始终认为已确认
        }
    }

    @Override
    public void unsubscribe(String destination) {
        // 移除所有以 destination 开头的容器
        List<SimpleMessageListenerContainer> toStop = new ArrayList<>();
        List<String> keysToRemove = new ArrayList<>();
        containers.entrySet().removeIf(e -> {
            String key = e.getKey();
            if (key.startsWith(destination + "|")) {
                toStop.add(e.getValue());
                keysToRemove.add(key);
                return true;
            }
            return false;
        });
        // 清理 autoAckMap
        keysToRemove.forEach(autoAckMap::remove);
        // 停止所有容器并等待完成
        toStop.forEach(container -> {
            try {
                container.stop();
                // 等待容器完全停止
                int maxWait = 5000; // 最多等待 5 秒
                int waited = 0;
                while (container.isRunning() && waited < maxWait) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // 恢复中断状态
                        log.warn("Interrupted while waiting for container to stop for destination: {}", destination);
                        break; // 中断时退出循环
                    }
                    waited += 100;
                }
                if (container.isRunning()) {
                    log.warn("Container did not stop within timeout for destination: {}", destination);
                }
            } catch (Exception e) {
                log.warn("Error stopping container for destination: {}", destination, e);
            }
        });
    }

    @Override
    public String getType() {
        return "rabbitmq";
    }

    @Override
    public boolean isConnected() {
        return true; // rely on container/template; assume available
    }

    @Override
    public void close() {
        List<SimpleMessageListenerContainer> toStop = new ArrayList<>(containers.values());
        containers.clear();
        autoAckMap.clear(); // 清理 autoAckMap
        // 停止所有容器并等待完成
        toStop.forEach(container -> {
            try {
                container.stop();
                // 等待容器完全停止
                int maxWait = 5000; // 最多等待 5 秒
                int waited = 0;
                while (container.isRunning() && waited < maxWait) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // 恢复中断状态
                        log.warn("Interrupted while waiting for container to stop during close");
                        break; // 中断时退出循环
                    }
                    waited += 100;
                }
                if (container.isRunning()) {
                    log.warn("Container did not stop within timeout during close");
                }
            } catch (Exception e) {
                log.warn("Error stopping container during close", e);
            }
        });
    }

    private static byte[] toBytes(Object payload) {
        return switch (payload) {
            case null -> new byte[0];
            case byte[] bytes -> bytes;
            case String str -> str.getBytes(StandardCharsets.UTF_8);
            default -> Objects.toString(payload).getBytes(StandardCharsets.UTF_8);
        };
    }
}
