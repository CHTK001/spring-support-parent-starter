package com.chua.starter.queue.rocketmq;

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
    public void subscribe(String destination, MessageHandler handler) {
        subscribe(destination, props.getRocketmq().getConsumerGroup(), handler);
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler) {
        String key = destination + "|" + (group == null ? "" : group);
        consumers.computeIfAbsent(key, k -> {
            try {
                DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group == null || group.isEmpty() ? props.getRocketmq().getConsumerGroup() : group);
                consumer.setNamesrvAddr(props.getRocketmq().getNameServer());
                consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
                consumer.subscribe(destination, "*");
                consumer.setMessageListener(new MessageListenerConcurrently() {
                    @Override
                    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                        for (MessageExt ext : msgs) {
                            Message m = Message.builder()
                                    .destination(ext.getTopic())
                                    .payload(ext.getBody())
                                    .timestamp(ext.getBornTimestamp())
                                    .type(getType())
                                    .build();
                            handler.handle(m);
                        }
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
                });
                consumer.start();
                return consumer;
            } catch (Exception e) {
                throw new RuntimeException("RocketMQ subscribe failed for topic: " + destination, e);
            }
        });
    }

    @Override
    public void unsubscribe(String destination) {
        consumers.entrySet().removeIf(e -> {
            String key = e.getKey();
            if (key.startsWith(destination + "|")) {
                try { e.getValue().shutdown(); } catch (Exception ignored) {}
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
        consumers.values().forEach(c -> { try { c.shutdown(); } catch (Exception ignored) {} });
        consumers.clear();
    }
}
