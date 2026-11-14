# spring-support-taskdistribution-starter

任务分发模块：基于 `TaskDistributionFramework`（utils 层 `com.chua.common.support.taskdistribution`）实现任务的派发、执行、结果回传、重试/超时与多节点负载均衡。
默认开启（`plugin.taskdistribution.enabled` 缺省即 true），零配置即可跑通（内存存储、单消费线程）；存储可切换为文件或数据库（MyBatis-Plus），服务端监听（`taskdistribution.server.*`）与任务默认参数（`task.timeout-ms` / `task.max-retries`）默认有值但当前运行期未装配/未读取（见「七、已知限制」）。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 自动配置 | `TaskDistributionAutoConfiguration` | `plugin.taskdistribution.enabled`（默认 true，`spring.factories` 注册） |
| 配置绑定 | `TaskDistributionProperties` | `plugin.taskdistribution.*` |
| 持久化存储（多态） | `TaskStore`：`InMemoryTaskStore` / `FileTaskStore` / `DbTaskStore` | `plugin.taskdistribution.store-type` |
| 数据库存储实现 | `DbTaskStore` / `TaskStoreMapper` / `SysTaskStore` | `store-type=db` + MyBatis-Plus |
| 节点注册表 | `NodeTable` | `node/list` 管理接口 |
| 任务管理器 | `TaskManager`（启动自动恢复未完成任务） | 直接注入 Bean |
| 结果缓冲 | `ResultBuffer` | 注入 Bean / 删除任务记录 |
| 执行器注册中心 | `TaskExecutorRegistry` | `TaskExecutor` 实现自动注册 |
| 派发提供者（批量+消费线程） | `DispatcherProvider` / `InMemoryDispatcherProvider` | `batch-size` / `consumer-threads` |
| 初始化（nodeId + 执行器注册） | `taskDistributionInitializer`（`ApplicationRunner`） | 启动日志 |
| 管理接口 | `TaskDistributionController` | `/v2/taskdistribution/**` |
| 服务信息登记 | `taskDistributionServiceInfoContributor` | 控制台模块能力 |

实际生效的能力：

- 启动时生成/校验 nodeId（未配置时自动生成 `node-<timestamp>`）并设置到 `TaskIdGenerator`；
- 自动注册容器内全部 `TaskExecutor<?>` 实现（按 `taskType()` 归类）；
- 按 `store-type` 选择存储：内存（默认）/ 文件 / 数据库，`TaskManager` 启动时 `recover()` 恢复未完成任务；
- `InMemoryDispatcherProvider` 按 `consumer-threads` 消费线程与 `batch-size` 批量派发；
- 管理接口：任务清单、任务详情、取消/暂停/恢复、删除任务记录、节点清单、注销节点（均带 `@Permission` 权限声明与 Swagger 注解）；
- 数据库存储的 upsert 语义与 MDC 链路追踪（`MdcDecorator.KEY_TASK_ID` / `KEY_TRACE_ID`）。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-taskdistribution-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `spring-boot-starter` | 容器与自动配置 |
| `spring-support-common-starter` | `ServiceInfoContributor`，以及 `@Permission` 注解（供控制器鉴权声明） |
| `spring-support-mybatis-starter` | **optional**：`store-type=db` 时的 MyBatis-Plus / 数据源环境 |
| `spring-support-swagger-starter` | **provided**：`@Tag` / `@Operation` 文档注解 |
| `utils-support-*`（taskdistribution 内核） | `TaskManager` / `TaskStore` / `DispatcherProvider` / `TaskExecutorSPI`，经 common-starter 传递引入 |

> `spring-support-taskdistribution-starter` 依赖的 `TaskDistributionFramework` 等 utils 内核类经由 `spring-support-common-starter` 传递；模块自身仅在 `store-type=db` 时才需要 MyBatis-Plus 相关依赖。

## 三、配置项

配置前缀：**`plugin.taskdistribution`**。

### 3.1 基础

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.taskdistribution.enabled` | `true` | 是否装配任务分发（缺省即开启，关闭后自动配置整体不加载） |
| `plugin.taskdistribution.node-id` | null（自动生成） | 节点标识；未配置时启动自动生成 `node-<timestamp>` 并回写 |
| `plugin.taskdistribution.strategy` | `ROUND` | 派发策略（`DispatchStrategy` 枚举）；**仅用于日志输出与服务信息登记，当前未传给派发器** |
| `plugin.taskdistribution.batch-size` | `1` | 每次批量处理的任务数（传入 `InMemoryDispatcherProvider`） |
| `plugin.taskdistribution.consumer-threads` | `1` | 消费线程数（传入 `InMemoryDispatcherProvider`） |
| `plugin.taskdistribution.store-type` | `memory` | 持久化类型：`memory`（默认）/ `file` / `db` |
| `plugin.taskdistribution.store-path` | `./data/taskstore/` | 文件存储数据目录（`store-type=file` 时生效） |

### 3.2 服务端（`plugin.taskdistribution.server.*`）—— 仅声明

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.taskdistribution.server.enabled` | `true` | 是否启用服务端模式（接收任务）；**仅声明，当前自动配置未装配任何 server Bean** |
| `plugin.taskdistribution.server.port` | `19390` | 监听端口；**仅声明，运行期未读取** |
| `plugin.taskdistribution.server.host` | `0.0.0.0` | 监听主机；**仅声明，运行期未读取** |
| `plugin.taskdistribution.server.protocol` | `tcp` | 传输协议 tcp/udp；**仅声明，运行期未读取** |

