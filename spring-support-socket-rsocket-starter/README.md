# spring-support-socket-rsocket-starter

`rsocket` 协议别名的实现模块：用 Spring WebSocket 文本通道承载 JSON 事件（`{event, data, timestamp}`），向 `spring-support-socket-starter` 提供 `SocketSessionTemplate`；**不是** RSocket 二进制协议与 Reactor 响应式栈。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| SPI 实现 | `RSocketProvider`（`@Spi("rsocket")`） | `SocketProvider` 扩展点 |
| 会话模板 | `RSocketSessionTemplateImpl`（`TextWebSocketHandler` + `SocketSessionTemplate`） | 由 `SocketLifecycle` 调 `start()` |
| 会话模型 | `RSocketSessionImpl`（包装 `WebSocketSession`） | `template.getSession(sessionId)` |
| 入站事件分发 | `EventListenerProcessor` + `@OnEvent` | `SocketListener` Bean |
| 客户端分组 | 从连接路径提取 `clientId`（`/ws/webrtc` → `webrtc`） | `afterConnectionEstablished` |
| 文本发送 | `RSocketSessionImpl.send(event, data)`、`sendRaw(message)` | 会话方法 |
| 同步协议适配 | `RsocketSyncServer`（继承 `AbstractSyncServer`） | 未标注组件注解，本模块不自动注册 |

本模块**没有** Spring 自动配置类（`AutoConfiguration.imports` 仅一行说明注释），也没有 `@ConfigurationProperties`。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-rsocket-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 是否可选 |
|---|---|---|
| `com.chua:spring-support-socket-starter` | 抽象层、SPI、`SocketProperties` | 否 |
| `org.springframework.boot:spring-boot-starter-websocket` | `TextWebSocketHandler` / `WebSocketSession` | 否 |
| `com.chua:spring-support-sync-starter` | `AbstractSyncServer` 基类 | 否 |
| `com.chua:utils-support-common-starter` | 公共工具 | 否 |

**没有** `io.rsocket:rsocket-core`、**没有** `projectreactor`、**没有** `utils-support-rsocket-starter` 依赖；本模块不提供背压、四种交互模式（request-response / fire-and-forget / request-stream / channel）等 RSocket 语义。

## 三、配置项

本模块**无 `@ConfigurationProperties`**，接收抽象层传入的 `SocketProperties`（前缀 `plugin.socket`）。

| key | 默认值 | 本模块是否读取 |
|---|---|---|
| `enable` | `false` | 是（抽象层据此决定是否创建模板） |
| `auto-start` | `true` | 是（抽象层决定是否调 `start()`） |
| `protocol` | `SSE` | 需设为 `rsocket` 才会选中本实现 |
| `room` | `[]` | 否 |
| `host` / `ping-interval` / `ping-timeout` | `0.0.0.0` / `25000` / `60000` | 否 |
| `boss-count` / `work-count` / `max-frame-size` | `1` / `100` / `1048576` | 否 |
| `auth-factory` | 空 | 否 |
| `codec-type` / `encrypt-*` | `json` / 未设置 | 否 |

`RSocketSessionTemplateImpl` 只保存 `SocketProperties` 引用，实现中未读取任何字段；改这些配置不会影响本实现的行为。

示例（当前仅能表达"选择该实现"，端口与端点由宿主的 `WebSocketHandler` 注册决定）：

```yaml
plugin:
  socket:
    enable: true
    protocol: rsocket
    auto-start: true
```

## 四、快速开始

**第一步：引入依赖。**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-rsocket-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

**第二步：配置协议。**

```yaml
plugin:
  socket:
    enable: true
    protocol: rsocket
```

**第三步：把模板注册为 WebSocket 端点（当前必须由宿主完成）。**

```java
@Configuration
@EnableWebSocket
public class RsocketEndpointConfig implements WebSocketConfigurer {

    private final SocketSessionTemplate template;

    public RsocketEndpointConfig(SocketSessionTemplate template) {
        this.template = template;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler((RSocketSessionTemplateImpl) template, "/ws/*")
                .setAllowedOrigins("*");
    }
}
```

`start()` 只把内部 `running` 置为 `true` 并打日志，不会自行绑定端口或注册端点；不完成上面的注册，客户端无处可连。

## 五、运行示例

### 5.1 调用示例

**本模块无链式 API**，按调用步骤使用：

1. 注入 `SocketSessionTemplate`（抽象层 `@Primary`）或强转为 `RSocketSessionTemplateImpl` 使用端点注册。
2. 发送：`send(sessionId, event, msg)` 走会话 `send(event, data)`，序列化为 `{"event":...,"data":...,"timestamp":...}` 后作为文本帧写出。
3. 广播：`broadcast(event, msg)` 遍历全部已登记会话逐个发送。
4. 按用户：`sendToUser(userId, event, msg)` 遍历会话比较 `session.getUser().getUserId()`；本实现未在连接时建立用户身份，实际很难命中。
5. 查询：`getOnlineSessions()`、`getOnlineCount()`、`getOnlineSession(type, roomId)`、`getOnlineUsers(type)`。
6. 原始文本：强转 `RSocketSessionImpl` 后调 `sendRaw(message)`。

