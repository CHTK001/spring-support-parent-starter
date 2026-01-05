package com.chua.starter.queue.kafka;

import com.chua.starter.queue.Acknowledgment;
import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageHandler;
import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.SendResult;
import com.chua.starter.queue.properties.QueueProperties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
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
    public void subscribe(String destination, MessageHandler handler, boolean autoAck) {
        subscribe(destination, props.getKafka().getGroupId(), handler, autoAck);
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler, boolean autoAck) {
        subscribe(destination, group, handler, autoAck, 1);
    }

    @Override
    public void subscribe(String destination, MessageHandler handler, boolean autoAck, int concurrency) {
        subscribe(destination, props.getKafka().getGroupId(), handler, autoAck, concurrency);
    }

    @Override
    public void subscribe(String destination, String group, MessageHandler handler, boolean autoAck, int concurrency) {
        // Kafka 的并发消费应该使用同一个 group，让 Kafka 自动分配分区
        // 如果 concurrency > 1，创建多个消费者实例，但使用同一个 group
        String actualGroup = group == null || group.isEmpty() ? props.getKafka().getGroupId() : group;
        
        for (int i = 0; i < Math.max(1, concurrency); i++) {
            String key = destination + "|" + actualGroup + "|" + autoAck + "|" + i;
            consumers.computeIfAbsent(key, k -> {
                Properties cfg = new Properties();
                cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getKafka().getBootstrapServers());
                cfg.put(ConsumerConfig.GROUP_ID_CONFIG, actualGroup); // 使用同一个 group
                cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
                cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, props.getKafka().getAutoOffsetReset());
                // 如果手动确认，禁用自动提交
                cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, String.valueOf(autoAck && props.getKafka().isEnableAutoCommit()));

                KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(cfg);
                consumer.subscribe(Collections.singletonList(destination));

                ConsumerRunner runner = new ConsumerRunner(consumer, handler, destination, autoAck);
                runner.start();
                return runner;
            });
        }
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
        try { 
            producer.flush(); 
        } catch (Exception e) {
            log.warn("Failed to flush Kafka producer: {}", e.getMessage());
        }
        try { 
            producer.close(); 
        } catch (Exception e) {
            log.warn("Failed to close Kafka producer: {}", e.getMessage());
        }
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
        private final boolean autoAck;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-consumer-" + UUID.randomUUID());
            t.setDaemon(true);
            return t;
        });
        private volatile boolean running = true;

        ConsumerRunner(KafkaConsumer<String, byte[]> consumer, MessageHandler handler, String destination, boolean autoAck) {
            this.consumer = consumer;
            this.handler = handler;
            this.destination = destination;
            this.autoAck = autoAck;
        }

        void start() {
            executor.submit(() -> {
                try {
                    while (running) {
                        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));
                        if (records.isEmpty()) {
                            continue;
                        }

                        if (autoAck) {
                            // 自动确认模式
                            records.forEach(record -> {
                                Message msg = Message.builder()
                                        .destination(record.topic())
                                        .payload(record.value())
                                        .timestamp(record.timestamp())
                                        .type("kafka")
                                        .originalMessage(record)
                                        .build();
                                handler.handle(msg, new AutoAcknowledgment());
                            });
                        } else {
                            // 手动确认模式：跟踪每个消息的确认状态，只提交已确认的消息
                            List<ConsumerRecord<String, byte[]>> recordList = new ArrayList<>();
                            records.forEach(recordList::add);
                            
                            // 使用 Map 跟踪每个消息的确认状态
                            Map<ConsumerRecord<String, byte[]>, KafkaAcknowledgment> ackMap = new HashMap<>();
                            
                            for (ConsumerRecord<String, byte[]> record : recordList) {
                                KafkaAcknowledgment ack = new KafkaAcknowledgment(consumer, record);
                                ackMap.put(record, ack);
                                
                                Message msg = Message.builder()
                                        .destination(record.topic())
                                        .payload(record.value())
                                        .timestamp(record.timestamp())
                                        .type("kafka")
                                        .originalMessage(record)
                                        .acknowledgment(ack)
                                        .build();
                                handler.handle(msg, ack);
                            }

                            // 只提交已确认的消息的 offset
                            // 对于未确认的消息，不提交 offset，下次会重新消费
                            List<ConsumerRecord<String, byte[]>> acknowledgedRecords = new ArrayList<>();
                            for (Map.Entry<ConsumerRecord<String, byte[]>, KafkaAcknowledgment> entry : ackMap.entrySet()) {
                                if (entry.getValue().isAcknowledged()) {
                                    acknowledgedRecords.add(entry.getKey());
                                }
                            }
                            
                            if (!acknowledgedRecords.isEmpty()) {
                                try {
                                    // 按分区分组，只提交已确认消息的 offset
                                    // 对于每个分区，提交到最后一个已确认消息的 offset + 1
                                    Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = new HashMap<>();
                                    
                                    for (ConsumerRecord<String, byte[]> record : acknowledgedRecords) {
                                        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
                                        // 提交到当前消息的 offset + 1（表示已消费到此位置）
                                        long nextOffset = record.offset() + 1;
                                        OffsetAndMetadata offsetMetadata = new OffsetAndMetadata(nextOffset);
                                        
                                        // 如果同一个分区有多个已确认的消息，只保留最大的 offset
                                        OffsetAndMetadata existing = offsetsToCommit.get(partition);
                                        if (existing == null || existing.offset() < nextOffset) {
                                            offsetsToCommit.put(partition, offsetMetadata);
                                        }
                                    }
                                    
                                    // 提交所有分区的 offset
                                    if (!offsetsToCommit.isEmpty()) {
                                        consumer.commitSync(offsetsToCommit);
                                        log.debug("Committed offsets for {} partitions", offsetsToCommit.size());
                                    }
                                } catch (Exception e) {
                                    // 提交失败，记录日志但不中断处理
                                    log.error("Failed to commit offset: {}", e.getMessage(), e);
                                }
                            }
                            // 如果所有消息都未确认，不提交 offset，下次会重新消费
                        }
                    }
                } catch (Exception e) {
                    log.error("Kafka consumer error for destination: {}", destination, e);
                } finally {
                    try { consumer.close(); } catch (Exception e) {
                        log.warn("Failed to close Kafka consumer: {}", e.getMessage());
                    }
                }
            });
        }

        void shutdown() {
            running = false;
            try { 
                consumer.wakeup(); 
            } catch (Exception e) {
                log.warn("Failed to wakeup Kafka consumer: {}", e.getMessage());
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                        log.warn("Kafka consumer executor did not terminate within timeout");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for Kafka consumer executor to terminate");
            }
        }
    }

    /**
     * Kafka 手动确认实现
     */
    private static class KafkaAcknowledgment implements Acknowledgment {
        private final KafkaConsumer<String, byte[]> consumer;
        private final ConsumerRecord<String, byte[]> record;
        private volatile boolean acknowledged = false;
        private String deadLetterQueue;
        private String deadLetterReason;

        KafkaAcknowledgment(KafkaConsumer<String, byte[]> consumer, ConsumerRecord<String, byte[]> record) {
            this.consumer = consumer;
            this.record = record;
        }

        @Override
        public void acknowledge() {
            if (!acknowledged) {
                // Kafka 的确认是通过提交 offset 实现的
                // 这里标记为已确认，实际提交在 ConsumerRunner 中统一处理
                acknowledged = true;
            }
        }

        @Override
        public void nack(boolean requeue) {
            if (!acknowledged) {
                // Kafka 的 nack 语义说明：
                // 1. Kafka 不支持像 RabbitMQ 那样的 requeue 机制
                // 2. Kafka 的确认机制是通过提交 offset 实现的
                // 3. 如果 requeue=true：不提交 offset，消息会在下次 poll 时重新消费（相当于重新入队）
                // 4. 如果 requeue=false：也不提交 offset，消息不会立即丢弃，下次重启或重新消费时可能还会消费
                //    如果需要真正丢弃消息，需要提交 offset（但这与 nack 语义矛盾）
                //    建议：如果 requeue=false，应该调用 nackToDeadLetter() 发送到死信队列
                // 注意：Kafka 的 nack 语义与 RabbitMQ 不同，这里保持不提交 offset
                acknowledged = false; // 不标记为已确认，这样 offset 不会被提交
            }
        }

        @Override
        public void nackToDeadLetter(String deadLetterQueue, String reason) {
            if (!acknowledged) {
                // 记录死信队列信息，但实际发送到死信队列需要外部支持（如 DeadLetterTemplate）
                this.deadLetterQueue = deadLetterQueue;
                this.deadLetterReason = reason;
                // 标记为已确认，这样 offset 会被提交，消息不会重新消费
                // 但实际应该由外部组件负责发送到死信队列
                acknowledged = true;
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
            // 自动确认模式下，无需操作（Kafka 会自动提交）
        }

        @Override
        public void nack(boolean requeue) {
            // 自动确认模式下，nack 等同于 ack
            acknowledge();
        }
    }
}