### 3.3 任务默认参数（`plugin.taskdistribution.task.*`）—— 仅声明

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.taskdistribution.task.timeout-ms` | `30000` | 默认超时（毫秒）；**仅声明，当前未读取**（超时/重试需在 `Task.builder()` 上显式指定） |
| `plugin.taskdistribution.task.max-retries` | `3` | 默认最大重试次数；**仅声明，当前未读取** |

## 四、快速开始

**第一步：引入依赖**（见上）。

**第二步：配置 yaml**（零配置即可跑通，下面演示可选项）：

```yaml
plugin:
  taskdistribution:
    enabled: true
    node-id: node-01            # 推荐显式配置，未配置时自动生成
    consumer-threads: 2
    batch-size: 1
    store-type: memory          # 默认 memory；生产可切 db / file
```

**第三步：实现一个 `TaskExecutor` 并提交任务**

```java
@Component
public class HelloExecutor implements TaskExecutor<String> {

    @Override
    public String taskType() {
        return "hello";
    }

    @Override
    public TaskResult<String> execute(Task<String> task) {
        String input = task.getPayload();
        return TaskResult.success(task.getTaskId(), "Hello, " + input);
    }
}
```

```java
@Bean
public ApplicationRunner demoTask(TaskManager taskManager) {
    return args -> {
        Task<String> task = Task.<String>builder()
            .taskType("hello")
            .payload("CH")
            .priority(TaskPriority.HIGH)
            .timeoutMs(10_000)          // 显式指定超时/重试（task.timeout-ms 未接入）
            .maxRetries(2)
            .build();
        taskManager.addTask(task, new TaskCallback() {
            @Override public void onResult(TaskResult<?> result) { /* ... */ }
            @Override public void onError(String taskId, String error) { /* ... */ }
            @Override public void onTimeout(String taskId) { /* ... */ }
        });
    };
}
```

**第四步：验证**

1. 启动日志出现 `任务分发初始化完成, nodeId: node-01, strategy: ROUND, batchSize: 1, consumerThreads: 2` 与 `注册任务执行器: hello`（debug 级）；
2. 任务执行回调触发（`onResult`），管理接口可查到任务与节点：
   ```bash
   curl http://127.0.0.1:8080/v2/taskdistribution/task/list
   curl http://127.0.0.1:8080/v2/taskdistribution/node/list
   ```
3. 切换 `store-type: db` 时先建表（脚本见 `src/main/resources/db/init/V2.0__init_all_taskdistribution.sql`，表 `sys_task_store`），并确保 `@MapperScan` 扫描 `com.chua.starter.taskdistribution.support.mapper`，否则回退内存存储并告警。

## 五、运行示例

### 5.1 调用示例

注入 `TaskManager` / `DispatcherProvider` / `NodeTable` 编程式使用：

```java
@Service
public class TaskDispatchDemo {

    private final TaskManager taskManager;
    private final DispatcherProvider dispatcherProvider;
    private final NodeTable nodeTable;

    public TaskDispatchDemo(TaskManager taskManager,
                            DispatcherProvider dispatcherProvider,
                            NodeTable nodeTable) {
        this.taskManager = taskManager;
        this.dispatcherProvider = dispatcherProvider;
        this.nodeTable = nodeTable;
    }

    public String submit() {
        Task<String> task = Task.<String>builder()
                .taskType("hello")
                .payload("CH")
                .build();
        taskManager.addTask(task, callback(task.getTaskId()));
        return task.getTaskId();
    }

    public void manage(String taskId) {
        boolean cancelled = taskManager.cancel(taskId);
        dispatcherProvider.cancel(taskId);      // 通知派发器同步取消
        boolean paused = taskManager.pause(taskId);
        dispatcherProvider.pause(taskId);
        boolean resumed = taskManager.resume(taskId);
        dispatcherProvider.resume(taskId);
        taskManager.getStatus(taskId);          // TaskStatus
        taskManager.getResult(taskId);          // TaskResult
        nodeTable.unregister("node-01");        // 注销节点
    }
}
```

管理接口一览（均带 `@Permission` 权限声明）：

```text
GET    /v2/taskdistribution/task/list              # 任务清单
GET    /v2/taskdistribution/task/{taskId}          # 单个任务详情（task/status/result）
POST   /v2/taskdistribution/task/{taskId}/cancel   # 取消任务
POST   /v2/taskdistribution/task/{taskId}/pause    # 暂停任务
POST   /v2/taskdistribution/task/{taskId}/resume   # 恢复任务
DELETE /v2/taskdistribution/task/{taskId}          # 删除任务记录（从 ResultBuffer 移除）
GET    /v2/taskdistribution/node/list              # 节点清单（含离线节点）
DELETE /v2/taskdistribution/node/{nodeId}          # 注销节点
```

### 5.2 SPI 扩展示例

**本模块不提供 `META-INF/extensions` 形式的 SPI 装配**；扩展点是「自定义 `TaskExecutor` 实现」：

```java
/**
 * Spring 容器内的 TaskExecutor 实现会被自动注册到 TaskExecutorRegistry，
 * 按 taskType() 精确路由任务。
 */
