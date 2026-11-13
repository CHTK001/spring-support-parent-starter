# spring-support-sync-starter

客户端/服务端双向同步通道：按 `plugin.sync.type` 装配 `SyncServer` / `SyncClient`，协议名通过 SPI 选择具体传输实现，主题消息由业务处理器消费。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 服务端通道 | `SyncServer`、`SyncServerInstance`、`AbstractSyncServer`、`SyncSession` | `plugin.sync.server.*`，`type=server\|both` |
| 客户端通道 | `SyncClient`、`SyncClientWrapper` | `plugin.sync.client.*`，`type=client\|both` |
| 传输协议 | `ServiceProvider.getNewExtension(protocol, setting)` 选取 `com.chua.common.support.network.sync.SyncClient/SyncServer` 实现 | `instances[].protocol` / `client.protocol` |
| 已注册协议键 | `http`、`websocket`、`tcp`、`udp`（`utils-support-common-starter`）；`rsocket`、`socketio`、`kcp` 需对应 utils starter | 同上 |
| 主题分发 | `SyncServer#registerHandler`、`SyncClient#registerHandler`、`SyncMessageHandler` | `plugin.sync.topics` 或编程式注册 |
| 多实例监听 | `SyncServer#getInstances`、`ServerConfig#getEffectiveInstances` | `plugin.sync.server.instances[]` |
| 定向推送 | `publishByMetadata`、`publishByAppName`、`publish(host, port, ...)`、`publishToClients(Predicate)` | `SyncServer` 编程接口 |
| 客户端定制 | `SyncClientCustomizer`、`SyncClientInfoCustomizer` | Spring Bean，按 `getOrder()` 排序 |
| 传输加密 | `SyncCryptoUtils`（AES-256-GCM + HKDF-SHA256） | `plugin.sync.crypto-secret` / `crypto-version` |
| 服务发现寻址 | `ServiceDiscovery`（`@Primary` 唯一时接入） | `server.service-name` / `client.discovery-name` |
| 数据交换 | `SyncDataExchangeService`、`SyncDataEnvelope` | `plugin.sync.data-sync.*` |
| 数据同步代理 | `DataSyncAgentLifecycleConfiguration`、`SyncDataSyncConfiguration` | `utils-support-datasync-starter` 在 classpath 时 |
| 服务信息 | `SyncAutoConfiguration#syncServiceInfoContributor` | 服务信息注册表 |

自动配置只有一个入口：`com.chua.starter.sync.support.configuration.SyncAutoConfiguration`。

装配条件（两者同时满足才启动对应端）：

```
plugin.sync.type ∈ {server, both}  &&  plugin.sync.server.enable = true   → SyncServer
plugin.sync.type ∈ {client, both}  &&  plugin.sync.client.enable = true   → SyncClient
```

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-sync-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用域 | 说明 |
|---|---|---|
| `spring-boot-starter` | compile | Spring Boot 基础 |
| `spring-boot-configuration-processor` | optional | 配置元数据生成 |
| `spring-support-common-starter` | compile | 日志、服务信息 |
| `spring-support-job-starter` | provided | 任务能力扩展，使用方自行提供 |
| `spring-support-swagger-starter` | provided | 接口文档，非必需 |
| `utils-support-datasync-starter` | optional（`4.0.0.50`） | 数据同步代理，可选 |
| `io.rsocket:rsocket-core`、`rsocket-transport-netty` | provided | RSocket 传输，需自行引入 |
| `com.corundumstudio.socketio:netty-socketio` | provided | Socket.IO 传输，需自行引入 |

> 注意：`http`、`websocket`、`tcp`、`udp` 四个键由 `utils-support-common-starter` 的 `META-INF/extensions` 注册，无需额外依赖；`rsocket` 需引入 `utils-support-rsocket-starter`，`socketio` 需引入 `utils-support-socketio-starter`，本 pom 里的 `rsocket-core` / `netty-socketio` 只是编译期 `provided` 依赖，不构成协议注册。

## 三、配置项

前缀：`plugin.sync`（`SyncProperties.PRE`）。

### 顶层

| key | 默认值 | 说明 |
|---|---|---|
| `plugin.sync.type` | `client` | `server` 仅服务端、`client` 仅客户端、`both` 同时启用 |
| `plugin.sync.topics` | 空 | 主题与处理器映射（Key = 主题名） |
| `plugin.sync.crypto-secret` | 未设置 | 传输加密密钥，两端必须一致；未配置则明文 |
| `plugin.sync.crypto-version` | `2` | `2` = 每条消息随机盐派生密钥；`1` = 旧版固定密钥（仅滚动升级用） |
| `plugin.sync.server.*` | — | 服务端配置 |
| `plugin.sync.client.*` | — | 客户端配置 |
| `plugin.sync.data-sync.*` | — | 数据交换配置 |

