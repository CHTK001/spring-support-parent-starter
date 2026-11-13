# spring-support-apm-starter

APM 基础模块 —— 定义 `ApmProvider` SPI，把任意 APM 数据面（统一的 `OtnlEngine`）聚合成一套**协议无关**的 REST 接口，并支持「嵌入宿主 Servlet」与「独立 HTTP 服务」两种启动方式。

- 业务侧只依赖 `ApmProvider` 与 `OtnlEngine`，不直接依赖任何 APM SDK；新增一种 APM = 新增一个扩展模块。
- 本模块**不含任何具体 APM 实现**。没有引入扩展模块时（或都不可用），模块照常装配，`/status` 返回 `present=false` 与原因，前端据此渲染「本服务器未接入 APM」的降级页。

> 当前版本：`4.0.0.51`（跟随父 POM `spring-support-parent-starter`）

## 一、模块能力

| 能力域 | 关键类 | 入口 |
| --- | --- | --- |
| SPI 定义 | `com.chua.starter.apm.support.spi.ApmProvider` | 由各 `spring-support-apm-xx-starter` 实现 |
| 引擎探测与选择 | `ApmRegistry`（`detect` / `provider` / `engine` / `candidates` / `describe`） | 首次访问状态、或 `plugin.apm.detect-interval` 到期后自动探测 |
| 统一路由 | `ApmDispatcher#dispatch(ApmRequest)` | `{plugin.apm.path}/**`，默认 `/api/apm/v1/**` |
| 嵌入模式 | `ApmServlet` + `ServletRegistrationBean` | `ApmAutoConfiguration#apmServletRegistration`，映射 `path + "/*"` |
| 独立模式 | `ApmStandaloneServer`（内部用 utils 层 `ServerBuilder`） | `plugin.apm.mode=standalone` |
| 独立进程入口 | `ApmServerMain#main(String[])` | `java -cp ... com.chua.starter.apm.support.ApmServerMain` |
| 运行期设置 | `ApmRuntimeConfig`（`view` / `update` / `restore`） | `GET /config`、`POST /config` |
| 设置落盘 | `ApmSettingsStore`（`load` / `save` / `file`） | `plugin.apm.settings-file` 指向的 properties 文件 |
| 状态描述模型 | `ApmDescriptor`、`ApmDescriptor.ApmCandidate` | `GET /status` |
| 请求/结果模型 | `ApmRequest`（`get` / `param`）、`ApmResult`（`ok` / `fail`） | 两种模式共用，逐请求构造 |
| 写操作留痕 | `ApmOperLogSink#record(ApmRequest, ApmResult, long)` | 仅在 `POST /config` 分支回调 |
| 服务信息面板 | `ApmAutoConfiguration#apmServiceInfoContributor` | `ServiceInfoContributor` Bean（回调里调用 `registerFlag` / `registerEndpoint`） |
| 自动配置 | `ApmAutoConfiguration` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

### 统一接口清单

路径相对 `plugin.apm.path`（默认 `/api/apm/v1`），**只接受 `GET` / `POST`**，其它方法直接返回失败结果。

