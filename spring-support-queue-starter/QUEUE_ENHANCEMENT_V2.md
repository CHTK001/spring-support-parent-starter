# Queue模块第二轮增强功能

## 新增功能概览

### 1. 消息路由器（Router）
根据消息内容动态路由到不同的目标队列。

**核心类:**
- `MessageRouter`: 路由器接口
- `TenantBasedMessageRouter`: 基于租户的路由器

**使用场景:**
```java
// 自动根据租户ID路由到不同队列
// payment.order.created -> payment.order.created.tenant.1001
@Component
public class CustomRouter implements MessageRouter {
    @Override
    public String route(Message message) {
        String region = message.getHeaderAsString("region");
        return message.getDestination() + "." + region;
    }
}
```

### 2. 消息批量处理（Batch）
将多个消息聚合后批量处理，提高处理效率。

**核心类:**
- `MessageBatchProcessor`: 批量处理器

**使用场景:**
```java
MessageBatchProcessor processor = new MessageBatchProcessor();
processor.setBatchSize(100);
processor.setBatchTimeout(Duration.ofSeconds(5));
processor.setBatchHandler(messages -> {
    // 批量处理100条消息或5秒超时
    batchInsertToDatabase(messages);
});

// 添加消息
processor.add(message);

// 手动刷新
processor.flush();
```

### 3. 消息限流器（Rate Limiter）
控制消息处理速率，防止系统过载。

**核心类:**
- `MessageRateLimiter`: 限流器接口
- `SemaphoreMessageRateLimiter`: 基于信号量的限流器

**使用场景:**
```java
@Autowired
private SemaphoreMessageRateLimiter rateLimiter;

// 设置限流
rateLimiter.setPermits("payment.order.created", 100);

// 处理消息前获取许可
if (rateLimiter.tryAcquire(message)) {
    try {
        processMessage(message);
    } finally {
        rateLimiter.release(message);
    }
} else {
    log.warn("限流拒绝");
}
```

### 4. 消息链路追踪（Trace）
记录消息在系统中的流转路径和处理信息。

**核心类:**
- `MessageTraceContext`: 追踪上下文
- `MessageTraceInterceptor`: 追踪拦截器

**功能特性:**
- 自动生成traceId和spanId
- 记录发送时间、接收时间、处理时间
- 计算处理耗时和总耗时
- 支持分布式追踪

**使用场景:**
```java
@Autowired
private MessageTraceInterceptor traceInterceptor;

// 获取追踪信息
MessageTraceContext context = traceInterceptor.getTraceContext(messageId);
log.info("处理耗时: {}ms", context.getProcessDuration());
log.info("总耗时: {}ms", context.getTotalDuration());
```

### 5. 消息优先级（Priority）
支持消息优先级排序，高优先级消息优先处理。

**核心类:**
- `MessagePriorityComparator`: 优先级比较器

**使用场景:**
```java
// 设置消息优先级（0-10，数字越小优先级越高）
MessagePriorityComparator.setPriority(message, 1); // 高优先级
MessagePriorityComparator.setHighPriority(message); // 快捷方法
MessagePriorityComparator.setLowPriority(message);  // 低优先级

// 判断优先级
if (MessagePriorityComparator.isHighPriority(message)) {
    // 高优先级处理
}

// 使用优先级队列
PriorityQueue<Message> queue = new PriorityQueue<>(new MessagePriorityComparator());
```

### 6. 消息审计（Audit）
记录消息的完整生命周期信息，用于审计和问题排查。

**核心类:**
- `MessageAuditLog`: 审计日志实体
- `MessageAuditInterceptor`: 审计拦截器

**记录信息:**
- 消息ID、目标地址、消息类型
- 操作类型（SEND/RECEIVE/PROCESS）
- 操作时间、状态、耗时
- 租户ID、用户ID
- 消息大小、内容摘要
- 错误信息、重试次数

**配置启用:**
```yaml
plugin:
  queue:
    audit:
      enabled: true
```

## 完整使用示例

### 场景1: 高优先级消息处理

```java
@Service
public class OrderService {

    @Autowired
    private MessageTemplate messageTemplate;

    public void createUrgentOrder(OrderDTO dto) {
        // 创建订单
        Order order = orderRepository.save(new Order(dto));

        // 发送高优先级消息
        Message message = Message.of("payment.order.created", order);
        MessagePriorityComparator.setHighPriority(message);
        messageTemplate.send(message.getDestination(), message.getPayload(), message.getHeaders());
    }
}
```

### 场景2: 批量消息处理

```java
@Component
public class OrderBatchProcessor {

    private final MessageBatchProcessor batchProcessor;

    public OrderBatchProcessor() {
        batchProcessor = new MessageBatchProcessor();
        batchProcessor.setBatchSize(100);
        batchProcessor.setBatchTimeout(Duration.ofSeconds(5));
        batchProcessor.setBatchHandler(this::processBatch);
    }

    @QueueListener("payment.order.created")
    public void handleOrder(Message message) {
        batchProcessor.add(message);
    }

    private void processBatch(List<Message> messages) {
        List<Order> orders = messages.stream()
            .map(msg -> msg.getPayload(Order.class))
            .collect(Collectors.toList());

        // 批量插入数据库
        orderRepository.saveAll(orders);

        log.info("批量处理订单: {} 条", orders.size());
    }
}
```