### 服务端 `plugin.sync.server`

| key | 默认值 | 说明 |
|---|---|---|
| `enable` | `false` | 是否启用服务端 |
| `host` | `0.0.0.0` | 默认绑定地址 |
| `port` | `19380` | 默认端口（`instances` 为空时使用） |
| `protocol` | `http` | 默认协议 |
| `service-name` | 未设置 | 非空时注册到服务发现；客户端按同名查找 |
| `scatter-id` | 未设置 | 业务分组标识，为空回落到 `service-name` |
| `heartbeat` | `true` | 心跳开关 |
| `heartbeat-interval` | `30` | 心跳间隔（秒） |
| `connect-timeout` | `10000` | 连接超时（毫秒） |
| `instances` | 空 | 多实例列表；为空时自动生成名为 `default` 的实例 |

### 服务实例 `plugin.sync.server.instances[]`

| key | 默认值 | 说明 |
|---|---|---|
| `name` | 随机 UUID | 实例名 |
| `host` | `0.0.0.0` | 绑定地址 |
| `port` | `19380` | 端口 |
| `protocol` | `rsocket-sync` | 协议名，必须是 SPI 中已注册的键 |
| `enable` | `true` | 是否启用该实例 |
| `description` | 未设置 | 实例说明 |
| `topics` | 空 | 该实例专属主题映射，会覆盖全局同名主题 |

> `server.protocol` 默认是 `http`，而手写 `instances[]` 时的 `protocol` 默认是 `rsocket-sync`，两者不是同一个默认值。

### 客户端 `plugin.sync.client`

| key | 默认值 | 说明 |
|---|---|---|
| `enable` | `false` | 是否启用客户端 |
| `protocol` | `http` | 协议名 |
| `instance-id` | 自动生成 | 客户端实例标识 |
| `ip-address` | 未设置 | 多网卡时指定出口 IP |
| `server-host` / `server-port` | `localhost` / `19380` | 服务端地址 |
| `server-address` | 未设置 | 完整地址（如 `ws://host:port`），优先级高于 host:port |
| `discovery-name` | 未设置 | 按名称从服务发现解析地址，解析不到才回落到显式地址 |
| `scatter-id` | 未设置 | 业务分组，为空回落到 `discovery-name` |
| `heartbeat` / `heartbeat-interval` | `true` / `30` | 心跳开关 / 间隔（秒） |
| `connect-timeout` | `10000` | 连接超时（毫秒） |
| `auto-reconnect` | `true` | 自动重连 |
| `max-reconnect-attempts` | `-1` | 最大重连次数，`-1` 为无限 |
| `reconnect-interval` | `5000` | 重连间隔（毫秒） |
| `capabilities` | `job, actuator, file, log` | 能力声明 |
| `metadata` | 空 | 扩展元数据，供服务端定向推送 |
| `auto-register` | `true` | 连接后自动注册 |
| `register-topic` | `sync/client/register` | 注册主题 |
| `heartbeat-topic` | `sync/client/heartbeat` | 心跳主题 |
| `offline-topic` | `sync/client/offline` | 下线主题 |

### 数据交换 `plugin.sync.data-sync`

| key | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否装配 `SyncDataExchangeService` |
| `topic-prefix` | `sync/data` | 主题前缀 |
| `request-timeout` | `30000` | 请求超时（毫秒） |
| `default-channel` | `default` | 默认逻辑通道 |

## 四、快速开始

1. 引入依赖，并按需补齐传输层（RSocket / Socket.IO 为 provided）。
2. 选定 `type` 并打开对应的 `enable`：

   ```yaml
   plugin:
     sync:
       type: both
       crypto-secret: ${SYNC_SECRET:}
       server:
         enable: true
         protocol: websocket
         port: 19380
       client:
         enable: true
         protocol: websocket
         server-host: 127.0.0.1
         server-port: 19380
   ```

3. 在业务侧注册主题处理器（或配置 `plugin.sync.topics`），随后收发消息。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API：`SyncServer` / `SyncClient` 的收发方法返回 `void`、`boolean` 或命中数量 `int`，没有 `Builder`，也没有 `return this` 风格入口。配置只能来自 `SyncProperties` 或编程式注册。

