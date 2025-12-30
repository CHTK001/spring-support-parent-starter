# Spring Support Queue RocketMQ Starter

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 📖 模块简介

Spring Support Queue RocketMQ Starter 是基于 Apache RocketMQ 的消息队列实现模块，提供高性能、高可靠的分布式消息系统支持，特别适合金融、电商等对消息可靠性要求较高的场景。

### ✨ 主要特性

- 🔄 **顺序消息** - 支持全局顺序和分区顺序消息
- ⏰ **延迟消息** - 支持18个级别的延迟消息
- 🔁 **事务消息** - 支持分布式事务消息
- 📊 **消息追踪** - 完整的消息轨迹追踪
- 💾 **高可靠性** - 消息零丢失保证

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-queue-rocketmq-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>
```

## ⚙️ 配置说明

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `plugin.rocketmq.enable` | Boolean | false | 是否启用RocketMQ |
| `rocketmq.name-server` | String | localhost:9876 | NameServer地址 |
| `rocketmq.producer.group` | String | default-producer-group | 生产者组 |
| `rocketmq.consumer.group` | String | default-consumer-group | 消费者组 |

### 配置示例

```yaml
plugin:
  rocketmq:
    enable: true

rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: order-producer-group
    send-message-timeout: 3000
    retry-times-when-send-failed: 2
  consumer:
    group: order-consumer-group
```

## 📝 使用示例

### 发送普通消息

```java
@Service
public class OrderProducer {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    public void sendOrder(Order order) {
        rocketMQTemplate.convertAndSend("order-topic", order);
    }
}
```

### 发送事务消息

```java
@Service
public class TransactionalProducer {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    public void sendTransactionalMessage(Order order) {
        rocketMQTemplate.sendMessageInTransaction(
            "order-topic", 
            MessageBuilder.withPayload(order).build(),
            order
        );
    }
}
```

### 消费消息

```java
@Service
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "order-consumer-group"
)
public class OrderConsumer implements RocketMQListener<Order> {

    @Override
    public void onMessage(Order order) {
        log.info("收到订单: {}", order);
        processOrder(order);
    }
}
```

## 🔗 相关链接

- [返回消息队列抽象模块](../spring-support-queue-starter/README.md)
- [返回主文档](../README.md)
- [RocketMQ官方文档](https://rocketmq.apache.org/)

## 📄 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。
