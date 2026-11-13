# spring-support-socket-io-starter

Socket.IO 协议实现：基于 `netty-socketio` 为 `spring-support-socket-starter` 提供 `socketio` 协议，独立监听端口，按 `room` 配置为每个客户端标识启动一个 Socket.IO 服务实例。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| SPI 注册 | `SocketIOProvider`（`@Spi("socketio")`） | `META-INF/extensions/com.chua.socket.support.spi.SocketProvider` |
| 会话模板 | `SocketIOSessionTemplate`（实现 `SocketSessionTemplate`） | `plugin.socket` + `room` 配置 |
| 服务端封装 | `DelegateSocketIOServer`（包装 netty-socketio `Configuration`） | 每个 room 一个实例 |
| 会话模型 | `SocketIOSession`（消息编解码 / 加密） | `template.getSession(sessionId)` |
| 入站事件 | `EventListenerProcessor` + `@OnEvent` | `SocketListener` Bean |
| 握手鉴权 | `SocketAuthFactory` | `plugin.socket.auth-factory` |
| 主题常量 | `SocketTopics` | 业务事件命名参考 |
| 同步协议适配 | `SocketioSyncServer`（继承 `AbstractSyncServer`） | 未标注组件注解，本模块不自动注册 |
| native-image | `reflect-config.json`、`resource-config.json` | `META-INF/native-image/com.chua/spring-support-socket-io-starter` |

本模块**不注册任何 Spring 自动配置类**（`AutoConfiguration.imports` 里只有一行说明注释），所有 Bean 由 `spring-support-socket-starter` 的 `SocketConfiguration` 统一创建。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-io-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 是否可选 |
|---|---|---|
| `com.chua:spring-support-socket-starter` | 抽象层、SPI 发现、`SocketProperties`、`SocketLifecycle` | 否 |
| `com.corundumstudio.socketio:netty-socketio:2.0.14` | Socket.IO 服务端 | 否 |
| `com.chua:spring-support-sync-starter` | `AbstractSyncServer` 基类 | 否 |
| `com.chua:utils-support-common-starter` | 公共工具 | 否 |

引入本模块后并不会自动开启 Socket.IO：必须设置 `plugin.socket.enable=true` 并配置 `room`，否则 `SocketIOSessionTemplate.start()` 直接返回（`isReady()` 为 `false`）。

## 三、配置项

本模块**没有自己的 `@ConfigurationProperties`**，全部复用 `spring-support-socket-starter` 的 `SocketProperties`（前缀 `plugin.socket`）。

### 3.1 通用项（对本模块生效的部分）

| key | 默认值 | 在本模块中的作用 |
|---|---|---|
| `enable` | `false` | 总开关；关闭则不创建会话模板 |
| `auto-start` | `true` | 是否随容器启动自动 `start()` |
| `protocol` | `SSE` | 必须设为 `socketio`（或 `protocols[].protocol=socketio`） |
| `room` | `[]` | **必填**：每个元素启动一个服务实例 |
| `room[].client-id` | `default` | 实例标识，同时作为服务端 map 的 key |
| `room[].port` | `-1` | `>=0` 用该端口；`-1` → 9000；`-2` → 9001（负数以 9000 为基） |
| `room[].context-path` | `/socket.io` | 写入 `Configuration.setContext(...)` |
| `room[].host` | 空 | 为空则用 `plugin.socket.host` |
| `room[].auth-factory` | 空 | 该实例专属鉴权工厂 |
| `room[].enable` | `true` | `false` 时跳过该实例 |
| `host` | `0.0.0.0` | 默认监听地址 |
| `boss-count` | `1` | `Configuration.setBossThreads` |
| `work-count` | `100` | `Configuration.setWorkerThreads` |
| `allow-custom-requests` | `true` | `Configuration.setAllowCustomRequests` |
| `ping-interval` | `25000` | 心跳间隔（毫秒） |
| `ping-timeout` | `60000` | 心跳超时（毫秒） |
| `max-frame-size` | `1048576` | 同时作为 `maxFramePayloadLength` 与 `maxHttpContentLength` |
| `use-linux-native-epoll` | `false` | 是否启用 Linux Epoll |
| `auth-factory` | 空 | 握手鉴权工厂全限定类名 |
| `codec-type` | `json` | 保留字段；消息加密与 `SocketMessageCodec` 已移除 |

