# Spring Support RSocket Starter

## 模块简介

`spring-support-rsocket-starter` 是一个基于 RSocket 实现的高性能响应式双向通信模块，提供了完整的 RSocket 协议支持，包括服务器端和客户端实现。

RSocket 是一个应用层协议，提供了四种交互模式（Request/Response、Fire-and-Forget、Request/Stream、Channel），支持响应式流（Reactive Streams）规范，具有背压（Backpressure）机制，适用于微服务之间的高性能通信。

## 主要特性

### 核心功能

1. **四种交互模式**
   - **Request/Response**: 请求-响应模式，一对一通信
   - **Fire-and-Forget**: 单向消息模式，不需要响应
   - **Request/Stream**: 请求-流模式，一对多通信
   - **Channel**: 双向流模式，多对多通信

2. **响应式编程**
   - 基于 Project Reactor 实现
   - 支持异步非阻塞 I/O
   - 完整的响应式流（Reactive Streams）支持
   - 背压（Backpressure）机制

3. **高性能通信**
   - 基于 Netty 实现的高性能网络传输
   - 多路复用支持
   - 零拷贝技术
   - 低延迟、高吞吐量

4. **传输协议支持**
   - TCP 传输
   - WebSocket 传输
   - HTTP/2 传输

5. **元数据路由**
   - 复合元数据支持
   - 路由元数据
   - 自定义元数据

6. **连接管理**
   - 自动重连
   - 会话恢复
   - Keep-Alive 心跳机制
   - 连接状态监控

7. **流量控制**
   - 租约（Lease）机制
   - 请求限流
   - 背压控制

8. **安全认证**
   - 支持自定义认证机制
   - 元数据认证
   - Token 认证

## 技术架构

### 核心组件

1. **RSocketProtocol**: RSocket 协议实现类
2. **RSocketProtocolServer**: RSocket 服务器实现
3. **RSocketProtocolClient**: RSocket 客户端实现
4. **RSocketServletRequest**: RSocket 请求封装
5. **RSocketServletResponse**: RSocket 响应封装

### 交互模式

#### 1. Request/Response（请求-响应）

```java
// 服务器端自动处理
// 客户端发送
ServletResponse response = client.sendSync(request);
```

#### 2. Fire-and-Forget（单向消息）

```java
// 客户端发送
client.sendOneWay(request);
```

#### 3. Request/Stream（请求-流）

```java
// 客户端请求流数据
client.requestStream(request, payload -> {
    // 处理每个流数据
    System.out.println("Received: " + payload.getDataUtf8());
});
```

#### 4. Channel（双向流）

```java
// 客户端建立双向通道
Flux<Payload> requestFlux = Flux.interval(Duration.ofSeconds(1))
    .map(i -> ByteBufPayload.create("Request " + i));

client.requestChannel(requestFlux, responsePayload -> {
    // 处理响应流
    System.out.println("Response: " + responsePayload.getDataUtf8());
});
```

## 快速开始

### 1. 添加依赖

在项目的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-rsocket-starter</artifactId>
    <version>4.0.0.34</version>
</dependency>
```

### 2. 服务器端使用

#### 2.1 创建服务器

```java
import com.chua.common.support.protocol.ServerSetting;
import com.chua.starter.rsocket.support.server.RSocketProtocolServer;

public class RSocketServerExample {
    public static void main(String[] args) throws Exception {
        // 创建服务器配置
        ServerSetting setting = ServerSetting.builder()
                .host("0.0.0.0")
                .port(7000)
                .maxConnections(1000)
                .maxFrameSize(16 * 1024 * 1024) // 16MB
                .workerThreads(4)
                .bossThreads(1)
                .build();

        // 创建并启动服务器
        RSocketProtocolServer server = new RSocketProtocolServer(setting);
        server.start();

        System.out.println("RSocket服务器已启动: " + setting.host() + ":" + setting.port());
    }
}
```

#### 2.2 处理请求事件

```java
import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.common.support.protocol.annotation.OnMessage;
import com.chua.common.support.protocol.annotation.OnOpen;
import com.chua.common.support.protocol.annotation.OnClose;
import com.chua.common.support.protocol.annotation.OnEvent;
import com.chua.common.support.protocol.request.ServletRequest;

public class RSocketEventHandler {
    
    @OnOpen
    public void onConnect(String connectionId) {
        System.out.println("客户端连接: " + connectionId);
    }
    
