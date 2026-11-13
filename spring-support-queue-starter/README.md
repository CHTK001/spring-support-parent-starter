# spring-support-queue-starter

统一消息队列抽象：`MessageTemplate` 定义收发契约，`@QueueListener` / `@QueueEventListener` / `@EventToQueue` 把 Spring Bean 与队列打通；本模块自带基于 Guava EventBus 的本地实现。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 统一收发契约 | `MessageTemplate`、`Message`、`SendResult`、`Acknowledgment` | `send` / `sendAsync` / `sendDelayed` / `subscribe` |
| 本地队列实现 | `LocalMessageTemplate`（每目标一个 `EventBus`，异步走虚拟线程，延迟消息独立调度器） | `plugin.queue.type=local` |
| 方法级监听 | `MessageListenerBeanPostProcessor`、`@QueueListener` | 方法上标注 `@QueueListener` |
| 事件式监听 | `@QueueEventListener` + `QueueEventListener#eventType` | 消息反序列化为 `ApplicationEvent` |
| Spring 事件转队列 | `ApplicationEventToQueueListener`、`@EventToQueue` | `publishEvent(...)` |
| 死信队列 | `DeadLetterAutoConfiguration`、`DeadLetterTemplate`、`RetryPolicy` | `plugin.dead-letter.enable=true` |
| 发送/消费增强 | `MessageInterceptor`（日志/指标/审计/链路）、`MessageFilter`（去重）、`MessageConverter`（GZIP） | 类已提供，需自行装配（见第七节） |
| 事务消息 | `TransactionalMessageTemplate` | 事务提交后发送 |
| 其他工具 | `MessageBatchProcessor`、`MessageRateLimiter`、`MessageRouter`、`MessagePriorityComparator` | 独立工具类 |
| 队列 SPI（声明） | `MessageDispatcherProvider` | 尚未被自动配置消费 |
| 服务信息 | `QueueAutoConfiguration#queueServiceInfoContributor` | 服务信息注册表 |

自动配置登记文件只有一项：`com.chua.starter.queue.configuration.QueueAutoConfiguration`，它同时带 `@ComponentScan("com.chua.starter.queue")`，因此 `ApplicationEventToQueueListener`、`MessageListenerBeanPostProcessor`、`DeadLetterAutoConfiguration` 都在该扫描路径内。

`@EnableMessageQueue` 只是 `@Import(QueueAutoConfiguration.class)` 的语法糖，**仍需要** `plugin.queue.enable=true` 才会生效。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-queue-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用域 | 说明 |
|---|---|---|
| `spring-support-common-starter` | compile | JSON、日志、服务信息 |
| `com.google.guava:guava` | compile | `LocalMessageTemplate` 的 `EventBus` 底座 |
| `lombok` | provided | 仅编译期 |

模块**不自带** Kafka / RabbitMQ / RocketMQ / MQTT / NATS 客户端：这些 `type` 需要业务方自行引入客户端 jar 并提供 `MessageTemplate` Bean。

## 三、配置项

前缀：`plugin.queue`（`QueueProperties.PREFIX`）。

