# spring-support-control-starter

WebRTC 远程控制模块：把 `utils-support-webrtc-starter` 的信令服务、桌面采集与键鼠注入
装配成 Spring Bean，并对外提供 `/control/**` 的会话 / 屏幕 / 配置辅助接口。

## 一、模块能力

| 能力域 | 关键类 | 入口（REST 路径或 SPI） |
|---|---|---|
| 自动配置装配 | `com.chua.starter.control.configuration.ControlAutoConfiguration` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| 信令服务装配 | `ControlAutoConfiguration#webRtcServer(ControlProperties)` | Spring Bean `WebRtcServer`（`destroyMethod = "stop"`） |
| 会话列表 | `com.chua.starter.control.controller.ControlController#sessions` | `GET /control/sessions` |
| 创建会话 | `ControlController#createSession` | `POST /control/session/create?sessionId=...` |
| 销毁会话 | `ControlController#destroySession` | `DELETE /control/session/destroy/{sessionId}` |
| 桌面采集源列表 | `ControlController#screens` | `GET /control/screens` |
| 信令地址与配置下发 | `ControlController#config` | `GET /control/config` |
| 控制事件兜底通道 | `ControlController#sendEvent` | `POST /control/event/send?sessionId=...&peerId=...` |
| WebSocket 信令 | `com.chua.webrtc.server.WebRtcServer`（`SIGNALING_PATH = "/webrtc"`） | `ws://{host}:{port}/webrtc`（端口取 `control.webrtc.port`） |
| 会话视图 / 屏幕视图 | `com.chua.starter.control.model.SessionView`、`com.chua.starter.control.model.ScreenView` | 由控制器返回的 `record` |

控制器全部注解（`ControlController` 类注释即真实契约）：

```text
GET    /control/sessions                       活跃会话列表
POST   /control/session/create?sessionId=xxx   创建会话
DELETE /control/session/destroy/{sessionId}    销毁会话
GET    /control/screens                        桌面采集源
GET    /control/config                         信令地址、端口、path、wsUrl、token、采集参数
POST   /control/event/send                     控制事件 REST 兜底（DataChannel 不可用时）
```

返回值统一包在 `com.chua.common.support.lang.code.ReturnResult` 中。
视图结构：

| `record` | 字段 |
|---|---|
| `SessionView` | `sessionId`、`peerCount`、`capturing` |
| `ScreenView` | `id`、`name`、`width`、`height` |

`/control/screens` 在采集服务抛异常时返回 `ReturnResult.ok(Collections.emptyList())`，
即**空列表而不是错误**；`/control/event/send` 的请求体是原始 JSON 字符串，
由 `com.chua.webrtc.model.DefaultControlEventFactory#parse(String)` 解析成 `ControlEvent`，
再交给 `webRtcServer.extensions().controlService().onControlEvent(sessionId, peerId, event.path(), event)`。

## 二、依赖与引入

Maven 坐标（版本取父工程真实值）：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-control-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 版本来源 | 作用 |
|---|---|---|
| `utils-support-common-starter` | 父工程 `dependencyManagement` | `ReturnResult`、`ServerSetting`、`ProtocolType` 等公共设施 |
| `utils-support-webrtc-starter` | 父工程 `dependencyManagement`，**`optional=true`** | `WebRtcServer`、`WebRtcSession`、`ScreenCaptureService`、`ControlService` |
| `spring-boot-starter-web` | Spring Boot BOM | `@RestController`、`HttpServletRequest` |
| `spring-boot-configuration-processor` | Spring Boot BOM，`optional` | 配置元数据 |
| `lombok` | 父工程 `1.18.48`，`optional` | `@Data` / `@RequiredArgsConstructor` / `@Slf4j` |

**`utils-support-webrtc-starter` 是 `optional`**：它不会传递给宿主，宿主必须自己声明，
否则 `ControlAutoConfiguration` 装配 `WebRtcServer` 时会 `ClassNotFoundException`，
整个应用起不来。父工程里 `spring-support-monitor-starter` 就是这么做的
（同时声明本模块与 `utils-support-webrtc-starter`）。

## 三、配置项

前缀：`control.webrtc`（`com.chua.starter.control.properties.ControlProperties`，
`@ConfigurationProperties(prefix = ControlProperties.PREFIX)`，`PREFIX = "control.webrtc"`）。

