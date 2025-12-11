# spring-support-prometheus-starter

## 📖 模块简介

**Prometheus 监控模块** - 提供应用指标采集和暴露功能，支持 Prometheus 监控系统集成。

## ✨ 核心功能

### 📊 指标采集

- ✅ JVM 指标（内存、GC、线程等）
- ✅ HTTP 请求指标
- ✅ 数据库连接池指标
- ✅ 缓存指标
- ✅ 自定义业务指标

### 📈 指标暴露

- ✅ Prometheus 格式暴露
- ✅ 指标端点配置
- ✅ 指标过滤
- ✅ 指标标签

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-prometheus-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置开关

**配置文件**：`application.yml`

```yaml
plugin:
  prometheus:
    # 是否启用Prometheus监控
    # 默认: false
    # 说明: 设置为true时才会启用指标采集和暴露
    enable: true

    # 指标端点路径
    endpoint: /actuator/prometheus

    # 是否启用JVM指标
    enable-jvm-metrics: true

    # 是否启用HTTP指标
    enable-http-metrics: true
```

### 3. 访问指标

启动应用后，访问指标端点：

```
http://localhost:8080/actuator/prometheus
```

### 4. Prometheus 配置

在 Prometheus 配置文件中添加抓取任务：

```yaml
scrape_configs:
  - job_name: "spring-app"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["localhost:8080"]
```

## ⚙️ 配置说明

### 完整配置示例

```yaml
plugin:
  prometheus:
    # 功能开关
    enable: true

    # 指标端点配置
    endpoint: /actuator/prometheus

    # JVM 指标
    enable-jvm-metrics: true
    jvm-metrics:
      - memory
      - gc
      - threads
      - classes

    # HTTP 指标
    enable-http-metrics: true
    http-metrics:
      percentiles: [0.5, 0.95, 0.99]
      histogram: true

    # 数据库指标
    enable-db-metrics: true

    # 缓存指标
    enable-cache-metrics: true

    # 自定义标签
    common-tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
```

## 💡 使用示例

### 自定义业务指标

```java
@Component
public class BusinessMetrics {

    private final Counter orderCounter;
    private final Gauge activeUsers;
    private final Timer orderProcessTime;

    public BusinessMetrics(MeterRegistry registry) {
        // 计数器
        this.orderCounter = Counter.builder("business.order.total")
            .description("订单总数")
            .tag("type", "online")
            .register(registry);

        // 仪表盘
        this.activeUsers = Gauge.builder("business.user.active", this::getActiveUserCount)
            .description("活跃用户数")
            .register(registry);

        // 计时器
        this.orderProcessTime = Timer.builder("business.order.process.time")
            .description("订单处理时间")
            .register(registry);
    }

    public void recordOrder() {
        orderCounter.increment();
    }

    public void recordOrderProcess(Runnable task) {
        orderProcessTime.record(task);
    }

    private int getActiveUserCount() {
        // 获取活跃用户数逻辑
        return 100;
    }
}
```

### 使用注解记录指标

```java
@Service
public class OrderService {

    @Timed(value = "order.create", description = "创建订单耗时")
    public Order createOrder(OrderRequest request) {
        // 创建订单逻辑
        return order;
    }

    @Counted(value = "order.count", description = "订单计数")
    public void processOrder(Order order) {
        // 处理订单逻辑
    }
}
```

## 📊 常用指标说明

### JVM 指标

- `jvm_memory_used_bytes` - JVM 内存使用量
- `jvm_gc_pause_seconds` - GC 暂停时间
- `jvm_threads_live` - 活跃线程数
- `jvm_classes_loaded` - 已加载类数量

### HTTP 指标

- `http_server_requests_seconds` - HTTP 请求耗时
- `http_server_requests_total` - HTTP 请求总数
- `http_server_requests_active` - 活跃请求数

### 数据库指标

- `hikaricp_connections_active` - 活跃连接数
- `hikaricp_connections_idle` - 空闲连接数
- `hikaricp_connections_pending` - 等待连接数

## 🎯 设计原则

### 1. 低开销

- ✅ 轻量级指标采集
- ✅ 异步处理
- ✅ 最小性能影响

### 2. 易于集成

- ✅ 自动配置
- ✅ 开箱即用
- ✅ 与 Spring Boot Actuator 集成

### 3. 灵活配置

- ✅ 可选的指标类型
- ✅ 自定义标签
- ✅ 指标过滤

## 🔗 相关模块

- [spring-support-common-starter](../spring-support-common-starter) - 公共基础模块

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