| 方法 | 路径 | 说明 | 关键参数 |
| --- | --- | --- | --- |
| GET | `/status`（或 `/`） | 是否存在/启用了 APM，返回 `ApmDescriptor` | 无 |
| GET | `/capabilities` | 当前引擎支持的能力清单 | 无 |
| GET | `/overview` | 健康度 + 描述 | `minutes` / `startTime` / `endTime` / `service` |
| GET | `/services` | 服务目录 | 同上 |
| GET | `/instances` | 服务实例 | `service`（必填） |
| GET | `/endpoints` | 端点 | `service`（必填）、`keyword` |
| GET | `/traces` | 链路列表 | `service` / `state` / `pageNum` / `pageSize` / `orderByDuration` |
| GET | `/trace/{traceId}` | 链路详情（含 Span） | 路径参数 `traceId` |
| GET | `/topology` | 服务拓扑 | `service`（空表示全局） |
| GET | `/topology/multi` | 多服务合并拓扑 | `services=a,b`（必填，逗号分隔） |
| GET | `/metrics` | 指标时序 | `name`（必填）、`scope` / `service` / `instance` / `endpoint` / `step` |
| GET | `/metric-definitions` | 指标定义（下拉框） | `scope`（`SERVICE` 缺省） |
| GET | `/alarms` | 告警 | `keyword` / 时间窗口 |
| GET | `/alerts` | 告警（`/alarms` 的别名，能力判断走 `CAP_ALERTS`） | 同上 |
| GET | `/logs` | 日志 | `service` / `keyword` |
| GET | `/profiles` | 性能剖析任务列表 | `service` / `endpoint` |
| GET | `/profiles/{taskId}/segments` | 剖析任务采样片段 | 路径参数 `taskId` |
| GET | `/profiles/{taskId}/analysis` | 剖析结果（线程栈树） | 路径参数 `taskId` |
| GET | `/metrics-batch` | 批量指标（多实体对比） | `names=a,b`（必填）、`services=a,b`、`scope` |
| GET | `/infra-3d` | 全局拓扑 + 分层信息（3D 基础设施图） | `minutes` |
| GET | `/config` | APM 设置视图 | 无 |
| POST | `/config` | 保存 APM 设置（热改并落盘） | `enabled` / `type` / `detectInterval` / 引擎项（如 `oapUrl`）/ `action=detect` |

查询条件统一由 `ApmDispatcher` 解析：`minutes` 默认 `30`，`pageNum` 默认 `1`，`pageSize` 默认 `20`，`step` 缺省为 `MINUTE`，`scope` 缺省为 `OtnlScope.SERVICE`，`state` 缺省为 `OtnlTraceState.ALL`。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-apm-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>

<!-- 二选一或都加：真正提供数据面实现的扩展模块 -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-apm-skywalking-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-apm-hotspot-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 版本来源 | 作用 | 是否必需 |
| --- | --- | --- | --- |
| `spring-support-common-starter` | `4.0.0.51`（`project.version`） | `ServiceInfoContributor` / `ServiceInfoRegistry` | 是 |
| `utils-support-common-starter` | `4.0.0.50`（`utils.version`） | `OtnlEngine` / `OtnlQuery` / `OtnlMetricQuery` / `OtnlScope` / `OtnlTraceState` / `ReturnResult` / `ServerBuilder` / `ServiceProvider` | 是 |
| `jackson-databind` | 父 POM 管理 | 两种出口的 JSON 序列化 | 是 |
| `spring-boot-starter-web` | 父 POM 管理 | 嵌入模式所需的 `jakarta.servlet` API | **否**（`<optional>true</optional>`） |
| `spring-boot-configuration-processor` | 父 POM 管理 | 生成配置元数据 | `provided` |
| `lombok` | 父 POM 管理 | `@Slf4j` / `@Getter` / `@Setter` | `provided` |

## 三、配置项

`ApmProperties` 使用 `@ConfigurationProperties(prefix = "plugin.apm")`。

| key | 默认值 | 说明 | 可热改 |
| --- | --- | --- | --- |
| `plugin.apm.enabled` | `true` | 是否启用 APM 模块；`false` 时 `present=false`，`/status` 与 `/config` 仍可访问 | 是 |
| `plugin.apm.type` | `""`（空） | 引擎名，空表示自动探测：按 `ApmProvider#order()` 升序取第一个可用者 | 是 |
| `plugin.apm.mode` | `embedded` | `embedded` 注册 Servlet；`standalone` 单起 HTTP 服务 | 否 |
| `plugin.apm.path` | `/api/apm/v1` | 统一 REST 前缀（嵌入=Servlet 映射，独立=Server 路由） | 否 |
| `plugin.apm.host` | `0.0.0.0` | 独立模式监听地址 | 否 |
| `plugin.apm.port` | `12801` | 独立模式监听端口 | 否 |
| `plugin.apm.server-type` | `jdk` | 独立模式底层 `Server` SPI 类型 | 否 |
| `plugin.apm.detect-interval` | `10000` | 探测结果缓存毫秒数（下限 1000，小于下限按 1000 生效） | 是 |
| `plugin.apm.settings-file` | `config/apm-settings.properties` | 运行期设置落盘位置，相对路径按进程工作目录解析 | 否 |
| `plugin.apm.register-service-info` | `true` | 是否登记到统一服务信息面板 | 否 |