| key | 默认值 | 说明 |
|---|---|---|
| `control.webrtc.enabled` | `true` | 是否启用远控模块；`matchIfMissing = true` |
| `control.webrtc.token` | 空串 | 信令访问令牌；为空时启动自动生成（`UUID` 去横线）并写回该属性 |
| `control.webrtc.host` | `0.0.0.0` | 信令监听地址；同时是 `/control/config` 回包的 `host` |
| `control.webrtc.port` | `10000` | 信令端口；WebSocket 路径 `/webrtc` |
| `control.webrtc.auto-start` | `true` | 是否随应用启动信令服务 |
| `control.webrtc.frame-rate` | `15` | 桌面采集帧率，装配时调用 `setCaptureRate` |
| `control.webrtc.width` | `1280` | 采集最大宽度（仅随 `/control/config` 下发，见「已知限制」） |
| `control.webrtc.height` | `720` | 采集最大高度（同上） |

示例：

```yaml
control:
  webrtc:
    enabled: true
    host: 0.0.0.0
    port: 10000
    auto-start: true
    frame-rate: 15
    width: 1280
    height: 720
    # token 留空即启动时自动生成，随 /control/config 下发
```

## 四、快速开始

**第一步：引入依赖。** 同时声明 `spring-support-control-starter` 与
`utils-support-webrtc-starter`（后者在 pom 里是 `optional`，必须宿主显式添加）。

**第二步：确认端口。** 默认信令监听 `0.0.0.0:10000`、路径 `/webrtc`。
端口被占用时 `ControlAutoConfiguration` 只记录 `error` 日志（"远程控制信令启动失败"），
应用继续启动但远控不可用；需要换端口就改 `control.webrtc.port`。

**第三步：取配置并连信令。** 页面先调 `GET /control/config` 拿到 `wsUrl` 与 `token`，
再用 `wss/ws` 连接信令；媒体流与键鼠走 DataChannel，不经过 `/control/**`。

```bash
curl http://127.0.0.1:8080/control/config
```

## 五、运行示例

### 5.1 调用示例

**本模块无链式 API。** `ControlController` 是 `@RestController`，
`ControlAutoConfiguration#webRtcServer` 是普通 `@Bean` 方法；
`ControlProperties` 是 `@Data`（setter 赋值，没有 Builder），
`WebRtcServer` 的装配在自动配置里以「new → 逐项 set → start」的步骤式写法完成。

服务端 Java 调用（与控制器内部逻辑一致）：

```java
@Autowired
private WebRtcServer webRtcServer;

// 创建 / 查询 / 销毁会话
WebRtcSession session = webRtcServer.createSession("demo-1");
Collection<WebRtcSession> all = webRtcServer.sessions();
int peers = session.peers().size();
webRtcServer.destroySession("demo-1");
```

控制器端点：

```java
// 会话列表
@GetMapping("/sessions")
public ReturnResult<List<SessionView>> sessions() {
    List<SessionView> views = webRtcServer.sessions().stream().map(this::toSessionView).toList();
    return ReturnResult.ok(views);
}

// 创建会话（sessionId 为必填查询参数）
@PostMapping("/session/create")
public ReturnResult<SessionView> createSession(@RequestParam String sessionId) {
    return ReturnResult.ok(toSessionView(webRtcServer.createSession(sessionId)));
}
```

桌面源与采集对象（`ScreenCaptureService` 来自 webrtc 的 `extensions()`）：

```java
ScreenCaptureService capture = webRtcServer.extensions().screenCaptureService();
ScreenCaptureService.ScreenInfo[] screens = capture.screens();
capture.setCaptureRate(15);          // 自动配置启动时已按 control.webrtc.frame-rate 调用
boolean capturing = capture.videoTrack() != null;   // SessionView.capturing 的判定依据
```

REST 调用示例（控制事件兜底通道，字段与 DataChannel 协议一致、平铺在根节点）：

```bash
curl -X POST "http://127.0.0.1:8080/control/event/send?sessionId=demo-1&peerId=browser-1" \
  -H "Content-Type: application/json" \
  -d '{"type":"mouse","path":"control","timestamp":1,"x":100,"y":200,"button":"left","action":"press","delta":0}'
```

`sessionId` / `peerId` 都有默认值 `rest`（`@RequestParam(defaultValue = "rest")`），
不传时事件会投递到名为 `rest` 的会话/连接上，实际使用应显式传入。

