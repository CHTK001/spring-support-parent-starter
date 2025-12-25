package com.chua.starter.queue.kafka;

import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageHandler;
import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.SendResult;
import com.chua.starter.queue.properties.QueueProperties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class KafkaMessageTemplate implements MessageTemplate {

    private final KafkaProducer<String, byte[]> producer;
    private final QueueProperties props;
    private final Map<String, ConsumerRunner> consumers = new ConcurrentHashMap<>();

    public KafkaMessageTemplate(KafkaProducer<String, byte[]> producer, QueueProperties props) {
        this.producer = producer;
        this.props = props;
    }

    @Override
    public SendResult send(String destination, Object payload) {
        try {
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(destination, toBytes(payload));
            producer.send(record);
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
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(destination, toBytes(payload));
            if (headers != null) {
                for (Map.Entry<String, Object> e : headers.entrySet()) {
                    byte[] hv = Objects.toString(e.getValue(), "").getBytes(StandardCharsets.UTF_8);
                    record.headers().add(new RecordHeader(e.getKey(), hv));
                }
            }
            producer.send(record);
            return SendResult.success(null, destination);
        } catch (Exception e) {
            return SendResult.failure(destination, e);
        }
    }

    @Override
    public void subscribe(String destination, MessageHandler handler) {
        subscribe(destination, props.getKafka().getGroupId(), handler);
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler) {
        String key = destination + "|" + (group == null ? "" : group);
        consumers.computeIfAbsent(key, k -> {
            Properties cfg = new Properties();
            cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getKafka().getBootstrapServers());
            cfg.put(ConsumerConfig.GROUP_ID_CONFIG, group == null || group.isEmpty() ? props.getKafka().getGroupId() : group);
            cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
            cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, props.getKafka().getAutoOffsetReset());
            cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, String.valueOf(props.getKafka().isEnableAutoCommit()));

            KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(cfg);
            consumer.subscribe(Collections.singletonList(destination));

            ConsumerRunner runner = new ConsumerRunner(consumer, handler, destination);
            runner.start();
            return runner;
        });
    }

    @Override
    public void unsubscribe(String destination) {
        // Unsubscribe all groups for this destination
        consumers.entrySet().removeIf(e -> {
            String key = e.getKey();
            if (key.startsWith(destination + "|")) {
                e.getValue().shutdown();
                return true;
            }
            return false;
        });
    }

    @Override
    public String getType() {
        return "kafka";
    }

    @Override
    public boolean isConnected() {
        return producer != null; // KafkaProducer doesn't expose connection state
    }

    @Override
    public void close() {
        try { producer.flush(); } catch (Exception ignored) {}
        try { producer.close(); } catch (Exception ignored) {}
        consumers.values().forEach(ConsumerRunner::shutdown);
        consumers.clear();
    }

    private static byte[] toBytes(Object payload) {
        if (payload == null) return new byte[0];
        if (payload instanceof byte[]) return (byte[]) payload;
        if (payload instanceof String) return ((String) payload).getBytes(StandardCharsets.UTF_8);
        return Objects.toString(payload).getBytes(StandardCharsets.UTF_8);
    }

    private static class ConsumerRunner {
        private final KafkaConsumer<String, byte[]> consumer;
        private final MessageHandler handler;
        private final String destination;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-consumer-" + UUID.randomUUID());
            t.setDaemon(true);
            return t;
        });
        private volatile boolean running = true;

        ConsumerRunner(KafkaConsumer<String, byte[]> consumer, MessageHandler handler, String destination) {
            this.consumer = consumer;
            this.handler = handler;
            this.destination = destination;
        }

        void start() {
            executor.submit(() -> {
                try {
                    while (running) {
                        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));
                        records.forEach(record -> {
                            Message msg = Message.builder()
                                    .destination(record.topic())
                                    .payload(record.value())
                                    .timestamp(record.timestamp())
                                    .type("kafka")
                                    .build();
                            handler.handle(msg);
                        });
                    }
                } catch (Exception ignored) {
                } finally {
                    try { consumer.close(); } catch (Exception ignored2) {}
                }
            });
        }

        void shutdown() {
            running = false;
            try { consumer.wakeup(); } catch (Exception ignored) {}
            executor.shutdownNow();
        }
    }
}
