# spring-support-socket-websocket-starter

基于 Spring 原生 WebSocket 的独立实现：与应用同端口运行、不额外监听端口，也不注册 `spring-support-socket-starter` 的 `SocketProtocol` SPI（配置前缀是 `plugin.websocket`，不是 `plugin.socket`）。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 端点注册 | `WebSocketAutoConfiguration`（`@EnableWebSocket` + `WebSocketConfigurer`） | `plugin.websocket.endpoints` |
| 会话管理 | `WebSocketSessionManager` | `webSocketSessionManager` Bean |
| 消息发送 | `WebSocketTemplate` | `webSocketTemplate` Bean |
| 消息处理 | `WebSocketMessageHandler`、`DelegatingWebSocketHandler` | 自定义 `WebSocketMessageHandler` Bean |
| SockJS 回退 | `WebSocketHandlerRegistration.withSockJS()` | `plugin.websocket.sock-js=true` |
| 容器参数 | `ServletServerContainerFactoryBean` | `max-message-size`、`send-time-limit` |
| 房间信令（WebRTC P2P） | `RoomService`、`RoomSignalProcessor`、`RoomWebSocketHandler` | `room.*` |
| 同步协议适配 | `WebsocketSyncServer`（继承 `AbstractSyncServer`） | 由 `spring-support-sync-starter` 侧装配 |
| 服务信息 | `webSocketServiceInfoContributor` | 服务信息面板标记 `websocket` |

自动配置产出的 Bean（均在 `plugin.websocket.enable=true` 时创建，`@ConditionalOnMissingBean` 允许覆盖）：

| Bean | 类型 |
|---|---|
| `webSocketSessionManager` | `WebSocketSessionManager` |
| `webSocketTemplate` | `WebSocketTemplate` |
| `delegatingWebSocketHandler` | `DelegatingWebSocketHandler` |
| `roomService` | `RoomService` |
| `roomWebSocketHandler` | `RoomWebSocketHandler` |
| `createWebSocketContainer` | `ServletServerContainerFactoryBean` |
| `webSocketServiceInfoContributor` | `ServiceInfoContributor` |

所有文本消息都由同一个 `DelegatingWebSocketHandler` 接收，再依次分发给容器内全部 `WebSocketMessageHandler`（`onConnect` / `onMessage` / `onClose` / `onError`），单个处理器抛异常只记日志、不影响其它处理器。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-websocket-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 是否可选 |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-websocket` | `@EnableWebSocket`、`WebSocketHandler`、SockJS | 否 |
| `com.chua:utils-support-common-starter` | 公共工具 | 否 |
| `com.chua:spring-support-sync-starter` | `AbstractSyncServer` 等同步协议基类 | 否 |
| `com.chua:spring-support-socket-starter` | 统一 `SocketSession` / `SocketListener` 类型（如需共享抽象） | 是（`optional`） |

本模块**不依赖** netty-socketio、RSocket，也不会因引入它而自动获得 `plugin.socket` 协议能力。

## 三、配置项

### 3.1 `plugin.websocket`（`WebSocketProperties`）

| key | 默认值 | 说明 |
|---|---|---|
| `enable` | `false` | 总开关；关闭时整个自动配置不生效 |
| `endpoints` | `["/ws"]` | 端点路径列表，逐个注册到同一 handler |
| `allowed-origins` | `["*"]` | 允许的 Origin 列表 |
| `sock-js` | `false` | 是否对端点启用 SockJS 回退 |
| `heartbeat-interval` | `25000` | SockJS 心跳间隔（毫秒），仅在 `sock-js=true` 时生效 |
| `max-message-size` | `65536` | 文本/二进制帧缓冲上限（字节），写入 `ServletServerContainerFactoryBean` |
| `send-time-limit` | `10000` | 发送超时（毫秒）；实现里同时用于 `maxSessionIdleTimeout = send-time-limit * 2` |
| `send-buffer-size-limit` | `524288` | 发送缓冲上限（由 `@Data` 生成访问器，但当前自动配置未使用该值） |

### 3.2 `room`（`RoomProperties`，房间信令）

| key | 默认值 | 说明 |
|---|---|---|
| `room.enable` | `false` | 房间信令开关（当前仅作配置项，Bean 创建未按它判断） |
| `room.endpoint` | `/api/rooms/{roomId}/ws` | 文档性端点；实际端点仍由 `plugin.websocket.endpoints` 决定 |
| `room.max-peers` | `2` | 每房间最大端点数（P2P 即 2） |
| `room.timeouts.lease-expires-ms` | `30000` | 端点心跳续期时间，超时判定掉线 |
| `room.timeouts.remove-empty-room-ms` | `240000` | 空房间回收时间 |
| `room.stun-server` | 空 | STUN 服务器 |
| `room.turn-server` | 空 | TURN 服务器 URI |
| `room.turn-user` | 空 | TURN 用户名 |
| `room.turn-password` | 空 | TURN 密码 |

> `RoomProperties` 标注了 `@ConfigurationProperties(prefix = "room")`，但本模块未加 `@EnableConfigurationProperties(RoomProperties.class)`；若宿主没有另外注册该 Bean，`roomService(RoomProperties, ...)` 会因缺少依赖而无法创建。

## 四、快速开始

**第一步：引入依赖。**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-websocket-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

**第二步：开启端点。**

```yaml
plugin:
  websocket:
    enable: true
    endpoints:
      - /ws
    allowed-origins:
      - "*"
    sock-js: false
```

**第三步：实现消息处理器 Bean。**

```java
@Component
public class EchoHandler implements WebSocketMessageHandler {

    private final WebSocketTemplate template;