其余配置项（`protocols` 多协议分组等）由抽象层解析后以独立 `SocketProperties` 实例传入，本模块每次只处理一组 `room`。

### 3.2 配置示例

```yaml
plugin:
  socket:
    enable: true
    protocol: socketio
    host: 0.0.0.0
    boss-count: 1
    work-count: 100
    ping-interval: 25000
    ping-timeout: 60000
    max-frame-size: 1048576
    room:
      - client-id: default
        port: 9092
        context-path: /socket.io
      - client-id: webrtc
        port: -2            # 9093
        context-path: /webrtc
        enable: true
```

多协议同时启用时改用 `protocols`：

```yaml
plugin:
  socket:
    enable: true
    protocols:
      - protocol: socketio
        room:
          - client-id: chat
            port: 9092
```

## 四、快速开始

**第一步：引入依赖。**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-io-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

**第二步：配置 `plugin.socket`（`protocol: socketio` + 至少一个 `room`）。** 见 3.2。

**第三步：写一个 `@OnEvent` 监听器 Bean。**

```java
@Component
public class ChatListener implements SocketListener {

    @OnEvent("chat")
    public void onChat(SocketSession session, String data) {
        log.info("clientId={}, data={}", session.getClientId(), data);
    }
}
```

前端用 socket.io 客户端连接 `http://<host>:9092`（默认上下文 `/socket.io`），`socket.emit("chat", "hello")` 即可触发上面的方法。

## 五、运行示例

### 5.1 调用示例

`SocketSessionTemplate` 是接口，**本模块无链式 API**。注入 `SocketIOSessionTemplate` 可获得 Socket.IO 专属方法：

1. 注入 `SocketSessionTemplate`（抽象层 `@Primary`）或直接注入 `SocketIOSessionTemplate`。
2. `broadcast(event, msg)` / `send(sessionId, event, msg)` / `sendToUser(userId, event, msg)` 发消息（`sendToUser` 依赖握手鉴权建身份）。
3. `getOnlineSessions()` / `getOnlineCount()` 查在线状态。
4. `protocol()` 返回 `socketio`；`isReady()` 返回是否至少有一个服务端实例启动成功；`supportsUserTargeting()` 返回是否配置了 `auth-factory`。
5. `getServer(clientId)` / `getServers()` 取底层 `DelegateSocketIOServer`（做定制时使用）。
6. 生命周期由抽象层 `SocketLifecycle` 调 `start()` / `stop()`，一般无需手动调用。

```java
@Service
public class NotifyService {

    private final SocketSessionTemplate template;

    public NotifyService(SocketSessionTemplate template) {
        this.template = template;
    }

    public void push(String text) {
        if (!template.isReady()) {
            log.warn("Socket.IO 尚未就绪，跳过推送");
            return;
        }
        template.broadcast("notice", text);
    }
}
```

自定义握手鉴权（只有实现了 `getUser` 且返回带 `userId` 的用户才算通过；工厂加载失败时**拒绝所有连接**）：

```java
public class TokenAuthFactory implements SocketAuthFactory {

    @Override
    public boolean isAuthorized(Map<String, Object> handshakeData) {
        return getUser(handshakeData) != null;
    }

    @Override
    public SocketUser getUser(Map<String, Object> handshakeData) {
        String token = String.valueOf(handshakeData.get("token"));
        if (token == null || token.isBlank()) {
            return null;
        }
        SocketUser user = new SocketUser();
        user.setUserId(token);
        return user;
    }
}
```

```yaml
plugin:
  socket:
    enable: true
    protocol: socketio
    auth-factory: com.example.socket.TokenAuthFactory
    room:
      - client-id: default
        port: 9092
```

### 5.2 SPI 扩展示例

本模块自身是 `SocketProvider` 的一个实现，注册链路为三步：

