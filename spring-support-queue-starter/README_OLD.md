# Spring Support Queue Starter

[![Maven Central](https://img.shields.io/maven-central/v/com.chua/spring-support-queue-starter.svg)](https://search.maven.org/artifact/com.chua/spring-support-queue-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 📖 模块简介

Spring Support Queue Starter 是一个消息队列抽象模块，提供统一的消息队列接口，支持多种消息队列实现（RabbitMQ、Kafka、RocketMQ、MQTT）的无缝切换。

### ✨ 主要特性

- 🔌 **统一接口** - 提供统一的消息发送和接收接口
- 🔄 **多实现支持** - 支持RabbitMQ、Kafka、RocketMQ、MQTT
- 💀 **死信队列** - 支持死信队列配置
- 📊 **消息追踪** - 消息发送和消费日志追踪
- ⚙️ **灵活配置** - 丰富的配置选项

## 🚀 快速开始

### Maven 依赖

```xml
<!-- 队列抽象模块 -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-queue-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>

<!-- 根据需要选择具体实现 -->
<!-- RabbitMQ实现 -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-queue-rabbitmq-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>

<!-- 或者 Kafka实现 -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-queue-kafka-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>

<!-- 或者 RocketMQ实现 -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-queue-rocketmq-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>

<!-- 或者 MQTT实现 -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-queue-mqtt-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>
```

## ⚙️ 配置说明

### 通用队列配置

**配置前缀**: `plugin.queue`

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `enable` | Boolean | false | 是否启用消息队列 |
| `type` | String | rabbitmq | 队列类型：rabbitmq, kafka, rocketmq, mqtt |

### 死信队列配置

**配置前缀**: `plugin.queue.dead-letter`

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `enabled` | Boolean | false | 是否启用死信队列 |
| `exchange` | String | - | 死信交换机 |
| `routing-key` | String | - | 死信路由键 |
| `queue` | String | - | 死信队列 |
| `ttl` | Long | 86400000 | 消息存活时间（毫秒） |

### 配置示例

```yaml
plugin:
  queue:
    enable: true
    type: rabbitmq  # 选择消息队列类型
    
    # 死信队列配置
    dead-letter:
      enabled: true
      exchange: dlx.exchange
      routing-key: dlx.routing.key
      queue: dlx.queue
      ttl: 86400000  # 24小时
```

## 📝 使用示例

### 发送消息

```java
@Service
public class OrderService {

    @Autowired
    private QueueTemplate queueTemplate;
    
    public void createOrder(Order order) {
        // 保存订单
        orderRepository.save(order);
        
        // 发送订单创建消息
        queueTemplate.send("order.created", order);
    }
    
    public void sendDelayedMessage(Order order) {
        // 发送延迟消息（30秒后处理）
        queueTemplate.send("order.timeout.check", order, 30000);
    }
}
```

### 接收消息

```java
@Service
public class OrderMessageListener {

    @QueueListener(queue = "order.created")
    public void handleOrderCreated(Order order) {
        log.info("收到订单创建消息: {}", order);
        // 处理订单创建逻辑
        notificationService.sendOrderConfirmation(order);
    }
    
    @QueueListener(queue = "order.timeout.check")
    public void handleOrderTimeout(Order order) {
        log.info("检查订单超时: {}", order);
        // 检查订单支付状态
        if (!order.isPaid()) {
            orderService.cancelOrder(order.getId());
        }
    }
}
```

## 🔗 具体实现模块

### RabbitMQ 实现
- [spring-support-queue-rabbitmq-starter](../spring-support-queue-rabbitmq-starter/README.md)
- 支持Exchange、Queue、Binding配置
- 支持死信队列、延迟消息
- 支持消息确认机制

### Kafka 实现
- [spring-support-queue-kafka-starter](../spring-support-queue-kafka-starter/README.md)
- 支持分区、副本配置
- 支持消费组管理
- 支持事务消息

### RocketMQ 实现
- [spring-support-queue-rocketmq-starter](../spring-support-queue-rocketmq-starter/README.md)
- 支持顺序消息
- 支持延迟消息
- 支持事务消息

### MQTT 实现
- [spring-support-queue-mqtt-starter](../spring-support-queue-mqtt-starter/README.md)
- 支持QoS配置
- 支持主题订阅
- 适用于物联网场景

## 🔗 相关链接

- [返回主文档](../README.md)
- [配置示例文件](../application-example.yml)

## 📄 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。
