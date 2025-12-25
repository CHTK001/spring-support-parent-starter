package com.chua.starter.queue.mqtt;

import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageHandler;
import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.SendResult;
import com.chua.starter.queue.properties.QueueProperties;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MqttMessageTemplate implements MessageTemplate {

    private final MqttClient client;
    private final QueueProperties properties;
    private final Map<String, Integer> subscriptions = new ConcurrentHashMap<>();

    public MqttMessageTemplate(MqttClient client, QueueProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public SendResult send(String destination, Object payload) {
        try {
            byte[] bytes = toBytes(payload);
            MqttMessage mqttMessage = new MqttMessage(bytes);
            mqttMessage.setQos(Math.max(0, Math.min(2, properties.getMqtt().getQos())));
            client.publish(destination, mqttMessage);
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
        // MQTT v3 不支持自定义headers，这里忽略headers
        return send(destination, payload);
    }

    @Override
    public void subscribe(String destination, MessageHandler handler) {
        try {
            IMqttMessageListener listener = (topic, message) -> {
                Message msg = Message.builder()
                        .destination(topic)
                        .payload(message.getPayload())
                        .timestamp(System.currentTimeMillis())
                        .type(getType())
                        .build();
                handler.handle(msg);
            };
            int qos = Math.max(0, Math.min(2, properties.getMqtt().getQos()));
            client.subscribe(destination, qos, listener);
            subscriptions.put(destination, qos);
        } catch (Exception e) {
            throw new RuntimeException("MQTT subscribe failed for topic: " + destination, e);
        }
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler) {
        // MQTT没有消费组的概念，直接调用普通订阅
        subscribe(destination, handler);
    }

    @Override
    public void unsubscribe(String destination) {
        try {
            if (subscriptions.containsKey(destination)) {
                client.unsubscribe(destination);
                subscriptions.remove(destination);
            }
        } catch (Exception e) {
            throw new RuntimeException("MQTT unsubscribe failed for topic: " + destination, e);
        }
    }

    @Override
    public String getType() {
        return "mqtt";
    }

    @Override
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public void close() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
            if (client != null) {
                client.close();
            }
        } catch (Exception ignored) {
        }
    }

    private byte[] toBytes(Object payload) {
        if (payload == null) return new byte[0];
        if (payload instanceof byte[]) return (byte[]) payload;
        if (payload instanceof String) return ((String) payload).getBytes(StandardCharsets.UTF_8);
        return Objects.toString(payload).getBytes(StandardCharsets.UTF_8);
    }
}
