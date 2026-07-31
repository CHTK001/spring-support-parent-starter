# spring-support-sync-starter

同步协议模块，基于 `SyncProtocol` SPI 实现客户端/服务端双向通信。支持 WebSocket、HTTP、RSocket、Socket.IO 等多种协议，提供 Spring Boot 自动配置。

## 模块依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-sync-starter</artifactId>
</dependency>
```

### 可选协议依赖

| 协议 | 依赖 | 说明 |
|------|------|------|
| HTTP / WebSocket | `utils-support-common-starter`（默认引入） | 基于 JDK HttpServer / Socket 实现，无需额外依赖 |
| RSocket | `utils-support-rsocket-starter`（provided） | 需要额外引入 |
| Socket.IO | `utils-support-socketio-starter`（provided） | 需要额外引入 |

## 快速开始

### 1. 配置 `application.yml`

```yaml
plugin:
  sync:
    type: server              # server / client / both
    server:
      enable: true
      protocol: websocket     # http / websocket / rsocket / socketio
      host: 0.0.0.0
      port: 19380
    client:
      enable: true
      protocol: websocket
      serverHost: 127.0.0.1
      serverPort: 19380
```

### 2. 启动应用

服务端/客户端根据 `type` 和 `enable` 自动装配。

## 配置详解

### 顶层配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `plugin.sync.type` | String | `client` | 运行模式：`server` 仅作服务端，`client` 仅作客户端，`both` 同时启用两端 |
| `plugin.sync.topics` | Map | 空 | 全局主题与处理器映射，Key 为主题名，Value 为处理器名 |
| `plugin.sync.server.*` | ServerConfig | — | 服务端配置，仅在 `type=server` 或 `both` 时生效 |
| `plugin.sync.client.*` | ClientConfig | — | 客户端配置，仅在 `type=client` 或 `both` 时生效 |
| `plugin.sync.data-sync.*` | DataSyncConfig | — | 数据同步交换服务配置 |

### 服务端配置 `plugin.sync.server`

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enable` | boolean | `false` | 是否启用服务端 |
| `host` | String | `0.0.0.0` | 默认绑定地址 |
| `port` | int | `19380` | 默认端口（当 `instances` 为空时使用） |
| `protocol` | String | `http` | 默认协议类型 |
| `heartbeat` | boolean | `true` | 是否启用心跳检测 |
| `heartbeatInterval` | int | `30` | 心跳间隔（秒） |
| `connectTimeout` | int | `10000` | 连接超时时间（毫秒） |
| `instances` | List<ServerInstance> | 空 | 多实例配置列表，支持多端口/多协议同时运行 |

#### 服务实例 `plugin.sync.server.instances`

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | 随机UUID | 实例名称，用于标识和管理 |
| `host` | String | `0.0.0.0` | 绑定地址 |
| `port` | int | `19380` | 端口号 |
| `protocol` | String | `http` | 协议类型：`http`、`websocket`、`rsocket`、`socketio` |
| `enable` | boolean | `true` | 是否启用该实例 |
| `description` | String | 空 | 实例描述 |
| `topics` | Map<String,String> | 空 | 该实例专属的主题与处理器映射，可覆盖全局 `topics` |

