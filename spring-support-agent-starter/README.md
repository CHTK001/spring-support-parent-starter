# spring-support-agent-starter

引入依赖即向**当前 JVM** 自注入 Runtime Agent（字节码插桩 + APM 采集）。
零启动参数、零代码侵入：靠 Spring Boot 自动配置，在全部单例初始化完成后调用 `RuntimeBoot#attachSelf()`。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
| --- | --- | --- |
| 自动装配 | `AgentAutoConfiguration` | `AgentAutoConfiguration#agentAttachService(AgentAttachProperties)` |
| 配置属性 | `AgentAttachProperties` | `@ConfigurationProperties(prefix = "plugin.agent")` |
| 启动后自注入 | `AgentAttachService` | `AgentAttachService#afterSingletonsInstantiated()`（`SmartInitializingSingleton`） |
| 异步注入（虚拟线程） | `AgentAttachService` | `Thread.ofVirtual().name("agent-attach")` |
| 幂等注入 | `AgentAttachService` | `AgentAttachService#attach()`（`AtomicBoolean attachAttempted`） |
| 失败后重试 / 运行时重注入 | `AgentAttachService` | `AgentAttachService#reattach()` |
| 运行时启停采集 | `AgentAttachService` | `AgentAttachService#setAgentEnabled(boolean)` |
| 注入状态查询 | `AgentAttachService` | `AgentAttachService#isAgentActive()`、`#getStatus()` |
| agent jar 定位（classpath / fat jar） | `AgentAttachService` | `resolveAgentJar()`（私有静态） |

自动配置入口：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` →
`com.chua.starter.agent.support.config.AgentAutoConfiguration`。

装配条件：

- `@ConditionalOnProperty(prefix = "plugin.agent", name = "enabled", havingValue = "true", matchIfMissing = true)`；
- `@ConditionalOnMissingBean`：宿主自带 `AgentAttachService` Bean 时自动配置退避。

启动时序（一次正常注入）：

| 步骤 | 发生位置 | 行为 |
| --- | --- | --- |
| 1 | `AgentAutoConfiguration` | 注册 `AgentAttachService`（持有 `AgentAttachProperties`） |
| 2 | 容器刷新末尾 | Spring 回调 `afterSingletonsInstantiated()` |
| 3 | `AgentAttachService` | `plugin.agent.enabled=false` 则打日志直接返回 |
| 4 | `AgentAttachService` | `async=true`（默认）在虚拟线程里跑，`false` 则同步跑 |
| 5 | `doAttach()` | `RuntimeBoot.create().withAgent(jar).withAgentOptions(options).attachSelf()` |

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-agent-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 版本 | 作用 |
| --- | --- | --- |
| `org.springframework.boot:spring-boot-starter` | 由父 POM 管理 | 自动配置与容器生命周期 |
| `com.chua:utils-support-runtime-starter` | `${utils.version}` = `4.0.0.50` | `com.chua.runtime.starter.RuntimeBoot`（`create()` / `withAgent(Path)` / `withAgentOptions(String)` / `attachSelf()`） |
| `com.chua:utils-support-runtime-agent` | `${utils.version}` = `4.0.0.50` | 被 attach 的 agent jar 本体 |
| `org.projectlombok:lombok` | 由父 POM 管理（`provided`） | `@Slf4j` |
| `org.springframework.boot:spring-boot-starter-test` | 由父 POM 管理（`test`） | `ApplicationContextRunner` 契约测试 |

父 POM：`com.chua:spring-support-parent-starter:4.0.0.51`，`java.version = 25`
（虚拟线程 `Thread.ofVirtual()` 需要 JDK 21+）。

## 三、配置项

前缀 `plugin.agent`，绑定类 `com.chua.starter.agent.support.config.AgentAttachProperties`。

| 配置 key | 默认值 | 说明 |
| --- | --- | --- |
| `plugin.agent.enabled` | `true` | 是否装配并自注入；`false` 时 `AgentAutoConfiguration` 整体不装配 |
| `plugin.agent.options` | `""`（空串） | 传给 agent 的参数，如 `port=4567`。为空时由 `plugin.agent.shell-port` 拼成 `port=<shellPort>` |
| `plugin.agent.shell-port` | `4567` | `options` 为空时使用的默认端口 |
| `plugin.agent.async` | `true` | `true`：虚拟线程异步注入，不阻塞启动；`false`：在 `afterSingletonsInstantiated` 中同步注入 |

```yaml
plugin:
  agent:
    enabled: true
    async: true
    shell-port: 4567
    # options: port=4567
