# Spring Support Queue RabbitMQ Starter

[![Maven Central](https://img.shields.io/maven-central/v/com.chua/spring-support-queue-rabbitmq-starter.svg)](https://search.maven.org/artifact/com.chua/spring-support-queue-rabbitmq-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 📖 模块简介

Spring Support Queue RabbitMQ Starter 是基于 RabbitMQ 的消息队列实现模块，提供完整的 RabbitMQ 集成支持。

### ✨ 主要特性

- 🐰 **RabbitMQ集成** - 完整的RabbitMQ功能支持
- 📮 **Exchange支持** - Direct、Topic、Fanout、Headers交换机
- 💀 **死信队列** - 支持死信队列和延迟消息
- ✅ **消息确认** - 支持手动/自动确认机制
- 🔄 **消息重试** - 失败消息自动重试
- 📊 **消息追踪** - 完整的消息链路追踪

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-queue-rabbitmq-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>
```

### Gradle 依赖

```groovy
implementation 'com.chua:spring-support-queue-rabbitmq-starter:4.0.0.33-SNAPSHOT'
```

## ⚙️ 配置说明

### 基础配置

**配置前缀**: `plugin.rabbitmq` 和 `spring.rabbitmq`

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `plugin.rabbitmq.enable` | Boolean | false | 是否启用RabbitMQ |
| `spring.rabbitmq.host` | String | localhost | RabbitMQ服务器地址 |
| `spring.rabbitmq.port` | Integer | 5672 | RabbitMQ端口 |
| `spring.rabbitmq.username` | String | guest | 用户名 |
| `spring.rabbitmq.password` | String | guest | 密码 |
| `spring.rabbitmq.virtual-host` | String | / | 虚拟主机 |

### 消费者配置

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `spring.rabbitmq.listener.simple.acknowledge-mode` | String | manual | 确认模式：auto, manual, none |
| `spring.rabbitmq.listener.simple.prefetch` | Integer | 1 | 预取数量 |
| `spring.rabbitmq.listener.simple.retry.enabled` | Boolean | true | 是否启用重试 |
| `spring.rabbitmq.listener.simple.retry.max-attempts` | Integer | 3 | 最大重试次数 |

### 配置示例

```yaml
plugin:
  rabbitmq:
    enable: true

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: admin
    password: admin123
    virtual-host: /dev
    
    # 消费者配置
    listener:
      simple:
        acknowledge-mode: manual  # 手动确认
        prefetch: 10  # 每次预取10条消息
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000  # 初始重试间隔1秒
          multiplier: 2  # 重试间隔倍数
          max-interval: 10000  # 最大重试间隔10秒
    
    # 生产者配置
    publisher-confirm-type: correlated  # 发布确认
    publisher-returns: true  # 发布返回
```

### Properties格式配置

```properties
plugin.rabbitmq.enable=true

spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=admin
spring.rabbitmq.password=admin123
spring.rabbitmq.virtual-host=/dev
spring.rabbitmq.listener.simple.acknowledge-mode=manual
spring.rabbitmq.listener.simple.prefetch=10
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=3
```

## 📝 使用示例

### 定义Exchange、Queue和Binding

```java
@Configuration
public class RabbitMQConfig {

    // 定义交换机
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange("order.exchange", true, false);
    }
    
    // 定义队列
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable("order.queue")
                .withArgument("x-message-ttl", 60000)  // 消息TTL
                .build();
    }
    
    // 绑定关系
    @Bean
    public Binding orderBinding(Queue orderQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderQueue)
                .to(orderExchange)
                .with("order.create");
    }
    
    // 死信队列配置
    @Bean
    public Queue orderDlxQueue() {
        return QueueBuilder.durable("order.dlx.queue").build();
    }
    
    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange("order.dlx.exchange", true, false);
    }
    
    @Bean
    public Binding orderDlxBinding() {
        return BindingBuilder.bind(orderDlxQueue())
                .to(orderDlxExchange())
                .with("order.dlx");
    }
    
    // 带死信队列的业务队列
    @Bean
    public Queue orderQueueWithDlx() {
        return QueueBuilder.durable("order.with.dlx.queue")
                .withArgument("x-dead-letter-exchange", "order.dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "order.dlx")
                .withArgument("x-message-ttl", 60000)
                .build();
    }
}
```

### 发送消息

```java
@Service
public class OrderProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    public void sendOrder(Order order) {
        // 发送消息
        rabbitTemplate.convertAndSend("order.exchange", "order.create", order);
    }
    
    public void sendOrderWithCallback(Order order) {
        // 带回调的发送
        rabbitTemplate.convertAndSend(
            "order.exchange", 
            "order.create", 
            order,
            new CorrelationData(UUID.randomUUID().toString())
        );
    }
    
    public void sendDelayedMessage(Order order, long delayMillis) {
        // 发送延迟消息
        rabbitTemplate.convertAndSend(
            "order.exchange",
            "order.create",
            order,
            message -> {
                message.getMessageProperties().setDelay((int) delayMillis);
                return message;
            }
        );
    }
}
```

### 接收消息

```java
@Component
public class OrderConsumer {

    @RabbitListener(queues = "order.queue")
    public void handleOrder(Order order, Channel channel, 
                           @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            log.info("收到订单消息: {}", order);
            
            // 处理业务逻辑
            processOrder(order);
            
            // 手动确认
            channel.basicAck(tag, false);
            
        } catch (BusinessException e) {
            log.error("订单处理失败，拒绝消息: {}", e.getMessage());
            // 拒绝消息，重新入队
            channel.basicNack(tag, false, true);
            
        } catch (Exception e) {
            log.error("订单处理异常，丢弃消息: {}", e.getMessage());
            // 拒绝消息，不重新入队（进入死信队列）
            channel.basicNack(tag, false, false);
        }
    }
    
    @RabbitListener(queues = "order.dlx.queue")
    public void handleDeadLetter(Order order, Message message, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        log.warn("处理死信消息: {}", order);
        
        // 记录死信消息
        deadLetterService.save(order, message);
        
        // 确认死信消息
        channel.basicAck(tag, false);
    }
}
```

### Topic Exchange示例

```java
@Configuration
public class TopicExchangeConfig {

    @Bean
    public TopicExchange logExchange() {
        return new TopicExchange("log.exchange");
    }
    
    @Bean
    public Queue errorLogQueue() {
        return new Queue("log.error.queue");
    }
    
    @Bean
    public Queue infoLogQueue() {
        return new Queue("log.info.queue");
    }
    
    @Bean
    public Queue allLogQueue() {
        return new Queue("log.all.queue");
    }
    
    @Bean
    public Binding errorLogBinding() {
        return BindingBuilder.bind(errorLogQueue())
                .to(logExchange())
                .with("log.error.*");  // 匹配 log.error.xxx
    }
    
    @Bean
    public Binding infoLogBinding() {
        return BindingBuilder.bind(infoLogQueue())
                .to(logExchange())
                .with("log.info.*");  // 匹配 log.info.xxx
    }
    
    @Bean
    public Binding allLogBinding() {
        return BindingBuilder.bind(allLogQueue())
                .to(logExchange())
                .with("log.#");  // 匹配 log.xxx.xxx
    }
}
```

## 🔗 相关链接

- [返回消息队列抽象模块](../spring-support-queue-starter/README.md)
- [返回主文档](../README.md)
- [RabbitMQ官方文档](https://www.rabbitmq.com/documentation.html)
- [配置示例文件](../application-example.yml)

## 📄 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。