    public EchoHandler(WebSocketTemplate template) {
        this.template = template;
    }

    @Override
    public void onMessage(WebSocketSession session, String message) {
        template.send(session.getId(), "echo: " + message);
    }
}
```

启动后浏览器连接 `ws://localhost:8080/ws` 即可收发文本帧。

## 五、运行示例

### 5.1 调用示例

`WebSocketTemplate` 为普通类，方法返回 `boolean` / `int`，**本模块无链式 API**，按步骤调用：

1. `send(sessionId, message)` → 单会话发送，返回是否成功。
2. `sendToUser(userId, message)` → 按用户发送，需先 `bindUser(sessionId, userId)`。
3. `broadcast(message)` → 全量广播，返回成功条数。
4. `bindUser` / `unbindUser` / `getOnlineCount` → 会话与用户绑定、在线数。

```java
@RestController
@RequestMapping("/push")
public class PushController {

    private final WebSocketTemplate template;

    public PushController(WebSocketTemplate template) {
        this.template = template;
    }

    @PostMapping("/broadcast")
    public int broadcast(@RequestParam String text) {
        return template.broadcast(text);
    }

    @PostMapping("/bind")
    public void bind(@RequestParam String sessionId, @RequestParam String userId) {
        template.bindUser(sessionId, userId);
    }

    @GetMapping("/online")
    public int online() {
        return template.getOnlineCount();
    }
}
```

浏览器端最小示例（原生 WebSocket）：

```javascript
const ws = new WebSocket(`ws://${location.host}/ws`);
ws.onopen = () => ws.send("hello");
ws.onmessage = (event) => console.log("recv:", event.data);
```

启用 SockJS 时（`plugin.websocket.sock-js=true`）改用 SockJS 客户端连接同一路径：

```javascript
const sock = new SockJS("/ws");
sock.onmessage = (event) => console.log("recv:", event.data);
```

房间信令（WebRTC P2P）上行报文统一为 JSON，`type` 取 `hello` / `signal` / `heartbeat` / `leave`：

```json
{ "type": "hello", "roomId": "room-1", "slotId": "slot-a" }
```

服务端回执 `RoomMessage` 的 `type` 包含 `welcome`、`error`、`pong`、`left`、`peer-left` 等；`signal` 会把 offer / answer / candidate 转发给对端。

### 5.2 SPI 扩展示例

本模块不提供 SPI 扩展点（没有 `META-INF/extensions` 注册文件，也没有 `SocketProtocol` 别名）。

扩展方式是 **Spring Bean 列表注入**：新增一个 `WebSocketMessageHandler` 实现并交给容器，`WebSocketAutoConfiguration` 会用 `@Autowired(required = false) List<WebSocketMessageHandler>` 收集后传给 `DelegatingWebSocketHandler`，无需改配置。

## 六、与其它 starter 的关系

| 模块 | 关系 |
|---|---|
| `spring-support-socket-starter` | 仅 `optional` 依赖，用于复用 `SocketSession` / `SocketListener` / `@OnEvent` 类型；本模块不作为其 `SocketProvider` 实现，`SocketProtocol` 枚举里也没有 `websocket` 值 |
| `spring-support-socket-io-starter` | 平行实现：Socket.IO 独立监听端口，本模块复用应用端口；两者可同时存在，互不装配 |
| `spring-support-socket-rsocket-starter` | 平行实现，同样基于 Spring WebSocket 文本通道，但走 `plugin.socket` + SPI；与本模块无依赖关系 |
| `spring-support-socket-sse-starter` | 平行实现，走 `plugin.socket` + SPI，使用 MVC 的 `SseEmitter` |
| `spring-support-sync-starter` | 提供 `AbstractSyncServer`，本模块的 `WebsocketSyncServer` 继承它（该类未标注组件注解，本模块不会自动注册它） |

选择建议：需要浏览器原生 `WebSocket`、且希望与 Web 应用同端口时用本模块；需要 Socket.IO 协议兼容或独立端口时用 socket-io 实现。

## 七、已知限制

1. **只处理文本帧**：`DelegatingWebSocketHandler` 继承 `TextWebSocketHandler`，未覆写二进制消息处理，也不支持分片（`supportsPartialMessages()` 返回 `false`）。
2. **无 STOMP**：不启用 `@EnableWebSocketMessageBroker`，没有 `SimpMessagingTemplate`、`/topic`、`/queue` 订阅模型；需要 STOMP 请自行引入 Spring Messaging 并另写配置。
3. **无连接鉴权**：`WebSocketProperties` 没有 token / 认证相关配置，`HandshakeInterceptor` 也未注册；鉴权需自行加拦截器。
4. **无自动重连**：断线重连是客户端行为，服务端不提供会话恢复（房间信令的 `resumeToken` 只覆盖房间端点，不覆盖普通消息会话）。
5. **`RoomProperties` 未注册**：见 3.2 的提示，`room.*` 是否生效取决于宿主是否补 `@EnableConfigurationProperties`。
6. **房间信令不区分端点**：`RoomWebSocketHandler` 作为普通 `WebSocketMessageHandler` 注册，会在**所有** `plugin.websocket.endpoints` 上尝试解析房间报文，非房间流量会走 `parse.signal.failed` 分支并记日志。
7. **`room.enable` 不控制 Bean**：即使 `room.enable=false`，`roomService` / `roomWebSocketHandler` 仍会被创建（缺少条件注解）。
8. **单机会话**：`WebSocketSessionManager` 用本地 `ConcurrentHashMap`，多实例部署时 `broadcast` 只覆盖本进程连接，跨实例需自行接队列（可参考 socket-starter 的 `SocketQueueBridge` 思路）。