    @OnClose
    public void onDisconnect(String connectionId) {
        System.out.println("客户端断开: " + connectionId);
    }
    
    @OnMessage
    public String onMessage(String connectionId, String message, ServletRequest request) {
        System.out.println("收到消息: " + message);
        return "Echo: " + message;
    }
    
    @OnEvent("custom.event")
    public void onCustomEvent(String eventName, String connectionId, Object[] data, ServletRequest request) {
        System.out.println("收到自定义事件: " + eventName);
    }
}
```

#### 2.3 广播消息

```java
// 广播到所有客户端
server.broadcast("notification", "系统消息");

// 发送到指定客户端
server.sendToClient("connectionId", "message", "私信内容");

// 断开指定客户端
server.disconnectClient("connectionId");
```

### 3. 客户端使用

#### 3.1 创建客户端

```java
import com.chua.common.support.protocol.ClientSetting;
import com.chua.starter.rsocket.support.client.RSocketProtocolClient;
import com.chua.common.support.protocol.request.ServletRequest;
import com.chua.common.support.protocol.request.ServletResponse;

public class RSocketClientExample {
    public static void main(String[] args) throws Exception {
        // 创建客户端配置
        ClientSetting setting = ClientSetting.builder()
                .host("localhost")
                .port(7000)
                .connectTimeoutMillis(5000)
                .readTimeoutMillis(10000)
                .reconnectAttempts(3)
                .build();

        // 创建并连接客户端
        RSocketProtocolClient client = new RSocketProtocolClient(setting);
        client.connect();

        System.out.println("RSocket客户端已连接");
    }
}
```

#### 3.2 发送消息

```java
// Request/Response 模式（同步）
ServletRequest request = ServletRequest.post("/rsocket/echo")
        .body("Hello RSocket");
ServletResponse response = client.sendSync(request);
System.out.println("响应: " + response.getBodyString());

// Request/Response 模式（异步）
client.sendAsync(request)
        .thenAccept(resp -> System.out.println("响应: " + resp.getBodyString()))
        .exceptionally(ex -> {
            System.err.println("错误: " + ex.getMessage());
            return null;
        });

// Fire-and-Forget 模式（单向消息）
ServletRequest oneWayRequest = ServletRequest.post("/rsocket/notify")
        .body("Notification");
client.sendOneWay(oneWayRequest);
```

#### 3.3 流式通信

```java
// Request/Stream 模式
ServletRequest streamRequest = ServletRequest.post("/rsocket/stream")
        .body("Get stream data");

client.requestStream(streamRequest, payload -> {
    String data = payload.getDataUtf8();
    System.out.println("收到流数据: " + data);
    payload.release();
});

// Request/Channel 模式（双向流）
Flux<Payload> requestFlux = Flux.interval(Duration.ofSeconds(1))
        .take(10)
        .map(i -> ByteBufPayload.create("Request " + i));

client.requestChannel(requestFlux, responsePayload -> {
    System.out.println("收到响应: " + responsePayload.getDataUtf8());
    responsePayload.release();
});
```

#### 3.4 元数据推送

```java
// 推送元数据
client.metadataPush("Custom metadata information");
```

## 配置说明

### 服务器配置（ServerSetting）

| 配置项              | 类型      | 默认值      | 说明                   |
|------------------|---------|----------|----------------------|
| host             | String  | 0.0.0.0  | 监听地址                 |
| port             | int     | 7000     | 监听端口                 |
| maxConnections   | int     | 0（无限制）   | 最大连接数                |
| maxFrameSize     | int     | 16777216 | 最大帧大小（字节）            |
| workerThreads    | int     | CPU核心数*2 | Worker线程数            |
| bossThreads      | int     | 1        | Boss线程数              |
| contextPath      | String  | /        | 上下文路径                |

### 客户端配置（ClientSetting）

| 配置项                    | 类型      | 默认值   | 说明         |
|------------------------|---------|-------|------------|
| host                   | String  | -     | 服务器地址      |
| port                   | int     | -     | 服务器端口      |
| connectTimeoutMillis   | long    | 5000  | 连接超时（毫秒）   |
| readTimeoutMillis      | long    | 10000 | 读取超时（毫秒）   |
| reconnectAttempts      | int     | 3     | 重连尝试次数     |
| ssl                    | boolean | false | 是否启用SSL    |

## 高级特性

### 1. 自定义元数据

```java
// 服务器端添加自定义元数据
RSocketServletResponse response = new RSocketServletResponse("custom");
response.addMetadata("userId", "12345");
response.addMetadata("timestamp", String.valueOf(System.currentTimeMillis()));
response.setBodyString("Response with metadata");