```java
@Service
public class MessageService {

    private final SocketSessionTemplate template;

    public MessageService(SocketSessionTemplate template) {
        this.template = template;
    }

    public void push(String text) {
        template.broadcast("notice", text);
    }
}
```

事件监听（客户端发 `{"event":"chat.message","data":"hi"}` 时触发）：

```java
@Component
public class EventHandler implements SocketListener {

    @OnEvent("chat.message")
    public void onChat(SocketSession session, Object data) {
        log.info("session={}, data={}", session.getId(), data);
    }

    @OnEvent("user.login")
    public void onLogin(SocketSession session, Map<String, Object> data) {
        session.setAttribute("username", data.get("username"));
    }
}
```

客户端分组由路径决定：连接 `/ws/webrtc` 时 `clientId` 为 `webrtc`，`/ws` 或 `/` 归为 `default`；`getOnlineSession(type, roomId)` 用 `type` 作为 `clientId` 取分组，`roomId` 非空时再按会话属性 `roomId` 过滤——而该属性从未被写入，所以传了 `roomId` 通常返回空列表。

### 5.2 SPI 扩展示例

本模块不提供 SPI 扩展点：没有自己的 `META-INF/extensions` 目录，也不接受自定义别名。

它自身是抽象层 `SocketProvider` 的一个实现（`RSocketProvider` 标注 `@Spi("rsocket")`），但**未在 `META-INF/extensions/com.chua.socket.support.spi.SocketProvider` 中登记**，因此 `ServiceProvider` 的 `META-INF/extensions` 与同包扫描都发现不到它——详见第七节第 1 条。

## 六、与其它 starter 的关系

```
spring-support-socket-rsocket-starter
    ├── spring-support-socket-starter     （抽象层：SocketProvider / SocketSessionTemplate）
    ├── spring-support-sync-starter       （同步协议基类）
    └── spring-boot-starter-websocket     （文本通道，不引入 rsocket-core）
```

| 模块 | 与本模块的关系 |
|---|---|
| `spring-support-socket-starter` | 上游抽象层；`protocol=rsocket` 时按别名查找本模块的 `RSocketProvider` |
| `spring-support-socket-io-starter` | 兄弟实现（`socketio`）；Socket.IO 有房间、鉴权、独立端口，本模块都没有，不要按 Socket.IO 的配置项来写 |
| `spring-support-socket-sse-starter` | 兄弟实现（`sse`）；单向推送，本模块是双向文本通道 |
| `spring-support-socket-websocket-starter` | 同用 Spring WebSocket，但配置前缀是 `plugin.websocket`、自带端点注册与 `WebSocketTemplate`，与本模块无装配关系 |

能力边界：本模块只做「WebSocket 文本帧 + JSON 事件名分发」，不具备 RSocket 的二进制帧、背压、连接复用与响应式流。

## 七、已知限制

1. **SPI 未登记**：仓库内缺少 `spring-support-socket-rsocket-starter/src/main/resources/META-INF/extensions/com.chua.socket.support.spi.SocketProvider`。按 `protocol=rsocket` 启动时 `SocketConfiguration` 会抛 `IllegalStateException("未找到协议 [rsocket] 的 SPI 实现...")`；补齐一行 `rsocket=com.chua.rsocket.support.spi.RSocketProvider`（与 socket-io / sse 一致）后才会被发现。
2. **不是真正的 RSocket**：实现基于 `TextWebSocketHandler`，`pom.xml` 中没有 `rsocket-core` / Reactor；`pom` 的 description 写的是「RSocket 响应式通信实现」，实际能力以本节为准。
3. **`start()` 不监听端口**：只翻转 `running` 标记，端点需宿主用 `WebSocketHandlerRegistry` 注册；也不存在独立的 `host` / `port` 配置生效路径。
4. **配置项全部不生效**：`SocketProperties` 仅被保存、未被读取，`room`、`max-frame-size`、`ping-*`、`auth-factory`、`encrypt-*` 对本实现无影响。
5. **`protocol()` / `isReady()` / `supportsUserTargeting()` 用默认值**：分别返回 `unknown` / `true` / `false`，健康检查与定向发送能力判断都不可靠。
6. **无鉴权**：没有握手校验逻辑，连接建立即登记会话。
7. **不注册连接/断开回调**：`@OnConnect`、`@OnDisconnect` 注解不会被扫描；需要在 `afterConnectionEstablished` / `afterConnectionClosed` 之外自行扩展。
8. **仅文本帧**：未处理二进制消息，`sendBinary` 走抽象层默认实现（Base64 转文本）。
9. **会话仅存内存**：`sessionCache` 为本地 `ConcurrentHashMap`，多实例不共享，重启即失联。