| key | 默认值 | 说明 |
|---|---|---|
| `enable` | `false` | 总开关，`true` 才装配（`havingValue="true"`，不 matchIfMissing） |
| `type` | `chronicle-queue` | 队列类型；本模块只认 `local` |
| `default-destination` | 未设置 | 默认目标地址 |
| `local.delay-threads` | `2` | 本地延迟消息调度线程数 |
| `mqtt.address` | `tcp://localhost:1883` | MQTT 服务地址 |
| `mqtt.client-id` / `username` / `password` | 未设置 | 认证信息 |
| `mqtt.qos` | `1` | QoS 级别 |
| `mqtt.connection-timeout` / `keep-alive-interval` | `30` / `60` | 秒 |
| `kafka.bootstrap-servers` | `localhost:9092` | 集群地址 |
| `kafka.group-id` | `default-group` | 消费组 |
| `kafka.auto-offset-reset` | `earliest` | 位点策略 |
| `kafka.enable-auto-commit` / `auto-commit-interval-ms` | `true` / `5000` | 提交策略 |
| `kafka.acks` | `all` | 生产者确认 |
| `rabbitmq.host` / `port` | `localhost` / `5672` | 连接地址 |
| `rabbitmq.username` / `password` / `virtual-host` | 空 / 空 / `/` | 认证与 vhost |
| `rabbitmq.connection-timeout` | `60000` | 毫秒 |
| `rocketmq.name-server` | `localhost:9876` | NameServer |
| `rocketmq.producer-group` / `consumer-group` | `default-producer` / `default-consumer` | 组名 |
| `rocketmq.send-msg-timeout` / `max-message-size` | `3000` / `4194304` | 毫秒 / 字节 |
| `nats.url` | `nats://localhost:4222` | 服务地址 |
| `nats.username` / `password` / `token` | 未设置 | 认证 |
| `nats.consumer-group` | `default-group` | 消费组 |
| `nats.connection-timeout` / `reconnect-wait` / `max-reconnects` | `5000` / `2000` / `60` | 毫秒 / 毫秒 / 次 |

### 死信队列 `plugin.dead-letter`

`DeadLetterProperties` 的独立前缀，与 `plugin.queue` 同级。

| key | 默认值 | 说明 |
|---|---|---|
| `enable` | `false` | 是否装配 `DeadLetterTemplate` |
| `type` | `chronicle-queue` | 死信存储类型；当前 switch 只实现 `local`，其余一律回落本地 |
| `delay-threads` | `2` | 延迟调度线程数 |
| `max-retries` | `3` | 最大重试次数 |
| `retry-delay-seconds` | `5` | 首次重试延迟（秒） |
| `max-retry-delay-seconds` | `300` | 重试延迟上限（秒） |
| `exponential-backoff` / `backoff-multiplier` | `true` / `2.0` | 指数退避开关与乘数 |

## 四、快速开始

1. 引入依赖。
2. 打开开关并显式选择本地实现（默认值 `chronicle-queue` 在本模块内没有对应实现）：

   ```yaml
   plugin:
     queue:
       enable: true
       type: local
       local:
         delay-threads: 2
     dead-letter:
       enable: true
       max-retries: 3
       retry-delay-seconds: 5
   ```

3. 用注解声明监听，或用 `MessageTemplate` 直接收发。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API：`MessageTemplate` 的方法返回 `SendResult`、`CompletableFuture<SendResult>` 或 `void`，没有 `Builder` / `return this` 风格入口。

```java
// 发送：同步 / 异步 / 延迟
@Service
public class OrderPublisher {

    private final MessageTemplate messageTemplate;   // type=local 时由自动配置提供

    public OrderPublisher(MessageTemplate messageTemplate) {
        this.messageTemplate = messageTemplate;
    }

    public void publish(OrderCreatedEvent event) {
        SendResult result = messageTemplate.send("order.created", event);
        messageTemplate.sendAsync("order.audit", event)
                .thenAccept(r -> System.out.println("async done"));
        messageTemplate.sendDelayed("order.timeout", event, Duration.ofMinutes(30));
    }
}

// 接收：方法级监听，参数可以是 Message，也可以是已反序列化的 POJO
@Component
public class OrderListener {

    @QueueListener("order.created")
    public void onCreated(OrderCreatedEvent event) {
        // 处理业务
    }

    @QueueListener(value = "order.audit", group = "audit-group")
    public void onAudit(Message message, Acknowledgment ack) {
        ack.acknowledge();
    }
}
```

Spring 事件自动转队列（异步监听所有带注解的事件）：

```java
@EventToQueue(value = "payment.order.created", headers = {"priority=high"})
public class OrderCreatedEvent extends ApplicationEvent {
    public OrderCreatedEvent(Object source) {
        super(source);
    }
}

// 任意位置发布，ApplicationEventToQueueListener 会转发到队列
applicationEventPublisher.publishEvent(new OrderCreatedEvent(order));
```

消费侧如需把消息再发布成 Spring 事件：

```java
@QueueEventListener(value = "payment.order.created", eventType = OrderCreatedEvent.class)
public void handle(OrderCreatedEvent event) {
    // ...
}
```

