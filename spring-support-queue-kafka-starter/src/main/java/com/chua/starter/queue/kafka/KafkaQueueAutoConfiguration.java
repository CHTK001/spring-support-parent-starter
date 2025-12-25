package com.chua.starter.queue.kafka;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.properties.QueueProperties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(QueueProperties.class)
public class KafkaQueueAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaProducer<String, byte[]> kafkaProducer(QueueProperties props) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getKafka().getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaProducer<>(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageTemplate kafkaMessageTemplate(KafkaProducer<String, byte[]> producer, QueueProperties props) {
        return new KafkaMessageTemplate(producer, props);
    }
}
