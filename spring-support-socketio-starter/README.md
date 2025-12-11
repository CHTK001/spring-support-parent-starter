# spring-support-socketio-starter

## 📖 模块简介

**Socket.IO 实时通信模块** - 提供基于 Socket.IO 协议的实时双向通信功能，支持事件驱动、房间管理、广播等特性。

## ✨ 核心功能

### 💬 实时通信

- ✅ 双向通信
- ✅ 事件驱动
- ✅ 自动重连
- ✅ 心跳检测

### 🏠 房间管理

- ✅ 加入房间
- ✅ 离开房间
- ✅ 房间广播
- ✅ 私聊消息

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socketio-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置开关

```yaml
plugin:
  socketio:
    # 是否启用Socket.IO功能
    # 默认: false
    enable: true

    # 服务端口
    port: 9092

    # 主机名
    hostname: 0.0.0.0
```

### 3. 创建事件处理器

```java
@Component
public class ChatEventHandler {

    @Autowired
    private SocketIOServer socketIOServer;

    @OnConnect
    public void onConnect(SocketIOClient client) {
        log.info("客户端连接: {}", client.getSessionId());
    }

    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        log.info("客户端断开: {}", client.getSessionId());
    }

    @OnEvent("chat")
    public void onChatMessage(SocketIOClient client, ChatMessage message) {
        // 广播消息给所有客户端
        socketIOServer.getBroadcastOperations().sendEvent("chat", message);
    }
}
```

### 4. 前端连接

```javascript
const socket = io("http://localhost:9092");

socket.on("connect", () => {
  console.log("已连接");
});

socket.emit("chat", { message: "Hello" });

socket.on("chat", (data) => {
  console.log("收到消息:", data);
});
```

## ⚙️ 配置说明

```yaml
plugin:
  socketio:
    enable: true
    port: 9092
    hostname: 0.0.0.0

    # 最大帧长度
    max-frame-payload-length: 1048576

    # 最大HTTP内容长度
    max-http-content-length: 1048576

    # 心跳间隔（毫秒）
    ping-interval: 25000

    # 心跳超时（毫秒）
    ping-timeout: 60000
```

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