### 5.2 SPI 扩展示例

本模块的 SPI 接口是 `com.chua.starter.queue.spi.MessageDispatcherProvider`（`name()` / `isAvailable()` / `dispatch(...)`），设计意图是「引入适配器 jar → 注册 SPI → `plugin.queue.type` 激活」。**但当前版本没有任何代码消费它**（无 `ServiceLoader`、无 `META-INF/extensions` 注册），因此「实现 → 登记 → 生效」的第三步在本模块内不成立。

实际可用的扩展方式是提供 `MessageTemplate` Bean：

```java
// 1) 实现：把任意中间件适配成统一契约
public class MyKafkaMessageTemplate implements MessageTemplate {

    @Override
    public SendResult send(String destination, Object payload) {
        // 调用 KafkaTemplate#send 并封装 SendResult
        return SendResult.success(UUID.randomUUID().toString(), destination);
    }

    @Override
    public SendResult send(String destination, Object payload, Map<String, Object> headers) {
        return send(destination, payload);
    }

    @Override
    public CompletableFuture<SendResult> sendAsync(String destination, Object payload) {
        return CompletableFuture.supplyAsync(() -> send(destination, payload));
    }

    @Override
    public void subscribe(String destination, MessageHandler handler, boolean autoAck) {
        // 注册消费者
    }

    @Override
    public void unsubscribe(String destination) {
    }

    @Override
    public String getType() {
        return "kafka";
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void close() {
    }
}

// 2) 登记为 Bean（localMessageTemplate 带 @ConditionalOnMissingBean(MessageTemplate.class)，不会冲突）
@Bean
public MessageTemplate kafkaMessageTemplate() {
    return new MyKafkaMessageTemplate();
}

// 3) 生效：@QueueListener / @QueueEventListener / @EventToQueue 都会从容器里的 MessageTemplate 列表中取实现
```

## 六、与其它 starter 的关系

- `spring-support-common-starter`：JSON 序列化、日志、服务信息，本模块必需。
- `spring-support-sync-starter`：点对点/广播的同步通道，偏连接与主题推送；本模块偏"消息语义"（重试、死信、事务），二者可组合但互不依赖。
- `spring-support-job-starter`：任务调度自己的 `JobSyncBus` 不经过本模块，二者无装配关系。
- 各 MQ 客户端 starter（Kafka / RabbitMQ / RocketMQ / MQTT）：本模块未纳入依赖，需要业务方自行引入并通过 `MessageTemplate` Bean 接入。

## 七、已知限制

- `plugin.queue.type` 默认 `chronicle-queue`，而本模块只实现 `local`；**不显式配置 `type: local` 时容器里不会有 `MessageTemplate`**，`@EventToQueue` 会打印 `No MessageTemplate found for type` 并跳过。
- `MessageInterceptor`（Logging / Metrics / Audit / Trace）、`MessageFilter`（Duplicate）、`MessageConverter`（Gzip）只有实现类，**未在任何发送或消费链路中被调用**，需要自行在自定义 `MessageTemplate` 里组合使用。
- `@DeadLetterListener` 注解没有对应的解析器；死信重试由 `DeadLetterTemplate` 的 `DeadLetterConfig`（重试次数/退避）驱动，监听方法需自行订阅死信目标。
- `TransactionalMessageTemplate`、`MessageBatchProcessor`、`SemaphoreMessageRateLimiter`、`TenantBasedMessageRouter`、`MessagePriorityComparator` 都是独立工具类，未被自动配置装配。
- `DeadLetterAutoConfiguration` 不在 `AutoConfiguration.imports` 中，只靠 `QueueAutoConfiguration` 的 `@ComponentScan` 生效；因此 `plugin.queue.enable=false` 时即使 `plugin.dead-letter.enable=true` 也不会装配死信队列。
- `plugin.dead-letter.type` 除 `local` 外的取值会在初始化时告警并回落本地实现，不存在远端死信存储。
- `LocalMessageTemplate` 仅在单进程内有效，重启即丢消息，不能当作跨节点队列使用。
