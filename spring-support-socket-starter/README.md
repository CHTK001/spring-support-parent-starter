# spring-support-socket-starter

Socket 通信抽象层：只定义协议枚举、统一会话模型、SPI 发现机制与装配逻辑，自身不包含任何传输实现；具体协议由 `spring-support-socket-io-starter`、`spring-support-socket-rsocket-starter`、`spring-support-socket-sse-starter` 提供。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 协议装配 | `SocketConfiguration` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| 协议枚举 | `SocketProtocol`（`SOCKETIO` / `RSOCKET` / `SSE`） | `plugin.socket.protocol` |
| SPI 扩展点 | `SocketProvider` | `META-INF/extensions/com.chua.socket.support.spi.SocketProvider` |
| 统一会话操作 | `SocketSessionTemplate` | 注入 Bean（`@Primary` 默认模板） |
| 会话与用户模型 | `SocketSession`、`SocketUser` | `template.getSession(...)` / `session.getUser()` |
| 事件分发 | `EventListenerProcessor`、`@OnEvent` | 实现 `SocketListener` 的 Bean |
| 握手鉴权 | `SocketAuthFactory` | `plugin.socket.auth-factory` |
| 消息编解码 | `MsgEvent`/`MsgStep` 序列化 | 无独立 codec 类；消息加密已移除 |
| 消息载体 | `MsgEvent`、`MsgStep` | `broadcast(event, MsgEvent)` |
| 队列桥接 | `SocketQueueBridge` | `plugin.queue.enable=true`，订阅 topic `socket:push` |
| 服务端注册表 | `SocketServerRegistry`、`SocketServerController` | `GET /socket/servers`、`GET /socket/addresses` |
| 配置属性 | `SocketProperties` | `plugin.socket` |

自动配置实际产出的 Bean：

| Bean | 类型 | 条件 |
|---|---|---|
| `socketSessionTemplates` | `Map<String, SocketSessionTemplate>` | `plugin.socket.enable=true` 且容器内无同类型 Bean |
| `socketSessionTemplate` | `SocketSessionTemplate`（`@Primary`，取 map 中第一个） | `plugin.socket.enable=true` |
| `socketLifecycle` | `SocketLifecycle` | `plugin.socket.enable=true` |
| `socketServiceInfoContributor` | `ServiceInfoContributor` | 无条件 |

`SocketLifecycle` 在 `afterPropertiesSet()` 中对每个协议模板调用 `template.start()`，`destroy()` 中调用 `template.stop()`；`plugin.socket.auto-start=false` 时跳过启动但模板 Bean 仍会创建。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

版本取自父 POM `spring-support-parent-starter:4.0.0.51`（Java 25、Spring Boot 4.1.1）。

| 依赖 | 作用 | 是否可选 |
|---|---|---|
| `com.chua:spring-support-common-starter` | 日志、SPI 容器（`ServiceProvider`）、`ServiceInfoContributor` | 否 |
| `org.springframework.boot:spring-boot-starter` | Spring 基础容器 | 否 |
| `org.springframework.boot:spring-boot-configuration-processor` | 生成配置元数据 | 是（`optional`，编解码期） |
| `com.chua:spring-support-queue-starter` | `SocketQueueBridge` 的队列监听能力 | 是（`optional`） |
| `org.springframework.boot:spring-boot-starter-data-redis` | `SocketServerRegistry` 的动态注册 | 是（`optional`） |

本模块**不含**网络传输实现：只引入它并设置 `plugin.socket.enable=true`，会因找不到 `SocketProvider` 而抛 `IllegalStateException`。

## 三、配置项

前缀固定为 `plugin.socket`（`SocketProperties.PRE`），`ignoreInvalidFields = true`。