@Component
public class ReportExecutor implements TaskExecutor<Map<String, Object>> {

    @Override
    public String taskType() {
        return "report";
    }

    @Override
    public TaskResult<Map<String, Object>> execute(Task<Map<String, Object>> task) {
        Map<String, Object> payload = task.getPayload();
        // ... 业务执行
        return TaskResult.success(task.getTaskId(), Map.of("rows", 128));
    }
}
```

- 需要替换「存储」时：自行实现 `TaskStore` 并声明 Bean（`@ConditionalOnMissingBean` 生效，自动配置不再创建默认存储），例如适配 Redis / Elasticsearch；
- 需要替换「派发」时：实现 `DispatcherProvider` 并以 Bean 覆盖（`@ConditionalOnMissingBean`），如接入分布式队列；
- 需要替换「节点注册表/结果缓冲」时同理覆盖 `NodeTable` / `ResultBuffer` Bean。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
|:-----|:-----|:-----|
| `spring-support-common-starter` | 本模块依赖 | `ServiceInfoContributor`、`@Permission` 注解、TaskDistributionFramework 内核的传递引入 |
| `spring-support-mybatis-starter` | optional 依赖 | `store-type=db` 时的 MyBatis-Plus 运行环境 |
| `spring-support-swagger-starter` | provided 依赖 | 接口文档注解 |
| `spring-support-hotsport-starter` | 独立模块，无依赖 | APM 观测；若两者同装，任务执行链路可被其采集到（字节码/日志层） |
| `spring-support-osgi-starter` | 独立模块，无依赖 | OSGi 插件框架；任务执行器可作为扩展点放入 osgi 源码目录由框架侧加载 |
| `spring-support-device-starter` | 独立模块，无依赖 | 设备接入；不参与任务分发 |

注意：`taskdistribution.server.*` 目前是「仅声明」配置，不要依据 CONFIG.md 的印象以为引入依赖即会在 19390 端口监听；`server` 相关的散射-汇聚/工作端路由在当前自动配置中**没有装配**（见「七、已知限制」）。

## 七、已知限制

- **`server.*` 四键与 `task.*` 两键未接入**：`server.enabled` 在自动配置中无任何 `@ConditionalOnProperty` 消费，`server.port` / `server.host` / `server.protocol`、`task.timeout-ms` / `task.max-retries` 均只有字段与默认值，运行期不被读取——不会监听 19390 端口，也不需要集群协商；超时/重试只能靠 `Task.builder().timeoutMs(...)` / `.maxRetries(...)` 逐任务显式指定。
- **`strategy` 仅用于展示**：`plugin.taskdistribution.strategy` 只出现在初始化日志与服务信息登记里，`TaskDistributionFramework` 内核中的散射-汇聚路由（经 `NodeTable` 多节点）在该 starter 的自动配置中未装配，纯 `InMemoryDispatcherProvider` 不消费策略。
- **`store-type=db` 的依赖前提**：`spring-support-mybatis-starter` 是 optional；未引入且未扫描 `com.chua.starter.taskdistribution.support.mapper` 时，`TaskStoreMapper` 注入为 null，自动配置打 WARN 并回退到内存存储（`DbTaskStore` 的 upsert 用 `taskId.intern()` 同步，高并发写入存在 String-intern 锁竞争）。
- **内存/文件存储重启丢任务**：`InMemoryTaskStore` 重启即失；文件/数据库存储仅恢复 `PENDING` / `RUNNING` 状态任务（`getRecoverableTasks` 按状态过滤）。
- **数据库存储不是强一致**：`DbTaskStore` 以 `task_json` / `result_json` 的 TEXT 列存 JSON（与 `SysTaskStore` 表结构一致），`task_json` 为 TEXT（≈64KB），大 payload 需自行改 `LONGTEXT`；`upsert` 是「先 insert 失败再 updateById」，异常只打日志不抛出。
- **管理接口依赖 Web 环境**：`TaskDistributionController` 在非 Web 应用（无 spring-webmvc）下不会被扫描装配；接口带 `@Permission`，需要 common-starter 鉴权体系配合，且 Swagger 注解需要 `spring-support-swagger-starter`（provided）才生效。
- **重试/超时内核行为由 utils 层决定**：starter 只把 `batch-size` / `consumer-threads` 传给 `InMemoryDispatcherProvider`（容量取 `DEFAULT_CAPACITY`），超时巡检、重试间隔等由 `TaskManager` / 内核实现控制，本文档不展开。  
- **建表脚本在资源目录**：`src/main/resources/db/init/` 提供 `V2.0__init_all_taskdistribution.sql`（建表）与空种子脚本，是否自动执行取决于宿主的 Flyway/Liquibase 装配，starter 不负责建表。