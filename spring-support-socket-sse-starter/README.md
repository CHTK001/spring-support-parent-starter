# spring-support-socket-sse-starter

SSE（Server-Sent Events）实现：把 Spring MVC 的 `SseEmitter` 包装成 `SocketSessionTemplate`，向 `spring-support-socket-starter` 注册 `sse` 协议，复用 Web 应用端口做服务端单向推送。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| SPI 实现 | `SseSocketProvider`（`@Spi("sse")`） | `META-INF/extensions/com.chua.socket.support.spi.SocketProvider` |
| 统一会话模板 | `SseSocketSessionTemplate`（实现 `SocketSessionTemplate`） | `plugin.socket.protocol=sse` |
| 会话模型 | `SseSocketSession`（包装 `SseEmitter`） | `template.getSession(clientId)` |
| 连接管理 | `createConnection(clientId)` | `/sse/connect`（若控制器已注册） |
| 事件发送模板 | `SseTemplate` | 自动配置导入的 Bean |
| 事件总线 | `DefaultSseEventBusConfigurer` | 自动配置导入的 Bean |
| 消息模型 | `SseMessage`、`SseMessageType`、`SseMessageStatus` | `SseTemplate.emit(...)` |
| 订阅者模型 | `Emitter`（`@Builder`，可携带多个事件名） | `SseTemplate.createSseEmitter(...)` |
| 消息类型/状态 | `SseMessageType`、`SseMessageStatus` 枚举 | `SseMessage` 字段 |
| 服务信息 | `SseServiceInfoConfiguration` | 服务信息面板标记 `sse` |
| native-image | `reflect-config.json`、`resource-config.json` | `META-INF/native-image/com.chua/spring-support-socket-sse-starter` |

`AutoConfiguration.imports` 导入三个类：`SseTemplate`、`DefaultSseEventBusConfigurer`、`SseServiceInfoConfiguration`（前两个类本身没有 `@Configuration` 注解，按导入类注册为 Bean）。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-sse-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 是否可选 |
|---|---|---|
| `com.chua:utils-support-common-starter` | 公共工具 | 否 |
| `com.chua:spring-support-socket-starter` | 抽象层、SPI、`SocketProperties` | 是（`optional`） |
| `org.springframework:spring-webmvc` | `SseEmitter`、`@RestController` | 是（`provided`，由宿主提供） |
| `jakarta.servlet:jakarta.servlet-api` | Servlet API | 是（`provided`） |

没有 Web 容器（Servlet 环境）时 `SseEmitter` 无法工作；WebFlux 栈不在本模块支持范围内。

## 三、配置项

本模块**无 `@ConfigurationProperties`**，复用 `spring-support-socket-starter` 的 `SocketProperties`（前缀 `plugin.socket`）。

| key | 默认值 | 在本模块中的作用 |
|---|---|---|
| `enable` | `false` | 总开关；关闭则不创建会话模板 |
| `auto-start` | `true` | 是否随容器启动调用 `start()`（本实现只打日志） |
| `protocol` | `SSE` | 需为 `sse`（默认值恰好就是 `SSE`） |
| `ping-timeout` | `60000` | **被当作 SSE 连接超时**；`>0` 时传给 `SseEmitter(timeout)`，否则用 30 分钟 |
| `room` | `[]` | 不读取（SSE 无端口/房间概念） |
| `host`、`boss-count`、`work-count`、`max-frame-size` | — | 不读取 |
| `auth-factory` | 空 | 不读取 |
| `encrypt-*`、`codec-type` | — | 不读取 |

> 旧版 README 里的 `plugin.sse.enable`、`plugin.sse.heartbeat-interval`、`plugin.sse.timeout`、`plugin.sse.max-connections` 在本模块中**不存在**，配置了也不会生效。

示例：

```yaml
plugin:
  socket:
    enable: true
    protocol: sse
    ping-timeout: 1800000   # SSE 连接超时 30 分钟
```

## 四、快速开始

**第一步：引入依赖。**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-sse-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