### 5.2 SPI 扩展示例

**本模块不提供 SPI 扩展点。** `src/main/resources/META-INF` 下只有
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
（内容为 `com.chua.starter.control.configuration.ControlAutoConfiguration`），
没有 `META-INF/services/*`，也没有定义自己的扩展接口。

需要替换具体实现时，扩展点在**下游的 `utils-support-webrtc-starter`**，本模块只是消费者：

| 下游接口 | 本模块使用位置 |
|---|---|
| `com.chua.webrtc.spi.media.ScreenCaptureService` | `ControlController#screens`、`#toSessionView`、`ControlAutoConfiguration` 设置帧率 |
| `com.chua.webrtc.control.ControlService` | `ControlController#sendEvent` 投递控制事件 |

`WebRtcServer` 本身可以通过「宿主自己声明一个 `WebRtcServer` Bean」的方式接管：
`ControlAutoConfiguration#webRtcServer` 标注了 `@ConditionalOnMissingBean(WebRtcServer.class)`，
宿主 Bean 存在时本模块不再创建。

## 六、与其它 starter 的关系

依赖方向（本模块 → 依赖）：

- `utils-support-common-starter` — `ReturnResult` / `ServerSetting` / `ProtocolType`
- `utils-support-webrtc-starter`（`optional`）— 信令服务、媒体与注入实现
- `spring-boot-starter-web` — REST 控制器

反向依赖（仓库内真实存在）：

| 模块 | 依赖方式 |
|---|---|
| `spring-support-monitor-starter` | 同时声明 `spring-support-control-starter` 与 `utils-support-webrtc-starter`，把 `/control` REST 与 `/webrtc` 信令作为监控端「远控」页面的后端 |

定位关系：本模块只做「Spring 装配 + REST 辅助接口」，媒体与注入全部在
`utils-support-webrtc-starter`；本模块不依赖 `spring-support-monitor-starter`，
也不依赖 `spring-support-docker-starter`（两者在 monitor 中并列引入，互不相关）。

## 七、已知限制

1. **`utils-support-webrtc-starter` 必须由宿主显式声明。** 本模块把它标为 `optional`，
   漏加时不是「远控不可用」而是「应用启动失败」（`ControlAutoConfiguration` 装配
   `WebRtcServer` 时类缺失）。
2. **端口占用只降级不阻断。** 信令启动失败仅记录 `error` 日志，`WebRtcServer` Bean 仍然存在，
   后续 `/control/config` 会返回一个连不上的 `wsUrl`；`auto-start: false` 时同理
   （Bean 已创建但未 `start()`，需自行调用）。
3. **`width` / `height` 不进采集设置。** 自动配置只调用了
   `screenCaptureService().setCaptureRate(frameRate)`；
   `getWidth()` / `getHeight()` 的唯一用途是 `GET /control/config` 的回包字段。
4. **令牌每次启动会变。** `token` 为空时 `ControlAutoConfiguration` 生成
   `UUID.randomUUID().toString().replace("-", "")` 并 `properties.setToken(token)`；
   这是内存态，没有持久化，重启后旧令牌失效。
5. **`/control/**` 没有方法级鉴权。** `ControlController` 上没有任何 `@Permission`
   或安全注解，访问控制依赖宿主自身的拦截器/网关；`token` 只保护 `/webrtc` 信令连接
   （`server.setSignalingToken(token)`），不校验 REST 调用方。
6. **`wsUrl` 的 host 取自当前请求。** `config` 用 `request.getServerName()` 拼 `wsUrl`
   （不能直接用监听地址 `0.0.0.0`），因此反向代理/端口映射场景下页面拿到的是
   「浏览器访问到的 host + `control.webrtc.port`」，端口不一致时需在网关侧对齐。
7. **采集能力依赖平台实现。** 类注释写明桌面采集走 `ScreenCaptureService`
   （Windows DXGI Desktop Duplication）、键鼠走 `ControlService`（`java.awt.Robot`）；
   非 Windows 主机是否可用取决于 `utils-support-webrtc-starter` 的实现，
   本模块不做平台探测也不做降级。
8. **只支持单个 `WebRtcServer` Bean。** `@ConditionalOnMissingBean(WebRtcServer.class)`
   意味着宿主想并存多个实例需要自己管理，本模块只装配一个。