| key | 默认值 | 说明 |
|---|---|---|
| `enable` | `false` | 总开关；关闭时上述模板 Bean 都不创建 |
| `auto-start` | `true` | 是否在容器启动阶段自动调用 `start()` |
| `protocol` | `SSE` | 单协议模式；可选 `socketio` / `rsocket` / `sse` |
| `protocols` | `[]` | 多协议模式；非空时忽略 `protocol` 与 `room`，每种协议可独立配 `room` |
| `protocols[].protocol` | `SOCKETIO` | 该组协议类型 |
| `protocols[].room` | `[]` | 该组协议的房间（服务实例）列表 |
| `room[].client-id` | `default` | 服务实例标识 |
| `room[].context-path` | `/socket.io` | 上下文路径（Socket.IO 实现用作 `Configuration.context`） |
| `room[].port` | `-1` | `-1` 表示复用实现侧主端口（Socket.IO 实现固定取 9000）；`-2、-3…` 表示主端口 + 偏移 |
| `room[].host` | 空 | 为空则用 `plugin.socket.host` |
| `room[].auth-factory` | 空 | 该实例专属鉴权工厂 |
| `room[].enable` | `true` | 是否启用该实例 |
| `host` | `0.0.0.0` | 默认监听地址 |
| `codec-type` | `json` | 编解码类型 |
| `encrypt-enabled` | 未设置 | 是否加密（未设置时由 `encrypt-mode` 决定） |
| `encrypt-mode` | `AUTO` | `AUTO` / `ENCRYPTED` / `PLAIN` |
| `encrypt-key` | 空 | 加密密钥 |
| `max-frame-size` | `1048576` | 最大帧（字节） |
| `boss-count` | `1` | Boss 线程数 |
| `work-count` | `100` | 工作线程数 |
| `allow-custom-requests` | `true` | 是否允许自定义请求 |
| `ping-timeout` | `60000` | 心跳超时（毫秒）；SSE 实现把它当作连接超时 |
| `ping-interval` | `25000` | 心跳间隔（毫秒） |
| `auth-factory` | 空 | `SocketAuthFactory` 实现的全限定类名（实现模块负责加载） |
| `use-linux-native-epoll` | `false` | 是否启用 Linux Epoll |

示例（单协议）：

```yaml
plugin:
  socket:
    enable: true
    protocol: socketio
    room:
      - client-id: default
        port: 9000
        context-path: /socket.io
```

示例（多协议同时启用）：

```yaml
plugin:
  socket:
    enable: true
    protocols:
      - protocol: socketio
        room:
          - client-id: chat
            port: 9000
      - protocol: sse
        room:
          - client-id: push
            port: 9001
```

## 四、快速开始

**第一步：引入本模块 + 至少一个实现模块。**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-io-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

**第二步：配置开关、协议与 room。**

```yaml
plugin:
  socket:
    enable: true
    protocol: socketio
    room:
      - client-id: default
        port: 9000
```

**第三步：注入 `SocketSessionTemplate` 发消息。**

```java
@Service
public class PushService {
    private final SocketSessionTemplate template;

    public PushService(SocketSessionTemplate template) {
        this.template = template;
    }

    public void notifyAll(String text) {
        template.broadcast("notice", text);
    }
}
```

## 五、运行示例

### 5.1 调用示例

`SocketSessionTemplate` 是接口，**本模块无链式 API**，按调用步骤使用：

1. 注入 `SocketSessionTemplate`（`@Primary`，即第一个协议的模板）或注入 `Map<String, SocketSessionTemplate>` 按协议名取。
2. 单播：`send(sessionId, event, msg)`；对象重载 `sendObject(sessionId, event, obj)` 会用 `Json.toJson` 序列化。
3. 广播：`broadcast(event, msg)`；对象用 `broadcastObject(event, obj)`，`MsgEvent` / `MsgStep` 会走各自的 `toString()`。
4. 按用户：`sendToUser(userId, event, msg)`；是否真能送达取决于实现的 `supportsUserTargeting()`。
5. 查询：`getOnlineSessions()`、`getOnlineCount()`、`getOnlineSession(type, roomId)`、`getOnlineUsers(type)`。
6. 二进制：`sendBinary(sessionId, event, bytes)`、`broadcastBinary(event, bytes)`、`sendBinaryToUser(userId, event, bytes)`。
7. 生命周期：`start()` / `stop()`；健康判断用 `isReady()`、协议名用 `protocol()`。

```java
@Service
public class SessionQueryService {
    private final SocketSessionTemplate template;

    public SessionQueryService(SocketSessionTemplate template) {
        this.template = template;
    }

    public void report() {
        log.info("protocol={}, ready={}, online={}",
                template.protocol(), template.isReady(), template.getOnlineCount());
        template.getOnlineSessions().forEach(s -> template.send(s.getId(), "ping", "1"));
    }
}
```

事件处理（事件名必须与 `@OnEvent` 的 `value` 一致，实现模块把该事件注册为入站事件）：

```java
@Component
public class ChatListener implements SocketListener {

    @OnEvent("chat.send")
    public void onChat(SocketSession session, String payload) {
        log.info("session={}, payload={}", session.getId(), payload);
    }
}
```

`EventListenerProcessor` 支持 0~2 个参数：参数为 `SocketSession` 时注入会话，否则注入消息体；超过 2 个参数只记 WARN 不调用。

### 5.2 SPI 扩展示例

三步：**实现 → 登记 → 生效**。

> 下面的别名 `myproto` 仅演示注册机制：`protocol` 的类型是 `SocketProtocol` 枚举，真正可配置前必须先在枚举中增加 `MYPROTO("myproto")`，并让 `getProtocol()` 返回它。

1. 实现 `SocketProvider`，别名必须与 `SocketProtocol` 的 `value` 一致：