额外的部署侧开关（系统属性，不是 `ApmProperties` 字段）：

| 系统属性 | 说明 |
| --- | --- |
| `-Dplugin.apm.enabled.locked=true` | 锁定界面开关：`restore()` 不再用设置文件覆盖启动参数，视图中 `enabledLocked=true` |
| `-Dplugin.apm.type=skywalking` | 部署侧固定引擎；设置文件里的 `type` 会被忽略（启动日志会说明） |
| `-Dskywalking.url` / `-Dplugin.apm.skywalking.url` / 环境变量 `SW_OAP_ADDRESS` | 任一存在时，设置文件里的 `oapUrl` 不再覆盖（视图中 `oapUrlFromDeployment=true`） |

> 注意：`ApmServerMain`（不走 Spring）读取的是 kebab-case 系统属性 `plugin.apm.server-type` 与 `plugin.apm.detect-interval`；与 Spring 侧字段名 `serverType` / `detectInterval` 拼写不同，手写启动脚本时按 kebab-case 传。

## 四、快速开始

**第 1 步：加依赖**（见「二、依赖与引入」），至少加入一个扩展模块，否则只有 `/status` 与 `/config` 有意义。

**第 2 步：确认模式与开关**

```yaml
plugin:
  apm:
    enabled: true
    type: skywalking     # 留空则自动探测；填了就必须是已引入的实现名
    mode: embedded       # 嵌入宿主；若要做独立观测网关改成 standalone
    path: /api/apm/v1
```

**第 3 步：启动后先打状态接口**

```bash
curl http://127.0.0.1:8080/api/apm/v1/status
```

`data.present=true` 表示选中了可用引擎；`data.candidates` 会列出全部已探测引擎（含不可用的与各自原因），便于区分「装了但连不上」与「压根没装」。

## 五、运行示例

### 5.1 调用示例

**方式 A：在 Spring 应用内直接使用路由器**（本模块无链式 API，以下为步骤式）

```java
// 1) 从容器取（两个 Bean 都由 ApmAutoConfiguration 声明）
ApmRegistry registry = context.getBean(ApmRegistry.class);
ApmDispatcher dispatcher = context.getBean(ApmDispatcher.class);

// 2) 构造协议无关请求：ApmRequest.get(String path, Map<String,String> params)
ApmRequest request = ApmRequest.get("/api/apm/v1/traces",
        Map.of("service", "order-service", "minutes", "60", "pageSize", "50"));

// 3) 分发并读取结果（ApmResult 是 record：ok() / data() / message()）
ApmResult result = dispatcher.dispatch(request);
if (result.ok()) {
    Object traces = result.data();
} else {
    log.warn("APM 查询失败: {}", result.message());
}

// 4) 直接用注册表判断可用性与候选
boolean usable = registry.provider().isPresent();     // Optional<ApmProvider>
OtnlEngine engine = registry.engine();                // 可能为 null
ApmDescriptor descriptor = registry.describe();       // present() / name() / candidates()
```

**方式 B：独立模式纯 Java 启动**（不依赖 Spring，等价于 `ApmServerMain` 做的事）

```java
ApmProperties properties = new ApmProperties();
properties.setMode("standalone");
properties.setType("skywalking");          // ApmProperties 是 lombok setter，无链式写法
properties.setHost("127.0.0.1");
properties.setPort(12801);
properties.setPath("/api/apm/v1");
properties.setServerType("jdk");

ApmRegistry registry = new ApmRegistry(properties);
ApmDispatcher dispatcher = new ApmDispatcher(registry, properties); // 该构造器自带 ApmSettingsStore
ApmStandaloneServer server = new ApmStandaloneServer(properties, dispatcher, new ObjectMapper());

server.start();                            // 幂等
String url = server.url();                  // http://127.0.0.1:12801/api/apm/v1
// ... 进程存活期间对外提供同一套接口 ...
server.stop();                              // 幂等；或 server.close()
registry.close();
```

