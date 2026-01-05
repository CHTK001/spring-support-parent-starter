package com.chua.starter.queue.rocketmq;

import com.chua.starter.queue.Acknowledgment;
import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageHandler;
import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.SendResult;
import com.chua.starter.queue.properties.QueueProperties;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RocketMessageTemplate implements MessageTemplate {

    private final RocketMQTemplate rocketMQTemplate;
    private final QueueProperties props;
    private final Map<String, DefaultMQPushConsumer> consumers = new ConcurrentHashMap<>();

    public RocketMessageTemplate(RocketMQTemplate rocketMQTemplate, QueueProperties props) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.props = props;
    }

    @Override
    public SendResult send(String destination, Object payload) {
        try {
            rocketMQTemplate.convertAndSend(destination, payload);
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
            org.springframework.messaging.Message<Object> msg = MessageBuilder.withPayload(payload)
                    .copyHeaders(headers == null ? Map.of() : headers)
                    .build();
            rocketMQTemplate.send(destination, msg);
            return SendResult.success(null, destination);
        } catch (Exception e) {
            return SendResult.failure(destination, e);
        }
    }

    @Override
    public void subscribe(String destination, MessageHandler handler, boolean autoAck) {
        subscribe(destination, props.getRocketmq().getConsumerGroup(), handler, autoAck);
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler, boolean autoAck) {
        subscribe(destination, group, handler, autoAck, 1);
    }

    @Override
    public void subscribe(String destination, MessageHandler handler, boolean autoAck, int concurrency) {
        subscribe(destination, props.getRocketmq().getConsumerGroup(), handler, autoAck, concurrency);
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler, boolean autoAck, int concurrency) {
        String actualGroup = group == null || group.isEmpty() ? props.getRocketmq().getConsumerGroup() : group;
        String key = destination + "|" + actualGroup + "|" + autoAck;
        
        consumers.computeIfAbsent(key, k -> {
            try {
                DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(actualGroup);
                consumer.setNamesrvAddr(props.getRocketmq().getNameServer());
                consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
                consumer.subscribe(destination, "*");
                
                // 设置并发消费线程数
                if (concurrency > 1) {
                    consumer.setConsumeThreadMin(concurrency);
                    consumer.setConsumeThreadMax(concurrency);
                }
                
                final boolean finalAutoAck = autoAck;
                consumer.setMessageListener(new MessageListenerConcurrently() {
                    @Override
                    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                        // 如果自动确认，直接返回成功
                        if (finalAutoAck) {
                            for (MessageExt ext : msgs) {
                                Message m = Message.builder()
                                        .destination(ext.getTopic())
                                        .payload(ext.getBody())
                                        .timestamp(ext.getBornTimestamp())
                                        .type(getType())
                                        .originalMessage(ext)
                                        .build();
                                try {
                                    handler.handle(m, new AutoAcknowledgment());
                                } catch (Exception e) {
                                    // 自动确认模式下，即使异常也返回成功（避免消息堆积）
                                    // 如果需要重试，应该使用手动确认模式
                                }
                            }
                            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                        }
                        
                        // 手动确认模式：跟踪每个消息的确认状态
                        Map<MessageExt, RocketAcknowledgment> ackMap = new java.util.HashMap<>();
                        boolean allAcknowledged = true;
                        
                        for (MessageExt ext : msgs) {
                            RocketAcknowledgment ack = new RocketAcknowledgment(context, ext);
                            ackMap.put(ext, ack);
                            
                            Message m = Message.builder()
                                    .destination(ext.getTopic())
                                    .payload(ext.getBody())
                                    .timestamp(ext.getBornTimestamp())
                                    .type(getType())
                                    .originalMessage(ext)
                                    .acknowledgment(ack)
                                    .build();
                            
                            try {
                                handler.handle(m, ack);
                                
                                // 检查是否已确认
                                if (!ack.isAcknowledged()) {
                                    allAcknowledged = false;
                                }
                            } catch (Exception e) {
                                // 处理异常，标记为未确认
                                allAcknowledged = false;
                            }
                        }
                        
                        // 如果所有消息都已确认，返回成功；否则返回重试
                        // 注意：RocketMQ 的批量消息确认是原子性的，要么全部成功，要么全部重试
                        // 如果需要更细粒度的控制，应该使用 MessageListenerOrderly 逐个处理
                        return allAcknowledged ? ConsumeConcurrentlyStatus.CONSUME_SUCCESS : ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                });
                consumer.start();
                return consumer;
            } catch (Exception e) {
                throw new RuntimeException("RocketMQ subscribe failed for topic: " + destination, e);
            }
        });
    }

    /**
     * RocketMQ 的 ACK 实现
     */
    private static class RocketAcknowledgment implements Acknowledgment {
        private final ConsumeConcurrentlyContext context;
        private final MessageExt messageExt;
        private volatile boolean acknowledged = false;
        private String deadLetterQueue;
        private String deadLetterReason;

        RocketAcknowledgment(ConsumeConcurrentlyContext context, MessageExt messageExt) {
            this.context = context;
            this.messageExt = messageExt;
        }

        @Override
        public void acknowledge() {
            acknowledged = true;
            // RocketMQ 的确认通过返回 CONSUME_SUCCESS 实现
            // 这里标记为已确认，实际确认在 MessageListenerConcurrently 的返回值中处理
        }

        @Override
        public void nack(boolean requeue) {
            if (requeue) {
                // requeue=true 表示重新消费，不确认
                acknowledged = false;
            } else {
                // requeue=false 表示丢弃，确认（但 RocketMQ 不支持丢弃，只能重试）
                // 如果需要发送到死信队列，需要外部支持（如 DeadLetterTemplate）
                acknowledge();
            }
        }

        @Override
        public void nackToDeadLetter(String deadLetterQueue, String reason) {
            if (!acknowledged) {
                // 记录死信队列信息
                this.deadLetterQueue = deadLetterQueue;
                this.deadLetterReason = reason;
                // RocketMQ 不支持直接发送到死信队列，需要外部支持
                // 这里标记为已确认，但实际应该由外部组件负责发送到死信队列
                acknowledge();
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
        public void nackToDeadLetter(String deadLetterQueue, String reason) {
            // 自动确认模式下，无需操作
            acknowledge();
        }

        @Override
        public boolean isAcknowledged() {
            return true; // 自动确认模式下，始终认为已确认
        }
    }

    @Override
    public void unsubscribe(String destination) {
        consumers.entrySet().removeIf(e -> {
            String key = e.getKey();
            if (key.startsWith(destination + "|")) {
                try { 
                    e.getValue().shutdown(); 
                } catch (Exception ex) {
                    // 记录日志但不抛出异常
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public String getType() {
        return "rocketmq";
    }

    @Override
    public boolean isConnected() {
        return rocketMQTemplate != null; // template manages connection internally
    }

    @Override
    public void close() {
        consumers.values().forEach(c -> { 
            try { 
                c.shutdown(); 
            } catch (Exception e) {
                // 记录日志但不抛出异常
            } 
        });
        consumers.clear();
    }
}