**第二步：开启 sse 协议。**

```yaml
plugin:
  socket:
    enable: true
    protocol: sse
```

**第三步：注入 `SocketSessionTemplate` 推送。**

```java
@RestController
@RequestMapping("/demo")
public class DemoController {

    private final SocketSessionTemplate template;

    public DemoController(SocketSessionTemplate template) {
        this.template = template;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String clientId) {
        SseEmitter emitter = ((SseSocketSessionTemplate) template).createConnection(clientId);
        template.broadcast("notice", "hello");
        return emitter;
    }
}
```

客户端用原生 `EventSource` 订阅 `GET /demo/stream` 即可。

## 五、运行示例

### 5.1 调用示例

**本模块无链式 API**，按调用步骤使用；`SseEmitter` 需由 Controller 方法直接返回。

1. 建连：`SseSocketSessionTemplate.createConnection(clientId)` 返回 `SseEmitter`，`clientId` 为空时自动生成 uuid，并立刻推送一个 `connected` 事件（载荷含 `clientId`、`timestamp`、`message`）。
2. 单播：`send(sessionId, event, msg)`，`sessionId` 即 `clientId`。
3. 广播：`broadcast(event, msg)`，发送失败的连接会被自动剔除。
4. 绑定用户后定向：`bindUser(userId, clientId)` → `sendToUser(userId, event, msg)`；`unbindUser(userId)` 解绑。
5. 状态：`getConnectionStatus()` 返回 `totalConnections` / `onlineCount` / `timestamp`。
6. 断连：`remove(clientId, session)` 主动关闭。

```java
@Service
public class PushService {

    private final SseSocketSessionTemplate template;

    public PushService(SseSocketSessionTemplate template) {
        this.template = template;
    }

    public void pushToUser(String userId, String text) {
        template.sendToUser(userId, "notice", text);
    }

    public Map<String, Object> status() {
        return template.getConnectionStatus();
    }
}
```

使用自动配置的事件总线（多事件订阅、可指定重试时间）：

```java
@RestController
@RequestMapping("/sse")
public class SseBusController {

    private final SseTemplate sseTemplate;

    public SseBusController(SseTemplate sseTemplate) {
        this.sseTemplate = sseTemplate;
    }

    @GetMapping(value = "/bus", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam String clientId) {
        Emitter emitter = Emitter.builder()
                .clientId(clientId)
                .event("notice")
                .event("alarm")
                .build();
        return sseTemplate.createSseEmitter(emitter);
    }

    @PostMapping("/publish")
    public void publish(@RequestParam String clientId, @RequestParam String text) {
        SseMessage message = SseMessage.builder()
                .message(text)
                .event("notice")
                .build();
        sseTemplate.emit(message, Duration.ofSeconds(3), clientId);
    }
}
```

前端订阅：

```javascript
const es = new EventSource(`/sse/bus?clientId=u1`);
es.addEventListener("notice", (e) => console.log("notice:", e.data));
es.addEventListener("connected", (e) => console.log("connected:", e.data));
es.onerror = () => console.warn("SSE 断开，浏览器会自动重连");
```

### 5.2 SPI 扩展示例

本模块通过抽象层的 `SocketProvider` 扩展点接入，链路为三步：

1. **实现**：`SseSocketProvider` 标注 `@Spi("sse")`，`getProtocol()` 返回 `SocketProtocol.SSE`，`createSessionTemplate(properties, listeners)` 用 `properties.getPingTimeout()` 计算超时后 `new SseSocketSessionTemplate(timeout)`。
2. **登记**：`src/main/resources/META-INF/extensions/com.chua.socket.support.spi.SocketProvider` 内容为

```properties
sse=com.chua.starter.sse.support.socket.SseSocketProvider
```

3. **生效**：`plugin.socket.protocol=sse` 时 `SocketConfiguration` 通过 `ServiceProvider` 取到该 provider 并创建模板；`auto-start=true` 时调用 `start()`。

