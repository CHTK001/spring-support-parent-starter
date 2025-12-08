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

## 配置说明

### 基础配置

```yaml
plugin:
  sync:
    # 模式：server-服务端，client-客户端，both-同时启用
    type: client
```

### 客户端配置

```yaml
plugin:
  sync:
    type: client
    client:
      enable: true
      # 实例ID (默认自动生成)
      instance-id: 
      # 应用端口（默认从 server.port 获取）
      port: 8080
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
      # 连接配置
      connect-timeout: 10000
      reconnect-interval: 5
      max-reconnect-attempts: -1
      # 支持的功能
      capabilities:
        - job
        - actuator
        - file
        - log
```

### 自动获取的配置

以下配置会自动从 Spring Environment 获取，无需手动配置：

| Spring 配置 | 用途 |
|------------|------|
| `spring.application.name` | 应用名称 |
| `server.servlet.context-path` | 上下文路径 |
| `management.endpoints.web.base-path` | Actuator 路径（默认 `/actuator`） |
| `server.port` | 应用端口（当 client.port 为默认值 8080 时） |

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

## 版本信息

- 作者: CH
- 版本: 1.0.0
- 创建时间: 2024/12/04