**方式 C：独立模式的命令行入口**

```bash
java -cp "app.jar;lib/*" com.chua.starter.apm.support.ApmServerMain \
     -Dplugin.apm.type=skywalking \
     -Dskywalking.url=http://127.0.0.1:12800 \
     -Dplugin.apm.host=127.0.0.1 -Dplugin.apm.port=12801
```

**关于链式 API**：本模块自身没有链式 Builder（`ApmProperties` 为 setter 式，`ApmDispatcher` 只有 `setOperLogSink` 且返回 `void`，静态工厂 `ApmResult.ok(...)` / `ApmRequest.get(...)` 也不返回 `this`）。唯一的链式写法来自 utils 层的 `ServerBuilder`，出现在 `ApmStandaloneServer#start()` 内部：

```java
Server instance = ServerBuilder.create()
        .type(properties.getServerType())      // 返回 this
        .host(properties.getHost())
        .port(properties.getPort())
        .mapping(base + "/**", HttpMethod.GET, this::handle)
        .mapping(base + "/**", HttpMethod.POST, this::handle)
        .build();
```

### 5.2 SPI 扩展示例

真实 SPI 接口：`com.chua.starter.apm.support.spi.ApmProvider`（`extends AutoCloseable`）。需要实现的方法：`name()`、`engine()`；可选覆盖 `order()`、`isAvailable()`、`status()`、`runtimeSettings()`、`applyRuntimeSettings(Map)`、`mode()`、`close()`。

**① 实现（示例：伪代码骨架，方法签名与接口一致）**

```java
package com.example.apm;

import com.chua.common.support.apm.OtnlEngine;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.apm.support.spi.ApmProvider;

@Spi("myapm")
public class MyApmProvider implements ApmProvider {

    private volatile OtnlEngine engine;

    @Override
    public String name() {
        return "myapm";                       // 必须与 plugin.apm.type 的值一致
    }

    @Override
    public int order() {
        return 50;                            // 越小越优先；skywalking=0，hotspot=100
    }

    @Override
    public OtnlEngine engine() {
        OtnlEngine current = engine;
        if (current == null) {
            // 真正的数据面契约在 utils 层：OtnlEngine 的 SPI 名 = myapm
            current = ServiceProvider.of(OtnlEngine.class).getExtension("myapm");
            engine = current;
        }
        return current;
    }
}
```

**② 登记**：新建 `src/main/resources/META-INF/extensions/com.chua.starter.apm.support.spi.ApmProvider`，每行 `名称=实现类`：

```text
# ApmProvider SPI 注册：名称=实现类
myapm=com.example.apm.MyApmProvider
```

同时还需要一个 `META-INF/extensions/com.chua.common.support.apm.OtnlEngine`，把引擎本体也登记上（`myapm=com.example.apm.MyOtnlEngine`）。

**③ 生效**：`ApmRegistry#detect()` 调用 `ServiceProvider.of(ApmProvider.class).forEach(...)` 扫描全部登记项，按 `order()` 升序；`plugin.apm.type` 为空时取第一个 `isAvailable()` 为真的提供者，非空时只认同名提供者。选中后 `/status`、`/capabilities`、`/config` 立即反映它；`/config` 里该引擎的可热改项来自它自己的 `runtimeSettings()`。

**本模块内置实现：0 个。** 现有两个真实实现都在扩展模块里（各自带 `META-INF/extensions`）：

| SPI 名 | 实现类 | 所在模块 |
| --- | --- | --- |
| `skywalking` | `com.chua.starter.apm.skywalking.SkywalkingApmProvider` | `spring-support-apm-skywalking-starter` |
| `hotspot` | `com.chua.starter.apm.hotspot.HotspotApmProvider` | `spring-support-apm-hotspot-starter` |

