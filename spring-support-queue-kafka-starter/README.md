# Spring Support Queue Kafka Starter

[![Maven Central](https://img.shields.io/maven-central/v/com.chua/spring-support-queue-kafka-starter.svg)](https://search.maven.org/artifact/com.chua/spring-support-queue-kafka-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 📖 模块简介

Spring Support Queue Kafka Starter 是基于 Apache Kafka 的消息队列实现模块，提供高性能、高可用的分布式消息系统支持。

### ✨ 主要特性

- 📊 **高吞吐量** - 支持每秒百万级消息处理
- 🔄 **分区支持** - 支持主题分区和消费者组
- 💾 **持久化** - 消息持久化到磁盘
- 🔁 **事务消息** - 支持事务性消息发送
- 📈 **流处理** - 支持Kafka Streams流式计算
- 🎯 **精确消费** - 支持手动提交offset

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-queue-kafka-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>
```

## ⚙️ 配置说明

### 基础配置

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `plugin.kafka.enable` | Boolean | false | 是否启用Kafka |
| `spring.kafka.bootstrap-servers` | String | localhost:9092 | Kafka服务器地址 |
| `spring.kafka.consumer.group-id` | String | default-group | 消费者组ID |
| `spring.kafka.consumer.auto-offset-reset` | String | earliest | offset重置策略 |
| `spring.kafka.consumer.enable-auto-commit` | Boolean | false | 是否自动提交offset |
| `spring.kafka.producer.acks` | String | all | 确认机制 |
| `spring.kafka.producer.retries` | Integer | 3 | 重试次数 |

### 配置示例

```yaml
plugin:
  kafka:
    enable: true

spring:
  kafka:
    bootstrap-servers: localhost:9092
    
    # 生产者配置
    producer:
      acks: all  # 所有副本确认
      retries: 3
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    
    # 消费者配置
    consumer:
      group-id: my-app-group
      auto-offset-reset: earliest
      enable-auto-commit: false  # 手动提交
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.yourcompany.*"
```

## 📝 使用示例

### 发送消息

```java
@Service
public class KafkaProducer {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    public void sendMessage(String topic, String key, Object message) {
        kafkaTemplate.send(topic, key, message);
    }
    
    public void sendMessageWithCallback(String topic, Object message) {
        ListenableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(topic, message);
            
        future.addCallback(
            result -> log.info("发送成功: {}", result.getRecordMetadata()),
            ex -> log.error("发送失败", ex)
        );
    }
}
```

### 接收消息

```java
@Component
public class KafkaConsumer {

    @KafkaListener(topics = "order-topic", groupId = "order-group")
    public void handleOrder(ConsumerRecord<String, Order> record, Acknowledgment ack) {
        try {
            Order order = record.value();
            log.info("收到订单: partition={}, offset={}, order={}", 
                     record.partition(), record.offset(), order);
            
            // 处理业务逻辑
            processOrder(order);
            
            // 手动提交offset
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("处理失败", e);
            // 不提交offset，下次重新消费
        }
    }
    
    @KafkaListener(topics = "log-topic", containerFactory = "batchFactory")
    public void handleBatch(List<ConsumerRecord<String, String>> records, 
                           Acknowledgment ack) {
        log.info("批量消费 {} 条消息", records.size());
        records.forEach(record -> log.info("消息: {}", record.value()));
        ack.acknowledge();
    }
}
```

## 🔗 相关链接

- [返回消息队列抽象模块](../spring-support-queue-starter/README.md)
- [返回主文档](../README.md)
- [Kafka官方文档](https://kafka.apache.org/documentation/)
- [配置示例文件](../application-example.yml)

## 📄 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。