> 该注册文件缺失时协议不会被发现，`SocketConfiguration` 会抛 `IllegalStateException("未找到协议 [sse] 的 SPI 实现...")`，所有依赖 `SocketSessionTemplate` 的 Bean 随之失败。新增协议实现时务必同步补 `META-INF/extensions/<接口全名>` 与 `META-INF/native-image/**` 下的 `resource-config.json` / `reflect-config.json`。

业务侧扩展不需要动 SPI：新增 `SocketListener` + `@OnEvent` 或直接注入 `SocketSessionTemplate` 即可；本模块的入站方向没有协议帧（SSE 是单向推送）。

## 六、与其它 starter 的关系

```
spring-support-socket-sse-starter
    ├── spring-support-socket-starter   （抽象层：SocketProvider / SocketSessionTemplate）
    ├── spring-webmvc (provided)        （SseEmitter）
    └── spring-support-socket-starter 的 SocketLifecycle 统一 start/stop
```

| 模块 | 与本模块的关系 |
|---|---|
| `spring-support-socket-starter` | 上游抽象层；`protocol=sse` 时按别名查找 `SseSocketProvider`；`SocketQueueBridge` 可把 `socket:push` 队列消息广播到 SSE 连接 |
| `spring-support-socket-io-starter` | 兄弟实现（`socketio`）；双向、独立端口、有房间与鉴权，本模块只有单向推送 |
| `spring-support-socket-rsocket-starter` | 兄弟实现（`rsocket`）；双向文本通道 |
| `spring-support-socket-websocket-starter` | 独立实现（`plugin.websocket`、原生 WebSocket、同端口），不参与 `plugin.socket`，与本模块互不依赖 |
| `spring-support-queue-starter` | 可选：`plugin.queue.enable=true` 时队列消息经抽象层桥接广播 |

本模块只提供：SSE 连接管理 + 事件推送 + 用户绑定 + 与抽象层模板的适配。不提供心跳保活、连接数上限、`plugin.sse` 专属配置。

## 七、已知限制

1. **单向通道**：SSE 只能服务端 → 客户端；客户端上行需另开 HTTP 接口。
2. **无心跳**：`SseSocketSessionTemplate` 只有创建/发送/清理逻辑，没有定时心跳；`connected` 事件只在建连时发一次，中间层（代理、网关）空闲超时可能切断连接。
3. **超时来自 `ping-timeout`**：默认 `60000`（60 秒），比常见反代超时短；`SseSocketProvider` 在 `ping-timeout<=0` 时回落到 30 分钟。`SseTemplate.createSseEmitter` 则固定 `new SseEmitter(0L)`（永不超时）。
4. **无连接上限**：`max-connections` 之类配置不存在，连接表是 `ConcurrentHashMap`，规模由服务端内存与容器线程决定。
5. **`getOnlineSession(type, roomId)` 忽略参数**：SSE 无房间概念，直接返回全部在线会话。
6. **`protocol()` / `isReady()` / `supportsUserTargeting()` 用默认值**：分别返回 `unknown` / `true` / `false`；`sendToUser` 实际可用（靠 `userSessionMap`），但能力标记未同步。
7. **控制器可能未注册**：`SseSocketController`（`/sse/connect`、`/sse/status`、`/sse/bind`、`/sse/disconnect`）只带 `@ConditionalOnBean`，不在 `AutoConfiguration.imports` 中；宿主组件扫描未覆盖 `com.chua.starter.sse.support.socket` 时这些端点不存在，需自己写 `@GetMapping` 返回 `createConnection(...)`。
8. **事件总线为静态缓存**：`DefaultSseEventBusConfigurer.SSE_CACHE` 是 `static`，同 JVM 内多份 `SseTemplate` 共享同一批连接；测试或多上下文场景需注意隔离。
9. **`SseTemplate` / `DefaultSseEventBusConfigurer` 无 `@Configuration`**：仅靠 `AutoConfiguration.imports` 导入，若宿主关闭相关自动配置或自定义覆盖，需自行声明 Bean。