```

`options` 与 `shell-port` 的关系是「有则用之，无则拼之」：

```java
String options = properties.getOptions();
if (!StringUtils.hasText(options)) {
    options = "port=" + properties.getShellPort();
}
```

## 四、快速开始

**第一步：引入依赖**（见第二节）。不需要 `-javaagent` 启动参数，也不需要写任何代码。

**第二步（可选）：调整配置**。默认端口 4567；要改端口用 `plugin.agent.shell-port`，
要传更复杂的参数用 `plugin.agent.options`。想彻底关闭采集用 `plugin.agent.enabled=false`。

**第三步：启动并确认日志**。注入成功会打印：

```text
[AgentAttach] Agent 自注入完成，options=port=4567
```

失败不会中断启动，只会有一条 `warn`：

```text
[AgentAttach] Agent 自注入失败（运行时观测将不可用）: <原因>
```

## 五、运行示例

### 5.1 调用示例

注入服务读状态（**本模块无链式 API**；链式在 `RuntimeBoot` 上，见本小节末尾）：

```java
@RestController
public class AgentStatusController {

    @Resource
    private AgentAttachService agentAttachService;   // 由 AgentAutoConfiguration 注册

    @GetMapping("/ops/agent/status")
    public Map<String, Object> status() {
        return agentAttachService.getStatus();       // 状态快照，键见下表
    }

    @PostMapping("/ops/agent/reattach")
    public boolean reattach() {
        agentAttachService.reattach();               // 已注入则跳过；失败过则重试
        return agentAttachService.isAgentActive();
    }

    @PostMapping("/ops/agent/enabled")
    public boolean switchEnabled(@RequestParam boolean enabled) {
        agentAttachService.setAgentEnabled(enabled); // false: 仅停止后续注入与状态上报
        return agentAttachService.isAgentEnabled();
    }
}
```

`getStatus()` 返回的键（`LinkedHashMap`，顺序固定）：

| key | 类型 | 含义 |
| --- | --- | --- |
| `agentActive` | `boolean` | 注入是否成功 |
| `agentEnabled` | `boolean` | 运行时是否允许采集（`setAgentEnabled` 可改） |
| `version` | `String` | 硬编码 `"4.0.0.42"`（见第七节第 5 条） |
| `startedAt` | `Long` / `null` | 最近一次注入尝试的 epoch 毫秒；从未尝试为 `null` |
| `lastHeartbeat` | `long` | 取当前时间，不是 agent 真实心跳 |
| `heartbeatLostMs` | `long` | 恒为 `0` |
| `attachAttempts` | `int` | 注入尝试次数 |
| `lastAttachError` | `String` / `null` | 最近一次失败信息，无失败为 `null` |

`AgentAttachService` 的可用方法：`attach()`、`reattach()`、`isAgentActive()`、`isEnabled()`、
`getShellPort()`、`isAgentEnabled()`、`setAgentEnabled(boolean)`、`getStatus()`、
`afterSingletonsInstantiated()`（容器回调，一般不主动调用）。

手工走同一条链（**这是 `utils-support-runtime-starter` 的链式 API，不是本模块的**）：

```java
RuntimeBoot.create()
        .withAgent(Path.of("D:/plugins/utils-support-runtime-agent-4.0.0.50.jar"))
        .withAgentOptions("port=4567")
        .attachSelf();