// 客户端读取元数据
String userId = request.getHeader("X-Metadata-userId");
```

### 2. 租约（Lease）机制

服务器端自动配置租约机制，控制客户端请求频率：

```java
// 服务器启动时自动启用租约
// 每5秒续约一次，每次允许100个请求
```

### 3. 背压控制

RSocket 原生支持背压机制，当消费者处理速度慢时自动减缓生产者速度：

```java
// 消费者控制流速
Flux.from(payloadPublisher)
    .onBackpressureBuffer(100)  // 缓冲区大小
    .subscribe(payload -> {
        // 慢速处理
        Thread.sleep(100);
    });
```

### 4. 连接监控

```java
// 获取当前连接数
long connectionCount = server.getConnectionCount();

// 获取所有客户端
Map<String, RSocket> clients = server.getClients();

// 客户端连接状态
boolean isConnected = client.isConnected();
int pendingRequests = client.getPendingRequestCount();
```

## 性能优化

### 1. 零拷贝

RSocket 使用零拷贝技术，避免数据在内存中的多次复制：

```java
// 服务器配置中启用零拷贝解码器
.payloadDecoder(PayloadDecoder.ZERO_COPY)
```

### 2. 多路复用

RSocket 支持在单个连接上进行多路复用，减少连接开销。

### 3. 线程池配置

根据实际负载调整线程池大小：

```java
ServerSetting setting = ServerSetting.builder()
    .workerThreads(Runtime.getRuntime().availableProcessors() * 2)
    .bossThreads(1)
    .build();
```

## 最佳实践

### 1. 选择合适的交互模式

- **Request/Response**: 适用于需要立即响应的场景（如 REST API）
- **Fire-and-Forget**: 适用于不需要响应的通知场景（如日志上报）
- **Request/Stream**: 适用于需要返回大量数据的场景（如分页数据）
- **Channel**: 适用于实时双向通信场景（如聊天、游戏）

### 2. 资源释放

及时释放 Payload 资源：

```java
try (RSocketServletRequest request = new RSocketServletRequest(payload, mimeType)) {
    // 处理请求
} // 自动释放资源
```

### 3. 错误处理

```java
client.sendAsync(request)
    .exceptionally(throwable -> {
        log.error("请求失败", throwable);
        return ServletResponse.error(throwable.getMessage());
    });
```

### 4. 连接池管理

对于高并发场景，建议使用连接池管理客户端连接。

## 与 Socket.IO 的对比

| 特性       | RSocket              | Socket.IO         |
|----------|----------------------|-------------------|
| 协议层      | 应用层协议                | 基于 WebSocket      |
| 交互模式     | 4种（支持流式）             | 基于事件的双向通信         |
| 背压支持     | ✅ 原生支持               | ❌                 |
| 响应式编程    | ✅ 基于 Reactor          | ❌                 |
| 传输协议     | TCP/WebSocket/HTTP/2 | WebSocket/轮询     |
| 性能       | 更高（零拷贝、多路复用）         | 较高                |
| 学习曲线     | 较陡                   | 较平缓               |
| 适用场景     | 微服务通信、高性能实时通信        | Web实时应用、聊天、推送通知 |

## 常见问题

### 1. RSocket 服务器启动失败？

检查端口是否被占用，确保配置正确：

```bash
netstat -ano | findstr 7000
```

### 2. 客户端连接超时？

增加连接超时时间：

```java
ClientSetting.builder()
    .connectTimeoutMillis(10000)
    .build();
```

### 3. Payload 释放错误？

确保每个 Payload 都被正确释放：

```java
payload.release();
```

或使用 try-with-resources：

```java
try (RSocketServletRequest request = new RSocketServletRequest(payload, mimeType)) {
    // 处理请求
}
```

## 技术支持

- **版本**: 4.0.0.34
- **作者**: CH
- **日期**: 2024/10/24

## 更新日志

请参阅 [CHANGELOG.md](CHANGELOG.md)

## 许可证

本模块遵循项目主许可证。

