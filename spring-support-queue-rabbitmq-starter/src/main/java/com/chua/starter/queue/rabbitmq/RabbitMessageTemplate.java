package com.chua.starter.queue.rabbitmq;

import com.chua.starter.queue.Acknowledgment;
import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageHandler;
import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.SendResult;
import com.chua.starter.queue.properties.QueueProperties;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RabbitMessageTemplate implements MessageTemplate {

    private final AmqpTemplate amqpTemplate;
    private final ConnectionFactory connectionFactory;
    private final QueueProperties props;
    private final Map<String, SimpleMessageListenerContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> autoAckMap = new ConcurrentHashMap<>();

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
        return CompletableFuture.supplyAsync(() -> send(destination, payload));
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
                    Message m = Message.builder()
                            .destination(destination)
                            .payload(msg.getBody())
                            .timestamp(System.currentTimeMillis())
                            .type(getType())
                            .originalMessage(msg)
                            .build();
                    handler.handle(m, new AutoAcknowledgment());
                });
            } else {
                // 手动确认模式
                container.setMessageListener((ChannelAwareMessageListener) (msg, channel) -> {
                    long deliveryTag = msg.getMessageProperties().getDeliveryTag();
                    RabbitAcknowledgment ack = new RabbitAcknowledgment(channel, deliveryTag);
                    
                    Message m = Message.builder()
                            .destination(destination)
                            .payload(msg.getBody())
                            .timestamp(System.currentTimeMillis())
                            .type(getType())
                            .originalMessage(msg)
                            .acknowledgment(ack)
                            .build();
                    handler.handle(m, ack);
                });
            }
            
            container.start();
            return container;
        });
    }

    /**
     * RabbitMQ 手动确认实现
     */
    private static class RabbitAcknowledgment implements Acknowledgment {
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
                    throw new RuntimeException("Failed to acknowledge message", e);
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
                    throw new RuntimeException("Failed to nack message", e);
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
                    throw new RuntimeException("Failed to nack message to dead letter queue", e);
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
        containers.entrySet().removeIf(e -> {
            String key = e.getKey();
            if (key.startsWith(destination + "|")) {
                e.getValue().stop();
                return true;
            }
            return false;
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
        containers.values().forEach(SimpleMessageListenerContainer::stop);
        containers.clear();
    }

    private static byte[] toBytes(Object payload) {
        if (payload == null) return new byte[0];
        if (payload instanceof byte[]) return (byte[]) payload;
        if (payload instanceof String) return ((String) payload).getBytes(StandardCharsets.UTF_8);
        return Objects.toString(payload).getBytes(StandardCharsets.UTF_8);
    }
}
