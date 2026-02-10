# Spring Support Sync Starter

同步协议客户端/服务端模块，基于 SyncProtocol 实现长连接通信。

## 功能特性

- 支持 server、client、both 三种模式
- 订阅配置的所有主题
- 通过 SPI 加载处理器处理消息
- 自动重连机制
- 心跳保活
- 同步请求/响应
- 客户端信息注册
- 提供同步任务 SPI 列表与参数定义（Input/Output/DataCenter/Filter）
- 提供远程桌面/RDP/VNC/SSH 的 WebSocket 消息类型与创建方法

## 配置说明

### 基础配置

```yaml
plugin:
  sync:
    # 模式：server-服务端，client-客户端，both-同时启用
    type: client
```

### 同步任务配置

同步任务能力与长连接模块同属一个 starter，配置同样使用 `plugin.sync` 前缀：

```yaml
plugin:
  sync:
    # 是否启用同步任务
    enabled: true
    # 是否自动创建同步相关表（monitor_sync_*）
    auto-create-table: false
    # 是否启用WebSocket实时推送
    websocket-enabled: true
    # 是否启用定时任务调度集成
    scheduler-enabled: true
    # 默认批处理大小
    default-batch-size: 1000
    # 默认消费超时时间(ms)
    default-consume-timeout: 30000
    # 默认重试次数
    default-retry-count: 3
    # 默认重试间隔(ms)
    default-retry-interval: 1000
    # 任务执行日志保留天数
    log-retention-days: 30
    # 是否启用任务执行日志清理
    log-cleanup-enabled: true
```

### 同步任务配置项说明

| 配置项                     | 说明                            | 默认值  |
| -------------------------- | ------------------------------- | ------- |
| `enabled`                  | 是否启用同步任务                | `true`  |
| `auto-create-table`        | 启动时自动创建同步表            | `false` |
| `websocket-enabled`        | 是否启用WebSocket实时推送        | `true`  |
| `scheduler-enabled`        | 是否启用定时任务调度集成         | `true`  |
| `default-batch-size`       | 默认批处理大小                  | `1000`  |
| `default-consume-timeout`  | 默认消费超时时间(毫秒)          | `30000` |
| `default-retry-count`      | 默认重试次数                    | `3`     |
| `default-retry-interval`   | 默认重试间隔(毫秒)              | `1000`  |
| `log-retention-days`       | 任务执行日志保留天数            | `30`    |
| `log-cleanup-enabled`      | 是否启用任务执行日志清理         | `true`  |

### 客户端配置

```yaml
plugin:
  sync:
    type: client
    client:
      enable: true
      # 实例ID (默认自动生成)
      instance-id:
      # 客户端 IP 地址（多网卡场景下指定，为空则自动获取）
      ip-address:
      # 协议类型
      protocol: rsocket
      # 服务端地址
      server-host: localhost
      server-port: 19380
      # 或使用完整地址（优先级更高）
      server-address:
      # 心跳配置
      heartbeat: true
      heartbeat-interval: 30
      # 连接超时（毫秒）
      connect-timeout: 10000
      # 重连配置
      auto-reconnect: true
      max-reconnect-attempts: -1
      reconnect-interval: 5000
      # 自动注册
      auto-register: true
      # 支持的功能
      capabilities:
        - job
        - actuator
        - file
        - log
```

### 自动获取的配置

以下配置会自动从 Spring Environment 获取，无需手动配置：

| Spring 配置                          | 用途                              |
| ------------------------------------ | --------------------------------- |
| `spring.application.name`            | 应用名称                          |
| `server.servlet.context-path`        | 上下文路径                        |
| `server.port`                        | 应用端口                          |
| `management.endpoints.web.base-path` | Actuator 路径（默认 `/actuator`） |

### 客户端配置项说明

| 配置项                   | 说明                         | 默认值                       |
| ------------------------ | ---------------------------- | ---------------------------- |
| `enable`                 | 是否启用客户端               | `true`                       |
| `instance-id`            | 实例 ID                      | 自动生成                     |
| `ip-address`             | 客户端 IP 地址（多网卡场景） | 自动获取                     |
| `protocol`               | 协议类型                     | `rsocket`                    |
| `server-host`            | 服务端地址                   | `localhost`                  |
| `server-port`            | 服务端端口                   | `19380`                      |
| `server-address`         | 完整服务端地址（优先级高）   | -                            |
| `heartbeat`              | 是否启用心跳                 | `true`                       |
| `heartbeat-interval`     | 心跳间隔（秒）               | `30`                         |
| `connect-timeout`        | 连接超时（毫秒）             | `10000`                      |
| `auto-reconnect`         | 是否启用自动重连             | `true`                       |
| `max-reconnect-attempts` | 最大重连次数（-1 表示无限）  | `-1`                         |
| `reconnect-interval`     | 重连间隔（毫秒）             | `5000`                       |
| `auto-register`          | 是否自动注册                 | `true`                       |
| `capabilities`           | 支持的功能列表               | `[job, actuator, file, log]` |

### 服务端配置

```yaml
plugin:
  sync:
    type: server
    server:
      enable: true
      host: 0.0.0.0
      port: 19380
      protocol: rsocket-sync
      heartbeat: true
      heartbeat-interval: 30
      connect-timeout: 10000
```

## 服务发现

### SyncServerServiceDiscovery

基于 SyncServer 的服务发现实现，使用长连接客户端作为服务节点。

#### 功能特性

- 自动发现已连接到 SyncServer 的所有节点
- 通过应用名称（clientApplicationName）进行服务分组
- 支持多种负载均衡策略（weight/round/random 等）
- 实时感知节点上下线

#### 使用方式

```java
@Autowired
private SyncServer syncServer;

// 创建服务发现实例
SyncServerServiceDiscovery discovery = new SyncServerServiceDiscovery(syncServer);
discovery.start();

// 获取指定应用的服务实例
Discovery service = discovery.getService("user-service");

// 获取所有服务实例
Set<Discovery> allServices = discovery.getServiceAll("user-service");

// 获取在线客户端列表
List<ClientInfo> onlineClients = discovery.getOnlineClients();

// 获取所有应用名称
Set<String> appNames = discovery.getApplicationNames();
```

#### API 说明

| 方法                                 | 说明                                     |
| ------------------------------------ | ---------------------------------------- |
| `getService(path)`                   | 获取单个服务实例（默认 weight 负载均衡） |
| `getService(path, balance)`          | 获取单个服务实例（指定负载均衡策略）     |
| `getServiceAll(path)`                | 获取所有匹配的服务实例                   |
| `getOnlineClients()`                 | 获取所有在线客户端                       |
| `getOnlineClientsByAppName(appName)` | 获取指定应用的在线客户端                 |
| `getApplicationNames()`              | 获取所有应用名称                         |

## 版本信息

- 作者: CH
- 版本: 1.0.0
- 创建时间: 2024/12/04
- 更新时间: 2024/12/08 - 添加 SyncServerServiceDiscovery 服务发现实现
