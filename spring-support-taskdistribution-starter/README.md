# spring-support-taskdistribution-starter

> Spring Boot 自动装配层。基于 `utils-support-common-starter` 的 `com.chua.common.support.taskdistribution` 核心包，提供 Bean 装配、HTTP 管理接口、数据库持久化实现与配置属性。

---

## 引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-taskdistribution-starter</artifactId>
    <version>${revision}</version>
</dependency>
<!-- 当 storeType=db 时 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring</artifactId>
    <version>3.5.16</version>
</dependency>
```

## 配置

```yaml
taskdistribution:
  enabled: true                        # 是否启用（默认 true）
  node-id: ${HOSTNAME:node-001}        # 节点 ID（不填自动生成）
  strategy: ROUND                      # 派发策略：ROUND / RANDOM / WEIGHT
  store-type: memory                   # memory / file / db
  store-path: ./data/taskstore/        # storeType=file 时生效
  batch-size: 1                        # 每次批量派发任务数
  consumer-threads: 4                  # 消费线程池大小
  server:
    enabled: true                      # 启用 TCP/UDP 服务端
    host: 0.0.0.0
    port: 19390
    protocol: tcp                      # tcp / udp
  task:
    timeout-ms: 30000                  # 默认任务超时
    max-retries: 3                     # 默认最大重试
```

## 数据库模式

设置 `taskdistribution.store-type=db` 后，需建表：

```sql
CREATE TABLE sys_task_store (
  task_id      VARCHAR(64)  PRIMARY KEY,
  task_type    VARCHAR(128) NOT NULL,
  task_status  VARCHAR(32)  NOT NULL,
  task_json    TEXT         NOT NULL,
  result_json  TEXT,
  create_time  DATETIME,
  update_time  DATETIME
);
CREATE INDEX idx_status ON sys_task_store(task_status);
```

MyBatis-Plus `TaskStoreMapper` 自动随 starter 注入。

---

## 自动装配 Bean

| Bean | 类型 | 条件 | 说明 |
|---|---|---|---|
| `TaskDistributionProperties` | `@ConfigurationProperties` | `@EnableConfigurationProperties` | 配置属性 |
| `TaskStore` | `InMemoryTaskStore` / `FileTaskStore` / `DbTaskStore` | `@ConditionalOnMissingBean` | 按 `storeType` 选择 |
| `NodeTable` | 节点注册表 | `@ConditionalOnMissingBean` | 心跳保活 |
| `TaskManager` | 任务管理器 | `@ConditionalOnMissingBean` | 启动时 `recover()` |
| `ResultBuffer` | 结果缓冲 | `@ConditionalOnMissingBean` | 60s TTL |
| `TaskExecutorRegistry` | 执行器注册 | `@ConditionalOnMissingBean` | 自动注入 `List<TaskExecutor<?>>` |
| `DispatcherProvider` | `InMemoryDispatcherProvider` | `@ConditionalOnMissingBean` | 启动后即开始消费 |
| `taskDistributionInitializer` | `ApplicationRunner` | always | 节点 ID 初始化 + 注册执行器 |

---

## 管理接口

`TaskDistributionController` 暴露在 `/v2/taskdistribution`，全部带 `@Permission` 鉴权：

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/task/list` | `taskdistribution:task:list` | 所有任务列表 |
| GET | `/task/{taskId}` | `taskdistribution:task:get` | 任务详情 |
| POST | `/task/{taskId}/cancel` | `taskdistribution:task:cancel` | 取消 |
| POST | `/task/{taskId}/pause` | `taskdistribution:task:pause` | 暂停 |
| POST | `/task/{taskId}/resume` | `taskdistribution:task:resume` | 恢复 |
| DELETE | `/task/{taskId}` | `taskdistribution:task:delete` | 删除 |
| GET | `/node/list` | `taskdistribution:node:list` | 节点列表 |
| DELETE | `/node/{nodeId}` | `taskdistribution:node:delete` | 注销节点 |

### 示例

```bash
# 查询所有任务
curl -X GET http://localhost:8080/v2/taskdistribution/task/list \
  -H "Authorization: Bearer $TOKEN"

# 取消任务
curl -X POST http://localhost:8080/v2/taskdistribution/task/abc-123/cancel

# 暂停全局
# (通过 DispatcherProvider.pauseAll(), 当前未暴露 HTTP 接口)
```

---

## 自定义 TaskExecutor

```java
@Component
public class EmailSendExecutor implements TaskExecutor<EmailPayload> {
    @Override
    public String taskType() { return "email.send"; }

    @Override
    public TaskResult<EmailReceipt> execute(Task<EmailPayload> task) {
        MDC.put("taskId", task.getTaskId());
        try {
            EmailReceipt receipt = mailService.send(task.getPayload());
            return TaskResult.success(task.getTaskId(), receipt);
        } catch (Exception e) {
            log.error("邮件发送失败", e);
            return TaskResult.failure(task.getTaskId(), e.getMessage());
        } finally {
            MDC.clear();
        }
    }
}
```

`@Component` 注解后 starter 自动注入到 `TaskExecutorRegistry`。

---

## MDC 链路追踪

每个持久化操作（`saveTask`/`updateStatus`/`saveResult`）自动注入 `taskId`/`traceId` 到 MDC，与 `MdcDecorator` 配合确保跨线程上下文连续：

```java
try {
    if (task.getTaskId() != null) {
        MDC.put(MdcDecorator.KEY_TASK_ID, task.getTaskId());
    }
    if (task.getTraceId() != null) {
        MDC.put(MdcDecorator.KEY_TRACE_ID, task.getTraceId());
    }
    upsert(entity);
} finally {
    MDC.remove(MdcDecorator.KEY_TASK_ID);
    MDC.remove(MdcDecorator.KEY_TRACE_ID);
}
```

Logback pattern 中通过 `%X{taskId}` / `%X{traceId}` 即可输出。

---

## 集群模式

启动 `taskdistribution.server.enabled=true` 时节点自动注册到 `NodeTable`，并通过 TCP/UDP 协议加入散射-汇聚集群：

- TCP：`TcpScatterGatherNodeServer`（可靠）— 默认
- UDP：`UdpScatterGatherNodeServer`（低延迟）

节点间基于 `tags` 路由任务（`com.chua.common.support.taskdistribution.strategy.DispatchStrategy`）。

---

## 已知限制

| 项 | 现状 | 改进方向 |
|---|---|---|
| 重试调度 | `getRetryableTasks()` 返回但无调度器触发 | 接入 `ScheduledExecutorService` |
| 死信队列 | FAILED 直接清理 | 单独 `sys_task_dlq` 表 |
| 指标 | 无 Micrometer | 接入 Actuator + Prometheus |
| 健康检查 | 无 `/actuator/health` 项 | 计划新增 `TaskDistributionHealthIndicator` |
| 单元测试 | 0 个 | 计划补 `TaskManager` / `InMemoryDispatcherProvider` / `DbTaskStore` |
| 重试退避 | 立即重试 | 指数退避 + 抖动 |