### 场景3: 租户路由 + 限流 + 追踪

```java
@Component
public class TenantOrderHandler {

    @Autowired
    private SemaphoreMessageRateLimiter rateLimiter;

    @Autowired
    private MessageTraceInterceptor traceInterceptor;

    @QueueListener("payment.order.created.tenant.*")
    public void handleTenantOrder(Message message) {
        // 限流控制
        if (!rateLimiter.tryAcquire(message)) {
            log.warn("租户消息限流: tenantId={}", message.getHeader("tenantId"));
            return;
        }

        try {
            // 处理消息
            processOrder(message);

            // 查看追踪信息
            MessageTraceContext trace = traceInterceptor.getTraceContext(message.getId());
            if (trace != null && trace.getProcessDuration() > 1000) {
                log.warn("处理耗时过长: {}ms", trace.getProcessDuration());
            }
        } finally {
            rateLimiter.release(message);
        }
    }
}
```

### 场景4: 审计日志查询

```java
@Service
public class MessageAuditService {

    @Autowired
    private MessageAuditInterceptor auditInterceptor;

    public MessageAuditLog getAuditLog(String messageId) {
        return auditInterceptor.getAuditLog(messageId);
    }

    public void analyzePerformance(String messageId) {
        MessageAuditLog log = getAuditLog(messageId);
        if (log != null) {
            log.info("消息审计:");
            log.info("  操作: {}", log.getOperation());
            log.info("  状态: {}", log.getStatus());
            log.info("  耗时: {}ms", log.getDuration());
            log.info("  大小: {} bytes", log.getMessageSize());
            log.info("  租户: {}", log.getTenantId());
        }
    }
}
```

## 架构增强

```
┌─────────────────────────────────────────────────────────────┐
│                     Application Layer                        │
│  @EventToQueue  @QueueEventListener  @DeadLetterListener    │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Interceptor Chain                         │
│  TraceInterceptor → AuditInterceptor → MetricsInterceptor   │
│  → LoggingInterceptor → CustomInterceptor                   │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                      Filter Chain                            │
│  DuplicateFilter → TenantFilter → BusinessFilter            │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Converter Chain                           │
│  EncryptionConverter → GzipConverter → CustomConverter      │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                      Router Layer                            │
│  TenantRouter → RegionRouter → CustomRouter                 │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Rate Limiter Layer                        │
│  SemaphoreRateLimiter → TokenBucketRateLimiter              │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    MessageTemplate                           │
│  send() / sendAsync() / subscribe() / unsubscribe()         │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  Queue Implementation                        │
│  Kafka / RabbitMQ / RocketMQ / MQTT / Local                 │
└─────────────────────────────────────────────────────────────┘
```

## 新增文件清单

### 路由器
- MessageRouter.java
- TenantBasedMessageRouter.java

### 批量处理
- MessageBatchProcessor.java

### 限流器
- MessageRateLimiter.java
- SemaphoreMessageRateLimiter.java

### 链路追踪
- MessageTraceContext.java
- MessageTraceInterceptor.java

### 优先级
- MessagePriorityComparator.java

### 审计
- MessageAuditLog.java
- MessageAuditInterceptor.java

## 配置示例

```yaml
plugin:
  queue:
    enable: true
    type: local

    # 审计配置
    audit:
      enabled: true

    # 限流配置
    rate-limiter:
      default-permits: 100
      destinations:
        payment.order.created: 200
        payment.order.paid: 150

    # 批量处理配置
    batch:
      enabled: true
      default-batch-size: 100
      default-timeout: 5s

    # 追踪配置
    trace:
      enabled: true
      sample-rate: 1.0  # 采样率
```

## 性能优化建议

1. **批量处理**: 对于高吞吐量场景，使用批量处理可以显著提高性能
2. **限流保护**: 设置合理的限流阈值，防止系统过载
3. **优先级队列**: 对于有优先级需求的场景，使用优先级队列
4. **审计采样**: 生产环境可以降低审计采样率，减少性能开销
5. **追踪采样**: 对于高流量场景，可以设置追踪采样率（如10%）
6. **异步处理**: 审计和追踪日志应该异步写入，避免阻塞主流程

## 总结

第二轮增强为queue模块添加了6大核心功能：

1. **消息路由器**: 动态路由，支持多租户、多区域
2. **批量处理**: 提高吞吐量，减少数据库压力
3. **限流器**: 保护系统，防止过载
4. **链路追踪**: 分布式追踪，性能分析
5. **优先级**: 重要消息优先处理
6. **审计日志**: 完整记录，问题排查

这些功能使queue模块具备了企业级消息队列的完整能力！