### 客户端配置 `plugin.sync.client`

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enable` | boolean | `false` | 是否启用客户端 |
| `instanceId` | String | 自动生成 | 客户端实例ID |
| `ipAddress` | String | 空 | 指定客户端IP（多网卡场景） |
| `protocol` | String | `http` | 协议类型 |
| `serverHost` | String | `localhost` | 服务端主机名 |
| `serverPort` | int | `19380` | 服务端端口 |
| `serverAddress` | String | 空 | 完整服务端地址（如 `ws://host:port`），优先级高于 `serverHost:serverPort` |
| `heartbeat` | boolean | `true` | 是否启用心跳 |
| `heartbeatInterval` | int | `30` | 心跳间隔（秒） |
| `connectTimeout` | int | `10000` | 连接超时（毫秒） |
| `autoReconnect` | boolean | `true` | 是否自动重连 |
| `maxReconnectAttempts` | int | `-1` | 最大重连次数，`-1` 表示无限 |
| `reconnectInterval` | long | `5000` | 重连间隔（毫秒） |
| `capabilities` | String[] | `["job","actuator","file","log"]` | 支持的功能列表 |
| `metadata` | Map<String,Object> | 空 | 扩展元数据，用于服务端定向推送 |
| `autoRegister` | boolean | `true` | 连接后是否自动注册到服务端 |
| `registerTopic` | String | `sync/client/register` | 注册主题 |
| `heartbeatTopic` | String | `sync/client/heartbeat` | 心跳主题 |
| `offlineTopic` | String | `sync/client/offline` | 下线通知主题 |

### 数据同步交换配置 `plugin.sync.data-sync`

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | boolean | `true` | 是否启用数据同步交换服务 |
| `topicPrefix` | String | `sync/data` | 数据同步主题前缀 |
| `requestTimeout` | long | `30000` | 请求超时时间（毫秒） |
| `defaultChannel` | String | `default` | 默认逻辑通道 |

## 典型场景

### 场景 1：纯服务端

```yaml
plugin:
  sync:
    type: server
    server:
      enable: true
      protocol: websocket
      port: 19380
```

### 场景 2：纯客户端

```yaml
plugin:
  sync:
    type: client
    client:
      enable: true
      protocol: websocket
      serverAddress: ws://192.168.1.100:19380
```

### 场景 3：同时启用两端（调试/边缘节点）

```yaml
plugin:
  sync:
    type: both
    server:
      enable: true
      protocol: websocket
      port: 19380
    client:
      enable: true
      protocol: websocket
      serverHost: 127.0.0.1
      serverPort: 19380
```

### 场景 4：多端口多协议

```yaml
plugin:
  sync:
    type: server
    server:
      enable: true
      instances:
        - name: ws-port
          protocol: websocket
          port: 19380
        - name: http-port
          protocol: http
          port: 19381
```

### 场景 5：客户端带元数据定向推送

```yaml
plugin:
  sync:
    type: client
    client:
      enable: true
      protocol: websocket
      serverHost: 127.0.0.1
      serverPort: 19380
      metadata:
        app: order-service
        region: cn-east
```

服务端可通过 `SyncServer.publishByMetadata("app", "order-service", "topic", data)` 向指定元数据的客户端定向推送。

## 测试

```bash
cd spring-support-parent-starter/spring-support-sync-starter
mvn test -DskipTests=false
```

### 测试覆盖矩阵

| 协议 | 测试类 | 用例数 | 覆盖范围 |
|------|--------|--------|----------|
| WebSocket | `SyncEndToEndTest` | 6 | 连接/断开/重连、点对点消息、广播、客户端->服务端 |
| HTTP | `SyncEndToEndTest` | 3 | 连接注册、点对点消息、广播 |
| RSocket / Socket.IO | `SyncEndToEndTest.rsocket_socketio_spi_验证` | 1 | SPI 注册与实例化验证（provided 依赖） |
| 服务端生命周期 | `SyncServerTest` | 8 | 启动/停止、主题订阅、心跳、消息路由 |
| 配置绑定 | `SyncDataSyncConfigurationTest` | 3 | DataSync 配置校验 |

总计：20 个用例，全部通过。

## 注意事项

- RSocket 和 Socket.IO 依赖需显式声明为 `provided` Scope，仅在运行环境包含对应传输层时可用。
- `instances` 配置为空时，系统会自动生成一个名为 `default` 的默认实例，使用 `host`/`port`/`protocol` 配置。
- `type` 与 `server.enable`/`client.enable` 必须同时满足才会启动对应端，单独设置 `enable: true` 但 `type` 不匹配时不会生效。
