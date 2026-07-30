# Spring Support Job Starter

`spring-support-job-starter` 是任务执行模块，不是调度中心模块。

它只负责两类能力：

1. 单机模式：当前服务自己轮询任务表并执行本地 `@Job`
2. 中心模式客户端：当前服务作为执行器，接收调度中心下发并执行本地 `@Job`

如果你要做“统一管理任务、统一轮询、统一下发到多个客户端”，对应模块是 `spring-api-support-scheduler-starter`。

## 模块边界

| 场景 | 使用模块 | 谁轮询任务表 | 谁执行任务 |
| --- | --- | --- | --- |
| 单机模式 | `spring-support-job-starter` | 当前服务 | 当前服务 |
| 中心模式客户端 | `spring-support-job-starter` | 调度中心 | 当前服务 |
| 中心模式调度中心 | `spring-api-support-scheduler-starter` | 调度中心 | 各业务客户端 |

重点说明：

- `STATIC` / `DISCOVERY` 是调度中心配置，不是客户端配置
- 客户端只需要决定是否开启 `plugin.job.remote-executor.enabled`
- 任务级 `job_remote_executor_address` 优先级最高，配置后直接按任务地址下发

## Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-job-starter</artifactId>
    <version>${version}</version>
</dependency>
```

## 一、单机模式

### 适用场景

- 任务就在当前服务里
- 当前服务自己轮询、自己执行
- 不需要独立调度中心

### 完整配置

```yaml
plugin:
  job:
    enable: true
    config-table-enabled: true           # 当前服务自己轮询任务表
    table-init-mode: UPDATE              # 自动补齐任务表结构
    pool-size: 10
    job-annotation-sync-mode: UPDATE     # 启动时把 @Job 同步到任务表
    scheduled-annotation-sync-mode: NONE
    auto-backup-cron: "0 0 3 * * ?"
    remote-executor:
      enabled: false                     # 单机模式不对外暴露远程执行入口
      access-token: job-secret
      dispatch-path: /v1/job-executor/dispatch
    table:
      prefix: payment                    # 最终表名如 payment_sys_job
```

### 配置说明

- `config-table-enabled=true`
  - 当前服务负责扫表、抢占、执行
- `job-annotation-sync-mode=UPDATE`
  - 启动时把本地 `@Job` 同步到任务表
- `remote-executor.enabled=false`
  - 当前服务不是中心模式执行器

### 单机模式支持的动态能力

- 支持运行中修改：
  - cron
  - 默认执行参数
  - 启停状态
- 支持运行中重新加载：
  - `GLUE`
  - `SCRIPT`
- 普通 `BEAN` 方法代码改动仍然需要重新发布

## 二、中心模式

中心模式分成两个角色。

### 1. 调度中心

模块：

- `spring-api-support-scheduler-starter`

职责：

- 统一管理任务
- 统一轮询命名空间任务表
- 统一选择执行器
- 统一下发到各业务客户端

### 2. 执行器客户端

模块：

- 业务服务 + `spring-support-job-starter`

职责：

- 注册本地 `@Job`
- 暴露远程执行入口
- 接收中心下发
- 执行本地 handler

### 2.1 客户端完整配置

下面这段是业务服务配置，不是调度中心配置。

```yaml
plugin:
  job:
    enable: true
    config-table-enabled: false          # 客户端不能自己扫表
    table-init-mode: UPDATE
    pool-size: 10
    job-annotation-sync-mode: NONE
    scheduled-annotation-sync-mode: NONE
    auto-backup-cron: "0 0 3 * * ?"
    remote-executor:
      enabled: true                      # 开启远程执行器入口
      access-token: job-secret           # 与调度中心保持一致
      dispatch-path: /v1/job-executor/dispatch
    table:
      prefix: payment
```

客户端配置说明：

- `config-table-enabled=false`
  - 很关键，客户端不能和调度中心同时轮询同一批任务
- `remote-executor.enabled=true`
  - 表示当前服务作为远程执行器，对外提供下发入口
- 当前远程下发只支持 `BEAN` 类型任务

### 2.2 调度中心 STATIC 模式配置

下面这段属于 `spring-api-support-scheduler-starter`。

```yaml
plugin:
  job:
    enable: true
    config-table-enabled: true
    table-init-mode: UPDATE

  scheduler:
    job-platform:
      enabled: true
      remote-dispatch-enabled: true
      dispatch-interval-ms: 1000
      dispatch-batch-size: 200
      namespaces:
        payment:
          prefix: payment
          executor-mode: STATIC
          executors:
            - name: payment-node-1
              base-url: http://127.0.0.1:18081/payment/api
              access-token: job-secret
              dispatch-path: /v1/job-executor/dispatch
              connect-timeout-ms: 3000
              read-timeout-ms: 10000
