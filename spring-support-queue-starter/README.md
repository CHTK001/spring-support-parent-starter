# Spring Support Queue Starter 完整增强文档

## 概述

spring-support-queue-starter 是一个统一的消息队列抽象层，支持多种消息队列实现（Kafka、RabbitMQ、RocketMQ、MQTT、Local），并提供了丰富的企业级特性。

## 核心特性

### 1. 事件驱动架构支持
### 2. 消息拦截器（Interceptor）
### 3. 消息过滤器（Filter）
### 4. 消息转换器（Converter）
### 5. 重试策略（Retry Policy）
### 6. 死信队列（Dead Letter Queue）
### 7. 事务消息（Transactional Message）

详细文档请查看 [QUEUE_ENHANCEMENT.md](./QUEUE_ENHANCEMENT.md)

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-queue-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

```yaml
plugin:
  queue:
    enable: true
    type: local  # 或 kafka, rabbitmq, rocketmq, mqtt
```

### 3. 使用

```java
// 定义事件
@EventToQueue("payment.order.created")
public class OrderCreatedEvent extends ApplicationEvent {
    // ...
}

// 发布事件（自动发送到队列）
eventPublisher.publishEvent(new OrderCreatedEvent(...));

// 接收事件
@QueueEventListener(value = "payment.order.created", eventType = OrderCreatedEvent.class)
public void handleOrderCreated(OrderCreatedEvent event) {
    // 处理事件
}
```

## 新增功能

### 事件自动转队列
- @EventToQueue: 标注在ApplicationEvent类上，自动将事件发送到队列
- @QueueEventListener: 从队列接收消息并自动转换为ApplicationEvent
- ApplicationEventToQueueListener: 自动监听所有标注了@EventToQueue的事件

### 消息拦截器
- MessageInterceptor: 拦截器接口
- LoggingMessageInterceptor: 日志记录拦截器
- MetricsMessageInterceptor: 性能监控拦截器

### 消息过滤器
- MessageFilter: 过滤器接口
- DuplicateMessageFilter: 消息去重过滤器

### 消息转换器
- MessageConverter: 转换器接口
- GzipMessageConverter: GZIP压缩转换器

### 重试和死信
- RetryPolicy: 重试策略配置
- @DeadLetterListener: 死信队列监听注解

### 事务消息
- TransactionalMessageTemplate: 事务消息模板，支持在Spring事务提交后发送消息

## 文件清单

### 注解
- EventToQueue.java
- QueueEventListener.java
- DeadLetterListener.java

### 拦截器
- MessageInterceptor.java
- LoggingMessageInterceptor.java
- MetricsMessageInterceptor.java

### 过滤器
- MessageFilter.java
- DuplicateMessageFilter.java

### 转换器
- MessageConverter.java
- GzipMessageConverter.java

### 重试和事务
- RetryPolicy.java
- TransactionalMessageTemplate.java

### 监听器
- ApplicationEventToQueueListener.java

## 最佳实践

1. 使用事务消息确保消息发送与数据库操作的一致性
2. 启用消息去重防止重复消息处理
3. 配置死信队列处理失败消息
4. 使用消息压缩减少网络传输成本
5. 监控性能指标及时发现问题
6. 合理设置并发数提高处理效率
7. 实现幂等性确保消息处理的正确性
8. 添加消息签名确保消息安全性
