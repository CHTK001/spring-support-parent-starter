# Spring Support Parent Starter

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.6%2B-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

`com.chua` 的 Spring Boot starter 集合：57 个模块，覆盖 Web 公共层、数据源、缓存、队列、实时通信、文件、设备接入、AI、APM、隧道与云服务。

这份 README 只回答三个问题：

1. 依赖怎么加、自动装配怎么发生、开关在哪里；
2. 每个模块对外暴露哪些 Bean、注入后能做什么；
3. 每个 `@ConfigurationProperties` 有哪些字段、字段是什么类型、默认值是什么。

REST 接口清单、表结构、验收记录不在这里，见各模块自己的 `README.md`。

> 版本：`4.0.0.51`；父 POM 继承 `spring-boot-starter-parent:4.1.0`，编译级别 Java 25。

**目录**

- [一、模块全景](#一模块全景)
- [二、依赖与装配](#二依赖与装配)
  - [2.1 依赖坐标](#21-依赖坐标) · [2.2 自动装配](#22-自动装配发生在哪里) · [2.3 开关](#23-开关enable-与-enabled-并存) · [2.4 条件与覆盖](#24-条件装配与覆盖) · [2.5 绑定规则](#25-配置绑定规则) · [2.6 装配顺序](#26-装配顺序) · [2.7 排查](#27-排查装配问题)
- [三、怎么读模块章节](#三怎么读下面的模块章节)
- [四、模块详解](#四模块详解)
- [五、配置前缀索引](#五配置前缀索引)
- [六、常见问题](#六常见问题)
- [附录 A：文件在线预览](#附录-a文件在线预览能力矩阵) · [附录 B：GraalVM](#附录-bgraalvm-native-image-支持)

## 一、模块全景

| 分类 | 模块 | 定位 | 最常注入的 Bean / 入口 |
| --- | --- | --- | --- |
| 基础与数据 | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 统一响应/异常/参数/验证码/线程池/服务信息等 Web 基础能力 | `ApiProperties`、`AuthService`、`CaptchaProvider`、`ThreadPoolTaskExecutor` |
| 基础与数据 | [`spring-support-cache-starter`](spring-support-cache-starter/README.md) | Caffeine + Redis 多级缓存与注解增强 | `CacheManager`、`CacheTemplate`、`KeyGenerator` |
| 基础与数据 | [`spring-support-redis-starter`](spring-support-redis-starter/README.md) | Redis 客户端、分布式锁、嵌入式 Redis | `RedissonClient`、`RedisTemplate`、`LockTemplate` |
| 基础与数据 | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 动态多数据源、脚本迁移、H2/SQLite/Calcite/Sharding | `JdbcTemplate`、`TransactionTemplate`、`DataSource` |
| 基础与数据 | [`spring-support-datasource-hub-starter`](spring-support-datasource-hub-starter/README.md) | 数据源台账、探测与连接构建 | `DatasourceHubProperties`；服务组件扫描（`DatasourceServiceImpl` 等） |
| 基础与数据 | [`spring-support-mybatis-starter`](spring-support-mybatis-starter/README.md) | MyBatis-Plus 增强、分页、数据权限、慢 SQL | `MybatisPlusInterceptor`、`MybatisPlusProperties` |
| 任务与消息 | [`spring-support-job-starter`](spring-support-job-starter/README.md) | `@Job`/`@Scheduled` 调度，SCHEDULER/EXECUTOR/SINGLE 三模式 | `JobOperator`、`JobExecutor`、`JobHandlerFactory` |
| 任务与消息 | [`spring-support-queue-starter`](spring-support-queue-starter/README.md) | 队列抽象（Kafka/RabbitMQ/RocketMQ/MQTT/Local） | `MessageTemplate`（`localMessageTemplate`）、`DeadLetterTemplate` |
| 任务与消息 | [`spring-support-message-starter`](spring-support-message-starter/README.md) | 消息模板与多渠道发送 | `MessageService`（组件扫描）、`MessageTrackProperties` |
| 任务与消息 | [`spring-support-sync-starter`](spring-support-sync-starter/README.md) | `SyncProtocol` 双向同步（WebSocket/HTTP/RSocket/Socket.IO） | `SyncServer`、`SyncClient` |
| 任务与消息 | [`spring-support-taskdistribution-starter`](spring-support-taskdistribution-starter/README.md) | 任务分发（旧式 `spring.factories` 装配） | `TaskDistributionAutoConfiguration` 产出的执行器 |
| 通信与协议 | [`spring-support-socket-starter`](spring-support-socket-starter/README.md) | Socket 门面，按 `plugin.socket.protocol` 选 SSE/Socket.IO/RSocket | `SocketSessionTemplate`、`SocketLifecycle` |
| 通信与协议 | [`spring-support-socket-websocket-starter`](spring-support-socket-websocket-starter/README.md) | Spring 原生 WebSocket 端点与 P2P 房间信令 | `WebSocketSessionManager`、`ServletServerContainerFactoryBean` |
| 通信与协议 | [`spring-support-socket-io-starter`](spring-support-socket-io-starter/README.md) | Socket.IO 事件/房间/广播（走 socket 门面） | `SocketSessionTemplate`（协议 socketio） |
| 通信与协议 | [`spring-support-socket-sse-starter`](spring-support-socket-sse-starter/README.md) | SSE 事件总线与服务端推送 | `SseTemplate`、`SseEventBus` |
| 通信与协议 | [`spring-support-socket-rsocket-starter`](spring-support-socket-rsocket-starter/README.md) | RSocket 注解与会话（走 socket 门面） | `SocketSessionTemplate`（协议 rsocket） |
| 通信与协议 | [`spring-support-rpc-starter`](spring-support-rpc-starter/README.md) | RPC 服务暴露/引用（Dubbo 等 SPI） | 带 `@RpcService`/`@RpcResource` 的 Bean |
| 通信与协议 | [`spring-support-ssh-starter`](spring-support-ssh-starter/README.md) | 内嵌 SSH 服务端（命令/文件传输） | `SshServerService` |
| 文件与设备 | [`spring-support-filesystem-starter`](spring-support-filesystem-starter/README.md) | 多 Provider 对象存储 + 内嵌文件服务器 | `FileStorageTemplate`、`UploadTaskManager`、`FileServerManager` |
| 文件与设备 | [`spring-support-device-starter`](spring-support-device-starter/README.md) | 设备接入 SPI：海康/大华/宇视/GB28181/ONVIF | `DeviceProvider`、`DeviceSession`、`DeviceRealtimePublisher` |
| 文件与设备 | [`spring-support-software-starter`](spring-support-software-starter/README.md) | 软件目录/源/安装记录（需 `@Import` 生效） | `SoftwareConfiguration` 扫描出的 Service |
| 文件与设备 | [`spring-support-server-starter`](spring-support-server-starter/README.md) | 服务器资产、远程终端、发布与批量执行 | `ReleaseAiCapability`；其余服务组件扫描 |
| 文件与设备 | [`spring-support-docker-starter`](spring-support-docker-starter/README.md) | Docker 容器/镜像/网络/卷管理（经 SSH） | `com.chua.starter.docker` 包内的 Service（组件扫描） |
| 文件与设备 | [`spring-support-control-starter`](spring-support-control-starter/README.md) | WebRTC 远程控制信令与桌面采集 | `WebRtcServer` |
| 文件与设备 | [`spring-support-monitor-starter`](spring-support-monitor-starter/README.md) | 主机/应用监控台账、采集与告警 | `SeriesEngine`、`FileStorageTemplate`、`SseSocketSessionTemplate` |
| AI 与观测 | [`spring-support-ai-starter`](spring-support-ai-starter/README.md) | AI 对话/生成/协议/用量/缓存/网关安全 | `ChatClient`、`ChatClientRegistry`、`AgentRegistry`、`SysAiUsageService` |
| AI 与观测 | [`spring-support-ai-training-starter`](spring-support-ai-training-starter/README.md) | 训练数据上传、异步训练、超参数 | `TrainService`、`DataService` |
| AI 与观测 | [`spring-support-eduai-starter`](spring-support-eduai-starter/README.md) | 教育 AI：题库/组卷/批改管线 | `CacheManager`、`CredentialProvider` |
| AI 与观测 | [`spring-support-agent-starter`](spring-support-agent-starter/README.md) | 启动后向当前 JVM 自注入 Runtime Agent | `AgentAttachService` |
| AI 与观测 | [`spring-support-ueba-starter`](spring-support-ueba-starter/README.md) | UEBA 分析引擎装配 | `UebaEngine` |
| AI 与观测 | [`spring-support-datasearch-starter`](spring-support-datasearch-starter/README.md) | 天气/台风/汇率/节假日/IP 归属地服务化 | `WeatherDataService` 等五个 DataService |
| AI 与观测 | [`spring-support-apm-starter`](spring-support-apm-starter/README.md) | APM 门面：Provider 聚合 + 统一 REST/独立服务 | `ApmRegistry`、`ApmDispatcher`、`ApmRuntimeConfig` |
| AI 与观测 | [`spring-support-apm-skywalking-starter`](spring-support-apm-skywalking-starter/README.md) | SkyWalking Provider | `ApmProvider`（SPI `skywalking`） |
| AI 与观测 | [`spring-support-apm-hotspot-starter`](spring-support-apm-hotspot-starter/README.md) | 自研 hotspot Agent Provider（SPI extensions） | `ApmProvider`（SPI `hotspot`） |
| AI 与观测 | [`spring-support-hotsport-starter`](spring-support-hotsport-starter/README.md) | 热点观测台：REST + 内置前端 + Agent | `ApmQueryService`、`SlowCallFilter` |
| AI 与观测 | [`spring-support-report-client-starter`](spring-support-report-client-starter/README.md) | 设备硬件/系统信息定时上报 | `ThreadPoolTaskScheduler`、上报组件（依赖 `SyncClient`） |
| 平台与治理 | [`spring-support-discovery-starter`](spring-support-discovery-starter/README.md) | 服务注册与发现（多协议） | `DiscoveryListProperties`；发现实现由 utils SPI 提供 |
| 平台与治理 | [`spring-support-configcenter-starter`](spring-support-configcenter-starter/README.md) | 远程配置中心 + 热更新 | `ConfigCenter`、`ConfigValueBeanPostProcessor` |
| 平台与治理 | [`spring-support-console-starter`](spring-support-console-starter/README.md) | 运行期 FTL 控制台，覆盖 `@ApiFeature`/`@ApiGray` | `ConsoleSqliteStore`、`ConsoleOverrideProvider` |
| 平台与治理 | [`spring-support-osgi-starter`](spring-support-osgi-starter/README.md) | Apache Felix OSGi 运行时 | `SourceExtensionScheduler`、`GogoSshShellServer` |
| 平台与治理 | [`spring-support-bundle-starter`](spring-support-bundle-starter/README.md) | 按 Maven 坐标装卸 OSGi Bundle | `BundleManagerService` |
| 平台与治理 | [`spring-support-collect-starter`](spring-support-collect-starter/README.md) | 采集平台：流程、调度、Agent | `CollectFlowSchedulePort` 等 |
| 平台与治理 | [`spring-support-datasync-starter`](spring-support-datasync-starter/README.md) | 目录/数据库映射的数据同步 | `DataSyncServer`、`DataSyncMappingManager` |
| 平台与治理 | [`spring-support-lowcode-starter`](spring-support-lowcode-starter/README.md) | 低代码目录/表单/页面与版本发布 | `LowFormService`、`LowPageService`（组件扫描） |
| 平台与治理 | [`spring-support-account-starter`](spring-support-account-starter/README.md) | 账号、渠道、消息模板、临时邮箱 | `MessageTemplateService`、`MailboxService`（组件扫描） |
| 平台与治理 | [`spring-support-oauth-client-starter`](spring-support-oauth-client-starter/README.md) | OAuth 客户端认证与 Principal/权限 | 认证过滤器与 `AuthClientProperties` |
| 平台与治理 | [`spring-support-swagger-starter`](spring-support-swagger-starter/README.md) | OpenAPI 文档与导出 | `SpringDocConfigProperties`、Knife4j 页面 |
| 平台与治理 | [`spring-support-swagger2-starter`](spring-support-swagger2-starter/README.md) | Swagger 2.x 注解兼容 | 无独立 Bean，复用宿主 Swagger |
| 隧道与云服务 | [`spring-support-tunnel-starter`](spring-support-tunnel-starter/README.md) | 内网穿透统一入口（ngrok/cloudflared/tailscale） | `TunnelProvider`、隧道管理 Service |
| 隧道与云服务 | [`spring-support-tunnel-ngrok-starter`](spring-support-tunnel-ngrok-starter/README.md) | ngrok 隧道 Provider | `TunnelProvider`（ngrok） |
| 隧道与云服务 | [`spring-support-tunnel-cloudflared-starter`](spring-support-tunnel-cloudflared-starter/README.md) | Cloudflare Tunnel Provider | `CloudflaredTunnelProvider` |
| 隧道与云服务 | [`spring-support-tunnel-tailscale-starter`](spring-support-tunnel-tailscale-starter/README.md) | Tailscale Funnel Provider | `TunnelProvider`（tailscale） |
| 隧道与云服务 | `spring-support-cdp-starter` | Chrome DevTools Protocol 调试代理 | CDP 服务（`spring.cdp.*`） |
| 隧道与云服务 | [`spring-support-httpman-starter`](spring-support-httpman-starter/README.md) | 接口调试控制台后端 | 集合/请求/历史 Service |
| 隧道与云服务 | [`spring-support-tencent-starter`](spring-support-tencent-starter/README.md) | 腾讯云/微信小程序/短信/存储 | `TencentWechatPayClientFactory` |
| 隧道与云服务 | [`spring-support-aliyun-starter`](spring-support-aliyun-starter/README.md) | 支付宝客户端 | `AlipayClient`、`AliyunAlipayClientFactory` |
| 隧道与云服务 | [`spring-support-payment-starter`](spring-support-payment-starter/README.md) | 支付主线：订单/退款/回调/对账 | `ThreadPoolTaskScheduler`、支付 Service（组件扫描） |

## 二、依赖与装配

### 2.1 依赖坐标

没有启用 BOM 模块（父 POM 里 `spring-support-bom` 仍被注释），每个 starter 都要写版本号：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-common-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

桌面/服务端应用通常再按需加 `server-starter`、`filesystem-starter`、`datasource-starter` 等；不要为了省事把全部 starter 一次引进来——默认开关大多为关，但类路径上的自动配置仍会被加载和求值，排障成本会变高。

### 2.2 自动装配发生在哪里

54 个模块带 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件，其中 51 个共注册 104 条自动配置类；`socket-io`、`socket-rsocket`、`swagger2` 三个文件里只有注释。例如：

```text
# spring-support-redis-starter
com.chua.starter.redis.support.RedisConfiguration
com.chua.starter.redis.support.CacheConfiguration
com.chua.starter.redis.support.LockTemplateConfiguration
```

有三个例外，接入时必须知道：

| 模块 | 生效方式 |
| --- | --- |
| `spring-support-taskdistribution-starter` | 没有 `imports`，走 `META-INF/spring.factories` 的 `EnableAutoConfiguration`（老式写法） |
| `spring-support-apm-hotspot-starter` | 没有 `imports`，走 `META-INF/extensions/com.chua.starter.apm.support.spi.ApmProvider` 的 SPI 扩展文件 |
| `spring-support-software-starter` | `SoftwareConfiguration` 带 `@AutoConfiguration`，但**未登记**到 `imports`；引入依赖后需宿主 `@Import(SoftwareConfiguration.class)`，或让组件扫描覆盖 `com.chua.starter.software.support` |

仓库里另有 9 个 `spring.factories`，它们不是自动配置，只注册 `EnvironmentPostProcessor`、`AdviceResolver`、`Protocol` 等扩展点（配置中心、数据源、Swagger、OAuth 的环境预处理都靠它，早于容器启动）。

### 2.3 开关：`enable` 与 `enabled` 并存

开关没有统一成一个拼写，这是现状，接入时以模块章节里的“装配”一行为准。常用开关与默认值：

| 模块 | 开关 | 不配置时 |
| --- | --- | --- |
| redis | `plugin.redis.server.enable` | 开 |
| cache（Redis 二级） | `plugin.cache.enabled` | 关 |
| job | `plugin.job.enable` | 开 |
| queue | `plugin.queue.enable` | 关 |
| filesystem | `plugin.filesystem.enable` | 关 |
| socket | `plugin.socket.enable` | 关 |
| socket-websocket | `plugin.websocket.enable` | 关 |
| report-client | `plugin.report.client.enabled` | 关 |
| bundle | `plugin.bundle.enabled` | 关 |
| config-center | `plugin.config-center.enable` | 关 |
| oauth-client | `plugin.oauth.enable` | 开 |
| swagger | `plugin.swagger.enable` | 开 |
| tunnel | `plugin.tunnel.enable` | 开 |
| ngrok | `plugin.ngrok.enable` | 开 |
| control | `control.webrtc.enabled` | 开 |
| cdp | `spring.cdp.enabled` | 开（会启动本机 Chromium） |
| hotsport 请求采集 | `plugin.hotsport.request-record.enabled` | 开 |

规律：

- 默认开：`matchIfMissing = true`（redis、job、oauth、swagger、tunnel、control、hotsport 等）；
- 默认关：没写 `matchIfMissing`（queue、filesystem、socket、bundle、config-center 等），必须显式 `=true`；
- **条件通过 ≠ 功能启动**：`ssh`、`rpc` 等模块的配置类会在 `matchIfMissing=true` 时装配，但真正的监听/扫描还看字段本身（例如 `plugin.ssh.server.enabled` 默认 `false`，`SshServerService` 在 `afterPropertiesSet` 里才决定是否 `start()`）；
- `cdp` 的前缀是 `spring.cdp`，`httpman` 是 `httpman`，`control` 是 `control.webrtc`，都不在 `plugin.*` 下；
- 同一模块可能有多个子开关（datasource 的 H2/SQLite/Calcite/Sharding、ai 的 generate/usage/protocol），总开关关掉后子开关不再求值。

### 2.4 条件装配与覆盖

模块大量使用 `@ConditionalOnMissingBean`（主源码 220 处）。这意味着**你自己声明的同类型 Bean 会优先**，自动配置退避。典型做法：

```java
@Configuration
public class MyOverrides {

    // 只要容器里已有 CacheManager，cache-starter 就不再建默认实现
    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager();
    }
}
```

三种关闭/替换方式，优先级从高到低：

1. **关开关**：`plugin.<域>.enable=false` / `enabled=false`，最干净；
2. **排除自动配置**：`spring.autoconfigure.exclude=com.chua.starter.xxx.XxxAutoConfiguration`，适合某个类反复抢 Bean；
3. **同名覆盖**：Boot 默认禁止 Bean 覆盖，必须同时设 `spring.main.allow-bean-definition-overriding=true`，只建议在迁移期临时使用。

`@ConditionalOnClass` 决定“类路径有才装配”：例如 `spring-support-cache-starter` 的 Redis 缓存配置要求 classpath 有 spring-data-redis，`report-client` 要求容器里有 `SyncClient`。加依赖后没生效，先看这两类条件。

### 2.5 配置绑定规则

所有属性类都是标准 Spring Boot 绑定：

- **命名**：Java 字段 `openEmbedded` ↔ 配置 `open-embedded`（kebab-case），也接受 `openEmbedded`；YAML 推荐 kebab-case。
- **类型**：`Duration` 写 `30s`/`5m`；`DataSize` 写 `10MB`；枚举写大写常量（如 `plugin.job.role=SINGLE`）；`List<String>` 可用 YAML 列表或逗号分隔；`Map<String, XxxConfig>` 用 YAML 映射。
- **嵌套**：嵌套对象用点路径，如 `plugin.filesystem.storage-server.port`；嵌套类的字段在模块章节的折叠块里（HTML `details` 标签，不是 Markdown 语法）。
- **校验**：部分类带 `@Validated` / `@Min` / `@Max` / `@Pattern`；`ApiProperties`、`SocketProperties` 还带 `ignoreInvalidFields=true`，非法值会被忽略而不是启动失败。
- **未知字段**：默认忽略，不会报错；拼错前缀不会生效，也不会提示。

### 2.6 装配顺序

- 自动配置类之间用 `@AutoConfigureAfter`/`@AutoConfigureBefore` 与 `@AutoConfigureOrder` 定序（例如 SQL 监控排在最后、SQLite 引擎排在 H2 之后、SkyWalking 桥接排在 utils 的 SkyWalking 配置之后）。
- 数据源脚本用 `DataSourceScriptBeanPostProcessor`，保证建表脚本先于 `SqlSessionFactory`/`EntityManagerFactory` 执行；这是“脚本通道”存在的原因。
- 配置中心、数据源、Swagger、OAuth 的环境变量在 `EnvironmentPostProcessor` 阶段就参与解析，早于 Bean 容器。

### 2.7 排查装配问题

1. 启动加 `--debug`，看 `ConditionEvaluationReport` 里哪个 `@ConditionalOnProperty`/`@ConditionalOnClass` 没通过；
2. 有 Actuator 时看 `/actuator/conditions` 与 `/actuator/configprops`；
3. `common-starter` 的 `/service-info` 会汇总各模块注册的开关与端点；
4. 确认生效方式：`imports`、`spring.factories`、SPI extensions、还是需要手动 `@Import`（software）。

## 三、怎么读下面的模块章节

每个模块先给固定几段信息，再按模块实际情况补充入口与扩展点：

- **定位**：它解决什么问题，不重复模块 README 的接口清单；
- **装配**：自动配置类、开关、类路径/Bean 条件；有内部 starter 依赖时补一行“内部依赖”；
- **公开 Bean**：只列显式 `@Bean` 方法（含返回接口类型）；
- **HTTP 入口**：模块自带的 `@RestController`/`@Controller`，给出类名、类级基础路径与用途；超过 10 个只列前 10 个并标注总数；
- **组件扫描 Service**：`@Service` 类名清单（最多列 15 个），可直接注入；未找到注册路径的会单独提示“需宿主扫描”；
- **SPI 扩展点**：`META-INF/extensions/` 下声明的扩展接口（如 `ApmProvider`、`TunnelProvider`）；
- **配置**：`@ConfigurationProperties` 的字段、Java 类型、默认值；嵌套对象折叠在 HTML `details` 块里；
- **通用场景**：什么时候该用它，以及接入时的注意事项。

`@Service`/`@Component` 不属于显式 `@Bean`，所以单列；需要完整方法签名时看模块源码或模块 README。

Bean 的类型按源码原样写（含泛型）；默认值写的是字段初始化表达式，`—` 表示没有初始化或由代码在运行期计算。

注入就是普通 Spring 用法，不需要 `@Enable*`：

```java
@Service
@RequiredArgsConstructor
public class DemoUseCase {

    private final FileStorageTemplate storage;   // filesystem-starter
    private final JobOperator jobOperator;       // job-starter

    public void run() {
        // 具体方法见各自模块 README
    }
}
```

```yaml
plugin:
  filesystem:
    enable: true          # 默认关，要显式开
  job:
    enable: true
    role: SINGLE          # 单机；集群改成 SCHEDULER/EXECUTOR
```

## 四、模块详解

### spring-support-common-starter

**定位**：Spring Web 应用的公共底座：统一响应/异常、参数校验、验证码、异步线程池、请求日志、Actuator 保护、服务信息汇总。

**装配**：19 个自动配置类，按域拆开关：`plugin.api.*`、`plugin.captcha.*`、`plugin.async.enable`（默认 true）、`plugin.log.enable`（默认 true）、`plugin.actuator.enable`（条件 matchIfMissing=true，默认装配）、`plugin.service-info.enable`（默认 true）。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `actuatorAuthenticationFilterRegistration` | `FilterRegistrationBean<ActuatorAuthenticationFilter>` | Actuator 认证过滤器 对 /actuator/* 端点进行 基础 认证 认证，防止未授权访问。 支持 IP 白名单和路径排除。 |
| `responseEncodeResponseBodyAdvice` | `ApiResponseEncodeResponseBodyAdvice` | 响应编码处理 |
| `apiResponseEncodeRegister` | `ApiResponseEncodeRegister` | 响应编码注册器 |
| `apiRequestDecodeBodyAdvice` | `ApiRequestDecodeBodyAdvice` | 请求解码处理 |
| `apiRequestDecodeRegister` | `ApiRequestDecodeRegister` | 请求解码注册器 |
| `webMvcRegistrations` | `WebMvcRegistrations` | 版本路由映射：@ApiVersion 同路径多版本路由（查询参数 api版本 / 请求头 X-Api-版本）。 经 WebMvcRegistrations#getRequestMappingHandlerMapping() 交由 Boot 4.x 的 DelegatingWebMvcConfiguration 装配——Boot 4 的默认映射为继承 Bean， 竞争式 @Bean 无法令其退避（… |
| `uniformResponseBodyAdvice` | `ApiUniformResponseBodyAdvice` | 创建统一响应体建议实例。 当容器中没有提供uniform响应主体advice实例且plugin.参数.enable为true时，使用此实现。 |
| `executor` | `ExecutorService` | 创建执行器服务实例。 用于处理异步任务的线程池。 |
| `apiExceptionAdvice` | `ApiExceptionAdvice` | 统一异常处理 |
| `apiServiceInfoContributor` | `ServiceInfoContributor` | API 控制的 服务信息 贡献者: 注册 @apixxx 系列开关。 原先在 WebMvcRegistrations#getRequestMappingHandlerMapping() 中触发, 重构为 @Bean WebMvcRegistrations 后丢失, 这里改为标准贡献者方式补齐, 保证 plugin.api.* 相关开关始终出现在服务信息横幅中。 |
| `apiVersionFilterRegistration` | `FilterRegistrationBean<ApiVersionFilter>` | API 版本过滤器 在每个响应头中添加当前 API 版本信息，便于客户端识别服务版本。 |
| `nonceXhrFilterRegistration` | `FilterRegistrationBean<NonceXhrFilter>` | XHR Nonce 防重放过滤器 仅对携带 x-nonce、x-sign、x-timestamp、x-req-fingerprint 的请求做校验， 校验失败返回 403。 |
| `apiSecurityHeaderFilterRegistrationBean` | `FilterRegistrationBean<ApiSecurityHeaderFilter>` | 安全响应头过滤器注册 与 CORS 过滤器同一生命周期开关：关掉跨域同时也关掉安全头 （两者都由 plugin.api.cors.enable 控制，避免出现 「CORS 关了但安全头还在」这种难以排查的半开状态）。 独立配置项 plugin.api.security.header.enable 可单独关闭过滤器， 但它只在 cors.enable 为 true 时才被创建。 |
| `corsFilterFilterRegistrationBean` | `FilterRegistrationBean<CorsFilter>` | 跨域过滤器 |
| `apiFeatureManager` | `ApiFeatureManager` | 创建 ApiFeatureManager Bean，统一管理所有 @ApiFeature 功能开关。 |
| `apiControlInterceptor` | `ApiControlInterceptor` | 创建 ApiControlInterceptor Bean，用于拦截未启用的功能开关。 |
| `permissionControlInterceptor` | `PermissionControlInterceptor` | 创建权限拦截器 |
| `sharedThreadPoolTaskExecutor` | `ThreadPoolTaskExecutor` | 创建共享线程池（默认_执行器2 和 默认_任务_执行器 均指向同一实例，保持向后兼容） |
| `asyncExecutor` | `ThreadPoolTaskExecutor` | `@Async` 默认使用的线程池。 |
| `taskDecorator` | `TaskDecorator` | 把 MDC/链路上下文透传到异步线程的装饰器。 |
| `asyncThreadPoolServiceInfoContributor` | `ServiceInfoContributor` | 异步线程池的 服务信息 贡献者。 |
| `captchaProvider` | `CaptchaProvider` | 验证码生成与校验的统一入口。 |
| `authService` | `AuthService / IptablesService` | 鉴权服务（Iptables 实现见下一个同名 Bean）。 |
| `nonceSignInitializer` | `Object` | 启动时初始化 Nonce 签名配置。 |
| `restTemplate` | `RestTemplate` | 通用 `RestTemplate`，带统一消息转换器。 |
| `watchPointcutAdvisor` | `WatchPointcutAdvisor` | `@Watch` 注解的 AOP 通知。 |
| `captchaSetting` | `CaptchaSetting` | 外部打码服务的运行期设置。 |
| `yesCaptchaParser` | `CaptchaParser` | yescaptcha 打码实现。 |
| `captchaRunParser` | `CaptchaParser` | captcha-run 打码实现。 |
| `globalSettingFactory` | `GlobalSettingFactory` | 全局环境工厂 Bean。 |
| `mdcHandlerFilter` | `FilterRegistrationBean<MdcHandlerFilter>` | 把 MDC 里的 traceId 写回响应头并清理线程变量。 |
| `messageConverterServiceInfoContributor` | `ServiceInfoContributor` | 消息转换器的 服务信息 贡献者。 |
| `requestFingerprintFilter` | `Filter` | 创建请求指纹过滤器 |
| `parameterLogFilterRegistration` | `FilterRegistrationBean<ParameterLogFilter>` | 参数日志过滤器 记录 HTTP 请求的详细信息，包括： 请求方法和 URL 请求参数（Query/Body） 请求头（以 x- 开头的自定义头） 客户端 IP 地址 请求耗时 |
| `logServiceInfoContributor` | `ServiceInfoContributor` | 日志/MDC 模块的 服务信息 贡献者: 输出 MDC 链路追踪、参数日志过滤器等开关状态。 MDC 链路追踪由 MdcHandlerFilter 实现, 通过 com.chua.starter.common.support.configuration.MessageConverterWebMvcConfigurer 注册为全局过滤器, order 为 Integer#MIN_VALUE (过滤… |
| `serviceInfoController` | `ServiceInfoController` | `/service-info` 端点控制器。 |
| `serviceInfoEndpointDetector` | `ServiceInfoEndpointDetector` | 探测 Actuator/Swagger 等端点是否可用。 |
| `serviceInfoBannerListener` | `ServiceInfoBannerListener` | 启动完成后打印服务信息横幅。 |
| `serviceInfoAuthenticationFilterRegistration` | `FilterRegistrationBean<ServiceInfoAuthenticationFilter>` | 注册 服务信息 认证过滤器 (可选)。 当 plugin.service-info.auth.enable=true 时注册, 否则不创建。 |

**内部依赖**：`cache`

**HTTP 入口**（`@RestController`/`@Controller`，共 3 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `ApiFeatureController` | `${plugin.api.feature.path:/api/features}` | API 功能开关管理控制器 提供功能开关的查询和管理接口。 此接口为内部接口，仅允许内网IP或白名单服务调用，不受 o认证 鉴权控制。 |
| `CaptchaProvider` | `${plugin.captcha.context-path:/v1/}` | 校验码 |
| `OptionalProvider` | `/v1/option` | 获取选项 |

**组件扫描 Service**：`IptablesServiceImpl`、`MailService`（共 2 个）。`@Service` 注册，可直接注入。

**SPI 扩展点**：`ExpressionParser`、`MediaTypeHandler`、`Formatter`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`plugin.actuator.*`，`ActuatorProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `enable-auth` | `boolean` | `false` | 是否开启认证 |
| `username` | `String` | `""` | 用户名 |
| `password` | `String` | `""` | 密码 |
| `ip-whitelist` | `Set<String>` | `new LinkedHashSet<>()` | IP白名单 |
| `exclude-paths` | `Set<String>` | `new LinkedHashSet<>()` | 排除的路径 |

**配置**（`plugin.api.cors.*`，`ApiCorsProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 开启跨域 |
| `pattern` | `Set<String>` | `new HashSet<>()` | 如果为空，默认匹配所有路径 /** |
| `allowed-origins` | `Set<String>` | `new HashSet<>()` | 允许的来源域名白名单 为空时不放行任何跨域来源（同源访问不受影响）。 这一项必须与 allowCredentials 成对考虑：一旦 allowCredentials=true， 浏览器就会把带凭证的跨域响应交给脚本，此时来源白名单是唯一防线。 不要填 "*"：Spring 的 allowedOriginPatterns("*") 会把请求里的 Origin 原样反射回 Access-Contr… |
| `allow-credentials` | `boolean` | `false` | 是否允许跨域请求携带凭证（Cookie / Authorization） 默认 false。打开前必须确认 #allowedOrigins 已配好白名单。 |

**配置**（`plugin.api.security.header.*`，`ApiSecurityHeaderFilter`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `properties` | `ApiSecurityHeaderProperties` | `—` | 安全响应头配置 |

<details><summary>ApiSecurityHeaderFilter 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `properties.enable` | `boolean` | `true` | 是否启用安全响应头过滤器 |
| `properties.frame-options` | `String` | `"SAMEORIGIN"` | X-Frame-Options 取值 默认 SAMEORIGIN（不用 DENY：文件预览依赖 iframe 渲染）。 |
| `properties.referrer-policy` | `String` | `"strict-origin-when-cross-origin"` | Referrer-Policy 取值 |
| `properties.permissions-policy` | `String` | `"camera=(), microphone=(), geolocation=…` | Permissions-Policy 取值 关闭摄像头、麦克风、地理位置等本项目用不到的能力。 |
| `properties.csp` | `String` | `""` | Content-Security-Policy 默认空（不设置该头）。本项目大量使用内联样式与 @vite/client， 贸然启用会直接打挂页面，需按部署形态逐域评估后再配置。 |
| `properties.hsts-enabled` | `boolean` | `false` | 是否启用 HSTS 默认 false：当前部署为 http，启用会让浏览器强制升级到 https 而打不开。 |
| `properties.hsts-max-age-seconds` | `int` | `31536000` |  |

</details>

**配置**（`plugin.api.*`，`ApiProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `ignore-format-packages` | `String[]` | `—` | 忽略返回格式（针对返回格式不进行统计处理） |
| `version` | `Version` | `new Version()` | 版本 |
| `platform` | `Platform` | `new Platform()` | Platform |
| `uniform` | `boolean` | `true` | 自动统一返回结构 |
| `encode` | `ResponseEncodeProperties` | `new ResponseEncodeProperties()` | Encode |
| `decode` | `RequestDecodeProperties` | `new RequestDecodeProperties()` | Decode |
| `spi` | `SpiConfig` | `new SpiConfig()` | SPI |
| `mock` | `MockConfig` | `new MockConfig()` | 模拟 |
| `deprecated` | `DeprecatedConfig` | `new DeprecatedConfig()` | 废弃接口配置 |
| `internal` | `InternalConfig` | `new InternalConfig()` | 内部接口配置 |
| `feature` | `FeatureConfig` | `new FeatureConfig()` | 特征 |
| `gray` | `GrayConfig` | `new GrayConfig()` | Gray |

<details><summary>ApiProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `version.enable` | `boolean` | `true` | 是否开启版本控制 |
| `platform.enable` | `boolean` | `true` | 是否开启平台标识 |
| `platform.name` | `PlatformType` | `PlatformType.SYSTEM` | 平台类型（枚举，优先级高于 别名名称） |
| `platform.alias-name` | `String` | `—` | 平台别名（当 名称 无法满足需求时使用自定义名称） |
| `encode.enable` | `boolean` | `false` | 是否开启加密功能 |
| `encode.response-enable` | `boolean` | `false` | 是否开启响应加密 |
| `encode.ext-inject` | `boolean` | `false` | 是否由其它对象注入参数 |
| `encode.codec-type` | `String` | `"aes"` | Codec类型 |
| `encode.white-list` | `List<String>` | `Collections.emptyList()` | White列表 |
| `decode.enable` | `boolean` | `false` | 是否开启请求解密 |
| `decode.codec-request-key` | `String` | `—` | 请求解密密钥 |
| `decode.ext-inject` | `boolean` | `false` | 是否由其它对象注入参数 |
| `decode.codec-type` | `String` | `"aes"` | Codec类型 |
| `decode.reject-on-decode-failure` | `boolean` | `false` | 解密失败时是否拒绝请求 true: 解密失败时抛出异常，拒绝请求 false: 解密失败时返回原始数据（默认，保证业务连续性） |
| `decode.white-list` | `List<String>` | `Collections.emptyList()` | White列表 |
| `spi.enable` | `boolean` | `true` | 是否开启虚拟映射 |
| `spi.mapping` | `Map<String, String>` | `—` | 虚拟映射 用于将简短的类型名称映射到完整的类名。 例如：{"captcha": "com.chua.通用.支持.captcha.Captcha"} |
| `mock.enable` | `boolean` | `false` | 是否开启 Mock 功能 |
| `mock.profiles` | `String` | `"dev,test"` | 配置文件 |
| `feature.enable` | `boolean` | `false` | 是否开启功能开关 |
| `feature.path` | `String` | `"/api/features"` | 路径 |
| `internal.enable` | `boolean` | `true` | 是否开启内部接口控制 |
| `internal.global-allowed-ips` | `List<String>` | `Collections.emptyList()` | 全局IP白名单（适用于所有内部接口） |
| `internal.global-allowed-services` | `List<String>` | `Collections.emptyList()` | 全局服务白名单（适用于所有内部接口） |
| `deprecated.enable` | `boolean` | `true` | 是否开启废弃接口提示 |
| `deprecated.add-warning-header` | `boolean` | `true` | 是否在响应头中添加废弃警告 |
| `gray.enable` | `boolean` | `true` | 是否开启灰度发布功能 默认开启（非实验性功能，遵循"默认开关默认开启"约定） |
| `gray.header-name` | `String` | `"X-Gray-Hit"` | 头部名称 |
| `gray.global-users` | `List<String>` | `Collections.emptyList()` | 全局用户 |
| `gray.global-ips` | `List<String>` | `Collections.emptyList()` | 全局IPS |
| `gray.global-roles` | `List<String>` | `Collections.emptyList()` | 全局角色 |
| `gray.default-percentage` | `int` | `0` | 默认percentage |

</details>

**配置**（`plugin.security.fingerprint.*`，`RequestFingerprintAutoConfiguration`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `fingerprint-cleaner` | `ScheduledExecutorService` | `—` | Fingerprintcleaner |

<details><summary>RequestFingerprintAutoConfiguration 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `fingerprint-props.enabled` | `boolean` | `false` | 是否启用 |
| `fingerprint-props.header-name` | `String` | `"X-Request-Fingerprint"` | 头部名称 |
| `fingerprint-props.algorithm` | `String` | `"SHA-256"` | 算法 |
| `fingerprint-props.salt` | `String` | `—` | Salt |
| `fingerprint-props.include-method` | `boolean` | `true` | Includemethod |
| `fingerprint-props.include-path` | `boolean` | `true` | Include路径 |
| `fingerprint-props.include-params` | `boolean` | `true` | Include参数 |
| `fingerprint-props.include-headers` | `Set<String>` | `new LinkedHashSet<>()` | Includeheaders |
| `fingerprint-props.include-body` | `boolean` | `false` | Include请求体 |
| `fingerprint-props.include-body-max-bytes` | `long` | `1024 * 1024` | 1MB 默认上限 |
| `fingerprint-props.body-methods` | `Set<String>` | `new LinkedHashSet<>(Arrays.asList("POST…` | 请求体方法 |
| `fingerprint-props.validity-ms` | `long` | `60_000` | validityms |
| `fingerprint-props.reject-duplicate` | `boolean` | `false` | Rejectduplicate |
| `cached-body-http-servlet-request.cached-body` | `byte[]` | `—` | Cached请求体 |

</details>

**配置**（`plugin.jackson.*`，`JacksonProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `include-null` | `boolean` | `false` | 是否包含空值 |

**配置**（`plugin.log.*`，`LogProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 日志开启 |
| `open-interface-log` | `boolean` | `false` | 开启接口日志 |
| `slow-request-threshold-ms` | `long` | `3000` |  |

**配置**（`plugin.thread.*`，`AsyncThreadPoolProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `core-pool-size` | `int` | `20` |  |
| `max-pool-size` | `int` | `200` |  |
| `queue-capacity` | `int` | `100000` |  |
| `wait-for-tasks-to-complete-on-shutdown` | `boolean` | `—` | 线程池关闭：等待所有任务都完成再关闭 |
| `await-termination-seconds` | `int` | `60` |  |
| `keep-alive-seconds` | `int` | `10` |  |
| `thread-name-prefix` | `String` | `"async-thread-pool-%d"` | 线程名称前缀 |

**配置**（`plugin.captcha.*`，`CaptchaProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 是否启用 |
| `type` | `CaptchaTypeEnum` | `CaptchaTypeEnum.SPEC` | 验证码类型 |
| `ttl` | `long` | `120L` |  |
| `length` | `int` | `4` | 验证码内容长度 |
| `width` | `int` | `120` |  |
| `height` | `int` | `36` |  |
| `font-name` | `String` | `"Verdana"` | 验证码字体 |
| `font-style` | `Integer` | `Font.PLAIN` | 字体风格 |
| `font-size` | `int` | `20` |  |

**配置**（`plugin.ip.*`，`IpProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `database-file` | `String` | `"classpath:qqwry.dat"` | 数据库文件路径 |
| `ip-type` | `String` | `"ip2region"` | ip翻译实现方式 |

**配置**（`plugin.message.*`，`MessageConverterProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `data-format` | `String` | `"yyyy-MM-dd HH:mm:ss"` | 日期默认格式 |
| `open-desensitize` | `boolean` | `true` | 开启脱敏 |
| `open-crypto` | `boolean` | `true` | 开启加解密 |

**配置**（`plugin.notice.*`，`NoticeProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `email` | `Email` | `—` | Email |

<details><summary>NoticeProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `email.from` | `String` | `—` | 发送者 |
| `email.smtp-host` | `String` | `"smtp.qq.com"` | 主机 |
| `email.ssl-smtp-port` | `String` | `"465"` | ssl smtp端口 |
| `email.password` | `String` | `—` | 授权码 |
| `email.smtp-port` | `int` | `25` |  |

</details>

**配置**（`plugin.optional.*`，`OptionalProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 是否打开 |

**配置**（`plugin.service-info.*`，`ServiceInfoProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 是否启用 服务信息 端点 (/service-info) 和横幅渲染。 默认 true。 |
| `banner` | `boolean` | `true` | 是否在启动时输出横幅到 stdout。 默认 true。 |
| `auth-enable` | `boolean` | `false` | 是否对 /service-info 端点启用认证。 默认 false (内网场景)。 |
| `infrastructure` | `boolean` | `true` | 是否在横幅中展示基础设施连接信息 (Redis/JDBC/Kafka/RabbitMQ/springcloud/Dubbo)。 默认 true, 但仅展示 Environment 中真实配置了的条目; 未配置的中间件不会出现在横幅中。 |
| `auth-username` | `String` | `"admin"` | 基础 认证 用户名。 |
| `auth-password` | `String` | `""` | 基础 认证 密码（通过配置文件覆盖）。 |
| `auth-ip-whitelist` | `Set<String>` | `new LinkedHashSet<>()` | IP 白名单, 命中后跳过认证。支持精确匹配、通配符、CIDR。 |

**通用场景**

业务项目只依赖它就能拿到统一返回体、全局异常处理和线程池；要改返回结构时覆盖 `ApiUniformResponseBodyAdvice` 或改 `plugin.api.uniform`。


### spring-support-cache-starter

**定位**：Caffeine + Redis 多级缓存，含租户键生成与 `@Cacheable` 增强。

**装配**：`CacheConfiguration` 常驻；`RedisCacheManagerConfiguration` 需 `plugin.cache.enabled=true` 且类路径存在 spring-data-redis。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `caffeineCacheManager` | `CacheManager` | Caffeine 缓存管理器 当未显式定义 caffeine缓存管理器 Bean 时，根据 缓存属性 创建。 |
| `multiLevelCacheManager` | `MultiLevelCacheManager` | 多级缓存管理器 作为主缓存管理器，按配置的层级顺序组合 Caffeine、Redis 等。 当未显式定义 multi级缓存管理器 Bean 时创建。 |
| `tenantedKeyGenerator` | `TenantedKeyGenerator` | 租户缓存键生成器 用于生成包含租户、应用、平台等上下文的缓存键。 |
| `customTenantedKeyGenerator` | `org.springframework.cache.interceptor.KeyGenerator` | 兼容旧版的自定义缓存键生成器 Bean（返回 SimpleKeyGenerator）。 保留供既有 @Cacheable(keyGenerator = "customTenantedKeyGenerator") 引用兼容。 |
| `cacheCustomAnnotationParser` | `CacheCustomAnnotationParser` | 自定义缓存注解解析器 支持 Key 和 KeyGenerator 同时使用，默认使用 tenantedKeyGenerator。 |
| `cacheTemplate` | `CacheTemplate` | 缓存操作模板 提供手动缓存操作方法，内部自动使用 tenantedKeyGenerator 拼接完整缓存键， 保证手动操作与 @Cacheable 等注解生成的 Key 一致。 |
| `customCacheOperationSource` | `org.springframework.cache.interceptor.CacheOperationSource` | 缓存操作源（覆盖默认，使用自定义解析器） 无条件创建并标记 @Primary，确保 CacheInterceptor 始终使用 支持 Key 与 KeyGenerator 共用的 缓存自定义注解解析器； 若依赖 @ConditionalOnMissingBean 会被 @EnableCaching 默认的标准解析器抢注， 导致 Key+KeyGenerator 同时使用时报 illegal状态异… |
| `cacheServiceInfoContributor` | `com.chua.starter.common.support.serviceinfo.ServiceInfoContributor` | 注册缓存模块服务信息。 |
| `redisCacheManager` | `CacheManager` | Redis 缓存管理器 当未显式定义 Redis缓存管理器 Bean 时，根据 缓存属性 创建。 写操作强制同步（immediateWrites）：Spring Data Redis 4.x 默认异步写缓存， 失效指令的 Future 从不 join、异常静默丢失，实测 @CacheEvict 到不了 Redis， 改了数据读接口永久返回旧值。 |

**内部依赖**：`common`

**配置**（`plugin.cache.*`，`CacheProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `false` | 是否启用多级缓存 |
| `levels` | `List<String>` | `List.of("caffeine", "redis")` | 多级缓存层级（按读取顺序，越靠前越接近调用方） 支持：caffeine、redis |
| `caffeine` | `CaffeineProperties` | `new CaffeineProperties()` | 本地 Caffeine 缓存配置 |
| `redis` | `RedisProperties` | `new RedisProperties()` | Redis 分布式缓存配置 |
| `key` | `KeyProperties` | `new KeyProperties()` | 缓存键策略 |
| `caffeine-bean` | `String` | `CAFFEINE_BEAN_NAME` | Caffeine 缓存管理器 Bean 名称（用于多级缓存中按名称解析） |
| `redis-bean` | `String` | `REDIS_BEAN_NAME` | Redis 缓存管理器 Bean 名称（用于多级缓存中按名称解析） |
| `expires` | `Map<String, Long>` | `new LinkedHashMap<>()` | 按缓存名称细化的过期时间（秒） 未配置的缓存名使用 RedisProperties#getTtl() 默认值 |
| `cache-null-values` | `boolean` | `false` | 是否允许缓存空值（防缓存穿透） |
| `null-value-ttl` | `long` | `60` |  |
| `record-stats` | `boolean` | `true` | 多级缓存中是否启用命中率统计 |

<details><summary>CacheProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `caffeine.initial-capacity` | `int` | `256` |  |
| `caffeine.maximum-size` | `long` | `10_000L` | 最大容量 |
| `caffeine.expire-after-write` | `long` | `300` |  |
| `caffeine.expire-after-access` | `long` | `0` | 访问后过期时间（秒），小于等于 0 表示不设置 |
| `caffeine.record-stats` | `boolean` | `true` | 是否启用命中率统计 |
| `caffeine.weak-keys` | `boolean` | `false` | 弱引用 Key |
| `caffeine.weak-values` | `boolean` | `false` | 弱引用 值 |
| `redis.ttl` | `long` | `600` |  |
| `redis.key-prefix` | `String` | `"cache:"` | Key 前缀 |
| `redis.serializer` | `String` | `"jackson"` | 值序列化方式：jackson / jdk |
| `redis.cache-null-values` | `boolean` | `false` | 是否允许缓存空值 |
| `redis.separator` | `String` | `":"` | Key 与 值 是否使用相同分隔符 |
| `redis.use-key-prefix` | `boolean` | `true` | 是否在 Key 中携带应用名前缀 |
| `key.prefix` | `String` | `"cache:"` | 缓存键统一前缀 |
| `key.separator` | `String` | `":"` | 缓存键分隔符 |
| `key.with-tenant` | `boolean` | `true` | 是否在 Key 中追加租户标识 |
| `key.with-application` | `boolean` | `true` | 是否在 Key 中追加应用名 |
| `key.with-platform` | `boolean` | `true` | 是否在 Key 中追加平台标识 |

</details>

**通用场景**

单机用 Caffeine，多实例把 Redis 打开做二级缓存；缓存键要带租户时用 `TenantedKeyGenerator`。


### spring-support-redis-starter

**定位**：Redis/Redisson 客户端、分布式锁、嵌入式 Redis。

**装配**：`RedisConfiguration`（`plugin.redis.server.enable` 默认 true）、`CacheConfiguration`、`LockTemplateConfiguration`（按类路径回退 Noop）。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `systemCacheManager600` | `CacheManager` | 系统缓存管理器 |
| `systemCacheManager30` | `CacheManager` | 系统缓存管理器 |
| `systemCacheManager10` | `CacheManager` | 系统缓存管理器 |
| `systemCacheManager60` | `CacheManager` | 系统缓存管理器 |
| `systemCacheManager` | `CacheManager` | 系统缓存管理器 |
| `systemCacheManagerAlways` | `CacheManager` | 系统缓存管理器 |
| `redis86400CacheManager` | `CacheManager` | 系统缓存管理器 |
| `lockTemplate` | `LockTemplate` | 基于 Redisson 的分布式锁模板。 注册名为 customLockTemplate 的锁模板 Bean；当容器中存在 RedissonClient 时返回 RedissonLockTemplate，否则回退 NoopLockTemplate（不持锁，仅用于无 Redis 环境下的降级）。 |
| `redissonClient` | `RedissonClient` | 创建 Redisson 客户端，基于 Spring Boot Redis属性 构建单机连接配置。 当 RedisProperties.url 为空时，使用 host:port 拼接 redis:// 地址； 超时 与 连接超时 缺省时回退到 10000 毫秒。 |
| `timeSeriesService` | `TimeSeriesService` | 创建时间序列服务实例。 |
| `redisSearchService` | `RedisSearchService` | 创建 Redis 搜索服务实例，提供基于 Redis 的检索能力。 |
| `simpleRedisService` | `SimpleRedisService` | 创建简单 Redis 服务实例，提供指标自增/自减与 QPS 统计能力。 |
| `kvEngine` | `KvEngine` | 创建统一 KV 引擎实例，基于 StringRedisTemplate 实现 KvEngine 接口。 提供 get/put/delete/incr/expire/ttl 等基础 KV 操作，以及 findAllByPrefix 前缀查询。 |
| `container` | `RedisMessageListenerContainer` | 创建 Redis 消息监听容器，绑定所有实现了 RedisListener 的 Bean。 特别说明：消息序列化对象必须放在加入消息监听器之后设置， 否则会导致接收器接收不到消息（既影响发布方也影响订阅方）。 |
| `redisTemplate` | `RedisTemplate<String, Object>` | 创建 redistemplate，使用 字符串Redis序列化器 序列化 Key、 genericjackson2jsonredis序列化器 序列化 值，支持复杂对象类型。 源码 autoConfig 中默认为 ，开发中一般直接使用 ，本方法按此约定进行配置。 |
| `stringRedisTemplate` | `StringRedisTemplate` | 创建 字符串redistemplate，Key/值 均使用 字符串Redis序列化器。 StringRedisTemplate 的 key/value 必须使用 StringRedisSerializer， 否则 获取() 返回类型不匹配。 |
| `redisServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |

**内部依赖**：`common`、`cache`

**组件扫描 Service**：`RedisSearchServiceImpl`、`SimpleRedisServiceImpl`、`TimeSeriesServiceImpl`（共 3 个）。`@Service` 注册，可直接注入。

**配置**（`plugin.redis.server.*`，`RedisServerProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `open-embedded` | `boolean` | `false` | 嵌入式服务 |
| `port` | `Integer` | `6379` |  |
| `database` | `Integer` | `0` | 数据库 |
| `host` | `String` | `"0.0.0.0"` | 主机 |
| `max-memory` | `String` | `"128M"` | 最大内存 |
| `max-heap` | `String` | `"100M"` | 最大值heap |

**通用场景**

需要分布式锁注入 `LockTemplate`；本地开发用 `plugin.redis.server.open-embedded=true` 拉起嵌入式 Redis。


### spring-support-datasource-starter

**定位**：动态多数据源、SQL 初始化脚本、事务模板，以及 H2/SQLite/Calcite/Sharding 四种引擎。

**装配**：13 个配置类；主数据源无总开关；H2/SQLite 用 `plugin.h2.enable`/`plugin.sqlite.enable`，Calcite/Sharding 用 `plugin.datasource.calcite.enabled`/`plugin.datasource.sharding.enabled`；脚本通道 `plugin.datasource.script.enable` 默认 true。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `calciteDataSourceConversion` | `DataSourceConversion` | 注册 Calcite 数据源转换器 Bean |
| `calciteDataSource` | `DataSource` | 创建 Calcite 聚合数据源 Bean |
| `dsTransactionalAspect` | `DSTransactionalAspect` | 注册 @DSTransactional 事务切面 Bean |
| `dataSourceScriptBeanPostProcessor` | `DataSourceScriptBeanPostProcessor` | 暴露数据源脚本处理器。 BeanPostProcessor 必须用 static 工厂方法暴露：非 static 的 @Bean 方法会促使容器提前实例化本配置类，而此时其他 BeanPostProcessor 尚未注册完成，Spring 会打出「BeanPostProcessorChecker」告警。 参数用 ObjectProvider 延迟解析（而非直接注入 DataSourceScrip… |
| `transactionTemplate` | `TransactionTemplate` | 暴露一个默认的 Spring transactiontemplate |
| `jdbcTransactionManager` | `JdbcTransactionManager` | 暴露一个默认的 Spring jdbctransaction管理器 |
| `jdbcTemplate` | `JdbcTemplate` | 暴露一个默认的 Spring jdbctemplate |
| `passwordDecryptor` | `PasswordDecryptor` | 注册默认 AES 密码解密器 |
| `dataSourceHealthIndicator` | `DataSourceHealthIndicator` | 数据源健康检查指示器 仅在类路径存在 Spring Boot Actuator 及 HealthIndicator 时注册。 |
| `gracefulShutdownListener` | `GracefulShutdownListener` | 数据源优雅关闭监听器 监听 Spring 容器的 ContextClosedEvent，在应用关闭时 遍历所有已注册数据源并关闭其连接池。 |
| `datasourceServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |
| `dataSourceDocumentService` | `DataSourceDocumentService` | 文档服务（解析 + 缓存）。 若 classpath 缺 DocumentParser SPI 或 DataSource 不可用，返回 null |
| `databaseGraphService` | `DatabaseGraphService` | 数据库关系图谱服务。 |
| `dataSourceDocumentController` | `DataSourceDocumentController` | 文档控制器（图谱服务可选）。 |
| `dataSourceDocumentHandler` | `DataSourceDocumentHandler` | 注册器（把 控制器 方法挂到 Spring MVC）。 |
| `documentPathRegistrar` | `SmartInitializingSingleton` | * 触发路径注册 |
| `h2Engine` | `H2Engine` | 注册 H2 Engine Bean，通过 SPI "h2" 获取。 |
| `h2DataSource` | `DataSource` | 注册 H2 数据源 Bean（供 Spring jdbctemplate /@Transactional 使用）。 |
| `h2Flyway` | `Flyway` | 注册 H2 Flyway 迁移执行器。 如果配置了 script-path，则在 Bean 初始化时自动执行迁移。 |
| `h2CleanupPlugin` | `H2CleanupPlugin` | 注册 H2 清理插件（可选）。 |
| `defaultAdvisorAutoProxyCreator` | `DefaultAdvisorAutoProxyCreator / DefaultAdvisorAutoProxyCreator` | 为物理化路由/刷新切面开启自动代理。 |
| `seataDataSourcePostProcessor` | `SeataDataSourcePostProcessor` | 创建 Seata 数据源代理的初始化器 用于在 DynamicDataSource 注册完成后， 将其中每个实际的 DataSource 包装为 Seata 的 DataSourceProxy，从而支持 AT 模式的分布式事务。 |
| `shardingV5Conversion` | `DataSourceConversion` | 注册 分库分表v5 数据源转换器 Bean |
| `shardingDataSource` | `DataSource` | 创建 分库分表sphere 分片数据源 Bean |
| `sqliteEngine` | `SqliteEngine` | 注册 sqlite Engine Bean，优先于注解初始化加载。 使用 @Order(Ordered.LOWEST_PRECEDENCE) 确保在所有依赖 Engine 的 Bean 之后初始化，但在需要 sqlite 作为默认数据源的场景下可优先于其他 Engine。 |
| `sqliteDataSource` | `DataSource` | 注册 sqlite 数据源 Bean（供 Spring jdbctemplate / @Transactional 使用）。 |
| `dataSourceSqlMonitor` | `DataSourceSqlMonitor` | 创建 SQL 监控门面 |
| `sqlMonitorEndpoint` | `SqlMonitorEndpoint` | 创建 SQL 监听 Actuator 端点 依赖 actuator 环境，当类路径存在 Endpoint 时注册。 |
| `sqliteEngineEndpoint` | `SqliteEngineEndpoint` | 注册 sqlite 引擎 Actuator 端点。 |

**内部依赖**：`common`

**配置**（`plugin.datasource.monitor.*`，`SqlMonitorProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `Boolean` | `null` | 是否启用 SQL 监听 未设置（null）时根据 Spring 环境自动切换：匹配 #autoEnableEnvironments 中任一环境（dev/测试/uat/本地）则启用，否则关闭。显式设置后以设置为准。 |
| `auto-enable-environments` | `List<String>` | `Arrays.asList("dev", "test", "uat", "lo…` | 自动启用监听的环境列表 仅当 enabled 未显式设置时生效。任一激活 profile 命中该列表即启用。 |
| `slow-sql-threshold-ms` | `long` | `1000L` |  |
| `max-sql-length` | `int` | `2000` |  |
| `record-sql` | `boolean` | `true` | 是否记录 SQL 文本，关闭后事件只携带统计信息，减少内存开销 |
| `publish-event` | `boolean` | `true` | 是否发布 Spring 事件，关闭后只更新统计与慢 SQL 日志 |
| `max-tracked-data-sources` | `int` | `1000` |  |
| `overflow-policy` | `OverflowPolicy` | `OverflowPolicy.REJECT` | 数据源统计达到上限时的溢出策略 默认 OverflowPolicy#REJECT：达到上限后不再跟踪新数据源（慢 SQL 日志与 事件仍正常），已跟踪数据源不受影响。也可配置为 OverflowPolicy#EVICT_ELDEST 自动淘汰最早跟踪的数据源。 |

**配置**（`plugin.datasource.calcite.*`，`CalciteDataSourceProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `false` | 是否启用Calcite数据源合并 |
| `max-pool-size` | `int` | `10` |  |
| `schema-mapping` | `Map<String, String>` | `—` | 模式名称映射 Key: 原数据源名称 值: Calcite中的模式名称 |
| `default-schema` | `String` | `—` | 默认模式名称 |

**配置**（`plugin.datasource.script.*`，`DataSourceScriptProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 是否启用数据源脚本功能 默认值: true 示例: plugin.datasource.script.enable=true |
| `scan-mode` | `ScanMode` | `ScanMode.ONCE` | 表结构脚本扫描模式 无 : 不扫描 ONCE : 仅当当前数据库没有任何业务表时扫描一次 ALWAYS : 每次启动都扫描 默认值: ONCE 示例: plugin.datasource.script.扫描-mode=ALWAYS |
| `migration-scan-mode` | `RepeatableScanMode` | `RepeatableScanMode.ALWAYS` | 升级补丁脚本扫描模式 无 : 不扫描 ALWAYS : 每次启动都扫描 默认值: ALWAYS 示例: plugin.datasource.script.migration-扫描-mode=无 |
| `data-scan-mode` | `ScanMode` | `ScanMode.ONCE` | 初始化数据脚本扫描模式 无 : 不扫描 ONCE : 仅当当前数据库不存在业务数据时扫描一次 ALWAYS : 每次启动都扫描 默认值: ONCE 示例: plugin.datasource.script.数据-扫描-mode=ALWAYS |
| `continue-on-error` | `boolean` | `true` | 是否继续执行脚本，如果某个脚本执行失败，是否继续执行下一个脚本 默认值: false 示例: plugin.datasource.script.继续-on-错误=true |
| `ignore-failed-drops` | `boolean` | `true` | 是否忽略失败的掉落语句 默认值: true 示例: plugin.datasource.script.ignore-失败-掉落=true |
| `separator` | `String` | `";"` | 脚本分隔符 默认值: ; 示例: plugin.datasource.script.separator=; |
| `sql-script-encoding` | `String` | `"UTF-8"` | 脚本编码 默认值: UTF-8 示例: plugin.datasource.script.编码=UTF-8 |
| `script-path` | `String` | `DEFAULT_SCRIPT_PATH` | 数据源脚本路径 支持多个路径，使用英文逗号、分号或换行分隔。 推荐值：类路径*:db/初始化/*.SQL 默认值: 类路径*:db/初始化/*.SQL,类路径*:db/migration/*.SQL,类路径*:db/同步/*.SQL 示例: plugin.datasource.script.script-路径=类路径*:db/初始化/*.SQL |
| `database-type` | `String` | `—` | 数据库类型 用于指定特定数据库的脚本目录 默认值: 空 (不指定数据库类型) 示例: plugin.datasource.script.database-类型=MySQL |
| `baseline-on-migrate` | `boolean` | `true` | 是否在迁移时生成基线 默认: true 示例: plugin.datasource.script.baseline-on-migrate=false |
| `clean-disabled` | `boolean` | `true` | 是否禁用清理 默认: true 示例: plugin.datasource.script.clean-已禁用=false |
| `version-table` | `String` | `"sys_database_version"` | 版本记录表名 默认值: sys_database_版本 示例: plugin.datasource.script.版本-table=Flyway_模式_历史 |
| `baseline-version` | `String` | `"1.0.0"` | 基线版本号 默认值: 1.0.0 示例: plugin.datasource.script.baseline-版本=1.0.0 |
| `baseline-description` | `String` | `"Initial baseline"` | 基线描述 默认值: Initial baseline 示例: plugin.datasource.script.baseline-description=Initial setup |
| `script-prefix` | `String` | `"V"` | 脚本文件名前缀 默认值: V 示例: plugin.datasource.script.script-前缀=V |
| `script-suffix` | `String` | `".sql"` | 脚本文件名后缀 默认值: .SQL 示例: plugin.datasource.script.script-后缀=.SQL |
| `version-separator` | `String` | `"__"` | 版本分隔符 默认值: __ 示例: plugin.datasource.script.版本-separator=__ |
| `validate-checksum` | `boolean` | `true` | 是否验证脚本校验和 默认值: true 示例: plugin.datasource.script.校验-校验和=false |
| `mixed` | `boolean` | `false` | 是否允许混合模式（允许非版本化脚本） 默认值: false 示例: plugin.datasource.script.混合=true |
| `timeout` | `int` | `0` | 脚本执行超时时间（秒） 默认值: 0（无超时） 示例: plugin.datasource.script.超时=300 |
| `verbose` | `boolean` | `true` | 是否输出详细日志 默认值: true 示例: plugin.datasource.script.verbose=true |
| `release-type` | `ReleaseType` | `ReleaseType.STABLE` | 允许执行的版本类型 默认值: STABLE（只执行正式版本） 示例: plugin.datasource.script.release-类型=st 可选值: SNAPSHOT, ALPHA, BETA, RC, st, 全部 |
| `allowed-release-types` | `Set<ReleaseType>` | `—` | 允许执行的版本类型集合（用于更精细控制） 当设置此属性时，会覆盖 release类型 的配置 示例: plugin.datasource.script.allowed-release-类型=st,RC |
| `initdata-parallelism` | `int` | `0` | 初始化数据脚本(initdata)的并发执行数量 使用虚拟线程并发执行 initdata 脚本 默认值: 0（无限制，每个脚本一个虚拟线程） 示例: plugin.datasource.script.initdata-parallelism=4 |
| `initdata-timeout-minutes` | `int` | `5` | 初始化数据脚本(initdata)的等待超时时间（分钟） 默认值: 5 示例: plugin.datasource.script.initdata-超时-minutes=10 |
| `scope` | `String` | `"table"` | 数据库初始化脚本的安装判断范围 table（默认）: 基于业务表判断 — 在 是否database空() 中排除已知系统表前缀（如 sys_）， 避免其他模块创建的系统表导致初始化脚本被跳过。 文件: 基于表全量判断 — 保持原行为，仅在数据库完全为空时执行初始化脚本。 默认值: table 示例: plugin.datasource.script.scope=文件 |
| `exclude-table-prefixes` | `String[]` | `{"sys_"}` | 空库判断时排除的表名前缀列表 仅在 scope=table 时生效 默认值: ["sys_"] 示例: plugin.datasource.script.exclude-table-前缀=sys_,行为_ |

**配置**（`plugin.datasource.document.*`，`DocumentProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 是否启用 默认 true；非 Web 环境（无 DispatcherServlet）会自动跳过装配 |
| `base-path` | `String` | `"/plugin/datasource/document"` | 暴露路径前缀 默认 /plugin/datasource/document |
| `title` | `String` | `"数据库设计文档"` | 文档标题 |
| `version` | `String` | `"1.0.0"` | 文档版本号 |
| `template` | `String` | `"DEFAULT"` | HTML 模板类型： DEFAULT —— 内置默认 AURORA —— 深色主题 SWAGGER —— Swagger 风格 注意：实际可用模板由类路径下 DocumentTemplate SPI 决定；如未引入对应实现将回退到 DEFAULT |
| `include-tables` | `String` | `—` | 表名白名单（逗号分隔，* 通配） |
| `exclude-tables` | `String` | `"flyway_schema_history,sys_database_ver…` | 表名黑名单（逗号分隔，* 通配） |
| `include-schemas` | `String` | `—` | 模式 白名单（逗号分隔，* 表示全部） |
| `cacheable` | `boolean` | `true` | 是否在请求时强制刷新（apply 接口始终刷新；preview 接口若缓存存在则不刷新） |
| `options` | `Map<String, Object>` | `new LinkedHashMap<>()` | 透传给 文档parser 的扩展参数 例如：all=true 表示跨 catalog 导出 |

**配置**（`plugin.h2.*`，`H2DataSourceProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 H2 引擎自动配置 |
| `url` | `String` | `"jdbc:h2:mem:springh2db;DB_CLOSE_DELAY=…` | H2 JDBC URL，如 jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1 |
| `username` | `String` | `"sa"` | 用户名（默认 sa） |
| `password` | `String` | `""` | 密码（默认空） |
| `script-path` | `String` | `""` | 迁移脚本路径，支持 类路径: 前缀，如 classpath:db/migration/h2 |
| `cleanup-after-shutdown` | `boolean` | `false` | 是否在应用关闭时清理所有用户表（仅测试环境建议开启） |
| `cleanup-after-test` | `boolean` | `false` | 是否在测试结束后清理数据（通过 Spring 测试 生命周期绑定） |

**配置**（`plugin.datasource.materialized.*`，`MaterializedRouteProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `false` | 是否启用。 |
| `default-threshold` | `long` | `1000L` |  |
| `refresh-interval-seconds` | `long` | `0L` | 内存副本自动刷新间隔，单位秒。 小于等于 0 时表示关闭自动刷新。 |
| `skip-query-in-transaction` | `boolean` | `true` | 事务中是否跳过查询物理化。 默认跳过，避免与事务连接绑定冲突。 |
| `cache-data-source-prefix` | `String` | `"materialized#"` | 物理化数据源名称前缀。 |

**配置**（`plugin.multi-datasource.*`，`MultiDataSourceProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `data-source` | `List<DataSourceProperties>` | `—` | 数据源 |

**配置**（`plugin.multi-datasource.*`，`MultiDataSourceSettingProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `force-annotation` | `boolean` | `true` | 是否强制使用注解上的数据源 |
| `strict` | `boolean` | `false` | 严格模式 开启后，如果指定数据源不存在则抛出异常，不回退到默认数据源。 |
| `load-balance` | `String` | `"round_robin"` | 负载均衡策略：round_robin（轮询）/ 随机（随机） |

**配置**（`plugin.multi-datasource.hikari.*`，`MultiHikariDataSourceProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `data-source` | `List<HikariDataSourceProperties>` | `—` | 数据源 |

**配置**（`plugin.datasource.sharding.*`，`ShardingDataSourceProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `false` | 是否启用分片 |
| `show-sql` | `boolean` | `false` | 是否显示SQL |
| `tables` | `List<ShardingTableConfig>` | `—` | 分表配置列表 |
| `databases` | `List<String>` | `—` | 分库配置列表（逻辑库名） |

<details><summary>ShardingDataSourceProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `tables[].logic-table` | `String` | `—` | 逻辑表名 |
| `tables[].sharding-column` | `String` | `—` | 分片列（分片键） |
| `tables[].time-unit` | `String` | `—` | 时间分片单位: YEAR, QUARTER, MONTH, WEEK, DAY, HOUR |
| `tables[].strategy` | `String` | `"TABLE"` | 分片策略: TABLE, DB |

</details>

**配置**（`plugin.sqlite.*`，`SqliteDataSourceProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 sqlite 引擎自动配置 |
| `path` | `String` | `"./data/app.sqlite"` | sqlite 数据库文件路径，如 ./data/app.sqlite 或绝对路径 |
| `init-sql` | `String` | `""` | SQL 初始化脚本路径，支持 类路径: 前缀，如 classpath:db/init/sqlite |
| `max-pool-size` | `int` | `5` | 最大连接数（默认 5） |

**配置**（`plugin.transaction.*`，`TransactionProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `timeout` | `Integer` | `10` |  |
| `tx-mapper` | `String` | `""` | 事务位置 |
| `read-only` | `String` | `""` | 只读事务前缀 |
| `no-tx` | `String` | `""` | 无事务 |
| `write-only` | `String` | `""` | 写事务 |

**通用场景**

业务库用 `@DS("name")` 切数据源；初始化脚本放 `db/init/V*.sql` 由脚本通道执行；需要跨库联邦查询才开 Calcite。


### spring-support-datasource-hub-starter

**定位**：数据源台账：登记、探测、按台账构建连接，供监控/采集等系统复用。

**装配**：`DatasourceHubAutoConfiguration`，扫描 `com.chua.starter.datasourcehub.support` 并注册 Mapper，无类级开关。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `datasourceHubServiceInfoContributor` | `ServiceInfoContributor` | 数据源中心的服务信息贡献者。 |

**内部依赖**：`common`、`datasource`、`mybatis`、`swagger`、`ai`

**HTTP 入口**（`@RestController`/`@Controller`，共 29 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `DatasourceController` | 方法级 | 数据源中心接口：数据源台账 CRUD、类型枚举与连通性测试。 |
| `DatasourceMetricController` | `/ms/datasource/metric` | 数据源监控指标（SSE 推送 + 趋势）。 数据源不存在时直接 404，不建流：SSE 一旦返回 200， 浏览器就认为链路通了，此后既收不到数据也不会有报错，只会看到一条永远静止 的图 —— 那比… |
| `DatasourceOperLogController` | `/ms/datasource/oper-log` | 数据源中心操作日志查询。 与 GET /v2/log/page 的关系：那个端点在当前部署上稳定返回 S0503C0000 资源不存在（表不存在，且 @SysLog 切面发的事件 全仓无监听者，本来… |
| `DatasourceSchemaController` | 方法级 | 数据源结构与 DDL 接口：库表浏览、建表、字段映射。 这些能力归数据源模块：采集的入库节点、监控的库表浏览都调这里， 不再各自实现一份表结构读取与建表逻辑。 路径说明：/tables、/colum… |
| `DatasourceTunnelController` | `/ms/datasource/tunnel` | 数据源 SSH 隧道配置接口（高级配置）。 用于「目标库在本机不可直连」的场景：配置一条隧道后，/ms/datasource/{id/test} 会先确保隧道打开，再经隧道本地端口去连目标库并测延迟。 |
| `DbConsoleController` | `/ms/monitor/db` | } 不变，既不改既有前端与调用方， 也让依赖本模块的后端（监控 8081、采集 18190）开箱即得同一套控制台。 只依赖本模块的 DatasourceService 与事务注册表 DbTxRegi… |
| `DbDocumentController` | `/ms/monitor/db/document` | 数据库文档导出。 本控制器不自己实现文档生成：结构采集走 utils 层既有的 DocumentParser("database")（com.chua.datasource.support.docu… |
| `DbSpiCapabilityController` | `/ms/monitor/db` | 数据库 SPI 能力网关：把引擎侧 SPI 接到 HTTP 上。 为什么单独一个控制器：DbConsoleController 全是裸 JDBC（表结构 / SQL / 导入导出）， 而「用户管理 … |
| `ElasticsearchManageController` | `/ms/monitor/elasticsearch` | Elasticsearch 搜索引擎管理 REST 接口 覆盖三类能力：索引与字段设计（建索引/加字段/删索引，等价于建表与改表）、 集群配置（健康度/节点/设置/分片）、关系图（索引-字段层级 +… |
| `EtcdManageController` | `/ms/monitor/etcd` | etcd 配置中心管理 REST 接口。 键按前缀按需展开，避免一次拉全量；datasourceId 可缺省，由服务端退化为 第一个 etcd 数据源。键名走**查询参数**而不是路径参数：etcd… |
| … | … | 其余 19 个控制器见模块源码 |

**组件扫描 Service**：`DatasourceMetricServiceImpl`、`DatasourceOperLogServiceImpl`、`DatasourceSchemaServiceImpl`、`DatasourceServiceImpl`、`DatasourceTunnelServiceImpl`、`DbSchemaSnapshotServiceImpl`、`DbTableCopyService`、`ElasticsearchManageServiceImpl`、`EtcdManageServiceImpl`、`FileDbUploadService`、`InfluxdbManageServiceImpl`、`JdbcDriverJarService`、`MongoManageServiceImpl`、`NacosManageServiceImpl`、`Neo4jManageServiceImpl` 等 19 个。`@Service` 注册，可直接注入。

**SPI 扩展点**：`DatasourceConnector`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`plugin.datasource.hub.*`，`DatasourceHubProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 服务信息面板上「数据源中心」模块的展示旗标（非模块开关） |
| `connection-ttl-minutes` | `long` | `10L` |  |
| `connection-sweep-interval-ms` | `long` | `60_000L` | 空闲连接后台清扫周期（毫秒），默认 60 秒，0 = 关闭清扫。 ExpireValue 的过期是惰性的 —— 只有下一次取连接时才会触发 过期回调去关闭物理连接；数据源一旦闲置（页面关了、当天不再查询），那条连接 会一直挂到进程重启或数据源被删除。这里给后台一个定期主动核对的节拍： 到期的连接由清扫任务触发同一套过期回调关闭，既不泄漏 socket， 也不影响 TTL 内的复用（正在使用的连接… |
| `probe-timeout-ms` | `int` | `5000` |  |
| `connect-timeout-seconds` | `int` | `30` |  |
| `query-timeout-seconds` | `int` | `60` |  |
| `driver-jar-dir` | `String` | `"data" + java.io.File.separator + "jdbc…` | 上传的 JDBC 驱动 jar 落盘目录。 默认放在应用工作目录下的 data/jdbc-driver，而不是临时目录： 临时目录会被系统清理任务扫掉，用户某天重启就发现数据源连不上了， 而台账里还记着这个 jar。 文件名落盘时统一加内容指纹，避免同名不同内容的 jar 互相覆盖。 |
| `driver-jar-max-bytes` | `long` | `64L * 1024 * 1024` |  |
| `file-db-upload-dir` | `String` | `"data" + java.io.File.separator + "file…` | 文件库（SQLite / DuckDB / H2）库文件的上传落盘目录。 默认 data/file-db，落在应用工作目录下：这类库要的是 「服务器上一个可写的绝对路径」，放进临时目录会被系统清理任务扫掉， 台账里还留着那个路径，第二天就连不上了。 该目录应落在 fileJdbcAllowedDirs 允许的范围内， 否则建连会被路径白名单挡下 —— 默认值本身就在工作目录内，无需额外配置。 |
| `file-db-max-bytes` | `long` | `64L * 1024 * 1024` |  |
| `file-jdbc-allowed-dirs` | `List<String>` | `new ArrayList<>()` | 文件库（SQLite / H2 file / Derby / HSQLDB）JDBC URL 允许的根目录白名单。 这类连接串里带的是**磁盘路径**，而连接串来自数据源台账 —— 台账里出现 ../../etc/... 这类穿越路径时，探活与 SQL 执行会去 读写任意文件。所以解析后必须落在白名单目录内才允许建连。 留空时默认放行三个目录：工作目录（user.dir）、系统临时目录 （jav… |
| `secret-key` | `String` | `""` | 数据源口令的加密密钥（原文，内部经 SHA-256 派生 AES-256 密钥）。 留空表示不加密，口令按旧行为明文入库 —— 这是刻意选择： 用一个内置默认密钥「假装加密」比明文更糟，它会让人以为口令已经安全了。 配置后只影响之后写入的口令；台账里已有的明文与 b64: 值 仍能正常读出（按前缀分支解码），下次保存该数据源时自动转成密文。 配了就不要换。 更换密钥会让此前写下的密文全部解不开，… |

**通用场景**

平台型应用把外部数据源登记进台账，再用 `DatasourceHubService` 构建只读连接做采集。


### spring-support-mybatis-starter

**定位**：MyBatis-Plus 增强：分页、乐观锁、逻辑删除、数据权限、慢 SQL、XML 热加载。

**装配**：`MybatisPlusConfiguration`；慢 SQL `plugin.mybatis.slow-sql.enabled` 默认 true，XML 热加载 `plugin.mybatis.open-xml-reload` 默认 false。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `paginationInnerInterceptor` | `PaginationInnerInterceptor` | 分页 |
| `globalConfig` | `GlobalConfig` | 配置MyBatis Plus全局配置 |
| `deleteOnlySelfInterceptor` | `DeleteOnlySelfInterceptor` | 删除权限 |
| `dataPermissionInterceptor` | `DataPermissionInterceptor` | 数据权限 |
| `optimisticLockerInnerInterceptor` | `OptimisticLockerInnerInterceptor` | 乐观锁 |
| `cryptoInterceptor` | `CryptoInterceptor` | 加密 |
| `mybatisPlusInterceptor` | `MybatisPlusInterceptor` | mybatisplus拦截器 |
| `xmlReload` | `Reload` | SQL拦截器 |
| `supportInjector` | `SupportInjector` | 支持injector |
| `slowSqlDetectInterceptor` | `SlowSqlDetectInterceptor` | 慢 SQL 检测拦截器（可选）。 |
| `mybatisEndpoint` | `MybatisEndpoint` | MyBatis端点 |
| `sqlSessionFactory` | `SqlSessionFactory` | 创建 SqlSessionFactory。 基于动态数据源和 MyBatis Plus 全局配置、拦截器进行构建。 |
| `sqlSessionTemplate` | `SqlSessionTemplate` | 创建 SqlSessionTemplate。 用于给 Mapper 提供实际使用的 SqlSession。 |
| `mybatisPlusServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |
| `sysLoggerPointcutAdvisor` | `SysLoggerPointcutAdvisor` | 系统操作日志切面：拦截标注 @SysLog 的方法，发布 SysLoggerInfo 事件（由日志服务监听落库） |

**内部依赖**：`common`、`datasource`、`swagger`

**配置**（`plugin.mybatis.data.scope.*`，`MybatisPlusDataScopeProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `table-name` | `String` | `"sys_dept"` | 表名 |
| `dept-id-column` | `String` | `"sys_dept_id"` | 字段名 |
| `dept-tree-id-column` | `String` | `"sys_dept_tree_id"` | 树标识 |
| `current-user-id-column` | `String` | `"create_by"` | 当前用户标识 |

**配置**（`plugin.mybatis.*`，`MybatisPlusProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 是否启用 |
| `open-xml-reload` | `boolean` | `true` | xml是否可热加载 |
| `read-only` | `boolean` | `false` | 是否开启只读模式 开启后所有更新、插入、删除操作都会抛出只读异常 |
| `sql-method` | `List<SqlMethodProperties>` | `—` | 映射器加载源 |
| `reload-type` | `ReloadType` | `ReloadType.AUTO` | 自动热加载方式 |
| `reload-time` | `int` | `3_000` | 自动热加载时间 |
| `reload-directories` | `List<ReloadDirectory>` | `—` | 类路径路径示例: 类路径:映射器, 类路径*:映射器/** |

<details><summary>MybatisPlusProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `sql-method[].type` | `SqlMethodType` | `—` | SQL来源 |
| `sql-method[].source` | `String` | `—` | 源信息 |
| `sql-method[].timeout` | `int` | `3_000` | watchdog超时时间 |
| `sql-method[].watchdog` | `boolean` | `—` | 是否开启检测 |
| `reload-directories[].path` | `String` | `—` | - 类路径路径: 类路径:映射器, 类路径*:映射器/** |
| `reload-directories[].watch-enabled` | `boolean` | `true` | 是否启用监听 本地路径默认启用，类路径路径在jar包中无法监听 |
| `reload-directories[].pattern` | `String` | `"**/*Mapper.xml"` | 文件匹配模式 默认为双星号斜杠星号 映射器.xml |

</details>

**配置**（`plugin.mybatis.*`，`MybatisSlowSqlProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 是否启用慢 SQL 检测。 |
| `threshold-ms` | `long` | `500` |  |
| `print` | `boolean` | `true` | 是否打印慢 SQL 日志（WARN）。 |
| `include-sql` | `boolean` | `true` | 是否在事件中携带 SQL 文本（可能包含敏感信息）。 |
| `include-parameters` | `boolean` | `false` | 是否在事件中携带参数映射信息（可能包含敏感信息）。 |
| `max-sql-length` | `int` | `2000` |  |

**通用场景**

有租户/数据权限需求时配 `plugin.mybatis.data.scope.*`；分页统一用模块注册的 `MybatisPlusInterceptor`，不要自己再建一个。


### spring-support-job-starter

**定位**：自研调度：`@Job` 与 `@Scheduled` 扫描，SCHEDULER/EXECUTOR/SINGLE 三模式，带选主与日志。

**装配**：`JobAutoConfiguration`；`plugin.job.enable` 默认 true，`plugin.job.role` 默认 SINGLE。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `jobNodeId` | `String` | 当前节点 标识 Bean, 缺省时按 主机名称-pid 推导。 |
| `jobTaskScheduler` | `TaskScheduler` | Spring 上下文任务调度器 (供 Spring 原生 @Scheduled 使用)。 |
| `jobScheduledAnnotationScanner` | `ScheduledAnnotationScanner` | 注入 Environment 供其解析 @Scheduled 里的 ${...} 占位符， 否则 cron 表达式会按字面量注册导致启动失败。 |
| `jobHandlerFactory` | `JobHandlerFactory` | 处理器注册表。 |
| `jobAnnotationScanner` | `JobAnnotationScanner` | 扫描 `@Job` 并注册到调度器。 |
| `jobSyncBus` | `JobSyncBus` | 进程内事件总线。 |
| `jobOperator` | `JobOperator` | 任务操作器：登记任务、改调度时间、启停、立即派发、删除。 调度层的唯一写入口，业务模块（采集等）与对外 HTTP 接口共用同一实现， 保证"改了时间/点了启停"在两条路径上的生效语义完全一致。 |
| `jobGovernanceService` | `JobGovernanceService` | 任务治理的公共实现 (与传输层无关)。 装配门槛与治理控制器一致：Servlet 应用 + classpath 有 spring-webmvc + 显式开启 plugin.job.manage.enable=true。宿主若要换一个认证口径 (如自带控制台会话), 复用本 bean 再挂自己的出口即可。 |
| `jobManageController` | `JobManageController` | 任务治理外部接口 (HTTP)。 装配门槛：Servlet 应用 + classpath 有 spring-webmvc + 显式开启 plugin.job.manage.enable=true。sys_job 是全局资源 (无归属列)， 出口整体要求管理员权限，故默认关闭、按需开启。 |
| `emptyJobLogRepository` | `JobLogRepository` | 执行日志读取侧默认实现 (不落库时返回空页), MyBatis-Plus 存在时被 MyBatis 版覆盖。 |
| `mybatisJobLogRepository` | `JobLogRepository` | MyBatis-Plus 版执行日志读取侧 (依赖 MyBatis-Plus 时启用, 业务侧无需自行声明)。 |
| `inMemoryJobRepository` | `JobRepository` | 任务仓库默认实现 (入内存 兜底), MyBatis-Plus 存在时被 MyBatis 版覆盖。 |
| `jobLeaderElector` | `LeaderElector` | Leader 选举器 (CAS 占位)。 |
| `stdoutJobLogSink` | `JobLogSink` | 日志落库默认实现 (stdout 兜底), MyBatis-Plus 存在时被 MyBatis 版覆盖。 |
| `mybatisJobRepository` | `JobRepository` | MyBatis-Plus 任务仓库 (依赖 MyBatis-Plus 时启用, 覆盖默认 入内存)。 启用条件: classpath 含 com.baomidou.mybatisplus.core.mapper.BaseMapper; 业务侧无需自行声明 作业仓库。 |
| `mybatisJobLogSink` | `JobLogSink` | MyBatis-Plus 任务日志落库 (覆盖默认 stdout)。 当 plugin.job.log.enabled=true 时额外启用文件日志和异步写入。 |
| `inMemoryLeaderElectionResolver` | `com.chua.starter.job.support.cluster.LeaderElectionResolver` | Leader 选举解析器默认实现: 本地 CAS 占位, 适用于单机或无 MyBatis 场景。 |
| `mysqlLeaderElectionResolver` | `com.chua.starter.job.support.cluster.LeaderElectionResolver` | MyBatis-Plus 行锁版 Leader 选举 (依赖 MyBatis-Plus 时覆盖默认)。 |
| `jobSyncServer` | `SyncServer` | 调度器端注册到真实同步服务端。 |
| `jobSchedulerSyncBinder` | `SchedulerSyncBinder` | 调度器 角色: bind 同步, 把 bus 的 CMD 推到远端, 并落库 任务-日志。 |
| `jobGlueSyncService` | `GlueSyncService` | 脚本正文同步服务（调度中心侧）：保存/执行器上线时推送正文，派发命令只带版本号。 |
| `remoteHandlerRegistry` | `RemoteHandlerRegistry` | 执行器处理器清单注册表（调度中心侧）。 执行器上报 handler 清单后，调度中心据此校正"未注册"判定与执行器列表； 单机/执行器角色下注册表恒为空，不影响原有语义。 |
| `jobLeaderLoop` | `JobLeaderLoop` | 调度器 角色: 通过 SPI 抢占 Leader + 启动续约心跳 + 单秒循环。 三次装配顺序: com.chua.starter.job.support.cluster.LeaderElectionResolver 抢占 Leader; 抢占成功时 ClusterElectionHeartbeat 注册为 Spring Bean 持续续约; JobLeaderLoop 启动单秒扫描循环。 |
| `jobClusterElectionHeartbeat` | `com.chua.starter.job.support.cluster.ClusterElectionHeartbeat` | 调度器 角色: Leader 续约心跳守护线程。 |
| `jobSyncClient` | `SyncClient` | 执行器端装配真实同步客户端。 |
| `jobExecutorSyncBinder` | `ExecutorSyncBinder` | 执行器 角色: 绑定 同步客户端 与本地事件总线。 |
| `jobExecutor` | `JobExecutor` | 执行器 角色: 任务执行器。 |
| `jobSingleLeaderLoop` | `JobLeaderLoop` | 单个 角色: 本机自认 Leader, 通过本地 CAS 解析器 (MyBatis 未启用时)。 |
| `jobSingleClusterHeartbeat` | `com.chua.starter.job.support.cluster.ClusterElectionHeartbeat` | 单个 角色心跳守护线程 (与 调度器 共享同一实现)。 |
| `jobSingleExecutor` | `JobExecutor` | 单个 角色: 本地执行器, 消费本地 bus CMD 事件并执行。 |
| `jobSingleLogBinder` | `LocalJobLogBinder` | 单个 角色: 本机执行日志落地桥。 执行结果只发到本地总线, 而 TASK_LOG 的订阅者只在 调度器 角色装配; 缺这座桥时单机角色自己的每次执行都不会进 sys_job_log。 |
| `jobSampleSeeder` | `JobSampleSeeder` | 启动时把已注册 处理器 同步到内存仓库 (仅仓库为 入内存 时生效)。 |

**内部依赖**：`common`、`mybatis`、`datasource`

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `JobManageController` | `/v2/job` | 任务治理外部接口 (HTTP, 平台管理员鉴权)。 与进程内 JobOperator 同一套语义, 实现全部落在 JobGovernanceService, 本类只做路径绑定。调度配置落库后立即失效… |

**SPI 扩展点**：`JobScanCache`、`LeaderElectionResolver`、`JobRepository`、`GlueSynthesizer`、`JobLogRepository`、`JobLogSink`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`plugin.job.*`，`JobProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 是否启用调度模块,默认 true,引入即开箱即用 |
| `role` | `Role` | `Role.SINGLE` | 进程角色,默认 单个 (单机自闭环,开箱即用) |
| `node-id` | `String` | `—` | 当前节点唯一标识,留空时自动按 主机名称-pid 推导 |
| `cluster` | `ClusterConfig` | `new ClusterConfig()` | 集群配置 |
| `sync` | `SyncConfig` | `new SyncConfig()` | 长链同步配置 (嵌套 调度器/执行器) |
| `pool` | `PoolConfig` | `new PoolConfig()` | 执行线程池配置 |
| `log` | `LogConfig` | `new LogConfig()` | 日志配置 |
| `manage` | `ManageConfig` | `new ManageConfig()` | 外部治理接口配置 (HTTP /v2/job/**) |
| `cache` | `CacheConfig` | `new CacheConfig()` | 查询缓存配置 |

<details><summary>JobProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `cache.enabled` | `boolean` | `false` | 是否启用查询缓存（默认关闭，行为与历史一致） |
| `cache.ttl-ms` | `long` | `5000L` | 扫描结果缓存有效期（毫秒），默认 5000 允许阈值与 ClusterConfig#DEFAULT_PRE_READ_MS 同号：候选窗口越宽， 这个窗口的扫描结果越稳定，越值得复用。 |
| `cluster.group` | `String` | `"default"` | 命名空间, 同一 群体 内 Leader 唯一 本值是作业归属口径: Leader 只扫描 sys_job_group = group 的任务行, 与 sys_job.sys_job_group 对齐。不要拿它当选主租约的键, 见 #electionNamespace。 |
| `cluster.resolver` | `String` | `"mysql"` | 选主实现（SPI 键, 大小写不敏感）: 默认 mysql 走数据库租约; 注册在 META-INF/extensions/com.chua.starter.job.support.cluster.LeaderElectionResolver。 留空或取不到该键时回落数据库实现, 保证默认路径与历史行为一致 —— 选非 DB 实现的人自带其基础设施（如 Redis），不强制单机用户引入额外中间件。 |
| `cluster.election-namespace` | `String` | `—` | 选主租约命名空间, 留空时按 #resolveElectionNamespace(String) 推导。 与 #group 分工的原因: 选主租约在 sys_job_cluster 里以本值为主键, 同一行只能有一个 Leader。把「作业分组」直接当租约键时, 两个彼此独立、恰好都叫 default 的部署会抢同一条租约行, 互相把对方踢下 Leader, 表现为两边任务都只跑一半拍。 作业分… |
| `cluster.scan-interval-ms` | `long` | `DEFAULT_SCAN_INTERVAL_MS` | 单秒扫描间隔, 默认 1000ms |
| `cluster.pre-read-ms` | `long` | `DEFAULT_PRE_READ_MS` | 预读时间窗, 默认 5000ms |
| `cluster.lease-seconds` | `int` | `DEFAULT_LEASE_SECONDS` | Leader 抢占租约时长, 默认 15s |
| `sync.in-memory` | `boolean` | `false` | 兼容旧配置项；设置为 true 时明确拒绝启动，不提供内存同步伪实现。 |
| `sync.scheduler` | `SchedulerConfig` | `new SchedulerConfig()` | 调度中心侧配置 (角色=调度器 或 单个 时生效, 单个 仅占位) |
| `sync.executor` | `ExecutorConfig` | `new ExecutorConfig()` | 执行器侧配置 (角色=执行器 时生效) |
| `sync.scheduler.listen-host` | `String` | `"0.0.0.0"` | 监听主机 |
| `sync.scheduler.listen-port` | `int` | `DEFAULT_LISTEN_PORT` | 监听端口 |
| `sync.scheduler.protocol` | `String` | `DEFAULT_PROTOCOL` | 长链协议 (tcp / websocket / udp / http / rsocket) 按本值优先挑 SyncServer SPI 实现，挑不到再按内置兜底顺序回退。 |
| `sync.executor.server-url` | `String` | `—` | 调度中心长链 URL, 例如 ws://10.0.0.11:8081 |
| `sync.executor.reconnect-base-ms` | `int` | `DEFAULT_RECONNECT_BASE_MS` | 重连退避起点 |
| `sync.executor.reconnect-max-ms` | `int` | `DEFAULT_RECONNECT_MAX_MS` | 重连退避上限 |
| `pool.core-size` | `int` | `DEFAULT_CORE_SIZE` | 核心线程数 |
| `pool.max-size` | `int` | `DEFAULT_MAX_SIZE` | 最大线程数 |
| `pool.queue-size` | `int` | `DEFAULT_QUEUE_CAPACITY` | 任务队列容量 |
| `pool.virtual` | `boolean` | `true` | 是否启用虚拟线程执行器, 默认开启。 开启时使用 Executors.newVirtualThreadPerTaskExecutor() (每任务一个虚拟线程, 阻塞型任务大幅提升吞吐, 无 最大/队列 限制); 关闭时回退到 ThreadPoolExecutor (核心/最大/队列 平台线程池)。 |
| `log.enabled` | `boolean` | `false` | 是否启用文件日志 |
| `log.base-path` | `String` | `DEFAULT_BASE_PATH` | 日志根目录 |
| `log.async` | `boolean` | `true` | 是否启用异步写入 |
| `log.queue-size` | `int` | `DEFAULT_QUEUE_SIZE` | 异步队列容量 |
| `log.cleanup-enabled` | `boolean` | `false` | 是否启用日志自动清理 |
| `log.cleanup-retention-days` | `int` | `DEFAULT_RETENTION_DAYS` | 日志保留天数 (超过该天数的日志将被清理) |
| `log.cleanup-cron` | `String` | `DEFAULT_CLEANUP_CRON` | 日志清理定时 cron 表达式 |
| `manage.enable` | `boolean` | `false` | 是否暴露任务治理 HTTP 接口, 默认 false (需应用显式开启) |

</details>

**通用场景**

单机直接 `@Job` 即可；集群部署一个 SCHEDULER + 多个 EXECUTOR，`plugin.job.manage.enable=true` 暴露治理接口。


### spring-support-queue-starter

**定位**：队列抽象层，SPI 适配 Kafka/RabbitMQ/RocketMQ/MQTT/Local，带拦截器、重试、死信。

**装配**：`QueueAutoConfiguration`；`plugin.queue.enable=true`（默认关），`plugin.queue.type` 选实现。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `deadLetterTemplate` | `DeadLetterTemplate` | 死信队列消息模板 |
| `localMessageTemplate` | `MessageTemplate` | 本地消息队列模板（基于Guava 事件bus） 当配置类型=本地时启用，基于Guava 事件bus实现。 |
| `messageListenerBeanPostProcessor` | `MessageListenerBeanPostProcessor` | 消息监听注解处理器 |
| `queueServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |

**内部依赖**：`common`

**配置**（`plugin.dead-letter.*`，`DeadLetterProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `type` | `String` | `"chronicle-queue"` | 队列类型（chronicle-队列/本地/MQTT/Kafka/RabbitMQ/RocketMQ） |
| `delay-threads` | `int` | `2` | 延迟消息调度线程数 |
| `max-retries` | `int` | `3` | 最大重试次数 |
| `retry-delay-seconds` | `long` | `5` | 重试延迟（秒） |
| `max-retry-delay-seconds` | `long` | `300` |  |
| `exponential-backoff` | `boolean` | `true` | 是否启用指数退避 |
| `backoff-multiplier` | `double` | `2.0` | 退避乘数 |

**配置**（`plugin.queue.*`，`QueueProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `type` | `String` | `"chronicle-queue"` | 消息队列类型（chronicle-队列/本地/MQTT/Kafka/RabbitMQ/RocketMQ/NATS） |
| `default-destination` | `String` | `—` | 默认目标地址 |
| `local` | `LocalConfig` | `new LocalConfig()` | 本地队列配置（基于Guava 事件bus） |
| `mqtt` | `MqttConfig` | `new MqttConfig()` | MQTT配置 |
| `kafka` | `KafkaConfig` | `new KafkaConfig()` | Kafka配置 |
| `rabbitmq` | `RabbitMQConfig` | `new RabbitMQConfig()` | RabbitMQ配置 |
| `nats` | `NatsConfig` | `new NatsConfig()` | NATS配置 |
| `rocketmq` | `RocketMQConfig` | `new RocketMQConfig()` | RocketMQ配置 |

<details><summary>QueueProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `local.delay-threads` | `int` | `2` | 延迟消息调度线程数 |
| `mqtt.address` | `String` | `"tcp://localhost:1883"` | 服务器地址 |
| `mqtt.client-id` | `String` | `—` | 客户端标识 |
| `mqtt.username` | `String` | `—` | 用户名 |
| `mqtt.password` | `String` | `—` | 密码 |
| `mqtt.qos` | `int` | `1` | qos级别 |
| `mqtt.connection-timeout` | `int` | `30` |  |
| `mqtt.keep-alive-interval` | `int` | `60` |  |
| `kafka.bootstrap-servers` | `String` | `"localhost:9092"` | Bootstrap服务器 |
| `kafka.group-id` | `String` | `"default-group"` | 消费者组标识 |
| `kafka.auto-offset-reset` | `String` | `"earliest"` | 自动偏移重置策略 |
| `kafka.enable-auto-commit` | `boolean` | `true` | 是否自动提交 |
| `kafka.auto-commit-interval-ms` | `int` | `5000` |  |
| `kafka.acks` | `String` | `"all"` | 生产者确认机制 |
| `rabbitmq.host` | `String` | `"localhost"` | 主机地址 |
| `rabbitmq.port` | `int` | `5672` |  |
| `rabbitmq.username` | `String` | `""` | 用户名 |
| `rabbitmq.password` | `String` | `""` | 密码 |
| `rabbitmq.virtual-host` | `String` | `"/"` | 虚拟主机 |
| `rabbitmq.connection-timeout` | `int` | `60000` |  |
| `nats.url` | `String` | `"nats://localhost:4222"` | 服务器地址 |
| `nats.username` | `String` | `—` | 用户名 |
| `nats.password` | `String` | `—` | 密码 |
| `nats.token` | `String` | `—` | 令牌认证 |
| `nats.consumer-group` | `String` | `"default-group"` | 消费者组 |
| `nats.connection-timeout` | `long` | `5000` |  |
| `nats.reconnect-wait` | `long` | `2000` |  |
| `nats.max-reconnects` | `int` | `60` |  |
| `rocketmq.name-server` | `String` | `"localhost:9876"` | 名称服务端地址 |
| `rocketmq.producer-group` | `String` | `"default-producer"` | 生产者组 |
| `rocketmq.consumer-group` | `String` | `"default-consumer"` | 消费者组 |
| `rocketmq.send-msg-timeout` | `int` | `3000` |  |
| `rocketmq.max-message-size` | `int` | `4194304` |  |

</details>

**通用场景**

业务只依赖队列接口；换 MQ 时改 `plugin.queue.type`，不要改业务代码。


### spring-support-message-starter

**定位**：消息模板与多渠道发送（短信/邮件/站内信等渠道由 Provider 提供）。

**装配**：`MessageAutoConfiguration`；`plugin.message.track.*` 记录发送轨迹。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `messageServiceInfoContributor` | `ServiceInfoContributor` | 注册消息模块的服务信息贡献者 |
| `stationMessagePusherInitializer` | `ApplicationRunner` | 注入站内消息渠道的 Socket 推送执行器 仅在引入 Socket 服务（存在 SocketSessionTemplate）时生效， 将站内消息转发到目标用户的所有在线会话。 |

**内部依赖**：`common`、`mybatis`、`account`、`socket`、`oauth-client`

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `SysMessageController` | `/v2/message` | 消息推送接口 提供消息提供商、消息模板的管理接口，以及统一的消息发送接口。 默认 bean 名 sysMessageController，与 com.chua.starter.system.cont… |

**组件扫描 Service**：`SysMessageServiceImpl`（共 1 个）。`@Service` 注册，可直接注入。

**配置**（`plugin.message.track.*`，`MessageTrackProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `false` | 是否开启阅读追踪 默认值: false |
| `base-url` | `String` | `—` | 追踪地址基础路径 用于拼接追踪图地址，缺省时自动回退为当前服务地址。 |

**通用场景**

发送前先落模板，渠道差异交给 Provider；需要回执就开 `plugin.message.track.enabled`。


### spring-support-sync-starter

**定位**：`SyncProtocol` 双向同步，支持 WebSocket/HTTP/RSocket/Socket.IO。

**装配**：`SyncAutoConfiguration`；`plugin.sync.*`，数据同步子能力用 `plugin.sync.data-sync.enabled`/`plugin.sync.datasync.enabled`。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `syncServer` | `SyncServer` | 创建同步服务端 条件：类型 为 服务端 或 both，且 服务端.enable = true |
| `syncClient` | `SyncClient` | 创建同步客户端 条件：类型 为 客户端 或 both，且 客户端.enable = true |
| `syncDataExchangeService` | `SyncDataExchangeService` | 创建数据同步交换服务。 |
| `syncServiceInfoContributor` | `ServiceInfoContributor` | 注册同步模块服务信息。 |
| `dataSyncMessageBridge` | `DataSyncMessageBridge` | 创建 Spring 同步 -> 数据同步 数据桥接 |
| `dataSyncMessagePublisher` | `DataSyncMessagePublisher` | 创建 数据同步 -> Spring 同步 消息发布器 |

**内部依赖**：`common`、`job`、`swagger`

**SPI 扩展点**：`SyncServer`、`SyncClient`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`plugin.sync.datasync.*`，`SyncDatasyncProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 是否启用 数据同步 桥接 |

**配置**（`plugin.sync.*`，`SyncProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `type` | `String` | `"client"` | 程序类型：服务端-服务端，客户端-客户端，both-同时启用 |
| `topics` | `Map<String, String>` | `new LinkedHashMap<>()` | 主题与处理器映射 (全局) |
| `server` | `ServerConfig` | `new ServerConfig()` | 服务端配置 |
| `client` | `ClientConfig` | `new ClientConfig()` | 客户端配置 |
| `crypto-secret` | `String` | `—` | 同步消息传输加密密钥。 服务端与客户端需配置一致。配置后，消息 payload 在传输层自动 AES-256-GCM 加密， 每条消息用随机盐经 HKDF-SHA256 派生独立密钥，使用方（业务处理器）无感知； 未配置时明文传输（向后兼容）。 加密或解密失败会抛异常，不会再静默降级为明文。旧版本（固定密钥 + 随机 IV） 的密文仍可读，因此升级时先升级接收端、再升级发送端即可平滑过渡。 |
| `crypto-version` | `Integer` | `2` | 同步消息加密版本。 2：每条消息随机盐 + HKDF 派生独立密钥（默认，推荐）； 1：旧版固定密钥，仅用于滚动升级过渡。 升级顺序：新节点先设为 1 与旧节点互通，全部升级完成后再统一改为 2。 解密端始终同时兼容 1 与 2。 |
| `data-sync` | `DataSyncConfig` | `new DataSyncConfig()` | 数据同步交换配置 |

<details><summary>SyncProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `server.enable` | `boolean` | `false` | 是否启用服务端 |
| `server.instances` | `List<ServerInstance>` | `new ArrayList<>()` | 服务实例列表 (支持多端口) |
| `server.host` | `String` | `"0.0.0.0"` | 默认绑定地址 |
| `server.port` | `int` | `19380` | 默认端口 (当 instances 为空时使用) |
| `server.protocol` | `String` | `"http"` | 默认协议 |
| `server.service-name` | `String` | `—` | 服务发现名：非空时服务端启动后注册到发现中心。 客户端按同名查找，因此两端通常配置成同一个值。为空表示不注册。 |
| `server.scatter-id` | `String` | `—` | 业务分组标识（scatterId）：与服务端注册时写入的 scatterId 一致。 为空时回落到 serviceName。 |
| `server.heartbeat` | `boolean` | `true` | 心跳开关 |
| `server.heartbeat-interval` | `int` | `30` | 心跳间隔（秒） |
| `server.connect-timeout` | `int` | `10000` | 连接超时时间（毫秒） |
| `server.instances[].name` | `String` | `UUID.randomUUID().toString()` | 实例名称 (用于标识) |
| `server.instances[].host` | `String` | `"0.0.0.0"` | 绑定地址 |
| `server.instances[].port` | `int` | `19380` | 端口 |
| `server.instances[].protocol` | `String` | `"rsocket-sync"` | 协议类型 |
| `server.instances[].enable` | `boolean` | `true` | 是否启用 |
| `server.instances[].description` | `String` | `—` | 描述 |
| `server.instances[].topics` | `Map<String, String>` | `new LinkedHashMap<>()` | 该实例的主题映射 (可覆盖全局) |
| `client.enable` | `boolean` | `false` | 是否启用客户端 |
| `client.instance-id` | `String` | `—` | 实例标识 (默认自动生成) |
| `client.ip-address` | `String` | `—` | 客户端 IP 地址 多网卡场景下指定使用的 IP 地址，为空则自动获取 |
| `client.protocol` | `String` | `"http"` | 协议类型 |
| `client.server-host` | `String` | `"localhost"` | 服务端地址 |
| `client.server-port` | `int` | `19380` | 服务端端口 |
| `client.server-address` | `String` | `—` | 服务端地址 (完整格式，优先级高于 服务端主机:服务端端口) 格式：主机:端口 或 ws://主机:端口 |
| `client.discovery-name` | `String` | `—` | 服务发现名：非空时按该名称从发现中心解析服务端地址，解析不到才用 serverAddress / 服务端主机:端口。 取值与服务端注册时使用的 service-name 保持一致（默认就是本端注册的服务名）， 两边相同才能互相找到。协议名（如 redis/nacos）由 discovery 的 bean 名决定，不在这里配置。 |
| `client.scatter-id` | `String` | `—` | 业务分组标识（scatterId）：同一服务名下按业务集群隔离。 为空时回落到 discoveryName，保证零配置下客户端与服务端落在同一分组。 |
| `client.heartbeat` | `boolean` | `true` | 心跳开关 |
| `client.heartbeat-interval` | `int` | `30` | 心跳间隔（秒） |
| `client.connect-timeout` | `int` | `10000` | 连接超时时间（毫秒） |
| `client.auto-reconnect` | `boolean` | `true` | 是否启用自动重连 |
| `client.max-reconnect-attempts` | `int` | `-1` | 最大重连次数，-1表示无限重连 |
| `client.reconnect-interval` | `long` | `5000` | 重连间隔时间（毫秒） |
| `client.capabilities` | `String[]` | `new String[]{"job", "actuator", "file",…` | 支持的功能列表 |
| `client.metadata` | `Map<String, Object>` | `new HashMap<>()` | 扩展元数据 |
| `client.auto-register` | `boolean` | `true` | 连接后是否自动注册 |
| `client.register-topic` | `String` | `"sync/client/register"` | 注册主题 |
| `client.heartbeat-topic` | `String` | `"sync/client/heartbeat"` | 心跳主题 |
| `client.offline-topic` | `String` | `"sync/client/offline"` | 下线主题 |
| `data-sync.enabled` | `boolean` | `true` | 是否启用数据同步交换服务 |
| `data-sync.topic-prefix` | `String` | `"sync/data"` | 主题前缀 |
| `data-sync.request-timeout` | `long` | `30000L` | 默认请求超时时间（毫秒） |
| `data-sync.default-channel` | `String` | `"default"` | 默认逻辑通道 |

</details>

**通用场景**

端到端同步用 `SyncClient` + `SyncServer`；协议由配置选，不要写死传输层。


### spring-support-taskdistribution-starter

**定位**：任务分发的旧式装配模块（`spring.factories`，不是 imports）。

**装配**：`TaskDistributionAutoConfiguration` 通过 `META-INF/spring.factories` 的 `EnableAutoConfiguration` 注册；`plugin.taskdistribution.enabled` 默认 true。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `taskStore` | `TaskStore` | 持久化存储 Bean（按 存储类型 选择 内存 / 文件 / db）。 |
| `nodeTable` | `NodeTable` | 节点注册表 Bean。 |
| `taskManager` | `TaskManager` | 任务管理器 Bean（启动时自动恢复未完成任务）。 |
| `resultBuffer` | `ResultBuffer` | 结果缓冲器 Bean。 |
| `taskExecutorRegistry` | `TaskExecutorRegistry` | 执行器注册中心 Bean。 |
| `dispatcherProvider` | `DispatcherProvider` | 派发提供者 Bean（默认内存实现，支持批量处理）。 |
| `taskDistributionInitializer` | `ApplicationRunner` | 初始化节点 标识 和自动注册 任务执行器。 |
| `taskDistributionServiceInfoContributor` | `ServiceInfoContributor` | 任务分发的 服务信息 贡献者。 |

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `TaskDistributionController`（未确认注册） | `/v2/taskdistribution` | 任务分发管理接口。 提供任务清单、节点清单、任务状态查询、暂停/恢复/取消等管理能力。 |

**配置**（`plugin.taskdistribution.*`，`TaskDistributionProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `node-id` | `String` | `—` | 节点 标识（默认自动生成，建议显式配置） |
| `strategy` | `DispatchStrategy` | `DispatchStrategy.ROUND` | 派发策略 |
| `enabled` | `boolean` | `true` | 是否启用任务分发 |
| `batch-size` | `int` | `1` | 每次批量处理的任务数（默认 1） |
| `consumer-threads` | `int` | `1` | 消费线程数（默认 1） |
| `store-type` | `String` | `"memory"` | 持久化存储类型：内存 / 文件 / db（默认 内存） |
| `store-path` | `String` | `"./data/taskstore/"` | 文件存储数据目录（存储类型 = 文件 时生效） |
| `server` | `ServerConfig` | `new ServerConfig()` | 服务端配置 |
| `task` | `TaskConfig` | `new TaskConfig()` | 任务默认配置 |

<details><summary>TaskDistributionProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `server.enabled` | `boolean` | `true` | 是否启用服务端模式（接收任务） |
| `server.port` | `int` | `19390` |  |
| `server.host` | `String` | `"0.0.0.0"` | 监听主机 |
| `server.protocol` | `String` | `"tcp"` | 传输协议：tcp / udp |
| `task.timeout-ms` | `long` | `30000` |  |
| `task.max-retries` | `int` | `3` | 默认最大重试次数 |

</details>

**通用场景**

只在新项目里避免使用；存量项目保持 `spring.factories` 即可，升级时再迁 imports。


### spring-support-socket-starter

**定位**：Socket 门面：按协议选择 SSE/Socket.IO/RSocket，按 room 起多实例。

**装配**：`SocketConfiguration`；`plugin.socket.enable=true`（默认关），`plugin.socket.protocol` 选协议，`room` 配多实例。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `socketSessionTemplates` | `Map<String, SocketSessionTemplate>` | 创建多协议 Socket 会话模板集合 根据配置的协议类型通过 SPI 获取对应的提供者 |
| `socketSessionTemplate` | `SocketSessionTemplate` | 创建单一协议的会话模板（兼容旧代码） 返回第一个协议的模板作为默认模板 |
| `socketLifecycle` | `SocketLifecycle` | Socket 生命周期管理 |
| `socketServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |

**内部依赖**：`common`、`queue`

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `SocketServerController` | `/socket` | Socket 服务器信息获取接口 前端通过此接口获取所有在线 Socket 服务器地址列表 |

**组件扫描 Service**：`SocketServerRegistry`（共 1 个）。`@Service` 注册，可直接注入。

**配置**（`plugin.socket.*`，`SocketProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `—` | 是否开启 |
| `auto-start` | `boolean` | `true` | 是否在 Spring 容器启动阶段自动启动 Socket 服务 关闭后仍会创建对应的 com.chua.socket.support.session.SocketSessionTemplate Bean， 但不会在应用启动时自动监听端口，适合由外部逻辑或运维脚本显式控制生命周期。 |
| `room` | `List<Room>` | `new ArrayList<>()` | 房间配置列表 支持配置多个独立的 Socket 服务实例，每个实例可以有不同的端口和路径 示例配置： plugin: socket: enable: true room: - client-id: default port: 9000 - client-id: webrtc context-path: /webrtc port: 9001 - client-id: shell port: 9002 |
| `protocol` | `SocketProtocol` | `SocketProtocol.SSE` | 协议类型，默认 sse 可选值：Socket.IO, rsocket, sse |
| `protocols` | `List<ProtocolConfig>` | `new ArrayList<>()` | 多协议配置列表（可选） 支持同时启用多种协议，每种协议可独立配置房间 如果配置了此项，将忽略单一的 协议 和 room 配置 示例配置： plugin: socket: enable: true protocols: - protocol: socketio room: - port: 9000 - protocol: rsocket room: - port: 9001 |
| `host` | `String` | `"0.0.0.0"` | 本地IP |
| `codec-type` | `String` | `"json"` | 编解码器类型 |
| `encrypt-enabled` | `Boolean` | `—` | 是否开启数据加密 |
| `encrypt-mode` | `SocketEncryptMode` | `—` | 数据加密模式，默认自动协商且优先加密 |
| `encrypt-key` | `String` | `—` | 加密密钥 |
| `max-frame-size` | `int` | `1048576` | 最大帧大小（字节），默认1MB |
| `boss-count` | `int` | `1` | Boss 线程数 |
| `work-count` | `int` | `100` | 工作线程数 |
| `allow-custom-requests` | `boolean` | `true` | 是否允许自定义请求 |
| `ping-timeout` | `int` | `60_000` | Ping 消息超时时间（毫秒），默认60秒 |
| `ping-interval` | `int` | `25_000` | Ping 消息间隔（毫秒），默认25秒 |
| `auth-factory` | `String` | `—` | 认证工厂类名 |
| `use-linux-native-epoll` | `boolean` | `—` | 是否使用 Linux 的 NAT Epoll |

<details><summary>SocketProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `protocols[].protocol` | `SocketProtocol` | `SocketProtocol.SOCKETIO` | 协议类型 |
| `protocols[].room` | `List<Room>` | `new ArrayList<>()` | 房间配置列表 |
| `room[].client-id` | `String` | `"default"` | 客户端标识，用于区分不同的服务实例 |
| `room[].context-path` | `String` | `"/socket.io"` | 上下文路径，如 /WebRTC、/Shell 等 |
| `room[].port` | `int` | `-1` | 端口号 正数：使用指定端口启动独立服务 -1：使用主配置的端口 -2, -3...：使用主配置端口 + 偏移量（如主端口9000，-2表示9001） |
| `room[].host` | `String` | `—` | 本地IP（可选，不设置则使用主配置） |
| `room[].auth-factory` | `String` | `—` | 认证工厂类名（可选，不设置则使用主配置） |
| `room[].enable` | `boolean` | `true` | 是否启用此房间 |
| `protocols[].room[].client-id` | `String` | `"default"` | 客户端标识，用于区分不同的服务实例 |
| `protocols[].room[].context-path` | `String` | `"/socket.io"` | 上下文路径，如 /WebRTC、/Shell 等 |
| `protocols[].room[].port` | `int` | `-1` | 端口号 正数：使用指定端口启动独立服务 -1：使用主配置的端口 -2, -3...：使用主配置端口 + 偏移量（如主端口9000，-2表示9001） |
| `protocols[].room[].host` | `String` | `—` | 本地IP（可选，不设置则使用主配置） |
| `protocols[].room[].auth-factory` | `String` | `—` | 认证工厂类名（可选，不设置则使用主配置） |
| `protocols[].room[].enable` | `boolean` | `true` | 是否启用此房间 |

</details>

**通用场景**

一个应用要同时提供多种实时协议时用 `protocols` 列表；单协议用 `protocol` + `room`。


### spring-support-socket-websocket-starter

**定位**：Spring 原生 WebSocket 端点、会话管理与 P2P 房间信令。

**装配**：`WebSocketAutoConfiguration`；`plugin.websocket.enable=true`（默认关），端点由 `plugin.websocket.endpoints` 控制。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `webSocketSessionManager` | `WebSocketSessionManager` | WebSocket 会话的注册、查找与清理。 |
| `createWebSocketContainer` | `ServletServerContainerFactoryBean` | Servlet 容器级 WebSocket 参数（缓冲区/超时）。 |
| `webSocketServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |

**内部依赖**：`sync`、`socket`

**组件扫描 Service**：`RoomWebSocketHandler`（共 1 个）。`@Service` 注册，可直接注入。

**配置**（`plugin.websocket.*`，`WebSocketProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `endpoints` | `List<String>` | `new ArrayList<>(List.of("/ws"))` | webSocket 端点路径列表 |
| `allowed-origins` | `List<String>` | `new ArrayList<>(List.of("*"))` | 允许的源（跨域资源共享） |
| `sock-js` | `boolean` | `false` | 是否启用 SockJS 支持 |
| `heartbeat-interval` | `long` | `25000` |  |
| `max-message-size` | `int` | `65536` |  |
| `send-buffer-size-limit` | `int` | `524288` |  |
| `send-time-limit` | `int` | `10000` |  |

**配置**（`room.*`，`RoomProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用房间信令模块 */ |
| `endpoint` | `String` | `"/api/rooms/{roomId}/ws"` | 房间信令 WebSocket 端点（由协议层使用，实际端点由 plugin.websocket.endpoints 控制） */ |
| `max-peers` | `int` | `2` | 每房间最大参与端点数（P2P = 2） */ |
| `timeouts` | `RoomTimeouts` | `new RoomTimeouts()` | 房间超时配置 */ |
| `stun-server` | `String` | `""` | STUN 服务器（内网发现） */ |
| `turn-server` | `String` | `""` | TURN 服务器 URI（中继失败时备用） */ |
| `turn-user` | `String` | `""` | TURN 用户 */ |
| `turn-password` | `String` | `""` | TURN 密码 */ |

<details><summary>RoomProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `timeouts.lease-expires-ms` | `long` | `30_000` | 端点续期时间（ms） - 心跳未到达则端点断线 */ |
| `timeouts.remove-empty-room-ms` | `long` | `240_000` | 无参与者房间回收时间（ms） */ |

</details>

**通用场景**

浏览器原生 WebSocket 直连用这个；需要 SockJS 时把 `plugin.websocket.sock-js=true`。


### spring-support-socket-io-starter

**定位**：Socket.IO 协议适配（事件、房间、广播）。

**装配**：`AutoConfiguration.imports` 只有一行注释、不注册 Bean；由 `spring-support-socket-starter` 按 `plugin.socket.protocol=socketio` 经 SPI 拉起。

**公开 Bean**：本模块没有显式 `@Bean`；Socket.IO 协议实现由 `socket-starter` 的 SPI 拉起。

**内部依赖**：`socket`、`sync`

**SPI 扩展点**：`SocketProvider`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**：本模块没有 `@ConfigurationProperties`。

**通用场景**

前端用 socket.io-client 时选这个协议；不要同时再引一套独立 Socket.IO 服务端。


### spring-support-socket-sse-starter

**定位**：SSE 事件总线与服务端单向推送。

**装配**：imports 直接注册 `SseTemplate`、`DefaultSseEventBusConfigurer`、`SseServiceInfoConfiguration`，无类级开关。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `sseServiceInfoContributor` | `ServiceInfoContributor` | 注册 SSE 模块服务信息。 |

**内部依赖**：`socket`

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `SseSocketController`（未确认注册） | `/sse` | SSE Socket 连接控制器 提供 SSE 协议的连接端点 |

**SPI 扩展点**：`SocketProvider`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**：本模块没有 `@ConfigurationProperties`。

**通用场景**

只需要服务端推、客户端不收流的场景（通知、进度）用 SSE，比 WebSocket 省连接管理。


### spring-support-socket-rsocket-starter

**定位**：RSocket 响应式双向通信（注解与会话管理）。

**装配**：`AutoConfiguration.imports` 只有一行注释、不注册 Bean；由 `spring-support-socket-starter` 按 `plugin.socket.protocol=rsocket` 经 SPI 拉起。

**公开 Bean**：本模块没有显式 `@Bean`；RSocket 协议实现由 `socket-starter` 的 SPI 拉起。

**内部依赖**：`socket`、`sync`

**配置**：本模块没有 `@ConfigurationProperties`。

**通用场景**

服务间高吞吐双向流用 RSocket；浏览器端仍走 WebSocket/SSE。


### spring-support-rpc-starter

**定位**：RPC 服务暴露与引用，传输/序列化由 SPI 决定（默认 Dubbo 类型）。

**装配**：`RpcServiceAnnotationBeanPostProcessor`、`RpcResourceAnnotationBeanPostProcessor`、`RpcAutoConfiguration`；`plugin.rpc.enable` 默认 true。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `rpcServiceInfoContributor` | `ServiceInfoContributor` | 注册 RPC 模块服务信息。 |

**内部依赖**：`common`

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `RpcMonitorController`（未确认注册） | `/v2/rpc/monitor` | RPC 监控端点，暴露当前 RPC 服务端的连接信息与运行时指标。 GET /v2/rpc/monitor/info — 服务端基本信息（协议、服务列表） GET /v2/rpc/monitor/c… |

另有 1 个 `@Service` 标注的类未在自动配置里找到注册路径，需宿主扫描对应包才能注入。

**SPI 扩展点**：`RpcClient`、`RpcServer`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`plugin.rpc.*`，`RpcProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `scan` | `Set<String>` | `—` | 扫描 |
| `access-key` | `String` | `""` | ak，从配置项 rpc.access-Key 注入，严禁硬编码 |
| `secret-key` | `String` | `""` | sk，从配置项 rpc.secret-Key 注入，严禁硬编码 |
| `service-key` | `String` | `"98bb5008c946bdce065af13070241e10"` | 服务序列 |
| `crypto` | `String` | `"aes"` | 加密算法 |
| `type` | `RpcType` | `RpcType.DUBBO` | 实现 |
| `protocols` | `List<RpcProtocolConfig>` | `—` | 协议 |
| `application-name` | `String` | `"${spring.application.name:app}"` | 应用 |
| `consumer` | `RpcConsumerConfig` | `new RpcConsumerConfig()` | 消费者 |
| `registry` | `List<RpcRegistryConfig>` | `—` | 注册器 |

**通用场景**

内部服务用 `@RpcService` 暴露、`@RpcResource` 注入；协议在 `plugin.rpc.type` 与 `protocols` 里选。


### spring-support-ssh-starter

**定位**：内嵌 SSH 服务端：命令执行、文件传输、会话与安全策略。

**装配**：`SshAutoConfiguration`（`plugin.ssh.server.enabled` 条件，matchIfMissing=true，默认会创建 Bean）；但监听要字段 `plugin.ssh.server.enabled=true` 才发生——字段默认 false，`SshServerService` 在 `afterPropertiesSet` 里判断。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `sshServerService` | `SshServerService` | SSH服务端服务Bean |
| `sshServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |

**内部依赖**：`common`

**配置**（`plugin.ssh.server.*`，`SshServerProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `enabled` | `boolean` | `false` | 是否启用SSH服务端 |
| `host` | `String` | `"0.0.0.0"` | SSH服务端监听地址 |
| `port` | `int` | `2222` |  |
| `authentication` | `Authentication` | `new Authentication()` | 认证配置 |
| `session` | `Session` | `new Session()` | 会话配置 |
| `file-transfer` | `FileTransfer` | `new FileTransfer()` | 文件传输配置 |
| `security` | `Security` | `new Security()` | 安全配置 |

<details><summary>SshServerProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `authentication.password` | `boolean` | `true` | 是否启用密码认证 |
| `authentication.public-key` | `boolean` | `false` | 是否启用公钥认证 |
| `authentication.username` | `String` | `"admin"` | 默认用户名 |
| `authentication.user-password` | `String` | `""` | 默认密码（通过配置文件覆盖） |
| `authentication.public-key-file` | `String` | `—` | 公钥文件路径 |
| `authentication.private-key-file` | `String` | `—` | 私钥文件路径 |
| `session.timeout` | `long` | `3600` |  |
| `session.max-sessions` | `int` | `10` |  |
| `session.idle-timeout` | `long` | `1800` |  |
| `file-transfer.scp-enabled` | `boolean` | `true` | 是否启用SCP |
| `file-transfer.sftp-enabled` | `boolean` | `true` | 是否启用SFTP |
| `file-transfer.root-directory` | `String` | `"/tmp"` | 文件传输根目录 |
| `security.allowed-ips` | `List<String>` | `new ArrayList<>()` | 允许的IP地址列表 |
| `security.denied-ips` | `List<String>` | `new ArrayList<>()` | 禁止的IP地址列表 |
| `security.max-login-attempts` | `int` | `3` | 最大登录尝试次数 |
| `security.lockout-duration` | `long` | `300` |  |
| `security.host-key-verification` | `boolean` | `true` | 是否启用主机密钥验证 |

</details>

**通用场景**

运维通道用 SSH 而不是自研 TCP；生产必须改默认用户名密码并限制 `max-sessions`。


### spring-support-filesystem-starter

**定位**：多 Provider 对象存储 + 内嵌 HTTP 文件服务器（上传、预览、协同）。

**装配**：`FileSystemAutoConfiguration`；`plugin.filesystem.enable=true`（默认关）；文件服务器再按 `plugin.filesystem.storage-server.enable` 与 `mode=embedded|standalone`。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `fileStorageTemplate` | `FileStorageTemplate` | 创建文件存储模板。 |
| `fileObjectController` | `FileObjectController` | 创建文件对象控制器。 |
| `fileDirController` | `FileDirController` | 创建文件目录控制器。 |
| `fileServerConfigController` | `FileServerConfigController` | 创建文件服务器运行期配置控制器（前端进入文件管理页前拉取当前形态与地址）。 |
| `fileSystemServiceInfoContributor` | `ServiceInfoContributor` | 创建文件系统服务信息贡献者。 |
| `uploadTaskManager` | `UploadTaskManager` | 创建上传任务管理器（时间轮调度 + 虚拟线程分片合并）。 |
| `uploadTaskController` | `UploadTaskController` | 创建上传任务控制器（任务化分片上传）。 |
| `fileCollabStore` | `com.chua.starter.filesystem.support.collab.FileCollabStore` | 协同文档存储器（内存态，与上传任务同生命周期）。 文件资产右键「创建协同」的落库容器：快照 + 版本乐观锁 + SSE 广播。 无外部依赖，直接注册单例。 |
| `fileCollabPersistence` | `com.chua.starter.filesystem.support.collab.FileCollabPersistence` | 协同文档定时落盘。 存储器本身是内存态（编辑零阻塞），这里按 plugin.filesystem.collab.persist-seconds（默认 30s）把有改动的整库 刷到 plugin.filesystem.collab.persist-path（默认 data/file-collab/collab-store.json，相对进程工作目录）。 启动时先装载；Bean 销毁时补一次；空闲无… |
| `fileCollabHub` | `com.chua.starter.filesystem.support.collab.FileCollabHub` | 实时协同 hub（操作定序 + 日志 + presence）。 与 #fileCollabStore() 的分工：store 管「文档快照」（用户看到的 文件内容与版本号），hub 管「操作日志」（实时编辑的顺序与续接）。 两者通过 seq 对齐：客户端保存文档时把当时的 session.rev 一起报给 store，store 再让 hub 登记「内容已到 seq」。 |
| `fileCollabController` | `FileCollabController` | 协同文档控制器（/v2/file/collab）。 |
| `fileServerManager` | `FileServerManager` | 创建文件服务端管理器。 |
| `fileStorageEmbeddedFilter` | `FilterRegistrationBean<FileStorageEmbeddedFilter>` | 注册内置文件服务器过滤器：{path-prefix/*}。 |
| `fileStorageServerLauncher` | `FileStorageServerLauncher` | 创建文件存储服务器启动器。 |

**内部依赖**：`common`、`mybatis`、`datasource`、`swagger`

**HTTP 入口**（`@RestController`/`@Controller`，共 5 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `FileCollabController` | `/v2/file/collab` | 协同文档接口（/v2/file/collab）。 文件资产右键「创建协同」落库的入口：把表格文件（Univer 支持）升级为 多人实时协同文档。协同语义：快照 + 版本乐观锁 + SSE 广播——客… |
| `FileDirController` | `/v2/file/dir` | 服务器目录浏览接口（安全开关控制，默认关闭）。 提供服务器本地目录树浏览能力，供前端「上传目录 / 选择目录」选择器使用。 通过 plugin.filesystem.dir-list 开关控制是否暴… |
| `FileObjectController` | `/v2/file` | 文件对象接口（单次上传与回显）。 落盘与读取全部走 FileStorageTemplate 的 FileStorage SPI，对象键为 归一化桶名/随机文件名，文件名由服务端生成，不接受调用方命名… |
| `FileServerConfigController` | `/v2/file` | 文件服务器运行期配置接口。 前端进入文件管理页前，先请求本接口获取「文件服务器当前部署形态与连接地址」， 再决定用嵌入式相对路径还是独立端口，而不是写死 http://host:port。 返回模型… |
| `UploadTaskController` | `/v2/file/upload-task` | 上传任务接口：任务化、异步化的分片上传。 上传动作只创建任务（同步返回任务号），分片与合并全部在后台完成： 分片经 #putChunk 落本地暂存区，#complete 后由 common-star… |

**SPI 扩展点**：`FileStorage`、`FileStorageImageFilterProvider`、`FileStorageRemoteReader`、`FileStorageUrlPreviewProvider`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`plugin.filesystem.*`，`FileStorageProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用文件存储模块 |
| `dir-list` | `boolean` | `false` | 是否开放服务器目录浏览接口（安全开关，默认关闭） 开启后可通过 GET /dir/list 浏览服务器本地目录，用于前端目录选择器。 生产环境建议保持关闭，避免泄露服务器目录结构。 |
| `default-provider` | `String` | `—` | 默认 提供者 名称 (用于 文件storagetemplate.获取默认storage()) |
| `providers` | `Map<String, ProviderConfig>` | `new LinkedHashMap<>()` | 多 提供者 配置列表 (按名称路由) |
| `servers` | `List<ServerConfig>` | `new ArrayList<>()` | HTTP 文件服务器配置列表 |
| `temp-path` | `String` | `—` | 分片上传临时目录 (用于存储分片文件) 不配置时默认使用系统临时目录 + /filesystem-chunks |
| `task-expire-minutes` | `long` | `60` | 上传任务过期分钟数：超时仍无新分片活动的任务标记失败并回收分片（默认 60） |
| `task-sync-max-mb` | `long` | `5` | 小文件同步直传阈值（MB）：不超过该大小的单文件直传同步写存储立即返回，不建任务（默认 5；0 表示全部走任务） |
| `collab` | `CollabPersistConfig` | `new CollabPersistConfig()` | 协同文档持久化配置（内存态 + 定时落盘） 协同文档本身是内存态（快照式协同，读写都在内存里，编辑零阻塞）， 这里配置的是「多久把整库刷到磁盘一次」，用于抗进程重启。 |
| `mode` | `AccessMode` | `AccessMode.BOTH` | 访问模式: view-仅预览, download-仅下载, both-两者都允许 |
| `open-webjars` | `boolean` | `true` | Openwebjars |
| `open-remote-file` | `boolean` | `false` | 打开远程文件 |
| `url-suffix` | `String` | `—` | 访问 URL 后缀（可选，如 "?大小=800x600&格式化=jpg"），生成访问地址时拼接 |
| `operation` | `FileOperationConfig` | `—` | 图片处理与预览设置（对应底层 文件operationsetting） |
| `storages` | `List<LegacyStorageConfig>` | `new ArrayList<>()` | Storages |

<details><summary>FileStorageProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `collab.persist-enable` | `boolean` | `true` | 是否启用定时落盘（默认启用；关掉则协同文档重启即丢） |
| `collab.persist-path` | `String` | `—` | 落盘文件路径（默认 data/file-collab/collab-store.json，相对进程工作目录） |
| `collab.persist-seconds` | `long` | `30` | 落盘间隔（秒，默认 30） 间隔越小丢数据窗口越小、写盘越频繁；协同快照较大，30s 是「几乎无感 IO」 与「最多丢半分钟改动」之间的折中。 |
| `collab.hub-path` | `String` | `—` | 实时协同 hub 的日志目录（默认 data/file-collab/hub，相对进程工作目录） hub 保存的是「快照 + 操作日志」，与文档快照本身分开存：前者约束实时 编辑的顺序与续接，后者是用户看到的文件内容。目录为空串表示纯内存 （重启即丢日志，只适合本地调试）。 |
| `operation.size` | `String` | `—` | 输出尺寸，如 "800x600" 或 "800"（等比） |
| `operation.format` | `String` | `—` | 输出格式，如 "jpg"、"png" |
| `operation.quality` | `Integer` | `—` | 图片质量 (1-100) |
| `operation.crop` | `String` | `—` | 裁剪区域，如 "100,100,200,200" |
| `operation.rotate` | `Integer` | `—` | 旋转角度 (0-360) |
| `operation.flip` | `String` | `—` | 翻转方向: "horizontal"/"vertical" |
| `operation.grayscale` | `Boolean` | `—` | 是否灰度 |
| `operation.blur` | `Float` | `—` | 模糊半径 |
| `operation.sharpen` | `Float` | `—` | 锐化强度 |
| `operation.auto-orient` | `Boolean` | `—` | 自动旋转校正 |
| `operation.watermark-text` | `String` | `—` | 文字水印 |
| `operation.watermark-image` | `String` | `—` | 水印图片 URL |
| `operation.force-download` | `Boolean` | `—` | 强制下载（预览时也走 attachment） |
| `operation.pdf-page` | `Integer` | `—` | PDF 页码 (1-based) |
| `operation.pdf-page-size` | `String` | `—` | PDF 页面尺寸 |
| `operation.pdf-orientation` | `String` | `—` | PDF 页面方向 |
| `providers.<key>.endpoint` | `String` | `—` | 服务端点 URL (本地/ftp 可空; minio/s3 例 http://10.0.0.1:9000) |
| `providers.<key>.bucket` | `String` | `—` | Bucket 名称 (存储空间) |
| `providers.<key>.access-key` | `String` | `—` | 访问密钥 标识 (本地 可空) |
| `providers.<key>.secret-key` | `String` | `—` | 访问密钥 Secret (本地 可空) |
| `providers.<key>.region` | `String` | `—` | 区域 (本地 可空; 例 cn-hangzhou, us-east-1) |
| `providers.<key>.base-path` | `String` | `—` | 本地存储根目录 (类型=本地 时有效) |
| `providers.<key>.connection-timeout` | `long` | `10_000` | 连接超时 (毫秒) |
| `providers.<key>.read-timeout` | `long` | `30_000` | 读取超时 (毫秒) |
| `providers.<key>.write-timeout` | `long` | `30_000` | 写入超时 (毫秒) |
| `providers.<key>.secure` | `boolean` | `false` | 是否使用 HTTPS |
| `providers.<key>.type` | `String` | `—` | 自定义 SPI 类型 (留空时根据配置自动推断) - 端点 为空 → 本地 - 端点 以 s3/amazonaws 开头 → s3 - 端点 包含 对象存储.aliyuncs → 对象存储 - 兜底 → minio (S3 兼容) |
| `servers[].name` | `String` | `"default"` | 名称 |
| `servers[].enable` | `boolean` | `true` | Enable |
| `servers[].type` | `String` | `"jdk"` | 类型 |
| `servers[].host` | `String` | `"0.0.0.0"` | 主机 |
| `servers[].port` | `int` | `9000` | 端口 |
| `servers[].ssl` | `boolean` | `false` | SSL |
| `servers[].ssl-cert-path` | `String` | `—` | SSL证书路径 |
| `servers[].ssl-cert-password` | `String` | `—` | SSL证书密码 |
| `servers[].provider-names` | `List<String>` | `new ArrayList<>()` | 提供者名称 |
| `servers[].context-path` | `String` | `"/"` | 上下文路径 |
| `servers[].read-timeout-millis` | `long` | `30_000` | 读取超时毫秒 |
| `servers[].write-timeout-millis` | `long` | `30_000` | 写入超时毫秒 |
| `servers[].filters` | `List<String>` | `new ArrayList<>()` | 过滤器 |
| `servers[].max-request-size` | `long` | `10L * 1024 * 1024` | 最大请求大小（字节） |
| `servers[].max-connections` | `int` | `512` | 最大连接数 |
| `servers[].worker-threads` | `int` | `8` | 工作线程数 |
| `servers[].response-timeout-millis` | `long` | `60_000` | 响应超时（毫秒） |
| `servers[].content-type` | `String` | `"application/octet-stream"` | 默认内容类型 |
| `servers[].gzip-enabled` | `boolean` | `false` | 是否启用 gzip 压缩 |
| `servers[].gzip-min-size` | `int` | `1024` | gzip 压缩最小字节数 |
| `servers[].charset` | `String` | `"UTF-8"` | 字符集 |
| `storages[].name` | `String` | `—` | 名称 |
| `storages[].type` | `String` | `"minio"` | 类型 |
| `storages[].endpoint` | `String` | `—` | 端点 |
| `storages[].access-key-id` | `String` | `—` | Access密钥标识 |
| `storages[].access-key-secret` | `String` | `—` | Access密钥secret |
| `storages[].bucket` | `String` | `—` | 存储桶 |
| `storages[].region` | `String` | `—` | 区域 |
| `storages[].default-storage` | `boolean` | `false` | 默认存储 |
| `storages[].base-path` | `String` | `—` | 基础路径 |

</details>

**配置**（`plugin.filesystem.storage-server.*`，`FileStorageServerProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 是否随应用启动文件服务器 |
| `host` | `String` | `"0.0.0.0"` | 监听地址 |
| `public-host` | `String` | `—` | 对外可达地址（用于 /v2/setting/default 下发的文件服务器地址）； 留空时回退 #host（0.0.0.0 归一为 127.0.0.1）。 |
| `port` | `int` | `18082` |  |
| `mode` | `Mode` | `Mode.EMBEDDED` | 部署形态（默认 Mode#EMBEDDED）。 EMBEDDED：把文件存储的完整协议（/{桶/{键}}、列表、预览、下载、 Range、系统配置等）作为 Servlet 过滤器挂进宿主 Spring 应用，走应用自己的端口， 对外地址形如 http://host:应用端口#pathPrefix/{桶}/{键}。 STANDALONE：由 FileStorageServerLauncher 另起… |
| `path-prefix` | `String` | `"/fs"` | 内置形态的路径前缀（默认 /fs）。 用于与应用自身的接口空间隔离：/fs/local/a.txt 在过滤器内会被还原成 /local/a.txt，因此前端只把「文件服务器地址」配成 http://host:应用端口/fs 即可，其余 URL 规则与独立服务完全一致。 允许 / 或空串（表示占用根路径，可能与应用路由冲突，需自行评估）。 |
| `type` | `String` | `"jdk"` | 服务器引擎：jdk / nio / aio |
| `open-preview` | `boolean` | `true` | 允许内联预览（?preview） |
| `open-download` | `boolean` | `true` | 允许附件下载（?download） |
| `open-range` | `boolean` | `true` | 允许 Range 分段下载 |
| `open-watermark` | `boolean` | `false` | 启用水印响应头 |
| `open-webjars` | `boolean` | `true` | 启用 webjars 静态资源（预览页依赖） |
| `open-remote-file` | `boolean` | `false` | 允许远程文件透传（http(s) 开头的对象键） |
| `third-url-preview` | `String` | `"NONE"` | 三方 URL 预览能力（取值来自 FileStorageUrlPreviewProvider SPI 实现名，NONE 表示关闭） 只有选择到声明 supportsUrlPreview() 的实现（内置为 SERVER，即后台拉取） 时，?url= 才会被放行。 |
| `layout` | `boolean` | `true` | 是否开启目录文件列表页面 |
| `layout-type` | `String` | `"SIMPLE"` | 文件列表布局类型：SIMPLE-简单文件列表树 / SYSTEM-资源管理器风格 |
| `watermark` | `String` | `—` | 水印文本 |
| `image-filter` | `String` | `"NONE"` | 图片滤镜 SPI 名称（NONE 表示原图） |
| `file-setting-key` | `String` | `"jdk"` | URL 参数能力 SPI 实现名（FileStorageFileSetting，默认 jdk）。 决定 ?size=100x100&format=webp 这类文件级参数由哪个实现解析； 管理界面从后台能力接口枚举可选项。 |
| `open-path-guard` | `boolean` | `true` | 是否启用路径穿越防护（默认开启：拦截 ..、反斜杠、NUL 等穿越请求，并筛查文件动作参数） |
| `image-filter-enabled` | `boolean` | `false` | 是否启用全局图片滤镜链（默认关闭；开启后每次图片请求按 filterChain 顺序施加滤镜） |
| `filter-chain` | `String` | `—` | 全局图片滤镜链（紧凑串 id:key=value;id2；id 取自后台 FileStorageFilterSetting SPI 能力） |
| `private-buckets` | `String` | `—` | 私有桶（逗号分隔，provider 名；* 表示全部私有）。 启动时作为 OAuth 鉴权过滤器（oauth 模块，反射挂载）的初始私有桶； 运行期可在文件服务器页「系统配置」热更新（写入 FileStorageSetting.privateBuckets）。 留空时回退环境配置 plugin.oauth.file-storage.private-buckets。 |
| `frame-ancestors` | `String` | `"*"` | 允许嵌入本文件服务器的来源（CSP frame-ancestors）。 文件服务器预览统一走 iframe，需允许被前端页面跨源嵌入；默认 *。 该值同时用于关闭 X-Frame-Options（避免 DENY 阻断 iframe 渲染）。 |
| `cors` | `Cors` | `new Cors()` | 跨域配置（默认开启）。 文件服务器是独立端口的网关（见 #port），前端页面基本都是跨源访问它， 因此默认开启 CORS。需要收紧时把 enable 置 false， 或把 allowedOrigins 从 * 收成来源白名单。 |

<details><summary>FileStorageServerProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `cors.enable` | `boolean` | `true` | 是否开启 CORS（默认开启） |
| `cors.allowed-origins` | `String` | `"*"` | 允许的来源，逗号分隔；* 表示任意来源。 注意：开启 #allowCredentials 后，即使是 * 也不会字面返回通配符， 而是回显实际请求来源——规范禁止「凭据 + 通配来源」并用。 |
| `cors.allow-credentials` | `boolean` | `true` | 是否允许携带凭据（Cookie / Authorization）。默认开启。 预览页会以 credentials:'include' 发起跨域 fetch，此时浏览器强制要求 响应带 Access-Control-Allow-Credentials: true，缺失会导致整个响应不可读。 |
| `cors.allowed-methods` | `String` | `"GET,HEAD,POST,PUT,DELETE,PATCH,OPTIONS"` | 允许的请求方法 |

</details>

**配置**（`plugin.filesystem.upload.*`，`FileStorageUploadProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `max-size-mb` | `long` | `10` |  |
| `chunk-size` | `long` | `5 * 1024 * 1024` |  |
| `temp-dir` | `String` | `System.getProperty("java.io.tmpdir") + …` | 临时目录路径 |
| `view-prefix` | `String` | `"/v2/file/view/"` | 文件回显端点前缀（应用侧提供该路径的读取接口）。 上传阶段以 viewPrefix + 提供者名称 + / + 对象键 作为访问地址落库， 避免把存储后端的内部 URI（如 file:）当作浏览器地址存库。 宿主应用有上下文路径时需一并写入，例如 /system/api/v2/file/view/。 |
| `retention-days` | `int` | `30` |  |
| `merge-interval-ms` | `long` | `10_000L` | 合并调度间隔（毫秒），默认 10 秒 |
| `upload-interval-ms` | `long` | `10_000L` | 上传 对象存储 调度间隔（毫秒），默认 10 秒 |
| `retry-interval-ms` | `long` | `60_000L` | 失败任务重试调度间隔（毫秒），默认 60 秒 |
| `cleanup-interval-ms` | `long` | `24 * 60 * 60 * 1000L` |  |

**通用场景**

普通业务只注入 `FileStorageTemplate`；要独立文件服务端口时把 `mode` 改 standalone，避免和主应用端口耦合。


### spring-support-device-starter

**定位**：设备接入 SPI：海康/大华/宇视/GB28181/ONVIF 等厂商与协议收敛成 `DeviceProvider` + `DeviceSession`。

**装配**：`DeviceAutoConfiguration` 聚合所有 `DeviceProvider`/`DevicePlatformSdkProvider`，并注册分页拦截器兜底。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `deviceProviders` | `List<DeviceProvider>` | 注入所有设备 提供者 Bean（@组件 自动注册），并登记到 common-starter 的 ServiceProvider SPI 注册表，供其它模块按 vendor 取用。 |
| `devicePlatformSdkProviders` | `List<DevicePlatformSdkProvider>` | 登记外部模块提供的平台 SDK 扩展（SPI）。 平台 SDK 门面走 common-starter 的 ServiceProvider 扩展点： 外部模块只要提供一个 DevicePlatformSdkProvider Bean（即一个 「厂商 + 平台」实现），本方法会自动把它登记到平台 SDK 工厂， 业务侧与控制器无需任何改动即可调用新平台的全部接口。 |
| `deviceMybatisPlusInterceptor` | `MybatisPlusInterceptor` | 分页插件兜底。 设备模块的 XML 分页 SQL 依赖 MyBatis-Plus 的 PaginationInnerInterceptor； 宿主若已通过 spring-support-mybatis-starter 注册同名 Bean，本方法自动退避， 不改变既有装配。模块独立运行（仅引入 device-starter）时也能正确返回 total。 |
| `deviceServiceInfoContributor` | `ServiceInfoContributor` | 注册设备模块服务信息。 |
| `noopDeviceRealtimePublisher` | `DeviceRealtimePublisher` | 空发布器：只记录 debug 日志，不做任何网络发送 |
| `socketDeviceRealtimePublisher` | `DeviceRealtimePublisher` | 宿主启用 socket 时的广播发布器 用 @ConditionalOnClass 的字符串形式，避免宿主未引入 socket starter 时 加载本配置导致 NoClassDefFoundError。 |

**内部依赖**：`mybatis`、`common`、`swagger`、`socket`、`oauth-client`

**HTTP 入口**（`@RestController`/`@Controller`，共 22 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `AccessController` | `/api/device/access` | 门禁控制 控制器 |
| `AttendanceController` | `/api/device/attendance` | 考勤记录控制器（只读）。 不提供新增/修改/删除接口：数据由 webhook 或门禁记录归集任务写入。 |
| `BarrierGateController` | `/api/device/barrier` | 车闸点位与过车记录控制器。 |
| `ChannelController` | `/api/device/channel` | 通道管理 控制器 |
| `DeviceAlarmController` | `/api/device/alarms` | 设备告警查询与处理接口（供告警页签使用）。 |
| `DeviceCallbackController` | `/api/device/callbacks` | 设备生命周期回调管理接口（纯后端）。 回调实现本身是 Spring Bean，不通过本接口注册；本接口只提供「查看已注册回调」 与「手工重放一条事件」两个运维入口，便于联调人脸录入等下游系统，以及失… |
| `DeviceCapabilityController` | `/api/device/capability` | 平台能力矩阵控制器。 前端加载一次能力矩阵后缓存，按「厂商 + 平台」决定功能入口的显隐： 平台支持的显示，不支持的隐藏（而不是显示后点了报错）。 不支持的平台会带上原因，便于在界面上给出「该平台不… |
| `DeviceController` | `/api/device` | 设备管理 控制器 |
| `DeviceGroupController` | `/api/device/group` | 设备分组接口。 分组是树形结构（group_parent 指向上级），设备与分组是多对多（device_group_device）； 一台设备可以同时出现在多个分组里。 接口前缀与其他设备接口一致：… |
| `DeviceMetricController` | `/api/device/metrics` | 设备环境指标控制器。 |
| … | … | 其余 12 个控制器见模块源码 |

**组件扫描 Service**：`DeviceAccessRecordServiceImpl`、`DeviceAlarmServiceImpl`、`DeviceAttendanceServiceImpl`、`DeviceBarrierGateServiceImpl`、`DeviceChannelServiceImpl`、`DeviceEventServiceImpl`、`DeviceGroupServiceImpl`、`DeviceMetricServiceImpl`、`DevicePersonServiceImpl`、`DeviceProviderConfigServiceImpl`、`DeviceProviderCredentialServiceImpl`、`DeviceServiceImpl`、`DeviceStatusService`、`DeviceSyncServiceImpl`、`DeviceTaskStateServiceImpl` 等 20 个。`@Service` 注册，可直接注入。

**配置**：本模块没有 `@ConfigurationProperties`。

**通用场景**

业务只依赖 `DeviceProvider`/`DeviceSession`；新增厂商实现 `DeviceProvider` 并声明能力位，不要把厂商 SDK 类型泄漏到业务层。


### spring-support-software-starter

**定位**：软件资产与安装管理：目录、软件源、安装/卸载/升级记录。

**装配**：`SoftwareConfiguration` 带 `@AutoConfiguration`，但**未登记到** `AutoConfiguration.imports`；引入依赖后需宿主 `@Import(SoftwareConfiguration.class)` 或让组件扫描覆盖 `com.chua.starter.software.support`。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `softwareServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |
| `noopInstallProgressPublisher` | `InstallProgressPublisher` | 空发布器：只记录 debug 日志，不做任何网络发送 嵌套类里的 socket 发布器已注册时本方法让位；socket 缺失时由本方法兜底， 保证 InstallProgressPublisher 这个依赖永远可注入。 |
| `socketInstallProgressPublisher` | `InstallProgressPublisher` | 基于全局 socket 通道的安装进度发布器 带 ConditionalOnMissingBean：业务方若自己提供了发布器（如落库 + 推送双写）， 这里让位而不是制造同类型双 Bean。嵌套配置先于外层被解析，因此本方法求值时 外层的空实现尚未注册，不会被它误挡。 |

**HTTP 入口**（`@RestController`/`@Controller`，共 5 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `LocalSoftwareController`（未确认注册） | `/v2/soft/local` | 本地软件管理接口 提供本地软件扫描、列表查询、安装、卸载等功能。 |
| `SoftConfigController`（未确认注册） | `/v2/soft/config` | 软件配置接口 |
| `SoftInstallController`（未确认注册） | `/v2/soft/install` | 软件安装接口 |
| `SoftSoftwareController`（未确认注册） | `/v2/soft/software` | 软件管理接口 |
| `SoftSourceController`（未确认注册） | `/v2/soft/source` | 软件源接口 |

另有 5 个 `@Service` 标注的类未在自动配置里找到注册路径，需宿主扫描对应包才能注入。

**配置**：本模块没有 `@ConfigurationProperties`。

**通用场景**

把它当业务模块而不是纯 starter：要么显式 `@Import`，要么在宿主扫描路径内。


### spring-support-server-starter

**定位**：服务器资产与远程运维：主机台账、远程终端、SFTP、发布、批量执行、代理隧道。

**装配**：`ServerAutoConfiguration`，组件扫描 `com.chua.starter.server.support` + Mapper 扫描，开启事务与定时任务。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `releaseAiCapability` | `ReleaseAiCapability` | 注册 AI 能力门面。 |
| `serverServiceInfoContributor` | `ServiceInfoContributor` | 服务器资产模块的 服务信息 贡献者。 |

**内部依赖**：`common`、`datasource`、`mybatis`、`filesystem`、`ai`、`swagger`、`socket-sse`、`socket`、`job`

**HTTP 入口**（`@RestController`/`@Controller`，共 26 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `AgentArtifactController` | `/ms/server/agent` | Agent 构件投放控制器。 给「目标主机上没有 arthas-boot.jar / skywalking-agent.jar」提供平台侧的自助通道： 远程下载或指定平台本机路径，平台缓存后经 SF… |
| `ArthasController` | `/ms/server/arthas` | Arthas 诊断工具管理控制器。 提供 Arthas 运行时 attach / detach / 命令执行 / 终端输出等管理接口。 |
| `ExecController` | `/exec` | 批量执行接口：批量命令、文件分发与计划任务 |
| `HostAgentController` | `/host/agent` | 服务器资产 Agent 上报接口 目标主机上的 Agent 按 Agent键 主动推送硬件/状态数据， 由 HostCollectProviderRegistry 解析出的 AGENT 采集实现处理… |
| `HostArchiveController` | `/host/archive` | 主机资产归档接口（导出 / 导入）。 与 GET /ms/server/host/export（CSV，只给人看）不同，这里的 JSON 带登录身份的口令密文，用于整机迁移或环境复制。因此： 只应挂… |
| `HostAssetsSseController` | `/host/assets` | 主机资产全局 SSE 连接 整个主机资产页面只保留这一条 SSE 长连接：连接建立后，底部资源摘要 （句柄数 / inode）与邮箱新邮件都经它推送。前端用固定 clientId 建连， 再通过订阅… |
| `MailboxConnectionController` | `/ms/server/identity` | 邮箱账号(MAILBOX) SSE 连接与临时邮箱管理 提供邮箱推送连接注册/注销、临时邮箱地址创建。前端通过 /sse/connect 建立 SSE 连接后注册到本控制器。 |
| `MailboxController` | `/identity` | 邮箱账号(MAILBOX 身份)邮件箱接口 读取邮箱身份的 IMAP 收件箱：列表 + 邮件正文详情。邮箱地址/授权码均取自登录身份，授权码加密存储、不对外返回。 |
| `ProxyController` | `/proxy` | 免费代理（业务——代理表 ms_proxy）： 获取（抓取→清空→录入——较慢）· 列表（读表——快）· 当前代理（自动——无需业务指定）· 台账维护（新增/编辑/删除/清空）· 检测（单条与批量） |
| `ReleaseAiController` | `/release/ai` | 发布管理的 AI 能力接口。 前端先问 /capabilities：没有可用模型就不显示 AI 按钮， 而不是让用户点一个必然失败的入口。 |
| … | … | 其余 16 个控制器见模块源码 |

**组件扫描 Service**：`AgentArtifactServiceImpl`、`ArthasServiceImpl`、`ExecServiceImpl`、`HostArchiveServiceImpl`、`HostAssetsSseHub`、`MailboxServiceImpl`、`MailboxSseService`、`ProjectOpLogServiceImpl`、`ProjectTaskService`、`ProxyServiceImpl`、`ReleaseServiceImpl`、`ReleaseSseHub`、`RemoteTokenService`、`ServerConfigServiceImpl`、`ServerGroupServiceImpl` 等 24 个。`@Service` 注册，可直接注入。

**SPI 扩展点**：`HostCollectProvider`、`HostDetectProvider`、`HostRemoteProvider`、`PackageManagerProvider`、`ProjectPlatformProvider`、`ProjectTypeProvider`、`SystemServiceProvider`、`TerminalSessionChannel`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`plugin.server.support.serial.*`，`SerialProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 串口远控开关：false 时不启用串口页面与 WS 端点， 但注册表/查询接口仍然可用（仅不上线） |
| `baud-rate` | `int` | `115200` |  |
| `data-bits` | `int` | `8` | 默认数据位 |
| `stop-bits` | `int` | `1` | 默认停止位 |
| `parity` | `int` | `0` | 默认校验位（0 无校验 / 1 奇 / 2 偶 / 3 标记 / 4 空）， 与 jSerialCommcom.fazecast.jSerialComm.SerialPort#NO_PARITY 一致 |
| `flow-control` | `int` | `0` | 默认流控（0 关 / 1 RTS / 2 CTS / 4 DSR / 8 DTR / 16 XON/XOFF 入 / 32 XON/XOFF 出） |
| `read-timeout` | `int` | `1000` |  |
| `write-timeout` | `int` | `0` | 写超时（毫秒），0 表示等待发送完成 |
| `exclusive` | `boolean` | `true` | 是否独占打开串口（大多数串口设备需要独占，避免与终端/监控系统冲突） |
| `device-path-prefix` | `String` | `"/dev/"` | 默认设备路径前缀： Unix 系（/dev/ttyUSB0/ttyACM0）前置 /，Windows 系（COM3）无前缀 仅用于设备路径规范化，实际路径以主机台账 serverHostAddress 字段为准 |

**配置**（`plugin.server.support.*`，`ServerProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 服务信息面板上"服务器资产"模块的展示旗标。 注意：这不是模块开关。本模块没有 @ConditionalOnProperty， 置 false 不会卸载任何 Bean；monitor/project 等模块直接注入本模块服务， 真要停用只能移除依赖。 |
| `guacd` | `Guacd` | `new Guacd()` | guacd 配置 |
| `credential-key` | `String` | `"server-support-default-credential-key"` | 凭据加密密钥（生产环境务必通过配置中心或环境变量注入） |
| `session-idle-minutes` | `int` | `30` |  |
| `exec-concurrency` | `int` | `16` |  |
| `telnet` | `Telnet` | `new Telnet()` | Telnet 直连配置 Telnet 是「目标机自己开着的 23 端口」，平台只做 WS → TCP 透传， 因此这里只留两个参数：没配端口时用哪个默认值、连不上时等多久。 |

<details><summary>ServerProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `telnet.port` | `int` | `23` |  |
| `telnet.timeout` | `int` | `8000` |  |
| `guacd.host` | `String` | `"127.0.0.1"` | guacd 主机地址 |
| `guacd.port` | `int` | `4822` |  |
| `guacd.client-host` | `String` | `"127.0.0.1:8082"` | guacd-客户端 网关地址（浏览器侧 guacamole 客户端回连后端 WS 隧道的 主机:端口； 前端与后端同域部署时置空即可，页面自动使用 位置.主机） |
| `guacd.drive-path` | `String` | `"/opt/spring-support/drive"` | RDP 磁盘映射挂载根目录（enable-Drive 时使用） |
| `guacd.recording-path` | `String` | `"/opt/spring-support/recordings"` | 屏幕录制文件输出目录 |

</details>

**通用场景**

远控/发布相关业务注入对应 Service；终端 WebSocket 由 `TerminalWebSocketConfig` 单独注册。


### spring-support-docker-starter

**定位**：Docker 管理：容器、镜像、网络、卷、镜像库、模板（经 SSH 执行 CLI）。

**装配**：`DockerAutoConfiguration`，组件扫描 `com.chua.starter.docker`，无类级开关；不需要暴露 Docker TCP 端口。

**公开 Bean**：本模块没有显式 `@Bean`；控制器/服务随组件扫描注册，见模块 README。

**内部依赖**：`server`

**HTTP 入口**（`@RestController`/`@Controller`，共 3 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `DockerController` | `/docker/{hostId}` | Docker 管理控制器 —— 独立模块的主机维度 REST 入口。 路径前缀 /ms/server/docker/{hostId}，与主机工作台的 /ms/server/host/{hostId/… |
| `RegistryController` | `/docker` | 镜像库控制器 —— 镜像库 CRUD、连通性测试与在线检索（不带 hostId）。 路径前缀与 Docker 主机维度接口一致（/ms/server/docker）：镜像库属于 全局配置，与具体主机… |
| `TemplateController` | `/docker/templates` | 容器模板控制器 —— 保存的 docker run 配置 CRUD（不带 hostId）。 模板不含主机维度：同一套配置可以下发到任意主机。安装容器时把 templateId 放进创建请求即可由服务… |

**组件扫描 Service**：`DockerServiceImpl`、`RegistryService`、`TemplateService`（共 3 个）。`@Service` 注册，可直接注入。

**SPI 扩展点**：`ProjectTypeProvider`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**：本模块没有 `@ConfigurationProperties`。

**通用场景**

宿主机已开 SSH 就能用；镜像库认证走 `DockerRegistryProvider` SPI。


### spring-support-control-starter

**定位**：WebRTC 远程控制：信令、桌面采集、键鼠注入。

**装配**：`ControlAutoConfiguration`；`control.webrtc.enabled` 默认 true；信令与 REST 共用 `host/port`。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `webRtcServer` | `WebRtcServer` | 创建 WebRTC 远控服务器。 |

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `ControlController`（未确认注册） | `/control` | WebRTC 远程控制 REST 接口。 为前端（@pages/control）提供设备/会话管理与桌面源查询能力。 真正的媒体流与键鼠控制走 WebSocket 信令 + DataChannel，… |

**配置**（`control.webrtc.*`，`ControlProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 是否启用远程控制模块 |
| `token` | `String` | `""` | 信令访问令牌；为空时启动自动生成，随 /control/config 下发给已登录页面。 |
| `host` | `String` | `"0.0.0.0"` | 信令监听地址 |
| `port` | `int` | `10000` |  |
| `auto-start` | `boolean` | `true` | 是否随应用启动信令服务 |
| `frame-rate` | `int` | `15` |  |
| `width` | `int` | `1280` |  |
| `height` | `int` | `720` |  |

**通用场景**

远控页面先拿会话再建 WebRTC 连接；生产要限制来源并配 TURN。


### spring-support-monitor-starter

**定位**：主机/应用监控台账、采集、时序与告警（含嵌入式/独立两种模式）。

**装配**：`MonitorAutoConfiguration` + `ControlRestConfiguration`；远控 REST 需 `control.webrtc.enabled`，时序/采集各有 `plugin.monitor.*` 子开关。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `monitorAcmeAccountMapper` | `MonitorAcmeAccountMapper` | ACME 账户 Mapper。 不用 @MapperScan 扫整个 com.chua.starter.monitor.mapper 包： 该包里还有与主后端同名的 MsMonitorMapper 等接口， 整包扫描会让两个同名 Bean 互相覆盖。 |
| `monitorAcmeCertificateMapper` | `MonitorAcmeCertificateMapper` | ACME 证书 Mapper。 |
| `monitorAcmeOrderMapper` | `MonitorAcmeOrderMapper` | ACME 订单 Mapper。 |
| `acmePathAliasFilterRegistration` | `FilterRegistrationBean<AcmePathAliasFilter>` | context-path 为空，控制器实际在 /v1/cert/**，不剥前缀就会落到 静态资源兜底并返回 No static resource monitor/api/v1/cert/page。 只对 /monitor/api/* 生效，其它路径不经过本过滤器。 |
| `agentTcpSyncServer` | `SyncServer` | 启动 TcpSyncServer。 |
| `hostRealtimePushBinder` | `ApplicationRunner` | 注册主机实时状态 hooks（延迟/在线/采集 → socket 推送 + 告警评估） |
| `datasearchConflictResolve` | `DatasearchConflictResolve` | 数据搜索接口的控制器冲突消歧器。 宿主同时装入了旧版 system/service 控制器（ /v2/holiday/get、 /v2/ip/lookup） 与 datasearch 版的新控制器，同路径双映射在 AuthFilter#isPass() 中引发 Ambiguous handler methods mapped 异常，构造 403 拦截点，使 HEAD 探测被前端判为 "未实现"并… |
| `monitorSchemaInitializer` | `ApplicationDataSourceScriptDatabaseInitializer` | 建表脚本初始化器。 框架的 datasource 插件会额外注册多个 DataSource Bean，Spring Boot 的 DataSourceInitializationAutoConfiguration 因 @ConditionalOnSingleCandidate(DataSource.class) 静默退避， spring.sql.init.schema-locations 于是从… |
| `monitorServiceInfoContributor` | `ServiceInfoContributor` | 监控模块的 服务信息 贡献者。 |
| `default` | `Object` | 注册本地磁盘存储后端（对外桶名 default，Java 方法名仍为 localFileStorage）。 |
| `fileStorageTemplate` | `FileStorageTemplate` | 创建文件存储模板并挂载 monitor 本地后端为网关桶。 模板先按 plugin.filesystem.providers 初始化（monitor 未配置时 仅告警跳过），再把 MonitorFileStorageConfig 的本地后端以 {@value #BUCKET} 为名注册进 storageMap——FileStorageServerLauncher 启动时 将 storageMap… |
| `fileServerConfigController` | `FileServerConfigController` | 文件服务器运行期配置接口（GET /v2/file/server-config）。 前端进文件管理页前必须先问这个接口「文件服务在哪、桶叫什么」， 拿到结果再决定用嵌入式相对路径还是独立端口；拿不到就只能按 localStorage → app.yaml → window.location.origin 逐级回退，于是同一个页面 会散出 /fs/* 与 /default 两种请… |
| `uploadTaskManager` | `UploadTaskManager` | 上传任务管理器（时间轮调度 + 虚拟线程分片合并）。 与 FileServerConfigController 同理：FileSystemAutoConfiguration 整链被排除后，任务化分片上传的 UploadTaskController 与 UploadTaskManager 一并消失。它们只依赖本配置已装配的 FileStorageTemplate（#fileStorageTempl… |
| `uploadTaskController` | `UploadTaskController` | 上传任务控制器（/v2/file/upload-task，任务化分片上传）。 补注册说明同 #uploadTaskManager(FileStorageTemplate, FileStorageProperties)： 只取任务化分片上传两件套，不触碰被排除的其余文件服务链。 |
| `fileCollabStore` | `com.chua.starter.filesystem.support.collab.FileCollabStore` | 协同文档存储器（内存态，与上传任务同生命周期）。 文件资产右键「创建协同」的落库容器：快照 + 版本乐观锁 + SSE 广播。 无外部依赖，直接注册单例。 |
| `fileCollabPersistence` | `com.chua.starter.filesystem.support.collab.FileCollabPersistence` | 协同文档定时落盘（重启不丢文档）。 与 FileSystemAutoConfiguration#fileCollabPersistence 同一实现， 只是补注册在这里：monitor 排除了整条自动配置链，若只补 fileCollabStore 而漏掉落盘器，存储器就退化成纯内存态——重启后 /v2/file/collab/list 返回空数组，协同文档与快照全部丢失（真机实测）。 路径口径与… |
| `fileCollabHub` | `com.chua.starter.filesystem.support.collab.FileCollabHub` | 实时协同 hub（操作定序 + 日志 + presence）。 与落盘器同理：漏注册会让控制器回退到 new FileCollabHub(null, false, true)（内存态、不落盘、空闲即卸载）， 后端一重启房间日志与快照全清，重连客户端无法续接。 目录 plugin.filesystem.collab.hub-path（默认 data/file-collab/hub，相对进程工作目录… |
| `fileCollabController` | `FileCollabController` | 协同文档控制器（/v2/file/collab）。 与 UploadTaskController 同理：FileSystemAutoConfiguration 整链被排除后，协同接口一并消失。本配置单独补回——它依赖本配置已装配的 fileCollabStore、uploadTaskManager（时间轮驱动 SSE 心跳） 与 fileCollabHub（实时操作定序与落盘）。 必须用三参构造… |
| `sseSocketSessionTemplate` | `SseSocketSessionTemplate` | SSE 会话模板（无连接的空实现，仅供 mailboxsse服务 构造注入） |
| `seriesEngine` | `SeriesEngine` | 装配唯一时序引擎。 |

**内部依赖**：`common`、`server`、`job`、`tunnel`、`ngrok`、`cloudflared`、`docker`、`control`、`datasource-hub`、`device`、`apm`、`apm-skywalking`、`apm-hotspot`、`datasource`、`mybatis`、`filesystem`、`discovery`、`sync`、`cache`、`ai`、`account`、`swagger`、`socket`、`socket-io`、`socket-websocket`、`queue`

**HTTP 入口**（`@RestController`/`@Controller`，共 27 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `AgentController` | `/ms/agent` | Agent 接收端 |
| `AgentDeployController` | `/host/agent` | Agent 自动部署控制器 只做参数与结果封装，安装/重装/升级/卸载的实际编排在 AgentDeployService， 全过程进度经 socket 事件 monitor/agent-deploy… |
| `AgentEndpointController` | `/ms/monitor/agent` | Agent 上报端点（开放 API，不走登录态） 外部探针以 X-Agent-Key 头鉴权，键 与 ms监控.监控Agent键 匹配。 推送数据与 提供者 轮询共用同一条写入管道（MySQL + … |
| `AlarmController` | `/ms/monitor/alarm` | 告警管理接口：策略 / 规则 / 事件 |
| `ApmOperLogController` | `/ms/monitor/apm/oper-log` | APM 操作日志查询。 写入侧不在这里：本控制器只读。APM 接口不走 Spring MVC， 日志由 ApmDispatcher → ApmOperLogSink 写入。 |
| `AssetLinkageController` | `/ms/monitor/asset` | 资产联动控制器 —— 主机台账与服务节点的聚合查询。 给监控台提供「资产为中心」的统一视角：按资产查看其服务节点、通道状态， 或按关键字检索资产。统一路由前缀 /ms/monitor/asset。 |
| `BulkImportController` | `/ms/monitor` | CSV 批量导入（快速模式）REST 接口。 为什么独立于 SyncTaskController：批量导入的前端契约 （bulkImportCsv）固定调 POST /ms/monitor/bulk… |
| `CdpController` | 方法级 | CDP 调试管理器 —— 基于 Chrome DevTools Protocol 的 DevTools 面板 REST 入口 路径前缀 /ms/cdp，所有操作面向本地 Chromium（通过 sp… |
| `DiscoveryFallbackController` | `/ms/monitor/discovery` | 服务发现回退控制器。 spring-support-discovery-starter 是可选依赖，普通监控 fat jar 不会携带它。 此时上游 com.chua.starter.monitor… |
| `FileAssetController` | `/ms/monitor/file-assets` | 文件资产 REST 接口 前端「文件资产」页面通过本接口读写 FileStorage 存储桶，与服务管理启动的 HTTP 文件服务器共用同一后端。浏览器不再直连文件服务器端口，避免地址硬编码与跨源限… |
| … | … | 其余 17 个控制器见模块源码 |

**组件扫描 Service**：`AgentDeployServiceImpl`、`AlarmEngineServiceImpl`、`ApmOperLogServiceImpl`、`AssetLinkageService`、`CdpEngineService`、`FileAssetServiceImpl`、`HostMetricHistoryServiceImpl`、`HostNetworkServiceImpl`、`HostProfileServiceImpl`、`HostSummarySseService`、`LocalSystemInfoServiceImpl`、`MaintenancePlanService`、`MetricServiceImpl`、`MonitorConfigServiceImpl`、`MonitorGatewayServiceImpl` 等 26 个。`@Service` 注册，可直接注入。

**SPI 扩展点**：`JdbcCsvLoader`、`NginxCommandProvider`、`NginxExecutor`、`NotifySender`、`HostOperationsProvider`、`MetricProvider`、`OperationProvider`、`SystemInfoProvider` 等 11 个（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`spring.cdp.*`，`CdpProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 总开关，false 时不开启任何 CDP 能力 |
| `port` | `int` | `9222` |  |
| `executable` | `String` | `—` | Chrome/Chromium 可执行文件路径；为空时按环境变量 + Playwright 缓存自动探测 |
| `headless` | `boolean` | `true` | 是否无头启动（新增：`--headless=new`），默认无头 |
| `user-data-dir` | `String` | `—` | 用户数据目录（持久化 Cookies/LocalStorage/书签） ，默认使用系统临时目录；无法持久化 |
| `proxy` | `String` | `—` | 代理地址（如 `http://127.0.0.1:7890`），可选 |
| `user-agent` | `String` | `—` | 全局 User-Agent，默认使用系统自带 |
| `viewport-width` | `int` | `1280` |  |
| `viewport-height` | `int` | `900` |  |
| `launch-timeout` | `Duration` | `Duration.ofSeconds(120)` |  |
| `command-timeout` | `Duration` | `Duration.ofSeconds(30)` |  |
| `screenshot-timeout` | `Duration` | `Duration.ofSeconds(60)` |  |
| `args` | `List<String>` | `new ArrayList<>()` | 用户级额外参数，给浏览器命令行追加 `--xxx` |
| `ws-path` | `String` | `"/ws/cdp"` | 暴露给前端的 WebSocket 路径（如 `/ws/cdp`） |
| `enable-console` | `boolean` | `true` | 是否启用控制台事件订阅（`Runtime.consoleAPICalled`），默认开 |
| `enable-network` | `boolean` | `true` | 是否启用网络事件订阅（`Network.enable` + `Runtime.responseReceived`），默认开 |

**配置**（`plugin.monitor.agent.*`，`AgentDeployProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jar-path` | `String` | `—` | agent.jar 路径。为空时按候选路径探测（工作目录 monitor-agent/agent.jar 等）， 全部落空则直接以明确错误终止部署，不再静默上传一个不存在或过期的包。 |
| `jar-url` | `String` | `""` | agent 包的平台下载地址（如 http://10.0.0.5:8094/ms/agent/package）。 非空时安装/升级改为让目标机自行 HTTP 拉取，拉取失败再回退通道上传； Windows 主机的 WinRM 通道受单条命令长度上限约束，大文件必须走这条路径。 |
| `linux-service-name` | `String` | `"monitor-agent"` | Linux 系统服务名（systemd unit） |
| `windows-service-name` | `String` | `"MonitorAgent"` | Windows 系统服务名（nssm） |
| `remote-dir-name` | `String` | `"monitor-agent"` | 远端安装目录名（位于各系统的临时目录下） |
| `port` | `int` | `DEFAULT_PORT` | agent 默认端口 |
| `token` | `String` | `""` | 写入远端 agent.yml 的接口令牌；为空表示不启用 /exec 令牌校验。 平台侧访问 agent 时会自动带上同值 token。 |
| `health-wait-seconds` | `int` | `30` |  |
| `request-timeout-seconds` | `int` | `8` | 单次 agent HTTP 请求超时（秒） |
| `java-command` | `String` | `""` | 远端 java 可执行文件；留空则自动探测（command -v java / where java） |
| `report-url` | `String` | `""` | 平台回写地址（写入 agent.yml 供 agent 主动上报预留使用） |
| `apm-jar-url` | `String` | `""` | APM RuntimeAgent jar 的平台下载地址（如 https://.../utils-support-runtime-agent.jar）。 为空时若未配置 #apmJarPath，后端将按候选路径探测本地 runtime-agent jar。 |
| `apm-jar-path` | `String` | `""` | 本地 APM RuntimeAgent jar 路径。优先于候选路径探测。 |
| `apm-remote-name` | `String` | `"apm-agent.jar"` | 远端存放 APM RuntimeAgent jar 的文件名。 |
| `apm-options` | `String` | `"port=4567"` | 注入到目标 JVM 的 javaagent options（不含 -javaagent:jar= 前缀）。 例如 port=4567 或 port=4567,enabled=true。 |
| `apm-linux-profile` | `String` | `"/etc/profile.d/apm-javaagent.sh"` | APM JavaAgent 注入状态远端探针文件（Linux profile.d）。 |
| `apm-windows-env-name` | `String` | `"JAVA_TOOL_OPTIONS"` | APM JavaAgent 注入状态远端探针文件（Windows 用户环境变量）。 |

**配置**（`plugin.monitor.influx.*`，`MonitorInfluxProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `false` | 是否启用 influxdb 时序写入与查询 |
| `url` | `String` | `"http://127.0.0.1:8086"` | influxdb 地址（v1 兼容端点，如 http://127.0.0.1:8086） |
| `database` | `String` | `"monitor"` | 数据库名 |

**配置**（`plugin.monitor.ssh.*`，`MonitorSshProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `connect-timeout-seconds` | `int` | `15` |  |
| `session-timeout-seconds` | `int` | `30` |  |
| `exec-timeout-seconds` | `int` | `60` |  |

**配置**（`plugin.monitor.timeseries.*`，`MonitorTimeseriesProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `engine` | `String` | `"influxdb"` | 时序引擎（influxdb / redis / datasource / 无），默认 influxdb（向后兼容） |
| `url` | `String` | `"http://127.0.0.1:8086"` | 时序存储地址：influxdb / questdb 用作服务地址（如 http://127.0.0.1:8086）； redis 配置为 redis:// 地址（或 host:port）时覆盖 spring.data.redis.*，否则回退后者 |
| `database` | `String` | `"monitor"` | 数据库名 |

**配置**（`plugin.monitor.tshark.*`，`TsharkInstallProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `bin` | `String` | `""` | 显式指定 tshark 可执行文件路径。 留空时按「环境变量 TSHARK_BIN → 本项 → 自动安装目录 → 系统常见安装路径 → PATH」 依次探测，命中即用。已有 Wireshark 安装的机器因此不需要任何配置。 |
| `auto-install` | `boolean` | `true` | 探测不到时是否自动下载并解压便携版。 关闭后只做探测与提示，不发生任何网络下载。 |
| `install-dir` | `String` | `""` | 便携版解压目录。 留空用 ~/spring-support/tshark。不用 /opt 的原因同抓包目录： 平台多以普通用户运行，/opt 不可写，会在真实部署里直接失败。 |
| `package-url` | `String` | `""` | 便携包下载地址（zip：tshark 可执行文件与其依赖 DLL / so）。 留空且未配置 #packagePath 时，启动自检会把状态标成「缺少安装包」并给出 明确的处理办法，而不是静默失败。 |
| `package-url-windows` | `String` | `""` | Windows 专用便携包地址，优先于 #packageUrl。 同一个部署包里可能同时带 Windows 与 Linux 的产物，用平台专用配置就不必为 不同系统准备两份 application.yml。 |
| `package-url-linux` | `String` | `""` | Linux 专用便携包地址，优先于 #packageUrl。 支持 .tar.gz / .tar.xz / .deb（deb 会取出其中的 data.tar.* 再解）；Wireshark 官方没有 Linux 便携 zip，通常用发行版的 deb 或自建 tar 包。 |
| `package-path` | `String` | `""` | 本地便携包路径，优先于 #packageUrl。 内网离线部署时把 zip 放到机器上，填这里即可，服务端不再访问外网。 |
| `package-path-windows` | `String` | `""` | Windows 专用本地包路径，优先于 #packagePath。 |
| `package-path-linux` | `String` | `""` | Linux 专用本地包路径，优先于 #packagePath。 |
| `timeout-seconds` | `int` | `600` |  |
| `tls-keylog-file` | `String` | `""` | TLS 会话密钥日志文件（NSS key log 格式，如 D:\keys\sslkeys.log）。 配置后 tshark 会带 -o tls.keylog_file:，把 TLS 里的 HTTP/2、 MSRP、SMPP 等明文解出来继续走协议还原。不配置则不传该参数，抓包行为与以前一致。 安全提醒：keylog 文件本身就是解密钥匙，只能用于自有环境的排障， 不要在生产机器上长期开启，也不… |
| `json-restore` | `boolean` | `true` | 是否在解析 pcap 时，额外启用 tshark json 通道回填 rawData（完整协议层 JSON） 与 restoredText（协议还原出的明文）。 开启后，{-T fields} 保障的骨架落成之后，服务端会再跑一次 tshark -T json -x： 逐包取出完整 JSON（写入 rawData，包含 payload 展开的十六进制）， 再调用 PacketParserServ… |

**配置**（`plugin.monitor.tshark.retention.*`，`TsharkRetentionProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `days` | `int` | `7` | 套餐会保留天数。超过时应清理。 |
| `size-mb` | `int` | `512` |  |
| `enabled` | `boolean` | `true` | 是否启用自动清理。 关闭时只统计空间、不删文件，真实的清理交给系统本身（避免误删别的服务数据）。 |

**配置**（`spring.cdp.*`，`CdpProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 总开关，false 时不开启任何 CDP 能力 |
| `port` | `int` | `9222` |  |
| `executable` | `String` | `—` | Chrome/Chromium 可执行文件路径；为空时按环境变量 + Playwright 缓存自动探测 |
| `headless` | `boolean` | `true` | 是否无头启动（新增：`--headless=new`），默认无头 |
| `user-data-dir` | `String` | `—` | 用户数据目录（持久化 Cookies/LocalStorage/书签） ，默认使用系统临时目录；无法持久化 |
| `proxy` | `String` | `—` | 代理地址（如 `http://127.0.0.1:7890`），可选 |
| `user-agent` | `String` | `—` | 全局 User-Agent，默认使用系统自带 |
| `viewport-width` | `int` | `1280` |  |
| `viewport-height` | `int` | `900` |  |
| `launch-timeout` | `Duration` | `Duration.ofSeconds(120)` |  |
| `command-timeout` | `Duration` | `Duration.ofSeconds(30)` |  |
| `screenshot-timeout` | `Duration` | `Duration.ofSeconds(60)` |  |
| `args` | `List<String>` | `new ArrayList<>()` | 用户级额外参数，给浏览器命令行追加 `--xxx` |
| `ws-path` | `String` | `"/ws/cdp"` | 暴露给前端的 WebSocket 路径（如 `/ws/cdp`） |
| `enable-console` | `boolean` | `true` | 是否启用控制台事件订阅（`Runtime.consoleAPICalled`），默认开 |
| `enable-network` | `boolean` | `true` | 是否启用网络事件订阅（`Network.enable` + `Runtime.responseReceived`），默认开 |

**通用场景**

监控自身被监控时注意端口与实例名；独立部署用 `plugin.monitor.mode=standalone`。


### spring-support-ai-starter

**定位**：AI 对话/生成/协议/用量/缓存/网关安全，统一 `ChatClient` 与模型注册。

**装配**：6 个配置类；`plugin.ai.generate.enabled`、`plugin.ai.usage.enabled` 默认 true；`plugin.protocol.enable` 默认 false；对话客户端由数据库 `sys_ai_provider` 与 `plugin.ai.chat` 共同决定。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `aiTransactionTemplate` | `TransactionTemplate` | 共享的编程式事务模板。 不设只读、不设超时：由调用方按业务语义决定。容器里只应有一个 PlatformTransactionManager（MyBatis-Plus 的 DataSourceTransactionManager）； 若宿主引入多数据源，请在此显式指定 @Qualifier。 |
| `aiCacheManager` | `CacheManager` | 默认缓存管理器：本地 Caffeine（未显式指定 cacheManager 的 @Cacheable 使用）。 刻意不标 @Primary。宿主（monitor / system / eduai）里已经有框架给的 主缓存管理器 defaultCacheManager 和 cacheWithRedis，本模块再标一个 @Primary 就变成「多个 primary」，所有按 CacheManag… |
| `redisCacheAlways` | `CacheManager` | Redis 永久缓存管理器，Bean 名 redis-cache--1。 |
| `redisCacheMin` | `CacheManager` | Redis 10 分钟缓存管理器，Bean 名 redis-cache-600。 |
| `aiCacheKeyGenerator` | `KeyGenerator` | AI 缓存 Key 生成器。 生成格式：ai:cache:{model:{provider}:{hash}} |
| `taskExecutor` | `Executor` | 异步任务线程池 |
| `yamlChatClient` | `ChatClient` | YAML 配置的对话客户端。 声明在 #defaultChatClient(ApplicationContext) 之前：后者带 @ConditionalOnMissingBean(ChatClient)，本 bean 先注册后它就会被跳过， 从而不会出现「聚合客户端」和「YAML 客户端」两个 ChatClient bean 相互干扰。 注册中心在 ApplicationReadyEvent … |
| `chatClientRegistry` | `ChatClientRegistry` | 全局 对话客户端 注册中心。 |
| `agentRegistry` | `AgentRegistry` | 全局 Agent 注册中心。 |
| `aiDialogRecordService` | `AIDialogRecordService` | 对话记录服务——默认使用内存实现。 |
| `defaultChatClient` | `ChatClient` | 默认 对话客户端 — 聚合客户端。 通过 SPI 发现 ChatClient 实现，聚合为 AggregateChatClient。 如果找不到任何实现，返回 空（业务层自行处理兜底）。 |
| `aiServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |
| `fileDirController` | `FileDirController` | 注册服务器目录浏览控制器。 |
| `aiProtocolServerFilters` | `List<AiProtocolServerFilter>` | 收集所有 AI 协议服务器过滤器。 两级开关都打开才注册：部署级 plugin.protocol.enable（YAML，重启生效） 与管理员级 runtime.protocol_server_enabled（设置表，热改后下次重启生效）。 后者此前只在设置页登记、后端从不读取 —— 关掉它，协议端点照样对外服务。 |
| `globalSettingInitializer` | `GlobalSettingInitializer` | 全局设置初始化器（applicationrunner）。 |
| `gatewayRateLimiter` | `GatewayRateLimiter` | 限流器：默认本地窗口（单节点），可用 plugin.ai.security.rate-limit-store=redis 切到 Redis 共享窗口。 默认选本地是刻意的：RedisSlidingWindowLimiter#allow 在 Redis 异常时 返回 false（拒绝），而多数内嵌应用只是「配了 Redis 但不一定连得上」。默认走 Redis 等于让一次 Redis 抖动变成全站… |
| `aiGatewayHealthCheckFilter` | `FilterRegistrationBean<Filter>` | 健康探针过滤器。 |
| `aiGatewayIpFilter` | `FilterRegistrationBean<Filter>` | IP 黑白名单过滤器。 |
| `aiGatewayBodySizeFilter` | `FilterRegistrationBean<Filter>` | 请求体大小过滤器。 |
| `aiGatewayRateLimitFilter` | `FilterRegistrationBean<Filter>` | QPM 限流过滤器。 |
| `aiGatewayConnectionBudgetFilter` | `FilterRegistrationBean<Filter>` | 并发预算过滤器。 |
| `aiGatewayRequestTimeoutFilter` | `FilterRegistrationBean<Filter>` | 首字节超时过滤器。 |
| `javaMailSender` | `JavaMailSender` | 本地开发用的邮件 Mock，不真正外发。 |
| `webSocketContainerFactory` | `ServletServerContainerFactoryBean` | 放宽 WebSocket 单帧上限，避免整段粘贴触发容器级超限断开。 |

**内部依赖**：`filesystem`、`common`、`oauth-client`、`rpc`、`datasearch`、`mybatis`、`datasource`、`cache`

**HTTP 入口**（`@RestController`/`@Controller`，共 116 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `AIChatController` | 方法级 | AI 对话聊天控制器。 提供会话管理（创建/删除/查询）和基于 SSE 的流式聊天能力。 继承 BaseAiController 以便统一使用基础服务能力。 |
| `AgentBrowserController` | `/api/browser` | Agent Browser 管理控制器。 提供浏览器实例（Lane）、标签页（Tab）、宿主进程（Host）和全局设置的 CRUD 操作。 对接前端 /api/browser/* 端点，替代原 Ru… |
| `AgentConfigController` | `/v2/agent-config` | Agent 配置管理控制器 —— Agent 编排、Agent-as-模型 转换。 编排配置持久化通过 SysAiGlobalSettingService 以 "orchestration" 分组存… |
| `AiAlertController` | `/v2/alerts` | 告警规则控制器，提供规则的增删改查与启停切换。 所有写接口均受 @Permission("admin") 保护。 |
| `AiAppKeyController` | `/v2/app-keys` | appKey 控制器，提供 appKey 的增删改查与启停切换。 统一返回 ReturnResult。鉴权按用户隔离：查询对全体登录用户开放， 非管理员仅返回本人名下记录；写接口由服务层的归属守卫校… |
| `AiBotBindingController` | `/v2/ai/bot-binding` | 微信 i链接 机器人 多绑定管理控制器。 支持多 Bot 绑定的增删改查。 |
| `AiChannelHealthController` | `/v2/channels` | 渠道健康探针控制器。 提供渠道健康状态的查询/手动探针/探针间隔设置接口， 页面入口 /channels/health |
| `AiCommandController` | `/v2` | 命令/技能列表控制器 |
| `AiGatewayController` | `/v2/ai/gateway` | 端点同时监听主端口与独立 AI 端口。 端口增删影响整台主机的监听面，整个控制器仅管理员可用。 |
| `AiGitController` | `/v2/sessions/{id}/git` | 会话工作区 Git 操作接口。 所有接口都以会话工作目录为作用域，路径参数使用仓库相对路径。 |
| … | … | 其余 106 个控制器见模块源码 |

**组件扫描 Service**：`AIDialogDbRecordService`、`AgentMcpRuntimeService`、`AgentPackageService`、`AiAccessGuardServiceImpl`、`AiAlertServiceImpl`、`AiAppKeyServiceImpl`、`AiBotBindingService`、`AiBotChatService`、`AiChatFacadeServiceImpl`、`AiFaceServiceImpl`、`AiGitService`、`AiGlobalSettingServiceImpl`、`AiGroupServiceImpl`、`AiImageRecognitionServiceImpl`、`AiInputOptimizeServiceImpl` 等 81 个。`@Service` 注册，可直接注入。

**SPI 扩展点**：`AgentProvider`、`ChatClient`、`GlobalSettingProvider`、`VectorStorageProvider`、`ModelRegistrar`、`AgentRequestStorage`、`AgentMarketSourceProvider`、`SkillMarketSourceProvider` 等 12 个（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`plugin.ai.generate.*`，`AiProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `image` | `Image` | `new Image()` | 图片生成默认配置 |
| `video` | `Video` | `new Video()` | 视频生成默认配置 |

<details><summary>AiProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `image.provider` | `String` | `"openai"` | 生成提供者（如 openai、zhipu） |
| `image.api-key` | `String` | `—` | API Key |
| `image.base-url` | `String` | `—` | 自定义 基础 URL（可选） |
| `image.model` | `String` | `"dall-e-3"` | 默认模型（如 dall-e-3） |
| `image.width` | `int` | `1024` |  |
| `image.height` | `int` | `1024` |  |
| `video.provider` | `String` | `"openai"` | 生成提供者（如 openai、zhipu） |
| `video.api-key` | `String` | `—` | API Key |
| `video.base-url` | `String` | `—` | 自定义 基础 URL（可选） |
| `video.model` | `String` | `"sora"` | 默认模型（如 sora） |
| `video.duration` | `Integer` | `5` | 默认时长（秒） |

</details>

**配置**（`plugin.ai.default-model.*`，`DefaultModelProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `chat` | `String` | `—` | 默认对话模型 |
| `title-summary` | `String` | `—` | 默认标题总结模型 |
| `visual-assistant` | `String` | `—` | 默认视觉辅助模型 |
| `history-compression` | `String` | `—` | 默认历史压缩模型 |
| `history-compression-prompt` | `String` | `—` | 默认历史压缩提示词（与历史压缩模型同组，供外部模块一并读取） |
| `translation` | `String` | `—` | 默认翻译模型 |
| `translation-prompt` | `String` | `—` | 默认翻译提示词（与翻译模型同组，供外部模块一并读取） |
| `plate-detect` | `String` | `—` | 默认车牌检测模型 |
| `plate-recognize` | `String` | `—` | 默认车牌识别模型 |
| `feature` | `String` | `—` | 默认特征提取模型 |
| `rag-embedding` | `String` | `—` | 默认 RAG 嵌入模型 |
| `rerank` | `String` | `—` | 默认重排序模型 |
| `image-quality` | `String` | `—` | 默认图像质量评估模型 |
| `text-to-video` | `String` | `—` | 默认文生视频模型 |
| `text-to-image` | `String` | `—` | 默认文生图模型 |
| `image-enhance` | `String` | `—` | 默认图像增强模型 |
| `ocr-detect` | `String` | `—` | 默认 OCR 文字检测模型 |
| `ocr-recognize` | `String` | `—` | 默认 OCR 文字识别模型 |
| `ocr-direction` | `String` | `—` | 默认 OCR 方向矫正模型 |
| `ocr-super-resolution` | `String` | `—` | 默认 OCR 超分模型 |
| `layout` | `String` | `—` | 默认版面分析模型 |
| `face-detect` | `String` | `—` | 默认人脸检测模型 |
| `face-recognize` | `String` | `—` | 默认人脸识别（特征）模型 |
| `liveness` | `String` | `—` | 默认活体检测模型 |
| `eye-detect` | `String` | `—` | 默认眼睛检测模型 |
| `smile-detect` | `String` | `—` | 默认微笑检测模型 |
| `image-classify` | `String` | `—` | 默认图像分类模型 |
| `image-detect` | `String` | `—` | 默认目标检测模型 |
| `image-segment` | `String` | `—` | 默认图像分割模型 |
| `image-captioning` | `String` | `—` | 默认图像描述模型 |
| `depth` | `String` | `—` | 默认深度估计模型 |
| `face-quality` | `String` | `—` | 默认人脸质量评估模型 |
| `pedestrian` | `String` | `—` | 默认行人检测模型 |

**配置**（`plugin.ai.rag.*`，`RagProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 是否启用 RAG |
| `embedding-engine` | `String` | `"memory"` | 默认向量化引擎（内存 / openai / siliconflow 等） |
| `vector-storage` | `String` | `"jvector"` | 默认向量数据库（jvector / milvus / MySQL / 内存） 默认 jvector；当运行环境缺少 jvector 核心依赖（native VectorizationProvider）时， com.chua.starter.ai.support.configuration.rag.RagClientManager 会自动降级为内存存储。 |
| `embedding-dimensions` | `int` | `1536` |  |
| `chunk-size` | `int` | `1000` |  |
| `chunk-overlap` | `int` | `200` |  |
| `default-top-k` | `int` | `5` | 默认召回 Top-K |
| `default-similarity-threshold` | `double` | `0.7` | 默认相似度阈值 |
| `upload-dir` | `String` | `"./rag-uploads"` | 默认上传目录 |

**通用场景**

业务注入 `ChatClient` 或 `ChatClientRegistry` 按模型取客户端；网关限流/超时在 `plugin.ai.security.*`。


### spring-support-ai-training-starter

**定位**：训练平台 REST 接入：ZIP 数据上传、异步训练、进度与超参数。

**装配**：`TrainAutoConfiguration`；`plugin.ai.training.enabled` 默认 true。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `dataService` | `DataService` | 训练数据集上传、解压与元数据管理。 |
| `trainController` | `TrainController` | 导出 REST 控制器：当宿主未通过组件扫描注册 TrainController 时生效， 避免训练端点依赖宿主的扫描包路径（与 通用/filesystem 启动 的惯例一致）。 |
| `trainServiceInfoContributor` | `ServiceInfoContributor` | AI 训练平台的 服务信息 贡献者。 |

**内部依赖**：`oauth-client`

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `TrainController` | `/v2/train` | AI 训练平台 REST 接口（面向对象风格）。 对应 AIAS 2_training_platform 的 TrainController，提供完整的训练闭环： 上传训练数据（ZIP）并自动解压 … |

**组件扫描 Service**：`DataServiceImpl`、`TrainServiceImpl`（共 2 个）。`@Service` 注册，可直接注入。

**配置**（`plugin.ai.training.*`，`TrainProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 是否启用 AI 训练模块，默认 true。 对应自动配置上的 plugin.ai.training.enabled 门控（默认开启，置 false 可整体关闭）。 |
| `max-size` | `int` | `3000` |  |
| `model-path` | `String` | `DEFAULT_BASE_DIR + File.separator + "re…` | 预训练模型路径（压缩 或目录，AIAS: resnet50_dl4j_推理.v3.压缩）。 |
| `data-root-path` | `String` | `DEFAULT_BASE_DIR + File.separator + "im…` | 数据解压根目录。 |
| `save-path` | `String` | `DEFAULT_BASE_DIR + File.separator + "mo…` | 新训练模型保存目录。 |
| `upload-path` | `String` | `DEFAULT_BASE_DIR + File.separator` | 文件上传目录。 |

**通用场景**

训练引擎按 `type` 走 SPI；新增引擎实现 `TrainProvider`，不要改调度器。


### spring-support-eduai-starter

**定位**：教育 AI：题库、组卷、批改管线与凭证/登录。

**装配**：`EduAiAutoConfiguration` + `@Import(EduAiCachePrimaryConfiguration)`；`plugin.eduai.*`。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `paperPipelineProperties` | `PaperPipelineProperties` | 把教育业务配置里的「试卷管线」参数映射给 ai-starter。 plugin.eduai.* 属于业务前缀，不能出现在 ai-starter 里； 管线包只认自己的 PaperPipelineProperties。加 @Primary： ai-starter 自带一份默认 Bean，宿主引入 eduai 时以这里的映射为准。 |
| `eduAiServiceInfoContributor` | `ServiceInfoContributor` | 注册教育AI模块的服务信息贡献者。 |
| `cacheManager` | `CacheManager` | 教育 AI 应用的唯一默认 CacheManager（Redis 源）。 |
| `studentCredentialProvider` | `CredentialProvider` | 注册学生凭据校验实现。 |
| `teacherCredentialProvider` | `CredentialProvider` | 注册教师凭据校验实现（工号 + 口令）。 |
| `studentLoginHandler` | `LoginHandler` | 注册学生登录处理器（oauth LoginHandler 扩展点，注册名 STUDENT）。 |
| `teacherLoginHandler` | `LoginHandler` | 注册教师登录处理器（oauth LoginHandler 扩展点，注册名 TEACHER）。 |

**内部依赖**：`common`、`mybatis`、`datasource`、`redis`、`queue`、`job`、`collect`、`ai`、`oauth-client`、`osgi`、`account`

**HTTP 入口**（`@RestController`/`@Controller`，共 21 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `EduAppConfigController` | `/v2/edu/app-config` | 应用配置控制器（管理员专属）。 键值开关的读写通道，供管理页「配置管理」使用 （如题库页的「老师上传自动通过」开关）。键名见 EduAppConfigService 常量，值一律字符串。 |
| `EduDimensionController` | `/v2/edu/dimension` | }。 整个控制器是管理员专属（2026-10-03 加）：本控制器原先一个 @Permission 都没有，而登录鉴权只校验「有没有有效令牌」，于是 任何已登录用户（含学生）都能全量读写这 5 个维… |
| `EduExamController` | `/v2/edu/exam` | 考试管理控制器 |
| `EduFunConfigController` | `/v2/edu/fun` | 趣味模块配置控制器 |
| `EduHanziController` | `/v2/edu/hanzi` | 汉字基础数据控制器（字 / 读音 / 笔顺 / 字画 / 释义 …）。 |
| `EduIdiomController` | `/v2/edu/idiom` | 成语游戏控制器 |
| `EduInsectController` | `/v2/edu/insect` | 昆虫图鉴控制器 |
| `EduKnowledgeDocController` | `/v2/edu/knowledge-doc` | 知识库文档管理控制器 |
| `EduKnowledgePointController` | `/v2/edu/knowledge-point` | 知识点管理控制器 |
| `EduMarkingController` | `/v2/edu/marking` | 阅卷管理控制器 |
| … | … | 其余 11 个控制器见模块源码 |

**组件扫描 Service**：`EduAppConfigService`、`EduDimensionService`、`EduExamServiceImpl`、`EduFunConfigServiceImpl`、`EduHanziServiceImpl`、`EduIdiomServiceImpl`、`EduInsectGuideServiceImpl`、`EduInsectServiceImpl`、`EduKnowledgeDocServiceImpl`、`EduKnowledgePointServiceImpl`、`EduMarkingServiceImpl`、`EduPaperServiceImpl`、`EduPaperTemplateServiceImpl`、`EduPoemServiceImpl`、`EduQuestionServiceImpl` 等 20 个。`@Service` 注册，可直接注入。

**SPI 扩展点**：`LoginHandler`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`plugin.eduai.*`，`EduAiProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `marking` | `Marking` | `new Marking()` | 阅卷配置 |
| `paper` | `Paper` | `new Paper()` | 组卷配置 |
| `knowledge` | `Knowledge` | `new Knowledge()` | 知识库配置 |
| `ai` | `Ai` | `new Ai()` | AI 引擎配置 |
| `collect` | `Collect` | `new Collect()` | 题库采集配置 |
| `alert` | `Alert` | `new Alert()` | AI 调用失败告警配置 |

<details><summary>EduAiProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `marking.async` | `boolean` | `true` | 是否异步阅卷 |
| `marking.timeout` | `int` | `1800` |  |
| `marking.call-timeout` | `int` | `90` |  |
| `marking.max-score` | `int` | `5` | 单题最大分（兜底值） |
| `marking.wrong-score-rate` | `double` | `0.6D` | 判错阈值：最终分 / 满分 低于该比例即记为错题。 「完成」把错题归入学生错题库时用它判错：0 表示退回旧口径（只有 0 分才算错）， 0.6 表示不及格（得分率低于 60%）就算错。 为什么要有这个开关：满分 10 分的主观题拿 3 分，旧口径（score > 0 就跳过） 会让它永远进不了错题本，学生看到的错题都是客观题，主观题薄弱点被系统性漏掉。 |
| `marking.image-dir` | `String` | `"./data/marking"` | 扫描阅卷的图片存放目录。 任务是异步且可重启续跑的，输入图片必须持久保存；不能只留在内存里。 |
| `marking.resume-limit` | `int` | `50` |  |
| `marking.max-concurrent` | `int` | `1` | 后台同时处理的阅卷任务数上限（默认 1 = 串行）。 每个任务都要跑 ONNX 版面 + OCR，原生/直连内存占用大且不受 -Xmx 约束； 并发跑多路会把整机内存打满 —— 实测 5 路并发触发内核 OOM 杀掉本服务 （journalctl -u eduai-backend 可见 Failed with result 'oom-kill'）， 重启后又续跑同样多的任务，形成崩溃循环、任务永… |
| `marking.preprocess` | `boolean` | `true` | 是否对上传图片做预处理（照度校正去阴影 + 灰度归一化）后再识别。 拍照卷常见「一侧亮一侧暗」，暗部题号会被识别成别的字符（实测 D→0）、 题号与作答被切成不同文字块。属于输入质量问题，调模型解决不了。 |
| `marking.layout-correction` | `boolean` | `false` | 是否启用 ONNX 卡片/布局矫正（card-correction-detector）。 开启时先在 paper 扫描管线里检测卡片四边形并透视拉平，再走原有预处理、 版面与 OCR；未检出或模型不可用时自动回退原图，不阻断阅卷。 |
| `marking.extract-strategy` | `String` | `"whole"` | 任意卷识别策略（SPI key）。 走 AnyExtractStrategy 扩展点，内置两个实现： whole（默认）= 整卷一次识别（版面规则切题 → 模型补题干 → 整卷模型 → 行规则兜底）； region = 按版面区域分块识别（区域切不出来时自动回退整卷）。 默认 whole：与既有行为一致，调用量最小；区域内逐块识别更细但调用量成倍上升， 配额充足、版式复杂（多栏/答题框）时再切到… |
| `paper.max-total-score` | `int` | `100` |  |
| `paper.default-question-count` | `int` | `20` |  |
| `paper.default-score-per-question` | `int` | `5` | 默认每题分值 |
| `knowledge.vector-dim` | `int` | `768` |  |
| `ai.enabled` | `boolean` | `true` | 是否启用 AI（关闭后回退到本地启发式逻辑） |
| `ai.provider` | `String` | `—` | AI 服务商（与 对话客户端registry 中的 提供者 一致） |
| `ai.model` | `String` | `—` | 模型名 |
| `ai.temperature` | `Double` | `0.3` | 温度参数 |
| `ai.retry` | `Retry` | `new Retry()` | AI 调用重试配置（限流 429 与瞬时故障）。 |
| `ai.retry.times` | `int` | `5` | 最大重试次数（0 = 不重试） |
| `ai.retry.backoff-millis` | `long` | `1000L` |  |
| `ai.retry.backoff-multiplier` | `double` | `2.0D` | 退避倍数（<=1 时按 2 处理） |
| `ai.retry.max-backoff-millis` | `long` | `20000L` |  |
| `ai.retry.retry-on-transient` | `boolean` | `true` | 除限流外，是否也重试瞬时故障（超时 / 502 / 503 / 连接异常） |
| `collect.default-cron` | `String` | `"0 */30 * * * ?"` | 采集任务未配置 Cron 时的默认调度周期（Spring 6 Cron） |
| `alert.enabled` | `boolean` | `false` | 是否启用告警 |
| `alert.webhook-url` | `String` | `—` | Webhook 地址（支持钉钉/企微/飞书等标准卡片格式） |
| `alert.timeout-millis` | `int` | `5000` |  |

</details>

**通用场景**

批改相关参数在 `plugin.eduai.marking.*`；学生/教师登录用模块注册的 `LoginHandler`。


### spring-support-agent-starter

**定位**：启动后向当前 JVM 自注入 Runtime Agent（字节码插桩 + APM 采集）。

**装配**：`AgentAutoConfiguration`；`plugin.agent.enabled` 默认 true，在全部单例初始化完成后 `attachSelf()`。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `agentAttachService` | `AgentAttachService` | 注册 Agent 自注入服务（宿主可自定义同类型 Bean 覆盖）。 |

**配置**（`plugin.agent.*`，`AgentAttachProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 是否启用自注入（默认启用）。 |
| `options` | `String` | `""` | agent 参数（如 port=4567；空串时由 RuntimeBoot 默认）。 |
| `shell-port` | `int` | `4567` |  |
| `async` | `boolean` | `true` | 是否异步注入（虚拟线程执行，避免阻塞 Spring 启动）。 |

**通用场景**

只想要观测不要改启动参数时用它；独立观测后台要关掉自身注入，避免自己观测自己。


### spring-support-ueba-starter

**定位**：把 UEBA 分析引擎装配成可注入 Bean，容器销毁时释放模型。

**装配**：`UebaAutoConfiguration`；`plugin.ueba.enabled` 默认 true。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `uebaEngine` | `UebaEngine` | 装配 UEBA 分析引擎 Bean。 |
| `uebaServiceInfoContributor` | `ServiceInfoContributor` | UEBA 分析引擎的 服务信息 贡献者。 |

**内部依赖**：`common`

**配置**（`plugin.ueba.*`，`UebaProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 是否启用 UEBA 分析引擎 |
| `llm` | `boolean` | `true` | 是否启用 minimind 语义解释（禁用时使用模板解释） |
| `config-resource` | `String` | `"ueba-config.yaml"` | 类路径 上的配置文件路径（当 配置路径 为空时使用） |
| `config-path` | `String` | `""` | 外部配置文件路径（优先于 配置resource） |
| `model-dir` | `String` | `""` | 模型目录（写入 ueba.模型.dir，auto编码器 与 LSTM 模型均从该目录加载） |

**通用场景**

纯自动配置模块，不做 REST/持久化；分析结果由调用方自行落库。


### spring-support-datasearch-starter

**定位**：天气/台风/汇率/节假日/IP 归属地服务化，统一前端契约。

**装配**：`DataSearchServiceConfiguration`；`plugin.datasearch.enabled` 默认 true，仅 Servlet Web。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `weatherDataService` | `WeatherDataService` | 天气数据服务（多气象源归一成本系统契约） |
| `typhoonDataService` | `TyphoonDataService` | 台风数据服务（风圈报文解析 + 多机构预报收敛） |
| `exchangeRateDataService` | `ExchangeRateDataService` | 汇率数据服务（最新价 + 周期涨跌 + 周期极值） |
| `holidayDataService` | `HolidayDataService` | 节假日数据服务（HolidayProvider SPI 归一成前端契约） |
| `ipLookupDataService` | `IpLookupDataService` | IP 归属地数据服务（本地 IP 库解析，无外呼） |
| `weatherV2Controller` | `WeatherV2Controller` | 顶栏天气接口 |
| `locationV2Controller` | `LocationV2Controller` | 顶栏定位解析接口 |
| `typhoonV2Controller` | `TyphoonV2Controller` | 顶栏台风接口 |
| `exchangeRateV2Controller` | `ExchangeRateV2Controller` | 顶栏汇率接口 |
| `holidayV2Controller` | `HolidayV2Controller` | 节假日前台接口 |
| `ipLookupV2Controller` | `IpLookupV2Controller` | IP 归属地接口 |

**内部依赖**：`oauth-client`

**HTTP 入口**（`@RestController`/`@Controller`，共 6 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `ExchangeRateV2Controller` | `/v2/exchange-rate` | 顶栏汇率接口。 前端启动期用 HEAD 探测本接口判断后台是否提供汇率能力，命中即以本接口为准， 未命中才回落 frankfurter。因此： 参数全部可选（HEAD 不带查询串也要能命中映射）； … |
| `HolidayV2Controller` | `/v2/holiday` | 节假日前台接口。 路径与前端 HOLIDAY_LIST_PATH（/v2/holiday/get）严格一致： 原实现由 system 模块的 SysHolidayController 提供并读库表，… |
| `IpLookupV2Controller` | `/v2/ip` | IP 归属地接口。 前端 ReIpText 在缺少归属地文案时才调用本接口（thirdPartyFix）， 因此： 参数可选：HEAD 探测不带查询串也要命中映射； 匿名可访问：IP 提示可能出现在… |
| `LocationV2Controller` | `/v2/location` | 定位解析接口。 顶栏天气的取位置环节：服务端按客户端来源 IP 出经纬度与地名， 省掉浏览器定位授权与三方定位接口。客户端来源 IP 无法确认时（内网/回环地址） 不再退回服务器自身定位，而是返回空… |
| `TyphoonV2Controller` | `/v2/typhoon` | 顶栏台风接口。 前端只探测 /active（详情与列表同属本控制器），故该路径必须匿名可访问、 无参也能命中映射。空列表是「当前无活跃台风」的权威结论，失败才是回落信号。 |
| `WeatherV2Controller` | `/v2/weather` | 顶栏天气接口。 前端启动期用 HEAD 探测本接口判断后台是否提供天气能力，命中即以本接口为准， 未命中才回落三方。因此： 参数全部可选（HEAD 不带查询串也要能命中映射）； 匿名可访问（顶栏在登… |

**组件扫描 Service**：`ExchangeRateDataServiceImpl`、`HolidayDataServiceImpl`、`IpLookupDataServiceImpl`、`TyphoonDataServiceImpl`、`WeatherDataServiceImpl`（共 5 个）。`@Service` 注册，可直接注入。

**配置**：本模块没有 `@ConfigurationProperties`。

**通用场景**

顶栏类小数据直接用这五个 Service；数据源差异由 utils 层 SPI 归一。


### spring-support-apm-starter

**定位**：APM 门面：聚合 `ApmProvider`，统一 REST，支持嵌入 Servlet 或独立 HTTP 服务。

**装配**：`ApmAutoConfiguration`；`plugin.apm.enabled` 默认 true，`plugin.apm.mode=embedded|standalone`。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `apmRegistry` | `ApmRegistry` | 引擎注册表 |
| `apmRuntimeConfig` | `ApmRuntimeConfig` | APM 设置读写入口。 装配时先把设置文件里保存的值套回内存配置（ApmRuntimeConfig#restore()）： 界面上改过的开关/引擎/地址，进程重启后应当继续生效，否则「保存」就是个假动作。 |
| `apmDispatcher` | `ApmDispatcher` | 统一路由器 |
| `apmServletRegistration` | `ServletRegistrationBean<ApmServlet>` | 嵌入模式：把 ApmServlet 注册进宿主容器 |
| `apmStandaloneServer` | `ApmStandaloneServer` | 独立模式：单独起一个 HTTP 服务 |
| `apmServiceInfoContributor` | `ServiceInfoContributor` | 注册到统一服务信息面板：一眼看出本机有没有 APM、是哪个 |

**内部依赖**：`common`

**配置**（`plugin.apm.*`，`ApmProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 是否启用 APM 模块 |
| `type` | `String` | `""` | 引擎类型（skywalking / opentelemetry / hotspot）。 留空表示自动探测：按 ApmProvider#order() 依次尝试，取第一个可用者。 |
| `mode` | `String` | `"embedded"` | 启动模式： embedded — 向当前 Spring 容器注册 Servlet，随宿主服务一起启动； standalone — 用 ServerBuilder 单独起一个 HTTP 服务。 |
| `path` | `String` | `"/api/apm/v1"` | 统一 REST 前缀（嵌入模式对应 Servlet 映射，独立模式对应 Server 路由） |
| `host` | `String` | `"0.0.0.0"` | 独立模式监听地址 |
| `port` | `int` | `12801` |  |
| `server-type` | `String` | `"jdk"` | 独立模式底层 Server SPI 类型（jdk / netty …） |
| `detect-interval` | `long` | `10_000L` | 探测结果缓存时间（毫秒）：避免前端轮询状态时反复探活 |
| `settings-file` | `String` | `"config/apm-settings.properties"` | 运行期可改配置（UI 上的「APM 设置」保存的值）落盘文件。 只存「允许热改」的键（enabled / type / detectInterval 与各引擎的 运行期设置如 OAP 地址）；部署侧的 mode/path/port 不在此列 —— 它们决定 Servlet 与端口的注册方式，改了必须重启，写进文件反而会造成 「文件里是新值、进程里是旧值」的假象。 相对路径按进程工作目录解析；留空… |
| `register-service-info` | `boolean` | `true` | 是否登记到服务信息面板（ServiceInfoRegistry） |

**通用场景**

接新 APM 只实现 `ApmProvider`；业务侧永远只调 `ApmDispatcher`。


### spring-support-apm-skywalking-starter

**定位**：SkyWalking `ApmProvider`，并把 `plugin.apm.skywalking.*` 桥接到进程级设置。

**装配**：`SkywalkingApmAutoConfiguration`；配置了 `plugin.apm.skywalking.url` 才生效。

**公开 Bean**：本模块没有显式 `@Bean`；控制器/服务随组件扫描注册，见模块 README。

**内部依赖**：`apm`

**SPI 扩展点**：`ApmProvider`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`plugin.apm.skywalking.*`，`SkywalkingApmProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `url` | `String` | `""` | OAP 地址，例如 http://127.0.0.1:12800。留空表示沿用 skywalking.url |
| `username` | `String` | `""` | OAP Basic 鉴权用户名，留空表示沿用 skywalking.username |
| `password` | `String` | `""` | OAP Basic 鉴权密码，留空表示沿用 skywalking.password |
| `timeout` | `long` | `0L` | 单次查询超时（毫秒），<=0 表示沿用 skywalking.timeout |

**通用场景**

已有 SkyWalking 部署时加它即可；配置优先于 utils 层 `skywalking.*`。


### spring-support-apm-hotspot-starter

**定位**：自研 hotspot Agent 的 `ApmProvider` 实现。

**装配**：没有 `AutoConfiguration.imports`；通过 `META-INF/extensions/com.chua.starter.apm.support.spi.ApmProvider` 的 SPI 文件注册。

**公开 Bean**：本模块没有显式 `@Bean`；控制器/服务随组件扫描注册，见模块 README。

**SPI 扩展点**：`ApmProvider`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**：本模块没有 `@ConfigurationProperties`。

**通用场景**

与 apm-starter 一起用时自动被发现；不需要额外 `@Bean`。


### spring-support-hotsport-starter

**定位**：热点观测台：REST + 内置 Vue 前端 + 自注入 Agent 的请求/日志采集。

**装配**：4 个配置类；请求记录与日志采集默认 true，`plugin.hotsport.request-record.*`、`plugin.hotsport.log-capture.*`。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `apmLogAppender` | `ApmLogAppender` | 注册日志采集 appender（销毁时自动从 root logger 摘除）。 |
| `slowCallFilter` | `FilterRegistrationBean<SlowCallFilter>` | 注册请求采集过滤器。 |

**内部依赖**：`common`、`agent`

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `ApmController` | `/api/apm` | APM 热点观测 REST 接口。 统一前缀 /api/apm，由前端 vue-support-hotspot-starter 经 @repo/utils 的 http().get('/api/ap… |

**组件扫描 Service**：`ApmQueryService`（共 1 个）。`@Service` 注册，可直接注入。

另有 3 个 `@Service` 标注的类未在自动配置里找到注册路径，需宿主扫描对应包才能注入。

**配置**（`plugin.hotsport.*`，`HotsportProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `agent` | `Agent` | `new Agent()` | Agent 配置。 |
| `apm` | `Apm` | `new Apm()` | APM 存储配置。 |

<details><summary>HotsportProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `agent.enabled` | `boolean` | `true` | 是否启用自注入。 |
| `agent.shell-port` | `int` | `4567` |  |
| `agent.options` | `String` | `""` | 额外 Agent 参数（逗号分隔 Key=值）。 |
| `apm.storage` | `Storage` | `new Storage()` | 存储配置。 |
| `apm.storage.type` | `String` | `"sqlite"` | 存储类型：sqlite / mysql / inmemory / noop。 默认 sqlite：真实数据库（SQLite 文件），重启不丢数据， 且不需要外部服务，观测组件不会因为数据库不可用而拖住宿主。 |
| `apm.storage.retention-ms` | `long` | `7L * 24 * 60 * 60 * 1000` |  |
| `apm.storage.capacity` | `int` | `100000` |  |
| `apm.storage.path` | `String` | `""` | 存储落盘目录（sqlite 等非内存实现使用；为空时用临时目录）。 |
| `apm.storage.url` | `String` | `""` | JDBC URL（type=mysql 时必填）。 形如 jdbc:mysql://127.0.0.1:3306/hotsport。驱动是可选依赖， 用 MySQL 存储时需自行引入 mysql-connector-j。 |
| `apm.storage.username` | `String` | `""` | 数据库用户名（type=mysql 时使用）。 |
| `apm.storage.password` | `String` | `""` | 数据库密码（type=mysql 时使用）。 |
| `apm.storage.pool-size` | `int` | `4` | 连接池大小（type=mysql 时使用，默认 4）。 |
| `apm.storage.batch-size` | `int` | `200` |  |
| `apm.storage.async` | `Boolean` | `—` | 写入模式：true 异步批量写（默认，业务线程不阻塞），false 逐条同步写。 null 表示「未显式配置」，沿用 APM 侧默认（异步）。 |
| `apm.storage.maintenance-interval-ms` | `long` | `—` | 自维护周期（毫秒）：按保留期清理过期数据、按容量淘汰最旧数据的检查间隔。 0 表示沿用 APM 侧默认（60 秒）。下限 5 秒。 |
| `apm.storage.flush-timeout-ms` | `long` | `—` | 关闭时等待异步队列落盘的最长时间（毫秒）。 0 表示沿用 APM 侧默认。设太短会在关闭瞬间丢数据，设太长会拖慢停机。 |

</details>

**通用场景**

排查慢接口时用 `threshold-ms` 只记慢请求；日志采集仅 logback 生效。


### spring-support-report-client-starter

**定位**：采集设备硬件/系统信息并定时上报到监控服务端。

**装配**：`ReportClientConfiguration`；`plugin.report.client.enabled=true`（默认关）且容器里有 `SyncClient` 才生效。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `customSanitizer` | `SanitizingFunction` | 上报前脱敏 Actuator 配置值。 |
| `jobAnnotationScanner` | `JobAnnotationScanner` | 注册 作业 注解扫描器 自动扫描 @作业 注解并注册为任务处理器 |
| `reportClientServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |
| `threadPoolTaskScheduler` | `ThreadPoolTaskScheduler` | 用线程池执行任务 |

**内部依赖**：`ssh`、`oauth-client`、`common`、`job`、`swagger`、`discovery`、`sync`

**配置**（`plugin.report.client.*`，`ReportProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `app-report` | `AppReport` | `new AppReport()` | 应用信息上报配置 |
| `metrics` | `Metrics` | `new Metrics()` | 设备指标上报配置 |
| `enable` | `boolean` | `false` | 是否启用同步 |
| `protocol` | `String` | `"rsocket-sync"` | 通信协议: rsocket |
| `host` | `String` | `"localhost"` | 服务端地址 |
| `port` | `int` | `29170` |  |
| `info` | `ClientInfo` | `new ClientInfo()` | 客户端信息配置 用于多网卡场景下指定客户端信息 |
| `url-qps` | `UrlQps` | `new UrlQps()` | URL QPS 统计配置 |

<details><summary>ReportProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `app-report.enabled` | `boolean` | `true` | 是否启用 |
| `app-report.interval` | `long` | `30` |  |
| `metrics.enabled` | `boolean` | `true` | 是否启用 |
| `metrics.interval` | `long` | `30` |  |
| `metrics.types` | `Set<MetricType>` | `new HashSet<>()` | 要上报的指标类型列表 可选值: CPU, 内存, DISK, NETWORK, 加载, 处理, TEMPERATURE 默认为空表示全部上报 |
| `info.host` | `String` | `—` | 客户端 IP 地址 多网卡场景下指定使用的 IP 地址，为空则自动获取 |
| `url-qps.enabled` | `boolean` | `true` | 是否启用 URL QPS 统计 |
| `url-qps.interval` | `long` | `30` |  |

</details>

**通用场景**

被监控端引它并指向服务端；没有 sync 模块时不会启动。


### spring-support-discovery-starter

**定位**：服务注册与发现，多协议适配。

**装配**：`DiscoveryConfiguration`；无类级开关，注册中心地址在 `plugin.discovery.*`。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `discoveryServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |

**内部依赖**：`common`

**配置**（`plugin.discovery.*`，`DiscoveryListProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `properties` | `List<DiscoveryProperties>` | `new LinkedList<>()` | 发现配置 |

**通用场景**

服务端/客户端都从发现模块取实例；不要自己维护地址列表。


### spring-support-configcenter-starter

**定位**：远程配置中心与 `@ConfigValue` 热更新。

**装配**：`ConfigValueAutoConfiguration`；`plugin.config-center.enable=true`（默认关）；环境后处理器在容器启动前加载远程配置。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `configCenter` | `ConfigCenter` | 获取配置中心 Bean 复用 配置center配置环境post处理器 创建的实例， 避免重复创建 配置center。 |
| `configValueBeanPostProcessor` | `ConfigValueBeanPostProcessor` | 创建 配置值 Bean 后置处理器 负责扫描 @配置值 注解并注册热更新监听。 |
| `valueAnnotationBeanPostProcessor` | `ValueAnnotationBeanPostProcessor` | 创建 @值 注解 Bean 后置处理器 负责扫描 @值 注解并注册热更新监听和配置缓存。 |
| `configCenterModuleEnvironment` | `ModuleEnvironmentRegistration` | 注册配置中心属性到全局环境 |
| `configCenterServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |

**内部依赖**：`common`

**配置**（`plugin.config-center.*`，`ConfigCenterProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用配置中心 |
| `connect-timeout` | `int` | `5000` |  |
| `read-timeout` | `int` | `5000` |  |
| `protocol` | `String` | `—` | 协议类型 支持：nacos, apollo, consul, ZooKeeper 等 |
| `address` | `String` | `—` | 配置中心地址 |
| `username` | `String` | `—` | 账号 |
| `password` | `String` | `—` | 密码 |
| `namespace-id` | `String` | `—` | 命名空间标识 不填写默认为 spring.配置文件.活跃 |
| `hot-reload` | `HotReload` | `new HotReload()` | 热更新配置 |

<details><summary>ConfigCenterProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `hot-reload.enabled` | `boolean` | `true` | 是否启用热更新 启用后，配置中心的配置变更会自动同步到应用 |
| `hot-reload.value-annotation-enabled` | `boolean` | `true` | 是否支持 @值 注解热更新 启用后，使用 @值 注解的字段也能实现热更新。 注意：需要配合 @refreshscope 使用，或者使用 @配置值 注解。 |
| `hot-reload.config-value-annotation-enabled` | `boolean` | `true` | 是否支持 @配置值 注解热更新 启用后，使用 @配置值 注解的字段支持热更新， 无需 @refreshscope。 |
| `hot-reload.refresh-delay-ms` | `long` | `100` |  |
| `hot-reload.log-on-change` | `boolean` | `true` | 是否在配置变更时打印日志 |

</details>

**通用场景**

需要热更新用 `@ConfigValue`；静态配置仍走 Spring Environment。


### spring-support-console-starter

**定位**：运行期 FTL 控制台：临时覆盖 `@ApiFeature`/`@ApiGray` 与通用 KV，落 SQLite。

**装配**：`ConsoleAutoConfiguration`；`plugin.console.enable` 默认 true；页面控制器随 `@Import` 注册。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `annotationSwitchScanner` | `AnnotationSwitchScanner` | 扫描 `@ApiFeature`/`@ApiGray` 生成开关清单。 |
| `consoleSqliteStore` | `ConsoleSqliteStore` | 控制台覆盖数据的 SQLite 持久层。 |
| `consoleOverrideProvider` | `ConsoleOverrideProvider` | 把控制台覆盖接到通用开关判定。 |
| `consoleStartupResetter` | `ConsoleStartupResetter` | 启动时比对注解指纹，不一致则重置覆盖库。 |
| `consoleServiceInfoContributor` | `ServiceInfoContributor` | 管理控制台的 服务信息 贡献者。 |

**内部依赖**：`oauth-client`

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `ConsolePageController` | `/console` | 管理控制台 FTL 页面控制器。 页面与调整接口统一以 #CONSOLE_PERMISSION 权限码鉴权（oauth-client @Permission + PermissionIntercep… |

**配置**（`plugin.console.*`，`ConsoleProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 是否启用控制台（默认启用——模块按需引入） |
| `sqlite-path` | `String` | `"console.sqlite"` | sqlite 持久化文件路径（相对运行目录，默认 控制台.sqlite） |
| `permission-code` | `String` | `"console:manage"` | 页面访问所需权限码（页面方法标注 @Permission(permissionCode)） |
| `kv-prefix` | `String` | `""` | 键值设置前缀过滤（为空表示管理全部键值） |

**通用场景**

灰度/开关临时调整用它，不要改代码重启；启动时注解指纹不一致会重置覆盖库。


### spring-support-osgi-starter

**定位**：Apache Felix OSGi 运行时、Gogo shell 与 bundle 生命周期。

**装配**：`OsgiAutoConfiguration`；`plugin.osgi.enabled` 默认 true；SSH/Telnet/端点各有子开关。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `osgiLauncher` | `FelixOsgiLauncher` | 创建并启动 Felix osgi 框架。 |
| `bundleDeployWatcher` | `BundleDeployWatcher` | 创建目录热部署监听器。 |
| `sourceExtensionScheduler` | `SourceExtensionScheduler` | 创建源码扩展点热重载调度器。 默认即开启：把 Java/Groovy 源码放进 plugin.osgi.source.directory，改动会被自动编译并注册成 SPI 扩展点。 不依赖 Felix 框架是否启动成功——源码扩展点走 ServiceProvider 扩展点表，与 bundle 机制相互独立， 这样即便框架启动失败，源码里的实现仍然可用。 调度器作为 BeanFactoryPos… |
| `gogoSshShellServer` | `GogoSshShellServer` | Gogo SSH Shell 服务器：SSH 模式远程管理（bundle 安装/卸载等 Gogo 命令）。 补齐 osgi.shell.ssh.* 配置无消费方的缺失装配——MINA SSHD 2.x 直连启动， SSH 通道会话接线到框架内 Gogo 命令处理器（按名服务查找 + 反射调用， 规避 bundle/应用类加载器类身份差异）。 |
| `osgiBeanDefinitionRegister` | `OsgiBeanDefinitionRegister` | 创建 osgi bean 定义注册 Bean，并将其挂接到核心对象上下文。 同时把注册器挂为框架生命周期监听器：bundle 的安装、启动、停止、升级、卸载 都会推进其内部快照版本，使桥接产出的扩展对象随之切换—— 这是「OSGi 侧变化 → 容器侧扩展对象变化」的传导链路。 |
| `osgiServiceInfoContributor` | `ServiceInfoContributor` | 注册 osgi 模块服务信息。 |
| `osgiMonitorController` | `OsgiMonitorController` | 创建 osgi 运行时只读观测端点。 仅在显式开启 plugin.osgi.endpoint.enabled 且 classpath 中存在 spring-web 的 Web 应用环境下装配，避免在非 Web 场景引入 web 依赖。 |

**内部依赖**：`common`

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `OsgiMonitorController` | `/osgi` | } 只对管理员开放。 仅在 plugin.osgi.endpoint.enabled=true 且 classpath 中存在 spring-web 时装配。 |

**配置**（`plugin.osgi.*`，`OsgiProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 是否启用 osgi 集成（默认开启）。 |
| `storage` | `String` | `"./osgi-cache"` | Felix 框架持久化存储目录。 |
| `start-level` | `int` | `4` | Felix 框架初始启动级别。 |
| `storage-clean` | `String` | `"onFirstInit"` | Felix 存储清理策略。 取值为 OSGi 规范定义的 onFirstInit / onInit / none： onFirstInit（默认）— 首次初始化时清空缓存目录 onInit — 每次初始化都清空 none — 不清空，保留上次运行的 bundle 状态（支持重启续用） |
| `auto-start-installed-bundles` | `boolean` | `true` | 是否在安装 bundle 后自动启动（默认开启）。 |
| `framework-properties` | `Map<String, Object>` | `new HashMap<>()` | Felix 框架附加配置（原样透传给 Felix 框架）。 用于需要自定义框架行为的场景，例如跨 bundle 共享应用侧接口包： plugin: osgi: framework-properties: org.osgi.framework.system.packages.extra: com.example:api |
| `deploy` | `Deploy` | `new Deploy()` | 目录热部署配置。 |
| `endpoint` | `Endpoint` | `new Endpoint()` | 运行时观测端点配置。 |
| `bridge` | `Bridge` | `new Bridge()` | Bean 桥接配置。 |
| `source` | `Source` | `new Source()` | 源码扩展点配置。 |
| `shell` | `Shell` | `new Shell()` | 远程管理配置（SSH 或 Telnet，二选一）。 |

<details><summary>OsgiProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `bridge.acceptance-enabled` | `boolean` | `true` | 是否启用 Bean 验收。 启用后，投影出的每个桥接定义都会经过 BeanAcceptance 验收器链； 任一验收器拒绝即不纳入快照，拒绝原因可经 OsgiBeanDefinitionRegister#getRejectedSnapshots() 查询。 |
| `bridge.invalidate-on-bundle-lifecycle` | `boolean` | `true` | 是否在 bundle 生命周期变化时失效桥接快照。 关闭后将退化为「每次查询都重新枚举框架」，正确性不变但失去缓存收益； 仅在调试切换行为时需要关闭。 |
| `endpoint.enabled` | `boolean` | `false` | 是否启用观测端点。 默认关闭：端点会暴露插件符号名、版本、来源位置等信息， 需由使用方显式开启并纳入既有的接口鉴权体系。 |
| `endpoint.manage-enabled` | `boolean` | `true` | 是否允许变更类操作（上传安装、升级、启动、停止、卸载）。 默认开启，但只有在 #enabled=true 时端点才存在。 关闭后仍可在界面上查看 bundle 清单，但不能改；适用于只读观测场景。 |
| `source.enabled` | `boolean` | `true` | 是否启用源码扩展点热重载（默认开启）。 |
| `source.directory` | `String` | `"./osgi-source"` | 源码目录。 目录不存在时按「无源码」处理，不报错——配置项可以先开起来再放源码。 |
| `source.output-directory` | `String` | `""` | 编译产物输出目录。 留空时取 /../osgi-source-classes。 磁盘占用提示：应用以 Spring Boot fat jar 部署时，编译器必须把 BOOT-INF/lib 里的依赖抽出到该目录 才能让 javac 读到它们 （javac 不认 jar:nested: 协议）。这意味着该目录会占用 与一份 BOOT-INF/lib 相当的空间——实测 eduai 约 685MB。… |
| `source.polling-interval` | `long` | `5000L` |  |
| `source.groovy-enabled` | `boolean` | `true` | 是否启用 Groovy 脚本扩展点。 开启后目录下的 .groovy 也会被加载，走既有 ScriptProvider SPI。若 classpath 中没有对应引擎实现， 该部分会告警并降级为「只支持 Java 源码」，不影响启动。 |
| `source.script-engine` | `String` | `"groovy"` | 脚本引擎名，对应 ScriptProvider#engineName。 |
| `deploy.enabled` | `boolean` | `false` | 是否启用目录热部署。 |
| `deploy.directory` | `String` | `"./osgi-deploy"` | 部署目录。 |
| `deploy.polling-interval` | `long` | `10000L` |  |
| `deploy.uninstall-on-removed` | `boolean` | `true` | 部署目录中 jar 移除后是否自动卸载对应 bundle。 |
| `deploy.update-on-changed` | `boolean` | `false` | 是否在移除对应 jar 后对已安装 bundle 执行原地升级。 关闭时移除 jar 会导致 bundle 停留在原版本；开启后 以框架原生 update 替换为新内容，保留 bundle 标识与状态。 |
| `shell.enabled` | `boolean` | `false` | 是否启用远程管理。 |
| `shell.protocol` | `String` | `"ssh"` | 协议类型：ssh（默认）或 telnet。 |
| `shell.port` | `int` | `—` | 监听端口。 SSH 协议：默认 8122 Telnet 协议：默认 6666 |
| `shell.host` | `String` | `"127.0.0.1"` | 绑定地址。 |
| `shell.username` | `String` | `""` | 认证用户名（SSH 模式）。 |
| `shell.password` | `String` | `""` | 认证密码（SSH 模式）。 |
| `shell.host-key` | `String` | `""` | 主机密钥文件路径（SSH 模式）。 留空时使用默认路径 target/osgi-hostkey.ser。 首次启动会自动生成并持久化，后续复用同一密钥，避免客户端反复出现主机指纹变更告警。 |
| `shell.allow-empty-credential` | `boolean` | `false` | 是否允许空凭据登录。 默认关闭。此项开启后，配置为空的用户名与空密码即可登录远程管理通道， 等同于无认证暴露，仅建议在完全受信任的本地调试环境临时开启。 |
| `shell.max-auth-attempts` | `int` | `3` | 单次连接允许的认证失败次数上限，超过则断开连接。 |
| `shell.install-framework-sshd-bundles` | `boolean` | `false` | 是否把 Apache SSHD 自身也作为 OSGI bundle 安装到框架内。 默认关闭，且开启前请先了解：该能力在 sshd 2.13.2 下无法真正工作。 sshd-sftp 与 sshd-scp 声明 Import org.apache.sshd.client;version="[2.13.2,2.14)"， 而唯一导出该包的 sshd-osgi 对 org.apache.sshd.c… |

</details>

**通用场景**

需要热插拔 bundle 时用；普通业务不要为了模块化引入 OSGi。


### spring-support-bundle-starter

**定位**：按 Maven 坐标安装/升级/卸载 OSGi Bundle，自动处理依赖顺序。

**装配**：`BundleAutoConfiguration`；`plugin.bundle.enabled=true`（默认关），类路径需 Felix/Osgi 启动器。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `bundleManagerService` | `BundleManagerService` | 创建 Bundle 管理器服务。 |
| `bundleStartupProcessor` | `org.springframework.beans.factory.SmartInitializingSingleton` | 启动时按配置自动安装/卸载 bundle。 执行顺序：Spring 容器初始化完成后（SmartInitializingSingleton）， 先卸载 uninstall-at-startup 列表，再安装 install-at-startup 列表。 |
| `bundleServiceInfoContributor` | `ServiceInfoContributor` | 注册 Bundle 模块服务信息。 |

**内部依赖**：`common`

**配置**（`plugin.bundle.*`，`BundleProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `false` | 是否启用 Bundle 管理器（默认禁用，需显式开启）。 |
| `install-at-startup` | `List<String>` | `new ArrayList<>()` | 启动时自动安装的 Bundle Maven 坐标列表。 格式：groupId:artifactId:version，例如： org.apache.felix:org.apache.felix.gogo.runtime:1.1.6 |
| `uninstall-at-startup` | `List<String>` | `new ArrayList<>()` | 启动时自动卸载的 Bundle 符号名列表。 |
| `auto-upgrade` | `boolean` | `true` | 是否允许自动升级已有 bundle（默认 true）。 |
| `repository-url` | `String` | `"https://repo1.maven.org/maven2"` | Maven 仓库 URL（用于从远程解析 bundle）。 |

**通用场景**

与 osgi-starter 配套；启动时批量装卸用 `plugin.bundle.*` 的启动清单。


### spring-support-collect-starter

**定位**：采集平台：流程、函数、脚本、Agent、通知与调度端口。

**装配**：`CollectAutoConfiguration`（`plugin.collect.enable` 默认 true）+ `CollectScheduleConfiguration`；有 job 台账时排期落 `sys_job`，否则本地兜底。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `collectFlowSchedulePort` | `CollectFlowSchedulePort` | 排期端口：有 job 台账用台账实现，没有则本地兜底（定时不可用）。 |
| `yamlMimeMappings` | `WebServerFactoryCustomizer<TomcatServletWebServerFactory>` | 追加 yaml 相关的 MIME 映射。 基于 MimeMappings#DEFAULT 复制一份再追加，避免覆盖 Tomcat 自带的映射 （例如 .wasm 已经是 application/wasm，丢掉会连带影响 WebAssembly 加载）。 |

**内部依赖**：`mybatis`、`datasource`、`datasource-hub`、`job`

**HTTP 入口**（`@RestController`/`@Controller`，共 14 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `CollectAlertRuleController` | `/v2/collect/alert-rule` | 采集告警规则接口。 |
| `CollectCategoryController` | `/v2/collect/category` | 采集分类接口。 |
| `CollectDatasourceController` | `/v2/collect/datasource` | 采集数据源接口。 列表与详情永不回传密码，只回是否已设置；连接测试允许用未保存的配置。 |
| `CollectDesignerController` | `/v2/collect/designer` | 采集设计器接口。 工具箱、属性面板、运行概览与定时预览的数据都来自这里，设计器不内置节点类型清单。 |
| `CollectExecutionController` | `/v2/collect/execution` | 采集执行接口。 运行请求提交后立即返回任务号，执行在虚拟线程里进行；试运行不落结果，只回任务与节点日志。 |
| `CollectFieldController` | `/v2/collect/field` | 采集结果字段接口。 |
| `CollectFlowController` | `/v2/collect/flow` | 采集流程接口。 流程定义即前端设计器保存的 DSL，列表不返回 DSL 正文，只有详情返回。 校验失败由全局异常处理转成 400 与中文提示，控制器不再重复包装。 |
| `CollectFlowVersionController` | `/v2/collect/flow-version` | 流程版本接口。 |
| `CollectFunctionController` | `/v2/collect/function` | 自定义函数接口。 函数登记后进的是编译校验这一关，存下来即可在脚本里 fn.名字(...) 调用， 也能在任意节点参数里写 ${名字(...)}，两条路共用同一张函数表。 |
| `CollectNoticeController` | `/v2/collect/notice` | 执行通知接口。 通知是平台级配置，库里没有流程维度，每条配置对每次执行都参与命中； 渠道清单来自注册表，第三方注册新的 CollectNotifyChannel 后这里与前端下拉都会自动出现。 |
| … | … | 其余 4 个控制器见模块源码 |

**组件扫描 Service**：`CollectAlertRuleServiceImpl`、`CollectCategoryServiceImpl`、`CollectDatasourceServiceImpl`、`CollectDesignerServiceImpl`、`CollectExecutionServiceImpl`、`CollectFieldServiceImpl`、`CollectFlowServiceImpl`、`CollectFlowVersionServiceImpl`、`CollectFunctionServiceImpl`、`CollectNoticeServiceImpl`、`CollectRecordServiceImpl`、`CollectSettingsServiceImpl`、`CollectTaskServiceImpl`、`CollectVariableServiceImpl`（共 14 个）。`@Service` 注册，可直接注入。

**配置**（`plugin.collect.*`，`CollectProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 平台总开关，关闭后不装配调度任务与执行引擎。 |
| `default-thread-count` | `int` | `4` | 流程默认并行线程数，可被流程定义覆盖。 |
| `request-timeout-ms` | `int` | `30000` |  |
| `request-retry-times` | `int` | `1` | 默认重试次数，节点未显式配置时使用。 |
| `user-agent` | `String` | `DEFAULT_USER_AGENT` | 默认 User-Agent，节点未显式配置时使用。 |
| `sql-allow-write` | `boolean` | `false` | SQL 写操作开关：采集约定强暗示，默认必须禁止写入，关闭时才允许。 */ |
| `task-timeout-ms` | `long` | `1800000L` |  |
| `max-record-per-task` | `int` | `100000` |  |

**通用场景**

采集流程与任务解耦：排期只依赖 `CollectFlowSchedulePort`，换调度器不用改流程。


### spring-support-datasync-starter

**定位**：目录扫描/数据库映射两种来源的数据同步。

**装配**：`DataSyncAutoConfiguration`；`plugin.datasync.enabled` 默认 true，`plugin.datasync.mapping-source=file|database`。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `dataSyncMappingManager` | `DataSyncMappingManager` | 同步映射的注册与查询。 |
| `dataSyncServer` | `com.chua.starter.datasync.DataSyncServer` | 同步服务端，接收/推送变更。 |
| `agentServerManager` | `AgentServerManager` | Agent 侧服务端连接管理。 |
| `directoryDataSyncMappingScanner` | `DirectoryDataSyncMappingScanner` | 从目录扫描映射文件。 |
| `dataSyncServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |
| `dataSource` | `DataSource` | 配置数据源。 启动类已排除 DataSourceInspect（datasource-starter 动态数据源注册器）， 本数据源即为上下文唯一 数据源，DatabaseMappingLoader 直接注入本数据源 查询映射表。 数据库模式默认使用嵌入式 H2（schema.sql 建表 + 预置 db-user-sync），保证无外网 也能真实启动验证数据库加载链路；仅当显式传入 --dat… |

**内部依赖**：`osgi`、`ssh`、`datasource`

**配置**（`plugin.datasync.*`，`DatasyncProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 是否启用 数据同步 |
| `mapping-source` | `String` | `"file"` | 映射来源：文件 \| database |
| `file-mapping` | `FileMapping` | `new FileMapping()` | 文件模式配置 |
| `database-mapping` | `DatabaseMapping` | `new DatabaseMapping()` | 数据库模式配置 |

<details><summary>DatasyncProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `file-mapping.directory-path` | `String` | `"./datasync/mappings"` | 映射配置文件目录路径 |
| `database-mapping.enabled` | `boolean` | `false` | 是否启用数据库映射源 |
| `database-mapping.table-prefix` | `String` | `"datasync_"` | 表名前缀 |
| `database-mapping.cron` | `String` | `"0/5 * * * * ?"` | 数据库轮询间隔（Cron 表达式） |

</details>

**通用场景**

文件模式把映射放目录；库模式把映射放表，启动时 `DatabaseMappingLoader` 加载。


### spring-support-lowcode-starter

**定位**：低代码目录/表单/页面与版本发布，自带表结构与建表脚本。

**装配**：`LowcodeAutoConfiguration`；`plugin.lowcode.enable` 默认 true。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `lowcodeMetaObjectHandler` | `MetaObjectHandler` | 审计字段自动填充。 必须在自动配置里用 @Bean 声明：@ConditionalOnMissingBean 只有在这里 才能看到消费方已注册的同类型 bean 并正确让位；挂在 @Component 上时判定发生在 组件扫描期，实测会导致本 bean 完全不注册，审计列全部为 NULL。 |

**内部依赖**：`mybatis`、`datasource`

**HTTP 入口**（`@RestController`/`@Controller`，共 3 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `LowFormController` | `/v2/lowcode/form` | 低代码-表单接口。 示例：设计器用 /code/{formCode} 回读，运行期用 /api/{formCode} 取已发布 Schema。 |
| `LowFormDirectoryController` | `/v2/lowcode/directory` | 低代码-目录接口。 |
| `LowPageController` | `/v2/lowcode/page` | 低代码-页面接口。 示例：设计器用 /code/{pageCode} 回读，运行期用 /api/{pageCode} 取已发布 Schema。 |

**组件扫描 Service**：`LowFormDirectoryServiceImpl`、`LowFormServiceImpl`、`LowPageServiceImpl`（共 3 个）。`@Service` 注册，可直接注入。

**配置**（`plugin.lowcode.*`，`LowcodeProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 功能总开关。 关闭后不装配低代码的任何能力，接口自然也不排除，已保存的 Schema 保留在库里不动。 |

**通用场景**

运行期只认已发布快照；设计器改动先 `publish` 再给业务用。


### spring-support-account-starter

**定位**：账号、渠道、消息模板、临时邮箱等账号域能力。

**装配**：`AccountAutoConfiguration` + `TemplateAutoConfiguration`，无类级开关；表结构与 Mapper 随依赖注册。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `templateMybatisPlusInterceptor` | `MybatisPlusInterceptor` | 分页插件兜底。 模块的 XML 分页 SQL 依赖 MyBatis-Plus 的 PaginationInnerInterceptor； 宿主若已通过 spring-support-mybatis-starter 注册同名 Bean，本方法自动退避， 不改变既有装配。模块独立运行（仅引入 template-starter）时也能正确返回 total。 |
| `templateServiceInfoContributor` | `ServiceInfoContributor` | 注册模板模块服务信息。 |
| `sourceSyncRunner` | `ApplicationRunner` | 启动时全量来源同步器（无同步器时不执行源行修复，只打日志） |

**内部依赖**：`common`、`mybatis`

**HTTP 入口**（`@RestController`/`@Controller`，共 13 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `AccountAppKeyController` | `/api/accounts/app-keys` | 账号模块外部访问密钥管理接口。 与 AI 的模型计费密钥无关，是账号能力（设备 / 脚本 / 外部系统）的访问凭据： GET /api/accounts/app-keys：列表； POST /api… |
| `AccountController` | `/api/accounts` | 统一账号管理接口。 账号是设备、模板等必选依赖模块的公共凭据来源，也是账号管理页的主体： GET /api/accounts：账号列表（支持种类与关键词过滤）； GET /api/accounts/… |
| `AccountCookieController` | `/api/accounts/cookies` | 账号模块 Cookie 池管理接口。 供免登录渠道与会话型站点保存登录 Cookie，内容加密落库、接口不回显： GET /api/accounts/cookies：列表； POST /api/ac… |
| `AccountSecretController` | `/api/accounts/secrets` | 账号模块密钥库管理接口。 密钥是账号 / 设备 / server 共用的底层凭据： GET /api/accounts/secrets：列表； POST /api/accounts/secrets：… |
| `ChatChannelController` | `/api/channels` | 免登录对话渠道（ChatClient）控制器 —— 渠道管理页的「模型」与「试对话」入口。 与 /api/channels 的账号类接口并列：本控制器的渠道来自 ChatChannelProvide… |
| `ImageChannelController` | `/api/channels` | 文生图渠道（ImageChannelProvider）控制器 —— 渠道管理页的「出图」入口。 与 /api/channels 的账号类接口、/{channel/chat} 的对话接口并列： 本控制… |
| `MailboxController` | `/api/accounts/{accountId}/mailbox` | 收 / 发信箱接口。 挂在统一账号下面：账号页右键「打开邮箱 / 打开短信」进入的就是这里的收信箱， 不跳外链、不依赖第三方 Web 控制台。 GET /api/accounts/{accountI… |
| `MessageTemplateController` | `/api/channels/templates` | 短信 / 邮件消息模板管理接口。 短信 / 邮件消息模板管理接口，归「模板管理」域（不再挂渠道管理页）。 模板把渠道收到的原始消息渲染成可读文本；供短信接码 / 邮件收信能力的 模板管理页（Mess… |
| `Model3DChannelController` | `/api/channels` | 图生 3D 渠道（Model3DChannelProvider）控制器 —— 渠道管理页的「3D 模型」入口。 GET /{channel/3d-models}：可选的 3D 格式 / 模型； PO… |
| `SmsTemplateSyncController` | `/api/accounts` | 短信模板同步接口。 按账号 vendor 解析 MessagePush SPI，拉取厂商云端的审核通过模板， 落到消息模板表（kind=SMS）；不依赖具体厂商。 |
| … | … | 其余 3 个控制器见模块源码 |

**组件扫描 Service**：`MailboxService`、`ManageAccountServiceImpl`、`MessageTemplateService`、`SmsTemplateSyncService`、`SysAccountAppKeyServiceImpl`、`SysAccountCookieServiceImpl`、`SysAccountSecretServiceImpl`、`TempMailService`、`TemplateServiceImpl`（共 9 个）。`@Service` 注册，可直接注入。

**SPI 扩展点**：`AccountInboxProvider`、`AccountVerifier`、`ChatChannelProvider`、`ImageChannelProvider`、`Model3DChannelProvider`、`VideoChannelProvider`、`AccountChannelAuthManager`、`ChannelCreditProvider` 等 11 个（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**：本模块没有 `@ConfigurationProperties`。

**通用场景**

渠道能力归账号模块，AI 侧只消费；消息模板统一走 `MessageTemplateService`。


### spring-support-oauth-client-starter

**定位**：OAuth 客户端认证、Principal/HttpServletRequest 增强与权限拦截。

**装配**：`AuthClientConfiguration`；`plugin.oauth.enable` 默认 true。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `authService` | `AuthService` | o认证认证服务 提供用户认证和权限管理功能，集成o认证用户信息。 支持获取当前用户信息、角色验证、权限检查等功能。 |
| `permissionProxyFactoryBean` | `PermissionInterceptor` | 权限拦截器 提供基于注解的权限控制功能，支持方法级别的权限验证。 集成o认证用户信息进行权限检查。 |
| `oauthExceptionAdvice` | `OauthExceptionAdvice` | o认证 异常全局处理器 本模块不在业务应用的组件扫描范围内，故显式装配，令权限拦截器抛出的 OauthException 按自带状态码返回而不是退化为 500。 |
| `tokenForTypeInterceptor` | `TokenForTypeInterceptor` | 令牌类型拦截器 提供基于令牌类型的拦截功能，支持不同类型令牌的处理。 根据@令牌for类型注解自动处理令牌类型转换和验证。 |
| `tempProvider` | `UserStatisticProvider` | 用户统计提供者 提供用户统计和临时数据处理功能。 |
| `authFilterFilterRegistrationBean` | `FilterRegistrationBean<AuthFilter>` | o认证认证过滤器注册 注册o认证认证过滤器，提供以下功能： 1. 用户身份验证 2. Principal支持（Java.安全性.Principal） 3. httpservlet请求增强（是否用户入角色、获取认证类型、logout、login） 4. 会话管理 5. 用户信息存储 过滤器会创建o认证httpservlet请求包装器来增强httpservlet请求， 使其支持标准的Java EE安… |
| `authFilter` | `AuthFilter` | o认证 认证过滤器 Bean 作为 Spring Bean 暴露，支持注入后动态添加/删除白名单规则： AuthFilter#addWhitelist(String) - 动态添加白名单规则 AuthFilter#removeWhitelist(String) - 动态删除白名单规则 AuthFilter#clearWhitelist() - 清空动态白名单 |
| `rateLimitFilterRegistrationBean` | `FilterRegistrationBean<RateLimitFilter>` | o认证 限流过滤器注册 置于最高优先级，在认证过滤器之前对包括未认证流量在内的所有请求执行限流检查。 |
| `aiAppKeySupport` | `AiAppKeySupport` | AI appKey 本地认证支持 基于 com.chua.datasource.support.engine.JdbcEngine 动态查询 appKey 表， 无需编写 映射器，用于单机部署或外部 o认证 服务不可达时的 AI appKey 认证。 仅在 plugin.oauth.ai-app-key.enabled=true 且存在 数据源 时装配。 |
| `authClientServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |
| `authFilterBypass` | `AuthFilter` | 创建开发测试环境认证过滤器。 |
| `authFilterBypassRegistration` | `FilterRegistrationBean<AuthFilter>` | 注册开发测试环境认证过滤器。 |

**内部依赖**：`common`、`cache`、`swagger`、`mybatis`

**HTTP 入口**（`@RestController`/`@Controller`，共 3 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `LocalLoginPageProvider`（未确认注册） | 方法级 | 业务自渲染登录页（非前后端分离模式）。 为什么需要它。client-starter 的登录协议（POST /v1/users/login 换 accessToken）早已就绪，但登录成功后没有任何地… |
| `OAuthWhitelistController`（未确认注册） | `/oauth/whitelist` | o认证 客户端白名单管理控制器。 提供白名单规则的动态添加、删除与查询能力，规则变更实时生效，无需重启。 |
| `UserStatisticProvider` | `/v1/users` |  |

**配置**（`plugin.oauth.*`，`AuthClientProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 是否启用 |
| `local-admin-fallback` | `boolean` | `false` | 权限拦截器是否允许「无用户上下文即本地 admin」兜底 该兜底等价于绕过鉴权，仅供没有 oauth 服务端的本地联调显式打开； 默认 false：取不到用户上下文即拒绝。 |
| `static-token-expire-seconds` | `long` | `24L * 60 * 60` | 静态账号（用户名 / 密码）登录令牌的有效期（秒）。 历史上硬编码为 7 天，令牌签发后长期有效、后端重启也仍被前端长期缓存； 现缩短为默认 24 小时，可通过 plugin.oauth.static-token-expire-seconds 调整， 实际取值会被夹到 [60, 7天] 区间。 |
| `use-default-whitelist` | `boolean` | `true` | 内置清单混有历史应用专用路径（如 /api/extensions/**、/v2/usage/**）， 新应用需要按自身路由收紧时置 false，此时仅 yaml 与动态白名单生效。 注意：关闭它不会影响 #ALWAYS_WHITELIST —— 登录链路 是鉴权组件自身的必备豁免，关掉会让服务自锁（登录 403）。 默认 true，保持既有应用行为不变。 |
| `dynamic-whitelist` | `java.util.Set<String>` | `java.util.concurrent.ConcurrentHashMap.…` | 动态白名单（运行时可增删，线程安全） |
| `connect-timeout` | `long` | `5000` | 连接超时时间（毫秒） |
| `read-timeout` | `long` | `10000` | 读取超时时间（毫秒） |
| `request-timeout` | `long` | `15000` | 请求超时时间（毫秒） |
| `retry` | `RetryConfig` | `new RetryConfig()` | 重试配置 |
| `circuit-breaker` | `CircuitBreakerConfig` | `new CircuitBreakerConfig()` | 熔断配置 |
| `rate-limit` | `RateLimit` | `new RateLimit()` | 限流配置 o认证 客户端限流能力，支持接口 QPS 限流与登录防暴力破解限流。 |
| `cache-timeout` | `long` | `6 * 60 * 60` | 客户端缓存鉴权信息(用于提高访问效率)(s) |
| `cache-hot-cold-backup` | `boolean` | `true` | 是否开启客户端缓存冷热备份 |
| `address` | `String` | `—` | 鉴权地址 |
| `login-address` | `String` | `"${plugin.oauth.auth-address:}"` | 登录地址 |
| `encryption` | `String` | `"aes"` | 加密方式 |
| `enable-encryption` | `boolean` | `true` | 是否启用数据加密 |
| `block-address` | `Set<String>` | `Sets.newHashSet("/*")` | 拦截地址 |
| `whitelist` | `List<String>` | `new LinkedList<>()` | 白名单 |
| `whitelist-file` | `String` | `"config/oauth.whitelist"` | 外部白名单文件路径 支持动态加载和热更新，文件变更后自动重新加载。 文件格式：每行一个URL 模式，支持 # 注释 |
| `protocol` | `String` | `"tcp"` | 协议类型 可选值： tcp - TCP协议，基于原生 Socket，默认 http - HTTP协议，基于Unirest，同步实现 oauth2 - OAuth2 协议 rsocket - RSocket 协议 static - 静态协议，用于测试 |
| `token-name` | `String` | `"x-oauth-token"` | 令牌-名称 |
| `cookie-name` | `String` | `"x-oauth-cookie"` | Cookie-名称 |
| `alias` | `String` | `"Boren-Token"` | 令牌 别名 |
| `login-page` | `String` | `"/login"` | 登录页 |
| `logout-page` | `String` | `"/logout"` | 登出页 |
| `login-form-action` | `String` | `"/login/form"` | 注意 Ant 语义：/**&#47;login 不匹配 /login/form， 因此该路径必须显式出现在白名单（见 DEFAULT_WHITELIST 的深层条目），否则 表单提交会被鉴权拦回登录页自锁。 |
| `temporary-token-page` | `String` | `"/temporary-token"` | 临时令牌页 |
| `upgrade-page` | `String` | `"/upgrade"` | 升级页 |
| `oauth-url` | `String` | `"/verify"` | 鉴权地址 |
| `balance` | `String` | `"polling"` | 均衡模式 |
| `no-permission-page` | `String` | `"/oauth-page"` | 无权限页面 |
| `key` | `Aksk` | `new Aksk()` | 密钥 |
| `temp` | `TempUser` | `new TempUser()` | 临时用户 |
| `fingerprint` | `Fingerprint` | `new Fingerprint()` | 指纹验证配置 |
| `ai-app-key` | `AiAppKey` | `new AiAppKey()` | AI appKey 本地认证配置 启用后，o认证 过滤器对 Bearer 令牌 先做本地 AI appKey 校验 （通过 com.chua.datasource.support.engine.JdbcEngine 动态查表，无需 映射器）。 用于单机部署或外部 o认证 服务不可达时的 AI appKey 认证。 |

<details><summary>AuthClientProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `ai-app-key.enabled` | `boolean` | `false` | 是否启用 AI appKey 本地认证 |
| `ai-app-key.table-name` | `String` | `"sys_ai_app_key"` | appKey 表名（动态表，默认 AI 模块的 sys_AI_app_Key） |
| `ai-app-key.app-key-column` | `String` | `"sys_ai_app_key_app_key"` | appKey 列名（默认 sys_AI_app_Key_app_Key） |
| `ai-app-key.status-column` | `String` | `"sys_ai_app_key_status"` | 状态列名（1 启用 / 0 停用，默认 sys_AI_app_Key_状态） |
| `ai-app-key.expire-time-column` | `String` | `"sys_ai_app_key_expire_datetime"` | 过期时间列名（默认 sys_AI_app_Key_expire_datetime，可为空） |
| `ai-app-key.username-column` | `String` | `—` | 用户名列（返回给认证结果使用，默认取 appKeycolumn） |
| `key.enabled` | `boolean` | `false` | 是否启用AK/SK加密 |
| `key.access-key` | `String` | `—` | accessKey (用于标识客户端) |
| `key.secret-key` | `String` | `—` | secretKey (用于加密数据) |
| `temp.menu-path` | `String` | `—` | 菜单文件 |
| `temp.user` | `String` | `""` | 临时账号配置（只用于账号类型为 Embed）。 [安全警告] 生产环境禁止启用临时账号功能！ 格式：用户名:口令;用户名2:口令2。口令支持三种写法，按序判定： 加盐摘要（用户名:$，推荐）： 配置里不出现明文，且每账号盐不同，一张彩虹表无法批量比对。 盐是公开的——它的价值正在于此，详见 com.chua.starter.oauth.client.support.user.SaltedPass… |
| `fingerprint.global-verification` | `boolean` | `false` | 是否开启全局指纹验证 开启后，所有需要认证的接口都会验证浏览器指纹， 而不仅仅是标记了 @验证fingerprint 注解的接口。 默认关闭，只有标记了 @验证fingerprint 注解的接口才会验证指纹。 |
| `fingerprint.enable-client-verification` | `boolean` | `false` | 是否启用客户端指纹校验 开启后，客户端在发送认证请求前会先校验指纹是否在白名单中。 如果指纹不在白名单中，客户端会拒绝发送请求，返回错误信息。 默认关闭，指纹校验由服务端完成。 |
| `fingerprint.config-cache-timeout` | `long` | `3600` | 指纹配置缓存时间（秒） 客户端会缓存从服务端获取的 appKey 指纹配置，此配置控制缓存的有效期。 默认 1 小时（3600 秒）。 |
| `fingerprint.app-key-configs` | `java.util.Map<String, AppKeyFingerprintConfig>` | `new java.util.HashMap<>()` | appKey 指纹配置映射 Key: appKey 值: 指纹配置（enablefingerprint: 0-不启用, 1-启用; fingerprintwhitelist: 指纹白名单，多个用逗号分隔） 配置示例： plugin: oauth: fingerprint: appKeyConfigs: "appKey1": enableFingerprint: 1 fingerprintWhit… |
| `fingerprint.app-key-configs.<key>.enable-fingerprint` | `Integer` | `0` | 是否启用指纹检测 0: 不启用, 1: 启用 |
| `fingerprint.app-key-configs.<key>.fingerprint-whitelist` | `String` | `—` | 指纹白名单（多个指纹用逗号分隔） |
| `retry.enabled` | `boolean` | `true` | 是否启用重试 |
| `retry.max-attempts` | `int` | `3` | 最大重试次数 |
| `retry.delay` | `long` | `1000` | 重试间隔（毫秒） |
| `retry.multiplier` | `double` | `1.5` | 重试间隔倍数（指数退避） |
| `retry.max-delay` | `long` | `5000` | 最大重试间隔（毫秒） |
| `circuit-breaker.enabled` | `boolean` | `true` | 是否启用熔断 |
| `circuit-breaker.failure-rate-threshold` | `int` | `50` | 失败率阈值（百分比），超过则触发熔断 |
| `circuit-breaker.slow-call-rate-threshold` | `int` | `80` | 慢调用率阈值（百分比） |
| `circuit-breaker.slow-call-duration-threshold` | `long` | `3000` | 慢调用时间阈值（毫秒） |
| `circuit-breaker.sliding-window-size` | `int` | `10` | 滑动窗口大小 |
| `circuit-breaker.wait-duration-in-open-state` | `int` | `30` | 熔断器打开后等待时间（秒） |
| `circuit-breaker.permitted-calls-in-half-open-state` | `int` | `5` | 半开状态允许的请求数 |
| `circuit-breaker.fallback-message` | `String` | `"认证服务暂时不可用，请稍后重试"` | 降级响应消息 |
| `rate-limit.enable` | `boolean` | `false` | 是否启用 o认证 限流（总开关） 默认值：false |
| `rate-limit.login` | `LoginLimit` | `new LoginLimit()` | 登录限流配置 |
| `rate-limit.api` | `ApiLimit` | `new ApiLimit()` | 全局接口限流配置 |
| `rate-limit.login.enable` | `boolean` | `true` | 是否启用登录限流 默认值：true（在总开关 enable=true 前提下） |
| `rate-limit.login.ip-limit` | `int` | `30` | 同一 IP 窗口内最大登录尝试次数 默认值：30 次 |
| `rate-limit.login.ip-window-seconds` | `long` | `60` | 登录 IP 限流窗口（秒） 默认值：60 秒 |
| `rate-limit.login.account-fail-limit` | `int` | `5` | 同一账号失败多少次后锁定 默认值：5 次 |
| `rate-limit.login.account-lock-minutes` | `long` | `5` | 账号锁定时间（分钟） 默认值：5 分钟 |
| `rate-limit.api.enable` | `boolean` | `true` | 是否启用全局接口限流 默认值：true（在总开关 enable=true 前提下） |
| `rate-limit.api.qps` | `int` | `200` | 同一 IP 窗口内最大请求次数（QPS） 默认值：200 次/秒 |
| `rate-limit.api.window-seconds` | `long` | `1` | 全局接口限流窗口（秒） 默认值：1 秒 |

</details>

**通用场景**

前端登录态校验用它的过滤器；需要临时放行看 `plugin.oauth.temp.open`。


### spring-support-swagger-starter

**定位**：OpenAPI 文档（springdoc + Knife4j）与离线导出。

**装配**：`SpringDocConfigPropertiesConfiguration`、`Knife4jConfiguration` 等 5 个类；`plugin.swagger.enable` 默认 true，导出 `plugin.swagger.export.enabled`。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `webEndpointServletHandlerMapping` | `WebMvcEndpointHandlerMapping` | 增加如下配置可解决Spring Boot与Swagger 3.0.0不兼容问题 |
| `knife4jServiceInfoContributor` | `ServiceInfoContributor` | 刀4j 模块开关。 只注册"模块开关": 端点 (含 host:port 的完整 URL) 由 ServiceInfoEndpointDetector 统一探测注册, 此处不再重复注册, 否则横幅会出现两行 doc.html。 |
| `getKnife4jEnumModelPropertyPlugin` | `Knife4jEnumModelPropertyPlugin` | 获取刀4j枚举模型属性插件 |
| `springDocConfigProperties` | `SpringDocConfigProperties` | 注册 springdoc配置属性 Bean（兜底）。 |

**内部依赖**：`common`

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `SwaggerExportController`（未确认注册） | `${plugin.swagger.export.path:/chua/swagger/export}` | Swagger 文档在线导出端点。 通过 REST 实时获取 OpenAPI 文档 HTML / Markdown, 便于集成到 CI 或下载到本地。 端点 GET /chua/swagger/ex… |

**配置**（`plugin.swagger.*`，`Knife4jProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否开启 |
| `knife4j` | `List<Knife4j>` | `—` | 刀4j |
| `default-header` | `Map<String, String>` | `—` | 默认请求头 |
| `username` | `String` | `""` | 账号 |
| `password` | `String` | `""` | 密码 |
| `log` | `boolean` | `—` | 是否开启Swagger注解记录用户操作日志 |
| `description` | `String` | `"无"` | 描述 |
| `version` | `String` | `"1.0"` | 版本 |

<details><summary>Knife4jProperties 嵌套字段</summary>

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `knife4j[].group` | `String` | `—` | 分组 |
| `knife4j[].group-name` | `String` | `—` | 分组名 |
| `knife4j[].paths-to-match` | `String[]` | `—` | 路径 openapi |
| `knife4j[].base-package` | `String[]` | `—` | 基础包 |
| `knife4j[].title` | `String` | `""` | 标题 |
| `knife4j[].description` | `String` | `""` | 描述 |
| `knife4j[].version` | `String` | `"v1.0.0"` | 版本 |

</details>

**配置**（`plugin.swagger.export.*`，`SwaggerExportProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 是否启用导出。 |
| `output` | `String` | `"./swagger-export.html"` | 输出文件路径（相对当前工作目录）。 |
| `title` | `String` | `"Open API 接口文档"` | 顶部标题。 |
| `version` | `String` | `"1.0.0"` | 版本号徽标。 |
| `download-after-start` | `boolean` | `true` | 启动后是否立即导出。 |
| `include-request-sample` | `boolean` | `true` | 是否生成请求/响应示例代码块。 |

**通用场景**

内网文档用 Knife4j 页面；对外交付用离线导出，避免生产暴露调试页。


### spring-support-swagger2-starter

**定位**：Swagger 2.x 注解兼容层。

**装配**：`AutoConfiguration.imports` 只有注释、不注册 Bean；只把 2.x 注解映射到 3.x/springdoc。

**公开 Bean**：本模块不注册 `@Bean`（只提供注释兼容）。

**配置**：本模块没有 `@ConfigurationProperties`。

**通用场景**

老项目迁移期加它兼容注解，迁完就移除。


### spring-support-tunnel-starter

**定位**：内网穿透统一入口，折叠 ngrok/cloudflared/tailscale 差异。

**装配**：`TunnelAutoConfiguration`；`plugin.tunnel.enable` 默认 true；各 Provider 由 SPI 发现。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `tunnelProviderRegistry` | `TunnelProviderRegistry` | 收集容器内全部 TunnelProvider，按白名单裁剪后建注册表。 用 ObjectProvider 是为了允许「一个实现都没引入」的场景： 此时注册表为空，接口会返回空提供方列表而不是启动失败。 |
| `tunnelService` | `TunnelService` | 统一编排服务。 |
| `tunnelServiceInfoContributor` | `ServiceInfoContributor` | 服务信息登记（控制台「模块能力」可见）。 |
| `tunnelApiController` | `TunnelController` | @RestController TunnelController 同名——实际是 /ms/tunnel/** 与 /ms/server/host/{hostId}/tunnel 两个独立端点集，需要兼容共存）， 所以这里的统一接口换一个名称。条件不再存在，本模块就是统一端点的提供者。 |

**内部依赖**：`common`

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `TunnelController` | `/ms/tunnel` | 内网穿透统一接口（/ms/tunnel）——所有提供方共用这一套路由。 改造前是「一个提供方一个入口」：/ms/tunnel/ngrok、 /ms/tunnel/cloudflared，两个控制器 … |

**配置**（`plugin.tunnel.*`，`TunnelProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 是否启用统一入口（关闭后既不注册 REST 接口，也不恢复自动启动的映射）。 |
| `store-dir` | `String` | `—` | 映射持久化根目录。 缺省为 ~/.chua/tunnel，与改造前一致：每个提供方一个子目录 （ngrok/、cloudflared/），已有映射文件无需搬迁。 |
| `providers` | `List<String>` | `new ArrayList<>()` | 对外暴露的提供方白名单；为空表示「容器里有几个就暴露几个」。 用于多实现共存时按环境裁剪：例如生产只允许 cloudflared， 即使 classpath 里同时有 ngrok 实现也不会出现在接口与页面里。 |
| `restore-on-startup` | `boolean` | `true` | 是否在应用启动时恢复 autoStart=true 的映射。 |
| `sse-interval` | `Duration` | `Duration.ofMillis(1500L)` |  |

**通用场景**

只需要一种隧道就直接引对应模块；多提供方切换用 tunnel 的门面与白名单。


### spring-support-tunnel-ngrok-starter

**定位**：ngrok 隧道 Provider（官方 Java Agent SDK）。

**装配**：`NgrokAutoConfiguration`；`plugin.ngrok.enable` 默认 true；必须配 `plugin.ngrok.authtoken`。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `ngrokServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |
| `ngrokTunnelProvider` | `NgrokTunnelProvider` | 内网穿透提供方实现（SPI）。 注册为 Bean 后由 spring-support-tunnel-starter 的 TunnelProviderRegistry 自动收集，统一入口 /ms/tunnel 即刻可用——本模块不再自带 Controller 与编排 Service。 |

**内部依赖**：`common`、`tunnel`

**配置**（`plugin.ngrok.*`，`NgrokProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 总开关。【选填】默认 true；设为 false 完全禁用。 |
| `authtoken` | `String` | `—` | ngrok authtoken。【必填】为空时自动读取环境变量 NGROK_AUTHTOKEN； 两者都为空且未禁用时启动期报错。 |
| `metadata` | `String` | `—` | 会话元数据。【选填】默认无；ngrok 控制台可见。 |
| `server-addr` | `String` | `—` | 私有部署 ngrok 服务端地址。【选填】默认无；公有云可不填。 形如 tunnel.example.com:443。 |
| `ca-cert` | `String` | `—` | 私有部署 CA 证书路径。【选填】默认无；类路径 资源路径， 例如 classpath:ngrok-ca.crt。 |
| `heartbeat-interval` | `Duration` | `—` | 心跳间隔。【选填】默认 ngrok 默认值。 |
| `heartbeat-tolerance` | `Duration` | `—` | 心跳容忍。【选填】默认 ngrok 默认值。 |
| `type` | `String` | `"http"` | 隧道类型。【选填】默认 http；可选 http / tcp / tls。 |
| `mode` | `String` | `"forward"` | 转发模式。【选填】默认 forward。 forward — 自动把本机 server.port 的流量通过 ngrok 暴露 listen — 仅暴露公网 URL，连接建立后由调用方处理 |
| `domain` | `String` | `—` | 自定义域名。【选填】默认无；http/tls 模式生效，需 ngrok 面板预留。 |
| `subdomain` | `String` | `—` | 子域名。【选填】默认无；http/tls 模式生效，部分套餐已下线该能力。 |
| `remote-address` | `String` | `—` | TCP 远程地址。【tcp 模式必填】默认无；形如 1.tcp.ngrok.io:20000。 |
| `scheme` | `String` | `"HTTPS"` | 协议方案（http 模式）。【选填】默认 HTTPS；可选 HTTP / HTTPS。 |
| `compression` | `boolean` | `false` | 启用 HTTP 响应压缩。【选填】默认 false。 |
| `basic-auth` | `String` | `—` | HTTP 基础认证 凭据。【选填】默认无；格式 user:password； 与 #basicAuthUsername/#basicAuthPassword 二选一， 同时配置时优先使用本字段。 |
| `basic-auth-username` | `String` | `—` | 基础认证 用户名。【选填】默认无；与 #basicAuthPassword 配合使用。 |
| `basic-auth-password` | `String` | `—` | 基础认证 密码。【选填】默认无；与 #basicAuthUsername 配合使用。 |
| `fail-fast` | `boolean` | `false` | 启动失败时是否中断应用。【选填】默认 false。 false — 打印 ERROR 日志后继续启动 Spring（不阻塞业务） true — 抛出异常中断启动（fail-fast 模式） 注意：仅 ngrok 会话 连接失败 / 启动失败时生效； authtoken 为空导致的硬错误无论 失败fast 设置如何都会中断启动（必填项）。 |
| `extra-local-ports` | `String` | `—` | 额外本地端口列表。【选填】默认无；逗号分隔，例如 8081,8082。 为空时只转发 server.port；非空时会同时为这些端口建立 http 转发隧道。 仅在 #mode = forward 且 #type = http 时生效。 |
| `auto-start` | `boolean` | `true` | 启动时是否按本配置文件自动建立默认隧道。【选填】默认 true（保持原有行为）。 设为 false 时模块（含管理接口与持久化映射）仍然启用，只是不在启动期 用 server.port 自动建隧道 —— 适合「从页面按需映射端口」的部署方式。 |
| `auto-start-stored` | `boolean` | `true` | 启动时是否恢复上次标记为「自动启动」的持久化映射。【选填】默认 true。 |
| `store-dir` | `String` | `—` | 映射持久化目录。【选填】默认 ~/.chua/tunnel/ngrok。 一条映射一个 JSON 文件，不使用数据库；目录可指向挂载盘做备份。 |

**通用场景**

本机演示最快；生产注意免费域名与连接数限制。


### spring-support-tunnel-cloudflared-starter

**定位**：Cloudflare Tunnel Provider（官方 cloudflared 进程）。

**装配**：`CloudflaredAutoConfiguration`；`plugin.cloudflared.enable` 默认 true；类路径需 cloudflared 客户端。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `cloudflaredServiceInfoContributor` | `ServiceInfoContributor` | cloudflared服务信息contributor |
| `cloudflaredTunnelProvider` | `CloudflaredTunnelProvider` | 内网穿透提供方实现（SPI）。 注册为 Bean 后由 spring-support-tunnel-starter 的 TunnelProviderRegistry 自动收集，统一入口 /ms/tunnel 即刻可用——本模块不再自带 Controller 与编排 Service。 |

**内部依赖**：`common`、`tunnel`

**配置**（`plugin.cloudflared.*`，`CloudflaredProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 总开关。【选填】默认 true；设为 false 完全禁用。 |
| `binary` | `String` | `—` | cloudflared 可执行文件路径。【选填】默认自动定位； 为空时按 CLOUDFLARED_BIN 环境变量 → PATH → 常见安装目录 顺序查找。 |
| `token` | `String` | `—` | Zero Trust 远程管理隧道 Token。【选填】默认无； 为空时使用免账号、免登录的 Quick Tunnel（随机 *.trycloudflare.com 域名）。 |
| `hostname` | `String` | `—` | 固定自定义域名。【选填】默认无；需该域名已添加到 Cloudflare 账户并配置 CNAME 路由。 |
| `local-url` | `String` | `—` | 本地待暴露的服务地址。【选填】默认 http://127.0.0.1:server.port。 |
| `no-auto-update` | `boolean` | `true` | 是否禁用 cloudflared 运行期自动更新。【选填】默认 true， 避免运行期自更新导致隧道中断。 |
| `log-level` | `String` | `"info"` | cloudflared 日志级别。【选填】默认 info； 可选 debug / info / warn / error / fatal。 |
| `start-timeout` | `Duration` | `Duration.ofSeconds(40)` |  |
| `fail-fast` | `boolean` | `false` | 启动失败时是否中断应用。【选填】默认 false。 false 只打 ERROR 日志、Spring 继续启动；true 抛出异常中断启动。 |
| `auto-start` | `boolean` | `true` | 启动时是否按本配置文件自动建立默认隧道。【选填】默认 true（保持原有行为）。 设为 false 时模块（含管理接口与持久化映射）仍然启用，只是不在启动期 自动为 server.port 建隧道 —— 适合「从页面按需映射端口」的部署方式。 |
| `auto-start-stored` | `boolean` | `true` | 启动时是否恢复上次标记为「自动启动」的持久化映射。【选填】默认 true。 |
| `store-dir` | `String` | `—` | 映射持久化目录。【选填】默认 ~/.chua/tunnel/cloudflared。 一条映射一个 JSON 文件，不使用数据库；目录可指向挂载盘做备份。 |

**通用场景**

免账号 Quick Tunnel 最省事；固定域名走命名隧道 + Zero Trust。


### spring-support-tunnel-tailscale-starter

**定位**：Tailscale Funnel Provider（tailscale CLI 子进程）。

**装配**：`TailscaleAutoConfiguration`；`plugin.tailscale.enable` 默认 true。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `tailscaleServiceInfoContributor` | `ServiceInfoContributor` | Tailscale 服务信息 contributor。 |
| `tailscaleTunnelProvider` | `TailscaleTunnelProvider` | 内网穿透提供方实现（SPI）。 注册为 Bean 后由 spring-support-tunnel-starter 的 TunnelProviderRegistry 自动收集，统一入口 /ms/tunnel 即刻可用——本模块不再自带 Controller 与编排 Service。 |

**内部依赖**：`common`、`tunnel`

**配置**（`plugin.tailscale.*`，`TailscaleProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 总开关。【选填】默认 true；设为 false 完全禁用。 |
| `binary` | `String` | `—` | tailscale 可执行文件路径。【选填】默认自动定位； 为空时按 TAILSCALE_BIN 环境变量 → PATH → 常见安装目录 顺序查找。 |
| `auth-key` | `String` | `—` | 设备认证 Key。【选填】默认无； 可通过环境变量 TS_AUTHKEY 设置，自动注入子进程环境。 依赖设备先加入 tailnet（tailscale up）时可不填。 |
| `start-timeout` | `Duration` | `Duration.ofSeconds(60)` | 启动等待公网 URL 的超时。【选填】默认 60 秒。 |
| `fail-fast` | `boolean` | `false` | 启动失败时是否中断应用。【选填】默认 false。 false 只打 ERROR 日志、Spring 继续启动；true 抛出异常中断启动。 |
| `auto-start` | `boolean` | `true` | 启动时是否按本配置文件自动建立默认隧道。【选填】默认 true（保持原有行为）。 设为 false 时模块（含管理接口与持久化映射）仍然启用，只是不在启动期 自动为 server.port 建隧道 —— 适合「从页面按需映射端口」的部署方式。 |
| `auto-start-stored` | `boolean` | `true` | 启动时是否恢复上次标记为「自动启动」的持久化映射。【选填】默认 true。 |
| `store-dir` | `String` | `—` | 映射持久化目录。【选填】默认 ~/.chua/tunnel/tailscale。 一条映射一个 JSON 文件，不使用数据库；目录可指向挂载盘做备份。 |

**通用场景**

团队已在用 Tailnet 时优先它；不支持 Funnel 的账号会被服务端拒绝。


### spring-support-cdp-starter

**定位**：Chrome DevTools Protocol 调试代理，启动本机 CDP 服务。

**装配**：`CdpAutoConfiguration` 无条件扫描 `com.chua.starter.cdp`；`spring.cdp.enabled`（默认 true）由 `CdpEngineService` 读取，决定浏览器是否启动，不是 `plugin.*` 前缀。

**公开 Bean**：本模块没有显式 `@Bean`；CDP 控制器/服务由 `@ComponentScan` 注册，见模块目录。

**配置**：本模块没有 `@ConfigurationProperties`。

**通用场景**

浏览器自动化/调试用 CDP；生产禁用，避免暴露调试端口。注：当前工作区该模块源码已被删除（未提交），本节按 git 跟踪版本描述。


### spring-support-httpman-starter

**定位**：接口调试控制台后端：集合、请求、历史与代理执行。

**装配**：`HttpmanAutoConfiguration` 组件扫描 `com.chua.starter.httpman` + Mapper 扫描；属性前缀 `httpman`。注意 `httpman.enabled` 目前没有在服务/控制器里读取，关掉它不会禁用接口——要禁用只能不引依赖或排除自动配置。

**公开 Bean**：本模块没有显式 `@Bean`；控制器/服务由 `@ComponentScan` 注册，见模块目录。

**内部依赖**：`common`、`mybatis`、`cache`

**HTTP 入口**（`@RestController`/`@Controller`，共 1 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `HttpmanController` | `/ms/httpman` | 接口调试控制台 - REST 入口 与前端约定前缀 /ms/httpman，通过 HttpmanService 管理集合/请求/历史/变量， 以及由 execution 转入代理执行。 |

**组件扫描 Service**：`HttpmanServiceImpl`（共 1 个）。`@Service` 注册，可直接注入。

**配置**（`httpman.*`，`HttpmanProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | 总开关，false 时不暴露任何 HttpMan 能力 |
| `page` | `String` | `"/httpman"` | 前端页面路径（不是非得用，CMS 留档） |
| `path-prefix` | `String` | `"/ms/httpman"` | 后端 REST 路径前缀（前端约定 /ms/httpman/**） |
| `default-collection` | `String` | `"默认集合"` | 集合根文件夹名称（首次打开时自动创建） |
| `history-retention-days` | `int` | `30` |  |
| `connect-timeout` | `Duration` | `Duration.ofSeconds(15)` |  |
| `read-timeout` | `Duration` | `Duration.ofSeconds(60)` |  |
| `ssl-insecure` | `boolean` | `false` | 代理执行：是否需要跳过证书校验（地对错误证书/自签的场景开放） |
| `proxy-from-env` | `boolean` | `false` | 代理执行：是否为全局代理（从 http_proxy / https_proxy 环境变量读） |
| `proxy` | `String` | `—` | 代理执行：显式代理地址（覆盖环境变量），如 http://127.0.0.1:7890 |
| `proxy-bypass` | `List<String>` | `new ArrayList<>()` | 代理执行：排除列表，命中这些前缀的请求不走代理（如 127.0.0.1 / localhost） |
| `sensitive-header-mask` | `boolean` | `true` | 是否在运行记录中剥离敏感头（Authorization / Cookie），防止凭据落库 |

**通用场景**

团队内替代 Postman 的轻量方案；历史记录会落库，注意清理策略。


### spring-support-tencent-starter

**定位**：腾讯云/微信生态：小程序、开放平台、微信支付、短信、存储。

**装配**：`TencentConfiguration`；各子能力 `plugin.tencent.<能力>.enable=true` 才创建客户端。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `tencentWechatPayClientFactory` | `TencentWechatPayClientFactory` | 按商户配置创建微信支付客户端。 |
| `tencentServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |

**内部依赖**：`oauth-client`

**SPI 扩展点**：`UserInfoService`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`plugin.tencent.mini-app.*`，`TencentMiniAppProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `enabled` | `boolean` | `false` | 是否启用（兼容字段） |
| `app-id` | `String` | `—` | 小程序appid |
| `app-secret` | `String` | `—` | 小程序appsecret |

**配置**（`plugin.tencent.open-platform.*`，`TencentWechatOpenPlatformProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | 是否启用 |
| `component-app-id` | `String` | `—` | 第三方平台appid |
| `component-app-secret` | `String` | `—` | 第三方平台appsecret |
| `default-mini-app-id` | `String` | `—` | 默认小程序appid（可选，调用转换接口时可省略miniappid参数） |
| `storage-path` | `String` | `"wechat-openid-mapping.json"` | 数据存储路径（JSON文件），用于持久化openid映射关系 |

**配置**（`plugin.tencent.wechat-pay.*`，`TencentWechatPayProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | Enable |
| `enabled` | `boolean` | `false` | 是否启用 |
| `app-id` | `String` | `—` | APPID |
| `merchant-id` | `String` | `—` | merchantid |
| `private-key` | `String` | `—` | 私有密钥 |
| `merchant-serial-number` | `String` | `—` | Merchantserial数字 |
| `api-v3-key` | `String` | `—` | apiv3Key |
| `notify-url` | `String` | `—` | 通知url |
| `sandbox` | `boolean` | `false` | 沙箱模式 true = 使用沙箱环境 (https://api.mch.weixin.qq.com/sandboxnew/) |
| `sandbox-sign-key` | `String` | `—` | 沙箱环境 apiv3 密钥 沙箱环境需要使用 sandbox_signkey 替代生产环境的 apiV3Key。 可通过 https://api.mch.weixin.qq.com/sandboxnew/pay/getsignkey 获取。 |

**通用场景**

按需引子能力；密钥统一放配置中心，不要写进代码。


### spring-support-aliyun-starter

**定位**：阿里云/支付宝生态，当前主要是支付宝客户端。

**装配**：`AliyunConfiguration`；`plugin.aliyun.alipay.enabled=true` 时创建 `AlipayClient` 与工厂。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `aliyunAlipayClientFactory` | `AliyunAlipayClientFactory` | 按配置创建/缓存支付宝客户端。 |
| `alipayClient` | `AlipayClient` | 默认支付宝客户端。 |
| `aliyunServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |

**内部依赖**：`oauth-client`

**SPI 扩展点**：`UserInfoService`（`META-INF/extensions/` 下的 `ServiceLoader` 文件）。

**配置**（`plugin.aliyun.alipay.*`，`AliyunAlipayProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `false` | Enable |
| `enabled` | `boolean` | `false` | 是否启用 |
| `sandbox` | `boolean` | `false` | Sandbox |
| `server-url` | `String` | `—` | 服务器URL |
| `app-id` | `String` | `—` | APPID |
| `private-key` | `String` | `—` | 私有密钥 |
| `alipay-public-key` | `String` | `—` | Alipay公开密钥 |
| `notify-url` | `String` | `—` | 通知url |
| `return-url` | `String` | `—` | 返回值URL |
| `format` | `String` | `"json"` | 格式 |
| `charset` | `String` | `"UTF-8"` | 字符集 |
| `sign-type` | `String` | `"RSA2"` | 签名类型 |
| `connect-timeout` | `Integer` | `10000` |  |
| `read-timeout` | `Integer` | `30000` |  |
| `max-idle-connections` | `Integer` | `5` | 最大值idleconnections |
| `keep-alive-duration` | `Long` | `300000L` |  |

**通用场景**

支付回调与证书模式在 `plugin.aliyun.alipay.*`；不要每个请求新建客户端。


### spring-support-payment-starter

**定位**：支付主线：订单/退款/回调/对账，渠道实现走 SPI。

**装配**：`PaymentAutoConfiguration`，组件扫描 + Mapper 扫描；调度器/事件日志/运维接口各有 `plugin.payment.*` 开关。

**公开 Bean**

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `paymentPaginationInnerInterceptor` | `PaginationInnerInterceptor` | 支付模块默认分页拦截器。 完整的 MyBatis Plus 拦截器链由 #mybatisPlusInterceptor(ObjectProvider) 按 Spring Bean 顺序组装， 这样各模块注入的动态表名拦截器也能一并生效。 |
| `mybatisPlusInterceptor` | `MybatisPlusInterceptor` | 支付模块 MyBatis Plus 拦截器链。 |
| `paymentMybatisPlusInterceptor` | `MybatisPlusInterceptor` | 支付库的分页拦截器。 |
| `paymentThreadPoolTaskScheduler` | `ThreadPoolTaskScheduler` | 支付回调/对账任务线程池。 |
| `paymentServiceInfoContributor` | `ServiceInfoContributor` | 把本模块的端点与开关注册到 `/service-info` 汇总。 |

**内部依赖**：`common`、`swagger`、`mybatis`、`oauth-client`、`datasource`、`redis`、`tencent`、`aliyun`、`job`

**HTTP 入口**（`@RestController`/`@Controller`，共 18 个）

| 控制器 | 基础路径 | 说明 |
| --- | --- | --- |
| `CurrentAccountOrderController` | `/account` | 当前用户订单自助 API 所有接口均校验当前用户身份，userId 从认证上下文获取，不允许客户端传入。 |
| `CurrentAccountWalletController` | `/account/wallet` | 当前用户钱包自助 API 所有接口均校验当前用户身份，userId 从认证上下文获取，不允许客户端传入。 |
| `MerchantChannelController` | `/channel` | 商户渠道管理控制器 |
| `MerchantController` | `/merchant` | 商户管理控制器 |
| `MerchantSettingController` | `/merchant` | 商户支付设置控制器 |
| `MpWalletController` | `/mp/wallet` | 小程序钱包 API 小程序使用 OAuth2 登录流程获取 Bearer Token，认证场景为 Web 端。 |
| `OrderConfigController` | `/order-config` | 订单配置控制器 |
| `OrderController` | `/order` | 订单管理控制器 |
| `PaymentGlobalConfigController` | `/payment-global-config` | 支付全局配置控制器 |
| `PaymentNotifyController` | `/notify` | 第三方支付异步通知控制器，统一处理微信支付和支付宝的支付、回调通知。 |
| … | … | 其余 8 个控制器见模块源码 |

**组件扫描 Service**：`AccountPaymentQueryServiceImpl`、`MerchantChannelServiceImpl`、`MerchantPaymentConfigServiceImpl`、`MerchantServiceImpl`、`OrderPartitionConfigServiceImpl`、`OrderStateLogServiceImpl`、`OrderStateMachineServiceImpl`、`PaymentDashboardServiceImpl`、`PaymentGlobalConfigServiceImpl`、`PaymentInternalSchedulerTaskOpsService`、`PaymentNotifyLogServiceImpl`、`PaymentNotifyProcessServiceImpl`、`PaymentNotifyServiceImpl`、`PaymentOrderServiceImpl`、`PaymentPlatformSchedulerTaskOpsService` 等 25 个。`@Service` 注册，可直接注入。

**配置**（`plugin.payment.callback.*`，`PaymentCallbackProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `base-url` | `String` | `—` | 对外可访问的支付服务根地址，例如：https://pay.example.com |

**配置**（`plugin.payment.provider.*`，`PaymentProviderProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `default-spi` | `String` | `AbstractMerchantPaymentChannel.DEFAULT_…` | 默认 SPI，未显式配置各渠道时的兜底实现标识。 |
| `alipay-spi` | `String` | `AbstractMerchantPaymentChannel.DEFAULT_…` | 支付宝渠道的 SPI 实现标识。 |
| `wechat-spi` | `String` | `AbstractMerchantPaymentChannel.DEFAULT_…` | 微信渠道的 SPI 实现标识。 |
| `epay-spi` | `String` | `"epay"` | 易支付渠道的 SPI 实现标识。 |

**通用场景**

接新渠道实现支付 SPI；对账与回调重放用模块自带任务，不要另起一套。

## 五、配置前缀索引

源码中共有 90 个 `@ConfigurationProperties`（不含 5 个 javadoc 误报/内部配置类）。字段明细在对应模块章节；这里用于反查前缀属于哪个类、哪个模块。

| 前缀 | 属性类 | 模块 | 字段数 |
| --- | --- | --- | --- |
| `control.webrtc` | `ControlProperties` | [`spring-support-control-starter`](spring-support-control-starter/README.md) | 8 |
| `httpman` | `HttpmanProperties` | [`spring-support-httpman-starter`](spring-support-httpman-starter/README.md) | 12 |
| `plugin.actuator` | `ActuatorProperties` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 6 |
| `plugin.agent` | `AgentAttachProperties` | [`spring-support-agent-starter`](spring-support-agent-starter/README.md) | 4 |
| `plugin.ai.default-model` | `DefaultModelProperties` | [`spring-support-ai-starter`](spring-support-ai-starter/README.md) | 33 |
| `plugin.ai.generate` | `AiProperties` | [`spring-support-ai-starter`](spring-support-ai-starter/README.md) | 13 |
| `plugin.ai.rag` | `RagProperties` | [`spring-support-ai-starter`](spring-support-ai-starter/README.md) | 9 |
| `plugin.ai.training` | `TrainProperties` | [`spring-support-ai-training-starter`](spring-support-ai-training-starter/README.md) | 6 |
| `plugin.aliyun.alipay` | `AliyunAlipayProperties` | [`spring-support-aliyun-starter`](spring-support-aliyun-starter/README.md) | 16 |
| `plugin.api` | `ApiProperties` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 44 |
| `plugin.api.cors` | `ApiCorsProperties` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 4 |
| `plugin.api.security.header` | `ApiSecurityHeaderFilter` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 8 |
| `plugin.apm` | `ApmProperties` | [`spring-support-apm-starter`](spring-support-apm-starter/README.md) | 10 |
| `plugin.apm.skywalking` | `SkywalkingApmProperties` | [`spring-support-apm-skywalking-starter`](spring-support-apm-skywalking-starter/README.md) | 4 |
| `plugin.bundle` | `BundleProperties` | [`spring-support-bundle-starter`](spring-support-bundle-starter/README.md) | 5 |
| `plugin.cache` | `CacheProperties` | [`spring-support-cache-starter`](spring-support-cache-starter/README.md) | 29 |
| `plugin.captcha` | `CaptchaProperties` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 9 |
| `plugin.cloudflared` | `CloudflaredProperties` | [`spring-support-tunnel-cloudflared-starter`](spring-support-tunnel-cloudflared-starter/README.md) | 12 |
| `plugin.collect` | `CollectProperties` | [`spring-support-collect-starter`](spring-support-collect-starter/README.md) | 9 |
| `plugin.config-center` | `ConfigCenterProperties` | [`spring-support-configcenter-starter`](spring-support-configcenter-starter/README.md) | 14 |
| `plugin.console` | `ConsoleProperties` | [`spring-support-console-starter`](spring-support-console-starter/README.md) | 4 |
| `plugin.datasource.calcite` | `CalciteDataSourceProperties` | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 4 |
| `plugin.datasource.document` | `DocumentProperties` | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 10 |
| `plugin.datasource.hub` | `DatasourceHubProperties` | [`spring-support-datasource-hub-starter`](spring-support-datasource-hub-starter/README.md) | 12 |
| `plugin.datasource.materialized` | `MaterializedRouteProperties` | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 5 |
| `plugin.datasource.monitor` | `SqlMonitorProperties` | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 8 |
| `plugin.datasource.script` | `DataSourceScriptProperties` | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 28 |
| `plugin.datasource.sharding` | `ShardingDataSourceProperties` | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 8 |
| `plugin.datasync` | `DatasyncProperties` | [`spring-support-datasync-starter`](spring-support-datasync-starter/README.md) | 8 |
| `plugin.dead-letter` | `DeadLetterProperties` | [`spring-support-queue-starter`](spring-support-queue-starter/README.md) | 8 |
| `plugin.discovery` | `DiscoveryListProperties` | [`spring-support-discovery-starter`](spring-support-discovery-starter/README.md) | 2 |
| `plugin.eduai` | `EduAiProperties` | [`spring-support-eduai-starter`](spring-support-eduai-starter/README.md) | 35 |
| `plugin.filesystem` | `FileStorageProperties` | [`spring-support-filesystem-starter`](spring-support-filesystem-starter/README.md) | 76 |
| `plugin.filesystem.storage-server` | `FileStorageServerProperties` | [`spring-support-filesystem-starter`](spring-support-filesystem-starter/README.md) | 29 |
| `plugin.filesystem.upload` | `FileStorageUploadProperties` | [`spring-support-filesystem-starter`](spring-support-filesystem-starter/README.md) | 9 |
| `plugin.h2` | `H2DataSourceProperties` | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 7 |
| `plugin.hotsport` | `HotsportProperties` | [`spring-support-hotsport-starter`](spring-support-hotsport-starter/README.md) | 18 |
| `plugin.ip` | `IpProperties` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 3 |
| `plugin.jackson` | `JacksonProperties` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 2 |
| `plugin.job` | `JobProperties` | [`spring-support-job-starter`](spring-support-job-starter/README.md) | 38 |
| `plugin.log` | `LogProperties` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 3 |
| `plugin.lowcode` | `LowcodeProperties` | [`spring-support-lowcode-starter`](spring-support-lowcode-starter/README.md) | 1 |
| `plugin.message` | `MessageConverterProperties` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 4 |
| `plugin.message.track` | `MessageTrackProperties` | [`spring-support-message-starter`](spring-support-message-starter/README.md) | 2 |
| `plugin.monitor.agent` | `AgentDeployProperties` | [`spring-support-monitor-starter`](spring-support-monitor-starter/README.md) | 17 |
| `plugin.monitor.influx` | `MonitorInfluxProperties` | [`spring-support-monitor-starter`](spring-support-monitor-starter/README.md) | 3 |
| `plugin.monitor.ssh` | `MonitorSshProperties` | [`spring-support-monitor-starter`](spring-support-monitor-starter/README.md) | 3 |
| `plugin.monitor.timeseries` | `MonitorTimeseriesProperties` | [`spring-support-monitor-starter`](spring-support-monitor-starter/README.md) | 3 |
| `plugin.monitor.tshark` | `TsharkInstallProperties` | [`spring-support-monitor-starter`](spring-support-monitor-starter/README.md) | 12 |
| `plugin.monitor.tshark.retention` | `TsharkRetentionProperties` | [`spring-support-monitor-starter`](spring-support-monitor-starter/README.md) | 3 |
| `plugin.multi-datasource` | `MultiDataSourceProperties` | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 2 |
| `plugin.multi-datasource` | `MultiDataSourceSettingProperties` | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 4 |
| `plugin.multi-datasource.hikari` | `MultiHikariDataSourceProperties` | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 2 |
| `plugin.mybatis` | `MybatisPlusProperties` | [`spring-support-mybatis-starter`](spring-support-mybatis-starter/README.md) | 14 |
| `plugin.mybatis.data.scope` | `MybatisPlusDataScopeProperties` | [`spring-support-mybatis-starter`](spring-support-mybatis-starter/README.md) | 5 |
| `plugin.mybatis.slow-sql` | `MybatisSlowSqlProperties` | [`spring-support-mybatis-starter`](spring-support-mybatis-starter/README.md) | 6 |
| `plugin.ngrok` | `NgrokProperties` | [`spring-support-tunnel-ngrok-starter`](spring-support-tunnel-ngrok-starter/README.md) | 22 |
| `plugin.notice` | `NoticeProperties` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 7 |
| `plugin.oauth` | `AuthClientProperties` | [`spring-support-oauth-client-starter`](spring-support-oauth-client-starter/README.md) | 77 |
| `plugin.optional` | `OptionalProperties` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 1 |
| `plugin.osgi` | `OsgiProperties` | [`spring-support-osgi-starter`](spring-support-osgi-starter/README.md) | 36 |
| `plugin.payment.callback` | `PaymentCallbackProperties` | [`spring-support-payment-starter`](spring-support-payment-starter/README.md) | 1 |
| `plugin.payment.provider` | `PaymentProviderProperties` | [`spring-support-payment-starter`](spring-support-payment-starter/README.md) | 4 |
| `plugin.queue` | `QueueProperties` | [`spring-support-queue-starter`](spring-support-queue-starter/README.md) | 42 |
| `plugin.redis.server` | `RedisServerProperties` | [`spring-support-redis-starter`](spring-support-redis-starter/README.md) | 7 |
| `plugin.report.client` | `ReportProperties` | [`spring-support-report-client-starter`](spring-support-report-client-starter/README.md) | 16 |
| `plugin.rpc` | `RpcProperties` | [`spring-support-rpc-starter`](spring-support-rpc-starter/README.md) | 11 |
| `plugin.security.fingerprint` | `RequestFingerprintAutoConfiguration` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 15 |
| `plugin.server.support` | `ServerProperties` | [`spring-support-server-starter`](spring-support-server-starter/README.md) | 13 |
| `plugin.server.support.serial` | `SerialProperties` | [`spring-support-server-starter`](spring-support-server-starter/README.md) | 10 |
| `plugin.service-info` | `ServiceInfoProperties` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 7 |
| `plugin.socket` | `SocketProperties` | [`spring-support-socket-starter`](spring-support-socket-starter/README.md) | 32 |
| `plugin.sqlite` | `SqliteDataSourceProperties` | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 4 |
| `plugin.ssh.server` | `SshServerProperties` | [`spring-support-ssh-starter`](spring-support-ssh-starter/README.md) | 25 |
| `plugin.swagger` | `Knife4jProperties` | [`spring-support-swagger-starter`](spring-support-swagger-starter/README.md) | 15 |
| `plugin.swagger.export` | `SwaggerExportProperties` | [`spring-support-swagger-starter`](spring-support-swagger-starter/README.md) | 6 |
| `plugin.sync` | `SyncProperties` | [`spring-support-sync-starter`](spring-support-sync-starter/README.md) | 49 |
| `plugin.sync.datasync` | `SyncDatasyncProperties` | [`spring-support-sync-starter`](spring-support-sync-starter/README.md) | 1 |
| `plugin.tailscale` | `TailscaleProperties` | [`spring-support-tunnel-tailscale-starter`](spring-support-tunnel-tailscale-starter/README.md) | 8 |
| `plugin.taskdistribution` | `TaskDistributionProperties` | [`spring-support-taskdistribution-starter`](spring-support-taskdistribution-starter/README.md) | 15 |
| `plugin.tencent.mini-app` | `TencentMiniAppProperties` | [`spring-support-tencent-starter`](spring-support-tencent-starter/README.md) | 4 |
| `plugin.tencent.open-platform` | `TencentWechatOpenPlatformProperties` | [`spring-support-tencent-starter`](spring-support-tencent-starter/README.md) | 5 |
| `plugin.tencent.wechat-pay` | `TencentWechatPayProperties` | [`spring-support-tencent-starter`](spring-support-tencent-starter/README.md) | 10 |
| `plugin.thread` | `AsyncThreadPoolProperties` | [`spring-support-common-starter`](spring-support-common-starter/README.md) | 8 |
| `plugin.transaction` | `TransactionProperties` | [`spring-support-datasource-starter`](spring-support-datasource-starter/README.md) | 6 |
| `plugin.tunnel` | `TunnelProperties` | [`spring-support-tunnel-starter`](spring-support-tunnel-starter/README.md) | 5 |
| `plugin.ueba` | `UebaProperties` | [`spring-support-ueba-starter`](spring-support-ueba-starter/README.md) | 5 |
| `plugin.websocket` | `WebSocketProperties` | [`spring-support-socket-websocket-starter`](spring-support-socket-websocket-starter/README.md) | 8 |
| `room` | `RoomProperties` | [`spring-support-socket-websocket-starter`](spring-support-socket-websocket-starter/README.md) | 10 |
| `spring.cdp` | `CdpProperties` | [`spring-support-monitor-starter`](spring-support-monitor-starter/README.md) | 16 |

## 六、常见问题

**Q1：依赖加了，但容器里没有对应的 Bean。**
按顺序查四件事：开关是否显式打开（2.3 的默认值表）；类路径条件是否满足（`@ConditionalOnClass`）；容器里是否已有同类型 Bean 导致退避（`@ConditionalOnMissingBean`）；该模块是否根本不走 `imports`（taskdistribution、apm-hotspot、software，见 2.2）。加 `--debug` 能看到具体失败条件。

**Q2：两个 starter 都注册 `MybatisPlusInterceptor`，会不会冲突？**
会。`mybatis-starter`、`device-starter`、`payment-starter` 都提供分页拦截器，后两者用 `@ConditionalOnMissingBean` 退避；如果宿主自己再声明一个，仍然可能重复分页。统一只保留一个 `MybatisPlusInterceptor`，其余模块的兜底 Bean 会自动让位。

**Q3：配置写了却不生效。**
先确认前缀拼写（`enable` 还是 `enabled`），再确认字段是 kebab-case；`List`/`Map`/`Duration` 的写法见 2.5。属性类带 `ignoreInvalidFields=true` 时，类型错误会被静默忽略，不会报错，这是最容易误判的一类。

**Q4：启动报 `BeanDefinitionOverrideException`。**
说明你声明了和 starter 同名的 Bean。改法优先级：删掉自己的同名 Bean，改用同类型覆盖（依赖 `@ConditionalOnMissingBean`）；确实要同名，再临时开 `spring.main.allow-bean-definition-overriding=true`。

**Q5：端口冲突。**
常见默认端口：`control-starter` 的 WebRTC 信令 10000、`ssh-starter` 2222、`redis-starter` 嵌入式 6379、`filesystem-starter` standalone 模式的文件服务端口。同机多实例时逐个改成独立端口，或把对应模块关掉。

**Q6：生产环境建议关掉什么。**
`cdp-starter`、`httpman-starter`、`console-starter`、`swagger-starter` 的导出页、`swagger` 的调试页、`agent-starter` 的自注入（观测后台自己观测自己）、`filesystem` 的 `dir-list`。这些默认值不一定是关的（见 2.3），要显式配置。

**Q7：Boot 3 升 Boot 4 要注意什么。**
本仓库父 POM 已是 Boot 4.1.0，包名仍是 `jakarta.*`；`@AutoConfiguration` 与 `imports` 机制不变。升级主要影响三方依赖版本与 `spring.factories` 扩展点，按模块 README 的“与其它 starter 的关系”逐项核对。

## 附录 A：文件在线预览能力矩阵

预览链路分两段：**取字节**（文件服务 `/fs` 接口，`raw=true` 直出对象字节，不做解析）
和**渲染**（前端 `@file-viewer` 按文件名扩展名选渲染器）。只有客户端渲染失败时才退
服务端通道（`?format=pdf` 转换，或 `render=server` 返回的 HTML 预览页）。

### WPS Office 家族（按内容魔数改名）

WPS 存出来的文档没有同名渲染器，预览前先按**容器魔数**改名到真实家族：

| 后缀 | 名称 | 新版容器（ZIP / OOXML） | 老版容器（OLE2） |
|---|---|---|---|
| `.wps` / `.wpt` | WPS 文字 / 文字模板 | → `.docx` | → `.doc` |
| `.et` / `.ett` | WPS 表格 / 表格模板 | → `.xlsx` | → `.xls` |
| `.dps` / `.dpt` | WPS 演示 / 演示模板 | → `.pptx` | → `.ppt` |

改名只发生在交给渲染器的那份 `File` 上，列表、弹层标题、下载名仍是原后缀。
表格家族的 `.et` / `.ett` 还支持拖选单元格查看求和 / 平均值 / 中位数 / 标准差 / 去重统计。

### 能力矩阵（真实样本 + 浏览器实测）

| 格式 | 样本来源 | 结论 |
|---|---|---|
| `.wps`（OOXML 容器） | docx 改名 | ✅ 正文直渲 |
| `.wpt` | docx 改名 | ✅ 正文直渲，标题保留原后缀 |
| `.et` | 真实 xlsx（Apache POI `SampleSS.xlsx`） | ✅ 表格、单元格、选区统计条 |
| `.docx` / `.xlsx` / `.pdf` | 常规文档 | ✅ 历史回归通过 |
| `.arrow` / `.feather` | apache-arrow IPC，120 行 / 3 列 | ✅ 表头、列类型、分页（每页 50 行） |
| `.jsonl` / `.ndjson` | 3 行 JSON Lines | ✅ 文本面板直渲，标题保留原后缀 |
| `.ett` / `.dps` / `.dpt` | 真实 xlsx / pptx | ⚠️ **未完成验收**：被测实例在运行途中被其它会话清掉，非格式不支持 |

### 客户端直渲清单与文本白名单

- 客户端可直渲的扩展名：`pages/filesystem/src/utils/officeRenderer.ts` 的 `CLIENT_RENDERABLE_EXTS`
  （Office 家族、iWork、WordPerfect、Hangul、OFD、PDF、WPS 家族等）。
- 文本 / 代码类白名单：`pages/filesystem/src/utils/previewEntryRenderers.ts` 的 `TEXT_EXTENSIONS`，
  必须与 `@file-viewer/renderer-text` 的 `file-viewer.capability.json.formats` **逐项一致**（当前 47 项）；
  白名单外的后缀（`jsonl`、`ndjson`、`conf` 等）在交给渲染器前替换扩展名为 `.txt`。

> 两个容易误判的现场：① 本机 Office / WPS 另存的文件可能被防泄密软件**透明加密**，
> 预览会明确提示「已被透明加密」，换未加密副本即可；② 服务端兜底通道是否有 PDF 转换器
> 取决于部署环境，没有时只能退 iframe，此时用预览底部的「下载原文件」。

## 附录 B：GraalVM Native Image 支持

本项目（Spring Boot 4.1.0）已为 GraalVM Native Image 提供基础支持。模块级 `META-INF/native-image` 反射与资源元数据已覆盖以下模块：

- `spring-support-common-starter`、`spring-support-ai-starter`、`spring-support-filesystem-starter`
- `spring-support-rpc-starter`、`spring-support-socket-io-starter`、`spring-support-sync-starter`

### 构建原生镜像

在你的 **应用模块**（包含 `@SpringBootApplication` 入口类）中执行：

```bash
# 构建原生可执行文件（Linux/macOS/WSL）
mvn -Pnative native:compile

# Windows Native Image 需 GraalVM for JDK 25，并使用 Visual Studio 构建工具链
# 请确保 `native-image` 命令可用，并设置 GRAALVM_HOME 环境变量
```

### 额外构建参数

如应用使用了 FFM（`java.lang.foreign`）或 JNI 加载动态库，需追加原生访问权限：

```bash
mvn -Pnative native:compile \
  -Dnative.buildtools.build-args="--enable-native-access=ALL-UNNAMED,--enable-url-protocols=http,https"
```

### JVM 运行兼容性

在 GraalVM JDK（如 Corretto 25 / GraalVM for JDK 25）上运行时，本项目可直接使用，无需修改。`graalvm` JDK 是完全兼容的 `openjdk` 实现。
