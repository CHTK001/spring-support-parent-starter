# spring-support-socket-starter

## 📖 模块简介

**Socket 通信模块** - 提供 TCP/UDP Socket 通信功能，支持服务端和客户端模式。

## ✨ 核心功能

### 🌐 Socket 服务端

- ✅ TCP 服务端
- ✅ UDP 服务端
- ✅ 多客户端连接管理
- ✅ 心跳检测

### 📡 Socket 客户端

- ✅ TCP 客户端
- ✅ UDP 客户端
- ✅ 自动重连
- ✅ 连接池管理

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置开关

```yaml
plugin:
  socket:
    # 是否启用Socket功能
    # 默认: false
    enable: true

    # Socket 服务端配置
    server:
      port: 9999
      protocol: tcp

    # Socket 客户端配置
    client:
      host: localhost
      port: 9999
      protocol: tcp
```

### 3. 创建 Socket 服务端

```java
@Component
public class SocketServerHandler {

    @SocketMessageHandler
    public String handleMessage(String message) {
        log.info("收到消息: {}", message);
        return "Echo: " + message;
    }
}
```

### 4. 使用 Socket 客户端

```java
@Service
public class SocketClientService {

    @Autowired
    private SocketClient socketClient;

    public String sendMessage(String message) {
        return socketClient.send(message);
    }
}
```

## ⚙️ 配置说明

```yaml
plugin:
  socket:
    enable: true

    server:
      port: 9999
      protocol: tcp
      # 最大连接数
      max-connections: 100
      # 读超时（毫秒）
      read-timeout: 30000
      # 写超时（毫秒）
      write-timeout: 30000

    client:
      host: localhost
      port: 9999
      protocol: tcp
      # 连接超时（毫秒）
      connect-timeout: 5000
      # 自动重连
      auto-reconnect: true
```

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
