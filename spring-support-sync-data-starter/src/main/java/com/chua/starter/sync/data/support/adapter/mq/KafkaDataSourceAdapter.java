package com.chua.starter.sync.data.support.adapter.mq;

import com.chua.starter.sync.data.support.adapter.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

/**
 * Kafka数据源适配器
 */
@Slf4j
public class KafkaDataSourceAdapter implements DataSourceAdapter {
    
    private KafkaConsumer<String, String> consumer;
    private KafkaProducer<String, String> producer;
    private DataSourceConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void connect(DataSourceConfig config) throws DataSourceException {
        this.config = config;
        try {
            Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getUrl());
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, config.getGroupId() != null ? config.getGroupId() : "sync-consumer");
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            this.consumer = new KafkaConsumer<>(consumerProps);
            
            Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getUrl());
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
            this.producer = new KafkaProducer<>(producerProps);
            
            log.info("Kafka连接成功: {}", config.getUrl());
        } catch (Exception e) {
            throw new DataSourceException("Kafka连接失败", e);
        }
    }
    
    @Override
    public Stream<Map<String, Object>> read(ReadConfig readConfig) {
        String topic = readConfig.getTopic();
        consumer.subscribe(Collections.singletonList(topic));
        
        return Stream.generate(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            if (records.isEmpty()) {
                return null;
            }
            
            ConsumerRecord<String, String> record = records.iterator().next();
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(record.value(), Map.class);
                return result;
            } catch (Exception e) {
                log.error("解析Kafka消息失败", e);
                return null;
            }
        }).takeWhile(Objects::nonNull);
    }
    
    @Override
    public void write(List<Map<String, Object>> records, WriteConfig writeConfig) {
        if (records == null || records.isEmpty()) {
            return;
        }
        
        String topic = writeConfig.getTopic();
        
        for (Map<String, Object> record : records) {
            try {
                String json = objectMapper.writeValueAsString(record);
                ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, json);
                producer.send(producerRecord);
            } catch (Exception e) {
                log.error("发送Kafka消息失败", e);
                throw new DataSourceException("发送Kafka消息失败", e);
            }
        }
        
        producer.flush();
        log.debug("批量写入{}条消息到主题: {}", records.size(), topic);
    }
    
    @Override
    public boolean testConnection() {
        try {
            producer.partitionsFor("test-topic");
            return true;
        } catch (Exception e) {
            log.error("测试Kafka连接失败", e);
            return false;
        }
    }
    
    @Override
    public void close() {
        if (consumer != null) {
            consumer.close();
        }
        if (producer != null) {
            producer.close();
        }
        log.info("Kafka连接已关闭");
    }
    
    @Override
    public DataSourceMetadata getMetadata() {
        DataSourceMetadata metadata = new DataSourceMetadata();
        metadata.setDatabaseType("Kafka");
        return metadata;
    }
}