```java
@Service
public class SyncGateway {

    private final SyncServer syncServer;      // type=server/both 时存在
    private final SyncClient syncClient;      // type=client/both 时存在

    public SyncGateway(ObjectProvider<SyncServer> serverProvider,
                       ObjectProvider<SyncClient> clientProvider) {
        this.syncServer = serverProvider.getIfAvailable();
        this.syncClient = clientProvider.getIfAvailable();
    }

    public void start() {
        // 服务端：按主题注册处理器，handle(topic, sessionId, data)
        if (syncServer != null) {
            syncServer.registerHandler("order", (topic, sessionId, data) -> {
                // 处理上行消息，可返回回执
                return Map.of("ack", true);
            });
        }

        // 客户端：批量订阅 + 发布结构化数据
        if (syncClient != null) {
            syncClient.subscribe("order");
            syncClient.subscribe("notice");
            syncClient.registerHandler("notice", (topic, sessionId, data) -> null);
            syncClient.publish("order", Map.of("orderId", 1, "amount", 100));
        }
    }

    public void pushToTenant() {
        // 服务端定向推送：按客户端 metadata 过滤
        int hit = syncServer.publishByMetadata("tenant", "t-001", "notice", Map.of("msg", "hello"));
    }
}
```

### 5.2 SPI 扩展示例

本模块有两层扩展点。

**(1) 业务定制（Spring Bean）**：实现 → 声明为 `@Bean` → 由 `SyncAutoConfiguration` 用 `ObjectProvider` 按 `getOrder()` 注入 `SyncClientWrapper`。

```java
// 1) 实现：客户端注册前补充元数据
public class TenantClientInfoCustomizer implements SyncClientInfoCustomizer {

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public void customize(ClientInfo clientInfo) {
        Map<String, Object> metadata = clientInfo.getClientMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
            clientInfo.setClientMetadata(metadata);
        }
        metadata.put("tenant", "t-001");
    }
}

// 2) 登记
@Bean
public SyncClientInfoCustomizer tenantClientInfoCustomizer() {
    return new TenantClientInfoCustomizer();
}

// 3) 生效：注册报文带上 tenant，服务端即可 publishByMetadata("tenant", "t-001", ...)
```

连接建立前还有 `SyncClientCustomizer#customize(SyncClient)`，适合提前注册主题与处理器。

**(2) 传输协议（utils 层 SPI）**：`SyncServerInstance` 与 `SyncClientWrapper` 都调用 `ServiceProvider.getNewExtension(protocol, setting)`，因此协议键由 `utils-support-common-starter` 的 `META-INF/extensions` 注册决定；新增协议需在 utils 侧实现 `com.chua.common.support.network.sync.SyncServer` / `SyncClient` 并登记键名。

## 六、与其它 starter 的关系

- `spring-support-common-starter`：日志与服务信息，本模块必需。
- `utils-support-common-starter`：`SyncClient` / `SyncServer` SPI 与协议实现宿主。
- `utils-support-datasync-starter`（optional）：提供 `DataSyncAgentLifecycleConfiguration` 所需的 Agent 能力。
- `spring-support-datasync-starter`：数据同步的映射加载侧，本模块负责把同步结果沿通道下发，二者常配对使用。
- `spring-support-job-starter`：任务模块借用同一套 `SyncClient` / `SyncServer` 做指令下发，本模块的通道能力可与之共存。
- `spring-support-rpc-starter`：方法级 RPC 门面，与本模块的消息通道是两种不同形态，互不替代。

## 七、已知限制

- `plugin.sync.topics` 的 Value 在 `SyncServer#registerHandlersToInstance` 中未被用于选择处理器：实现是把所有已注册处理器挂到配置的主题上，因此**不要依赖 Value 做路由**，需要精确绑定请用 `registerHandler(topic, handler)`。
- `server.instances[].protocol` 的默认值是 `rsocket-sync`，而 utils 层注册的键是 `http` / `websocket` / `tcp` / `udp` / `rsocket` / `socketio` / `kcp`——**`rsocket-sync` 这个键没有任何实现注册**，手写 `instances[]` 时必须显式写出协议名。
- RSocket / Socket.IO 为 `provided`，且协议注册在 `utils-support-rsocket-starter` / `utils-support-socketio-starter`；只加本模块依赖时这两个 `protocol` 取不到实例。
- `plugin.sync.topics` 之外的实例级 `topics` 会覆盖全局同名主题；未配置 `instances` 时只会生成一个 `default` 实例，多端口必须显式写 `instances[]`。
- 服务发现只在容器中恰好一个 `ServiceDiscovery`，或存在 `@Primary` 时接入；多个实现且无主时会告警并回落显式地址。
- `crypto-secret` 未配置即明文传输；滚动升级期需按 `crypto-version=1` 过渡后再统一切回 `2`，解密端兼容两版。
- `type` 与对应的 `enable` 必须同时满足，只设 `enable: true` 而 `type` 不匹配不会启动。