```java
@Spi("myproto")
public class MySocketProvider implements SocketProvider {

    @Override
    public SocketProtocol getProtocol() {
        return SocketProtocol.SSE; // 需同时扩展枚举才能配置 myproto
    }

    @Override
    public SocketSessionTemplate createSessionTemplate(SocketProperties properties, List<SocketListener> listeners) {
        return new MySessionTemplate(properties, listeners);
    }

    @Override
    public void start(SocketSessionTemplate template) {
        template.start();
    }

    @Override
    public void stop(SocketSessionTemplate template) {
        template.stop();
    }
}
```

2. 在实现模块登记 `src/main/resources/META-INF/extensions/com.chua.socket.support.spi.SocketProvider`（格式 `别名=实现类全名`）：

```properties
myproto=com.example.socket.MySocketProvider
```

3. 若使用 GraalVM native-image，同步补 `META-INF/native-image/<groupId>/<artifactId>/reflect-config.json`（参考 socket-io / sse 模块现成文件），否则反射构造会失败。

生效链路：`SocketConfiguration.socketSessionTemplates` → `ServiceProvider.of(SocketProvider.class).getNewExtension(协议名)` → `createSessionTemplate(...)` → `auto-start=true` 时 `provider.start(template)`。找不到实现会抛 `IllegalStateException("未找到协议 [xxx] 的 SPI 实现...")`。

> 注意：`protocol` / `protocols[].protocol` 的类型是 `SocketProtocol` 枚举，新增协议必须先在该枚举里加值，单靠 SPI 别名无法配置。

## 六、与其它 starter 的关系

依赖方向是单向的：本模块不依赖任何实现模块，实现模块依赖本模块。

```
spring-support-socket-io-starter ─┐
spring-support-socket-rsocket-starter ─┼──依赖──> spring-support-socket-starter（抽象层）
spring-support-socket-sse-starter ─┘                 │
                                                     └── SPI: SocketProvider
spring-support-socket-websocket-starter ──(optional)──┘（不走 SocketProtocol SPI）
```

| 模块 | 角色 | 协议别名 | 是否注册 `SocketProtocol` SPI |
|---|---|---|---|
| `spring-support-socket-starter` | 抽象层、装配与 SPI | — | 定义扩展点 |
| `spring-support-socket-io-starter` | Socket.IO 实现（netty-socketio，独立端口） | `socketio` | 是 |
| `spring-support-socket-rsocket-starter` | `rsocket` 别名实现（基于 Spring WebSocket 文本通道） | `rsocket` | 否（缺注册文件，见该模块 README） |
| `spring-support-socket-sse-starter` | SSE 实现（复用 MVC 端口） | `sse` | 是 |
| `spring-support-socket-websocket-starter` | 独立 WebSocket 实现（同应用端口） | 无 | 否（自带 `plugin.websocket` 配置，不参与 `plugin.socket` 装配） |

因此：`plugin.socket.protocol` 只能填 `socketio` / `rsocket` / `sse`；需要原生 WebSocket 时直接引入 `spring-support-socket-websocket-starter` 并配 `plugin.websocket`。

## 七、已知限制

1. **无传输实现**：`enable=true` 且 classpath 上没有实现模块时启动失败（`IllegalStateException`），而不是静默降级。
2. **`@OnConnect` / `@OnDisconnect` 未接线**：两个注解已定义，但 `EventListenerProcessor` 只扫描 `@OnEvent`，不要依赖连接/断开回调。
3. **事件参数上限 2 个**：超过 2 个参数只记 WARN，方法不会被调用。
4. **定向发送不保证**：`SocketSessionTemplate.supportsUserTargeting()` 默认 `false`，只有实现模块在握手阶段建立可信身份后才返回 `true`。
5. **`isReady()` 默认 `true`**：仅 Socket.IO 实现按实际服务端实例数判断；SSE / RSocket 未覆盖该默认值，端口未就绪也可能报就绪。
6. **Socket 消息加密已移除**：`SocketMessageCodec` 从未真正加密（依赖无实现的 `CodecKeyPair`，且会把私钥塞进报文），已连同 `SocketEncryptMode`、`encrypt-*` 配置一起删除。需要消息机密性请走 TLS，或用 `SessionCipher` 自定义会话层。
7. **服务端注册表依赖 Redis 与组件扫描**：`SocketServerRegistry` 为 `@Service`、构造器要求 `RedisTemplate<String, Object>`，`getDatabaseServers()` 默认返回空列表；`SocketServerController`（`/socket/*`）与本模块 `@ComponentScan(basePackages = "com.chua.socket.support.bridge")` 不同包，需宿主组件扫描覆盖 `com.chua.socket.support.controller` 才会暴露。
8. **队列桥接另有开关**：`SocketQueueBridge` 受 `plugin.queue.enable=true` 约束，且只在 `SocketSessionTemplate` 可用时广播。
