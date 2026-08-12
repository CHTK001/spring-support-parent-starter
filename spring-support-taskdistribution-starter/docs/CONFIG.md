# 配置手册（Configuration Cookbook）

> 5 种典型场景的完整 `application.yml` 配置。每种组合配 1 个 yaml + 1 段"何时使用" + 1 段"性能/可靠性注意"。

---

## 1. 单机内存（开发 / 测试 / 一次性任务）

**何时使用**：本地调试、单元测试、Demo。任务量 < 1k，重启可丢任务。

```yaml
taskdistribution:
  enabled: true
  store-type: memory
  consumer-threads: 2
  batch-size: 1
  task:
    timeout-ms: 10_000
    max-retries: 1
```

**注意**：
- 应用重启**所有任务丢失**
- 无并发安全检查（`InMemoryTaskStore` 用 `ConcurrentHashMap`）
- 不要用于生产

---

## 2. 单机数据库（生产推荐起点）

**何时使用**：单个 JVM 实例 + 关系型数据库（MySQL/PostgreSQL/Oracle）。任务量 1k-100k/天，需要可恢复。

```yaml
taskdistribution:
  enabled: true
  store-type: db
  consumer-threads: 4                  # 建议 = CPU 核数 / 2
  batch-size: 4
  server:
    enabled: false                     # 暂不加入集群
  task:
    timeout-ms: 30_000
    max-retries: 3
spring:
  datasource:
    url: jdbc:mysql://mysql-host:3306/app_db?useSSL=false
    username: ${DB_USER}
    password: ${DB_PASS}
    hikari:
      maximum-pool-size: 10
```

```sql
CREATE TABLE sys_task_store (
  task_id      VARCHAR(64)  PRIMARY KEY,
  task_type    VARCHAR(128) NOT NULL,
  task_status  VARCHAR(32)  NOT NULL,
  task_json    LONGTEXT     NOT NULL,    -- 任务量大时用 LONGTEXT
  result_json  LONGTEXT,
  create_time  DATETIME,
  update_time  DATETIME,
  INDEX idx_status (task_status),
  INDEX idx_type_status (task_type, task_status)
);
```

**注意**：
- `DbTaskStore.upsert` 用 `entity.getTaskId().intern()` 同步，高并发写入有 String-intern 锁竞争（监控 GC）
- `task_json` 默认 `TEXT` 仅 64KB；如 payload 含大图片改 `LONGTEXT`
- 必须有 `@MapperScan` 扫描 `com.chua.starter.taskdistribution.support.mapper`

---

## 3. 3 节点 TCP 集群（高可用）

**何时使用**：3 台 JVM 互相备份，任意一台宕机不影响整体。任务广播到所有节点（散射-汇聚），适合"低延迟 + 中等吞吐"。

```yaml
# 节点 1 application-node1.yml
taskdistribution:
  enabled: true
  node-id: node-01
  store-type: db
  consumer-threads: 8
  batch-size: 8
  strategy: WEIGHT                    # 按权重选择工作端
  server:
    enabled: true
    host: 0.0.0.0
    port: 19390
    protocol: tcp
spring:
  datasource:
    url: jdbc:mysql://mysql-host:3306/app_db

---
# 节点 2 application-node2.yml（仅差异）
taskdistribution:
  node-id: node-02
  # ...其他同上
---
# 节点 3 application-node3.yml
taskdistribution:
  node-id: node-03
```

**注意**：
- 每个节点独立数据库 OR 共享同一数据库（共享更简单）
- 散射模式下每个任务在所有节点都会执行一次（用于冗余），不是真正的工作端路由
- 如果要"任务只在一个节点执行"，需要额外的 leader election（目前未实现）

---

## 4. UDP 集群（低延迟）

**何时使用**：任务小（< 1KB），延迟敏感（< 10ms），容忍偶发丢包。常见于实时通知、IoT。

