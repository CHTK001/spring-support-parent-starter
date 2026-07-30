# Queue模块增强说明

## 新增功能

### 1. @EventToQueue 注解
自动将Spring ApplicationEvent发送到队列

```java
@EventToQueue("payment.order.created")
public class OrderCreatedEvent extends ApplicationEvent {
    // 事件字段
}
```

**注解参数:**
- `value`: 目标队列地址
- `type`: 消息队列类型（默认使用配置的类型）
- `async`: 是否异步发送（默认true）
- `headers`: 消息头（key=value格式）

### 2. @QueueEventListener 注解
监听队列消息并自动转换为ApplicationEvent

```java
@Component
public class PaymentEventHandler {

    @QueueEventListener(value = "payment.order.created", eventType = OrderCreatedEvent.class)
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 处理事件
    }

    @QueueEventListener(
        value = "payment.order.paid",
        eventType = PaymentSuccessEvent.class,
        republish = true  // 重新发布到Spring事件总线
    )
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        // 处理事件
    }
}
```

**注解参数:**
- `value`: 目标队列地址
- `eventType`: 事件类型（必须是ApplicationEvent子类）
- `group`: 消费组
- `type`: 消息队列类型
- `autoAck`: 是否自动确认（默认true）
- `concurrency`: 并发消费者数量（默认1）
- `republish`: 是否重新发布到Spring事件总线（默认false）

### 3. ApplicationEventToQueueListener
自动监听所有标注了@EventToQueue的ApplicationEvent并发送到队列

## 使用场景

### 场景1: 事件自动发送到队列
```java
// 1. 定义事件并标注@EventToQueue
@EventToQueue("payment.order.created")
public class OrderCreatedEvent extends ApplicationEvent {
    private final Long orderId;
    private final String orderNo;
    // ...
}

// 2. 发布事件（自动发送到队列）
eventPublisher.publishEvent(new OrderCreatedEvent(this, orderId, orderNo));
```

### 场景2: 从队列接收事件
```java
@Component
public class OrderEventHandler {

    // 方式1: 直接接收事件对象
    @QueueEventListener(value = "payment.order.created", eventType = OrderCreatedEvent.class)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("收到订单创建事件: {}", event.getOrderNo());
    }

    // 方式2: 接收事件并重新发布到Spring事件总线
    @QueueEventListener(
        value = "payment.order.created",
        eventType = OrderCreatedEvent.class,
        republish = true
    )
    public void handleAndRepublish(OrderCreatedEvent event) {
        // 处理后，事件会自动重新发布到Spring事件总线
        // 其他@EventListener也能接收到
    }

    // 方式3: 手动确认
    @QueueEventListener(
        value = "payment.order.created",
        eventType = OrderCreatedEvent.class,
        autoAck = false
    )
    public void handleWithManualAck(OrderCreatedEvent event, Acknowledgment ack) {
        try {
            // 处理事件
            ack.acknowledge();
        } catch (Exception e) {
            ack.nack(true); // 重新入队
        }
    }
}
```

### 场景3: 跨服务事件传递
```java
// 服务A: 发布事件（自动发送到队列）
@EventToQueue(value = "payment.order.created", type = "kafka")
public class OrderCreatedEvent extends ApplicationEvent {
    // ...
}

// 服务B: 接收队列消息并转换为事件
@QueueEventListener(
    value = "payment.order.created",
    eventType = OrderCreatedEvent.class,
    type = "kafka",
    group = "order-service"
)
public void handleOrderCreated(OrderCreatedEvent event) {
    // 处理事件
}
```

## 配置示例

```yaml
plugin:
  queue:
    enable: true
    type: local  # 或 kafka, rabbitmq, rocketmq, mqtt
    local:
      delay-threads: 2
```

## 与原有@QueueListener的区别

| 特性 | @QueueListener | @QueueEventListener |
|------|----------------|---------------------|
| 消息类型 | 任意对象 | ApplicationEvent |
| 自动转换 | 否 | 是 |
| 重新发布 | 不支持 | 支持（republish=true） |
| 与Spring事件集成 | 否 | 是 |
| 使用场景 | 通用消息处理 | 事件驱动架构 |

## 注意事项

1. @EventToQueue标注的事件类必须是ApplicationEvent的子类
2. @QueueEventListener的eventType参数必须指定正确的事件类型
3. 使用republish=true时，注意避免事件循环（事件A -> 队列 -> 事件A -> 队列 ...）
4. 跨服务传递事件时，确保事件类在各服务中的包名和字段一致
5. 建议使用异步发送（async=true）以避免阻塞主业务流程