1. **实现**：`SocketIOProvider` 标注 `@Spi("socketio")` 并实现 `SocketProtocol getProtocol()`、`createSessionTemplate(...)`、`start(...)`、`stop(...)`。
2. **登记**：`src/main/resources/META-INF/extensions/com.chua.socket.support.spi.SocketProvider` 内容为

```properties
socketio=com.chua.socketio.support.spi.SocketIOProvider
```

3. **生效**：`SocketConfiguration` 用 `ServiceProvider.of(SocketProvider.class).getNewExtension("socketio")` 取到该 provider，再 `createSessionTemplate(properties, listeners)`；`auto-start=true` 时立刻 `start()`。

配套的 native-image 文件（已有，新增 SPI 实现时照抄结构）：

```
src/main/resources/META-INF/native-image/com.chua/spring-support-socket-io-starter/reflect-config.json
src/main/resources/META-INF/native-image/com.chua/spring-support-socket-io-starter/resource-config.json
```

如果只是扩展业务事件，不需要动 SPI：新增 `SocketListener` Bean + `@OnEvent("事件名")` 即可，`SocketIOSessionTemplate` 会把注解里的事件名注册为 netty-socketio 的入站事件（载荷按 `String.class` 接收）。

## 六、与其它 starter 的关系

依赖方向（单向，本模块 → 抽象层）：

```
spring-support-socket-io-starter
    ├── spring-support-socket-starter   （抽象层：SocketProvider / SocketSessionTemplate / SocketProperties）
    ├── spring-support-sync-starter     （同步协议基类）
    └── netty-socketio 2.0.14           （协议栈）
```

| 模块 | 与本模块的关系 |
|---|---|
| `spring-support-socket-starter` | 上游抽象层；本模块向其注册 `socketio` 协议，生命周期、`@Primary` 模板、队列桥接都由它统一管理 |
| `spring-support-socket-sse-starter` | 兄弟实现（`sse` 别名）；互不依赖，可同时启用（不同 `protocols` 分组） |
| `spring-support-socket-rsocket-starter` | 兄弟实现（`rsocket` 别名）；互不依赖 |
| `spring-support-socket-websocket-starter` | 独立实现：用 `plugin.websocket` 配置、同应用端口、不参与 `plugin.socket`，与本模块无装配关系 |
| `spring-support-queue-starter` | 通过抽象层的 `SocketQueueBridge` 把 `socket:push` 队列消息广播到本模块会话（需 `plugin.queue.enable=true`） |

本模块的能力边界：提供 Socket.IO 协议（含房间、轮询/WebSocket 传输、握手鉴权、心跳）。不提供 SSE 推送、不提供原生 RSocket 语义、也不处理 `plugin.websocket` 配置。

## 七、已知限制

1. **`room` 必填**：没有配 `room` 时 `start()` 只打印 `未配置 room，跳过服务启动`，一个端口都不会监听，`isReady()` 返回 `false`。
2. **负数端口基准写死 9000**：`room[].port=-1` 得到 9000、`-2` 得到 9001，与 `plugin.socket.host` 无关；要自定义端口请写正数。
3. **`@OnConnect` / `@OnDisconnect` 不生效**：抽象层只扫描 `@OnEvent`，连接/断开逻辑需通过 `template.getServer(clientId).addConnectListener(...)` 自行挂载。
4. **入站载荷固定为 `String`**：`registerEventListeners` 以 `String.class` 注册事件，客户端发送对象时框架侧会做字符串转换，复杂结构请在监听器内自行反序列化。
5. **定向发送需要鉴权**：`sendToUser` 依赖握手阶段写入的 `SocketUser`；未配置 `auth-factory` 时 `supportsUserTargeting()` 为 `false`，不要假设一定能送达。
6. **鉴权失败关闭**：配置了 `auth-factory` 但类加载失败、或 `getUser` 返回空 `userId`，连接会被拒绝（`FAILED_AUTHORIZATION`），不会降级为匿名。
7. **端口占用直接抛错**：任一 room 启动失败会抛 `RuntimeException("Socket.IO 服务启动失败: ...")`，导致容器启动失败，不会跳过该实例。
8. **与应用不同端口**：默认监听 9092 这类独立端口，前端需要额外的跨域与地址配置；跨实例广播需自行接队列。