```

三个 `withXxx` 都 `return this`，最后 `attachSelf()` 执行注入。本模块内部就是这条链，
只是 `withAgent(...)` 的 jar 由 `resolveAgentJar()` 自动探测，探测不到时传 `null` 交给
`RuntimeBoot` 内建兜底（`resolveSelfAgentPath()`）。

`resolveAgentJar()` 的查找顺序：

1. 遍历 `java.class.path` 的每一项，逐个当 jar 打开；
2. 在 jar 的条目里找 `BOOT-INF/lib/` 下文件名包含 `utils-support-runtime-agent` 且以 `.jar` 结尾的条目；
3. 找到就把该条目解压到 `Files.createTempFile("agent-attach-", ".jar")` 并返回其路径；
4. 全部未命中返回 `null`，由 `RuntimeBoot` 自行探测（LaunchedClassLoader 不回填 `java.class.path` 的场景）。

### 5.2 SPI 扩展示例

**本模块不提供 SPI 扩展点**。模块里没有 `META-INF/services` 文件，也没有自定义扩展接口。

可用的扩展方式是 Spring 层的 Bean 覆盖（`@ConditionalOnMissingBean` 让自动配置让位），
典型用途是把端口来源换成配置中心、或换成带鉴权的自建服务：

```java
@Configuration
public class MyAgentConfig {

    @Bean
    public AgentAttachService agentAttachService(AgentAttachProperties properties) {
        // 自定义子类/包装：改注入时机、接监控告警
        return new AgentAttachService(properties);
    }
}
```

已有契约测试覆盖这三种装配场景（`AgentAutoConfigurationTest`）：默认装配、
`plugin.agent.enabled=false` 时不装配、宿主自定义 Bean 时退避。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
| --- | --- | --- |
| `utils-support-runtime-starter` | 本模块依赖它 | `RuntimeBoot` 提供 `create()` / `withAgent(Path)` / `withAgentOptions(String)` / `attachSelf()` |
| `utils-support-runtime-agent` | 本模块依赖它 | 被注入的 agent jar（classpath / fat jar 内定位后 attach） |
| `spring-support-hotsport-starter` | 它依赖本模块 | 通过本模块自动完成 `RuntimeBoot.attachSelf`，自己只引入 `spring-support-agent-starter` 而不重复引导 |
| `utils-support-runtime-apm` / `utils-support-runtime-protocol` | 间接相关 | 由 `spring-support-hotsport-starter` 侧使用（APM 采集与协议对象），本模块不直接依赖 |

方向小结：本模块是 runtime 体系的**引导层**（把 agent 塞进当前 JVM），
观测数据的存储与查询在 `spring-support-hotsport-starter` 与 `utils-support-runtime-apm` 侧。

## 七、已知限制

1. **只作用于当前 JVM**。`attachSelf()` 的语义是自注入，没有远程注入能力，
   也没有多进程/容器编排级的批量注入。
2. **依赖 JDK Attach API**。attach 需要能加载 `jdk.attach`、能定位到 agent jar；
   受限环境（裁剪 JRE、强烈限制的容器、`-XX:+DisableAttachMechanism`）下会失败。
3. **失败只告警不阻断**。`doAttach()` 捕获的是 `Throwable`，仅 `log.warn` 并记录 `lastAttachError`，
   应用照常启动——好处是可用性，代价是「注入没成功」不会让部署失败。
4. **`attach()` 只尝试一次**。`attachAttempted` 是 `AtomicBoolean.compareAndSet`，
   第一次失败后必须显式调 `reattach()` 才会再试；`reattach()` 只在 `agentActive` 为 `false` 时才真正执行。
5. **状态快照有两个已知偏差**。`getStatus()` 里 `version` 硬编码为 `"4.0.0.42"`（与当前 `4.0.0.51` 的
   POM 版本不一致）；`lastHeartbeat` 直接取 `System.currentTimeMillis()`、`heartbeatLostMs` 恒为 `0`，
   都不是真实心跳，不要用它判断 agent 存活。
6. **停用不会卸载插桩**。`setAgentEnabled(false)` 只关闭后续注入与状态上报，
   已经注入的 `Instrumentation` 实例不会被强制卸载；再打开时走 `reattach()`。
7. **`options` 无法为空**。`options` 为空白时一定会被替换成 `port=<shellPort>`，
   没法传「完全无参数」的 attach。
8. **没有配置元数据**。POM 未引入 `spring-boot-configuration-processor`，
   IDE 里没有 `plugin.agent.*` 的自动补全与提示，需要查本文件或看 `AgentAttachProperties` 源码。
9. **异步注入的可见性**。默认 `async=true` 时注入发生在虚拟线程里，
   启动日志出现「自注入完成」之前，`isAgentActive()` 可能仍是 `false`；
   依赖 agent 的探活/指标端点应做「未就绪」处理而不是直接判定故障。