**次要扩展点**：`ApmOperLogSink`（`web` 包，`@FunctionalInterface`）由宿主以 **Spring Bean** 形式提供，`ApmAutoConfiguration#apmDispatcher` 通过 `ObjectProvider<ApmOperLogSink>` 注入，不经过 `META-INF` 登记；未提供时 APM 模块照常工作。

## 六、与其它 starter 的关系

| 关系 | artifactId | 方向 |
| --- | --- | --- |
| 被依赖 | `spring-support-apm-skywalking-starter` | 扩展模块 → 本模块（`SkywalkingApmProvider` 实现本模块的 `ApmProvider`） |
| 被依赖 | `spring-support-apm-hotspot-starter` | 扩展模块 → 本模块（`HotspotApmProvider` 同上） |
| 依赖 | `utils-support-common-starter`（`4.0.0.50`） | 本模块 → utils 层：`OtnlEngine` 及模型、`ReturnResult`、`ServerBuilder`、`ServiceProvider` |
| 依赖 | `spring-support-common-starter` | 本模块 → 公共层：`ServiceInfoContributor` / `ServiceInfoRegistry` |
| 可选协作 | 宿主 Web 模块（`spring-boot-starter-web`） | 仅嵌入模式需要 `jakarta.servlet`；独立模式完全不需要 Web |

链路：`spring-support-apm-xx-starter`（`ApmProvider` 实现）→ `ApmRegistry`（选择）→ `ApmDispatcher`（路由）→ `ApmServlet` 或 `ApmStandaloneServer`（出口）。

## 七、已知限制

1. **本模块不产数据**：没有引入 `spring-support-apm-skywalking-starter` / `spring-support-apm-hotspot-starter`（或它们都不可用）时，除 `/status`、`/config` 外所有接口都返回失败结果（`ApmResult.fail`），不是空列表。
2. **只支持 GET / POST**：其它方法返回 `仅支持 GET / POST，当前为 XXX`。
3. **不经过 Spring MVC**：嵌入模式是独立 `Servlet`（映射 `path + "/*"`），因此 `@ControllerAdvice`、`@SysLog` 切面、MVC 拦截器、统一鉴权拦截器都拦不到它；需要留痕只能用 `ApmOperLogSink`。
4. **部分配置改了必须重启**：`mode` / `path` / `port` / `serverType` / `host` / `settingsFile` 在 `ApmRuntimeConfig#RESTART_KEYS` 里，`POST /config` 带上它们会抛 `IllegalArgumentException`（「不支持热改」），而不是静默写入。
5. **参数是单值**：`ApmServlet#params` 只取 `values[0]`，多值查询参数只保留第一个；需要多值请用逗号分隔（如 `services=a,b`）。
6. **批量指标有上限**：`/metrics-batch` 单次最多构造 `MAX_BATCH_SERIES = 24` 条曲线，超出部分被丢弃。
7. **探测有缓存**：结果缓存 `plugin.apm.detect-interval` 毫秒，实际 TTL 用 `Math.max(1000, detectInterval)` 兜底；通过 `POST /config` 设置时，小于 1000 会被 `parseInterval` 直接拒绝并报错。
8. **设置文件失败不阻断**：`ApmSettingsStore#save` 写盘失败只记日志，`POST /config` 返回 `persisted=false` 与 `persistMessage`，本次运行内仍生效，重启后丢失。
9. **`type` 校验依赖已探测候选**：`validateType` 只接受空或候选里出现过的名字，拼错会直接报错并列出可选值。
10. **`/alerts` 与 `/alarms` 同源但能力不同**：两者都调用 `engine.alarms(...)`，但 `/alerts` 判断 `CAP_ALERTS`、`/alarms` 判断 `CAP_ALARMS`；引擎若只声明其一，另一个接口会返回「不支持」。
11. **独立模式的参数来源**：取自 `ServerRequest#getParams()`，同样按单值 `Map<String,String>` 使用。