```yaml
taskdistribution:
  enabled: true
  node-id: edge-001
  store-type: file                   # 边缘节点用文件存储
  store-path: /var/lib/taskstore/
  consumer-threads: 2
  batch-size: 16                     # UDP 适合批量
  server:
    enabled: true
    port: 19390
    protocol: udp
  task:
    timeout-ms: 5_000
    max-retries: 0                   # UDP 不重试
```

**注意**：
- UDP payload 限制 65KB，**实际建议 < 1.5KB**（避免 IP 分片）
- 跨网段需关闭组播，改用单播列表（修改源码 `UdpScatterGatherNodeServer`）
- 必须配置 NTP，时间漂移 > 30s 节点会判 offline

---

## 5. 高吞吐批处理

**何时使用**：百万级任务/天，CPU 密集型（如图片处理、报表生成），不要求毫秒级响应。

```yaml
taskdistribution:
  enabled: true
  store-type: db
  consumer-threads: 16                # 接近 CPU 核数
  batch-size: 32                      # 一次拉 32 条减少调度开销
  task:
    timeout-ms: 300_000               # 5 分钟超时（长任务）
    max-retries: 5
spring:
  datasource:
    hikari:
      maximum-pool-size: 32           # 与 consumer-threads 匹配
      connection-timeout: 3000
  task:
    execution:
      pool:
        core-size: 16
        max-size: 32
        queue-capacity: 1000
```

**JVM 调优**：
```bash
java -Xms4g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -jar app.jar
```

**注意**：
- `batch-size` 不是越大越好——单个批次全失败 = 全部重试
- 监控 `pendingCount`：> 1000 说明消费慢，需要加节点或加线程
- DB 连接池必须 ≥ `consumer-threads`，否则任务会卡在获取连接

---

## 6. 仅用作后台任务（不暴露 HTTP 接口）

**何时使用**：纯内部使用，不需要 knife4j/管理端点。

```java
@Configuration
public class DisableController {
    @Bean
    public WebMvcRegistrations webMvcRegistrations() {
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
                mapping.setPathMatchOptions(new PathMatchOptions());
                // 不注册任何路径
                return mapping;
            }
        };
    }
}
```

或更简单——用 `taskdistribution.enabled=true` 但自定义 controller path：

```java
@RestController
@RequestMapping("/internal/taskdistribution")
public class InternalController {
    // 复用 starter 的 TaskManager / DispatcherProvider / NodeTable
}
```

---

## 7. 与 Spring Boot Actuator 集成

**何时使用**：要监控任务指标（`pendingCount` / 成功失败计数 / 队列深度）。

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
```

在 `TaskManager.checkTimeouts` 中增加 Micrometer 指标（待实现）：

```java
@Autowired MeterRegistry registry;

private void checkTimeouts() {
    long pending = tasks.values().stream()
        .filter(h -> h.status == PENDING || h.status == RUNNING)
        .count();
    registry.gauge("taskdistribution.pending", pending);
    registry.gauge("taskdistribution.dispatcher.pending", dispatcherProvider.pendingCount());
}
```

---

## 配置项速查表

| 字段 | 默认 | 说明 | 推荐范围 |
|---|---|---|---|
| `enabled` | `true` | 全局开关 | — |
| `node-id` | 自动生成 | 节点唯一标识 | 显式配置 |
| `strategy` | `ROUND` | 派发策略 | `WEIGHT` 推荐集群 |
| `store-type` | `memory` | 持久化类型 | 生产用 `db` |
| `store-path` | `./data/taskstore/` | file 存储目录 | 独立数据盘 |
| `batch-size` | `1` | 批量处理任务数 | 1-32 |
| `consumer-threads` | `1` | 消费线程数 | 2-16 |
| `server.enabled` | `true` | 是否启动 TCP/UDP 监听 | 集群开，单机关 |
| `server.port` | `19390` | 监听端口 | 固定 |
| `server.protocol` | `tcp` | 传输协议 | 大 payload 用 tcp |
| `task.timeout-ms` | `30000` | 默认超时 | 业务相关 |
| `task.max-retries` | `3` | 默认重试次数 | 0-5 |