```

中文说明：

- `executor-mode=STATIC`
  - 调度中心直接使用静态地址列表
- `executors`
  - 调度中心维护的执行器地址清单
- `base-url`
  - 执行器服务基础地址
- `dispatch-path`
  - 中心调用执行器的下发接口

### 2.3 调度中心 DISCOVERY 模式配置

下面这段也属于 `spring-api-support-scheduler-starter`。

仓库内当前真实运行验证用的是 `multicast`，因为它不依赖额外注册中心进程，适合本地联调。

```yaml
plugin:
  discovery:
    enable: true
    properties:
      - protocol: multicast
        address: 230.0.0.1:18888

  job:
    enable: true
    config-table-enabled: true
    table-init-mode: UPDATE

  scheduler:
    job-platform:
      enabled: true
      remote-dispatch-enabled: true
      dispatch-interval-ms: 1000
      dispatch-batch-size: 200
      namespaces:
        payment:
          prefix: payment
          executor-mode: DISCOVERY
          discovery:
            enabled: true
            protocol: multicast
            namespace: /job-executor/payment
            base-path: /payment/api
            access-token: job-secret
            dispatch-path: /v1/job-executor/dispatch
            connect-timeout-ms: 3000
            read-timeout-ms: 10000
```

中文说明：

- `executor-mode=DISCOVERY`
  - 调度中心通过执行器来源 SPI 去发现客户端
- `plugin.discovery`
  - 是调度中心自己接入的发现组件，用来查询执行器节点
- `discovery.namespace`
  - 客户端注册命名空间
- `discovery.base-path`
  - 客户端上下文路径
- `discovery.access-token`
  - 调度中心调用客户端时附带的令牌

### 2.4 客户端注册中心配置示例

下面这段是业务客户端使用 `spring-support-discovery-starter` 自动注册的配置。

#### 本地联调示例：multicast

```yaml
plugin:
  discovery:
    enable: true
    properties:
      - protocol: multicast
        address: 230.0.0.1:18888
        node:
          - server-id: ${spring.application.name}
            namespace: /job-executor/payment
            protocol: http
```

#### 生产注册中心示例：nacos

```yaml
plugin:
  discovery:
    enable: true
    properties:
      - protocol: nacos
        address: http://127.0.0.1:8848
        username: nacos
        password: nacos
        node:
          - server-id: ${spring.application.name}
            namespace: /job-executor/payment
            protocol: http
```

注册说明：

- 客户端启动后会把当前实例注册到 `/job-executor/payment`
- 调度中心查询到节点后，会按 `host + port + base-path + dispatch-path` 组合执行器地址
- 本仓库已经真实验证 `multicast` 联调
- 如果线上要多机房、多节点稳定运行，建议换成 nacos / consul / zookeeper 这类共享注册中心

## 三、中心模式任务要求

中心下发到客户端时，任务至少满足：

- `job_dispatch_mode=REMOTE`
- `job_execute_bean` 有值
- 客户端存在同名 `@Job`
- `job_glue_type` 为空或 `BEAN`
- `job_trigger_status=1`
- `job_trigger_next_time` 已计算

## 四、任务定义示例

```java
@Component
public class DemoJobHandler {

    @Job("demoJob")
    public void execute() {
        System.out.println("execute");
    }
}
```

## 五、执行器来源 SPI

`executor-mode` 不是 `if/switch` 固定分支，而是通过 `utils-common` 的 `ServiceProvider` 加载 `SchedulerExecutorResolver` SPI 实现。

当前内置实现：

- `STATIC`
- `DISCOVERY`

当前行为：

- 默认模式是 `DISCOVERY`
- `DISCOVERY` 和 `STATIC` 都是独立 SPI
- 缺少某个 SPI 对应依赖时，调度中心可以启动，只是该命名空间暂时解析不到执行器
- `DISCOVERY` 要做真实跨进程联调时，必须接入真实共享注册中心；单进程默认 `DefaultServiceDiscovery` 只能做本进程内发现，不能替代 nacos / consul / zookeeper 这类注册中心

扩展方式：

1. 实现 `com.chua.starter.scheduler.support.executor.SchedulerExecutorResolver`
2. 在实现类上标 `@Spi("你的模式名")`
3. 在 `META-INF/services/com.chua.starter.scheduler.support.executor.SchedulerExecutorResolver` 注册实现类

示例：

```java
@Spi({"CUSTOM", "custom"})
public class CustomSchedulerExecutorResolver implements SchedulerExecutorResolver {

    @Override
    public List<SchedulerJobPlatformProperties.Executor> resolve(SchedulerExecutorResolveContext context) {
        return List.of();
    }
}
```

## 六、真实运行入口

如果要复现仓库里的真实运行链路，可以直接看测试应用和配置：

- 单机模式应用：`src/test/java/com/chua/starter/job/support/demo/JobStarterRealRunApplication.java`
- 单机模式配置：`src/test/resources/application-real-local.yml`
- 中心模式执行器配置：`src/test/resources/application-real-executor.yml`
- 中心模式调度中心应用：`spring-api-support-scheduler-starter/src/test/java/com/chua/starter/scheduler/demo/SchedulerRealRunApplication.java`
- 中心模式调度中心配置：`spring-api-support-scheduler-starter/src/test/resources/application-real-static.yml`
- 中心模式 discovery 执行器配置：`src/test/resources/application-real-discovery-executor.yml`
- 中心模式 discovery 调度中心配置：`spring-api-support-scheduler-starter/src/test/resources/application-real-discovery.yml`

真实运行结果见同目录下 `调度单元测试报告.md`。

当前已真实验证：

- 单机模式
- 中心模式 `STATIC`
- 中心模式 `DISCOVERY`（基于 `multicast`）

当前未在本仓库内置样例中真实验证：

- `DISCOVERY + nacos`
- `DISCOVERY + consul`
- `DISCOVERY + zookeeper`
