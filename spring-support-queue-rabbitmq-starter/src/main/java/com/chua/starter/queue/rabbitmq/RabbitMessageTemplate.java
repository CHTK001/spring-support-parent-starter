package com.chua.starter.queue.rabbitmq;

import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageHandler;
import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.SendResult;
import com.chua.starter.queue.properties.QueueProperties;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessageBuilder;

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
    public void subscribe(String destination, MessageHandler handler) {
        containers.computeIfAbsent(destination, key -> {
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
            container.setQueueNames(destination);
            container.setMessageListener((org.springframework.amqp.core.Message msg) -> {
                Message m = Message.builder()
                        .destination(destination)
                        .payload(msg.getBody())
                        .timestamp(System.currentTimeMillis())
                        .type(getType())
                        .build();
                handler.handle(m);
            });
            container.start();
            return container;
        });
    }

    @Override
    public void unsubscribe(String destination) {
        SimpleMessageListenerContainer container = containers.remove(destination);
        if (container != null) {
            container.stop();
        }
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
