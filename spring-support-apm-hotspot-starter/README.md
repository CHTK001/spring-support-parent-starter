# spring-support-apm-hotspot-starter

`spring-support-apm-starter` 的「自研 hotspot agent」扩展：实现 `ApmProvider`（SPI 名 `hotspot`，`order=100`），把 `utils-support-runtime-apm` 提供的 `HotspotOtnlEngine` 交给上层统一路由。

- 数据全部来自**进程内**：`TraceHandler` 插桩产生的 Span，不需要任何外部后端、不需要数据库。
- 未以 `-javaagent` 挂载 agent 时 `isAvailable()` 为 `false`，`ApmRegistry` 会自动跳过它。
- 本模块只有**一个类 + 一个 SPI 登记文件**，没有任何 Spring 自动配置类。

> 当前版本：`4.0.0.51`（跟随父 POM `spring-support-parent-starter`）

## 一、模块能力

| 能力域 | 关键类 | 入口 |
| --- | --- | --- |
| SPI 实现 | `com.chua.starter.apm.hotspot.HotspotApmProvider`（类上 `@Spi("hotspot")`） | `META-INF/extensions/com.chua.starter.apm.support.spi.ApmProvider` |
| 引擎适配 | `HotspotApmProvider#engine()` | `ServiceProvider.of(OtnlEngine.class).getExtension("hotspot")` |
| 优先级声明 | `HotspotApmProvider#order()` = `100` | `ApmRegistry#detect()` 按 `order` 升序；SkyWalking 为 `0`，所以与之并存时后选 |
| 可用性判定 | 继承 `ApmProvider#isAvailable()` 默认实现 | `engine() != null && engine.status().available()` |
| 状态上报 | 继承 `ApmProvider#status()` 默认实现 → `HotspotOtnlEngine#status()` | `GET /api/apm/v1/status` 的 `candidates[].message` |
| 运行模式 | 继承 `ApmProvider#mode()` 默认实现 | 引擎可用时为 `agent`，否则 `none` |
| 能力清单 | `HotspotOtnlEngine#capabilities()`（utils 层） | `GET /api/apm/v1/capabilities` |
| 数据面实现 | `com.chua.runtime.apm.engine.HotspotOtnlEngine`（utils 层） | 由引擎 SPI `META-INF/extensions/com.chua.common.support.apm.OtnlEngine` 登记 |
| 数据来源 | `com.chua.runtime.apm.handler.TraceHandler`（字节码插桩产生的 Span） | `ApmBootstrap` 在 agent `premain` 里启动 |
| agent 入口（间接） | `com.chua.runtime.agent.RuntimeAgent#premain` / `#agentmain` | `utils-support-runtime-agent` 的 `Premain-Class` / `Agent-Class` |

### 本引擎真正支持的能力

`HotspotOtnlEngine#capabilities()` 只声明四项，**不是** `OtnlEngine` 默认的十项全集：

| 能力常量 | 值 | 对应接口 | 说明 |
| --- | --- | --- | --- |
| `OtnlEngine.CAP_SERVICES` | `services` | `GET /services` | 返回当前进程这 1 个服务（由内存 Span 聚合成调用数/端点/耗时/错误率） |
| `OtnlEngine.CAP_INSTANCES` | `instances` | `GET /instances?service=` | 返回当前实例，属性含 `host` / `pid` / `agent=hotspot` |
| `OtnlEngine.CAP_ENDPOINTS` | `endpoints` | `GET /endpoints?service=&keyword=` | 按端点名聚合，按调用数倒序 |
| `OtnlEngine.CAP_TRACES` | `traces` | `GET /traces`、`GET /trace/{traceId}` | 内存 Span 分组后的链路与明细 |

未声明的是 `topology` / `metrics` / `runtime` / `alarms` / `alerts` / `logs` / `profiles`。调用对应接口时，`ApmDispatcher#withEngine` 会先检查 `engine.supports(capability)`，不满足直接返回失败结果：

```text
引擎 hotspot 不支持 metrics
```

## 二、依赖与引入

```xml
<!-- 通常只需这一个：它会传递 spring-support-apm-starter 与 utils-support-runtime-apm -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-apm-hotspot-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 版本 | 作用 | 作用域 |
| --- | --- | --- | --- |
| `spring-support-apm-starter` | `4.0.0.51`（`${project.version}`） | `ApmProvider` SPI、`ApmRegistry` / `ApmDispatcher`、全部 REST 接口 | 编译 |
| `utils-support-runtime-apm` | `4.0.0.50`（`${utils.version}`） | `HotspotOtnlEngine`（引擎）+ `TraceHandler` 等插桩处理器 + `ApmBootstrap` | 编译 |
| `utils-support-common-starter` | `4.0.0.50`（`${utils.version}`） | `OtnlEngine` / `ServiceProvider` / `@Spi` | 编译 |
| `lombok` | 父 POM 管理 | `@Slf4j` | `provided` |

注意：本模块 pom **没有** `spring-boot-configuration-processor`，因为模块内没有 `@ConfigurationProperties`（见下节）。

真正提供采集能力的插件包是 `utils-support-runtime-agent`（`Premain-Class` / `Agent-Class` 均为 `com.chua.runtime.agent.RuntimeAgent`，并做了 shade 打包），实际部署时要挂的是它，而不是本 starter 的 jar。

## 三、配置项

**无 `@ConfigurationProperties`，复用宿主配置**：本模块不定义任何配置前缀，也不读取任何 key。它只响应 `spring-support-apm-starter` 的 `plugin.apm.*`：

| key（来自 apm-starter） | 与本模块的关系 |
| --- | --- |
| `plugin.apm.enabled` | `false` 时 `ApmRegistry#describe()` 直接降级，本实现不会被查询 |
| `plugin.apm.type` | 填 `hotspot` 可强制指定；留空则按 `order` 自动探测（有 SkyWalking 时不会选到它） |
| `plugin.apm.detect-interval` | 决定 `ApmRegistry` 多久重新跑一次 `isAvailable()` |
| `plugin.apm.mode` / `path` / `port` | 只影响 apm-starter 的出口，与本实现无关 |

运行期可改项：**没有**。`HotspotApmProvider` 未覆盖 `runtimeSettings()` / `applyRuntimeSettings(Map)`，因此：

- `GET /api/apm/v1/config` 的 `engine.settings` 为空表；
- `POST /api/apm/v1/config` 若带引擎级键（如 `oapUrl`），`ApmRuntimeConfig#applyEngineSettings` 会抛 `当前引擎不支持这些设置项: [...]`。

采集参数（采样范围、开关等）由 agent 侧的 `TraceHandler` 与 `plugin.apm` 之外的运行期插件配置决定，本模块不介入（以 utils 层代码为准）。

## 四、快速开始

**第 1 步：加依赖**（见上）。

**第 2 步：以 `-javaagent` 挂载 agent**（不挂则本实现在注册表里永远 `available=false`）

```bash
java -javaagent:/path/to/utils-support-runtime-agent.jar \
     -jar your-app.jar
```

**第 3 步：启动后确认状态**

```bash
curl http://127.0.0.1:8080/api/apm/v1/status
```

期望看到：

```json
{
  "code": "00000",
  "ok": true,
  "data": {
    "present": true,
    "name": "hotspot",
    "mode": "agent",
    "endpoint": "local",
    "candidates": [{ "name": "hotspot", "available": true, "mode": "agent" }]
  }
}
```

若 `available=false`，`candidates[].message` 会给出确切原因（两种可能见「七、已知限制」）。

## 五、运行示例

### 5.1 调用示例

**方式 A：直接使用 Provider**（本模块无链式 API，以下为步骤式）

```java
// 1) 无构造参数，直接 new
HotspotApmProvider provider = new HotspotApmProvider();

// 2) 元信息
String name = provider.name();     // "hotspot"
int order = provider.order();      // 100

// 3) 取引擎（惰性创建并缓存；utils 包缺失或 SPI 未登记时返回 null）
OtnlEngine engine = provider.engine();

// 4) 判断可用性（默认实现：engine != null && engine.status().available()）
if (provider.isAvailable()) {
    String mode = provider.mode();               // "agent"
    OtnlStatus status = provider.status();       // available / version / endpoint=local
    java.util.Map<String, String> settings = provider.runtimeSettings(); // 空表 Map.of()
}

// 5) 关闭（默认实现会调用 engine.close()；hotspot 引擎自身不持资源，agent 生命周期由 ApmBootstrap 管理）
provider.close();
```

**方式 B：走统一接口**（由 apm-starter 路由，只用到四项能力）

```java
// 服务目录：本引擎返回当前进程这 1 个服务
dispatcher.dispatch(ApmRequest.get("/api/apm/v1/services", Map.of()));

// 实例：attributes 含 host / pid / agent=hotspot
dispatcher.dispatch(ApmRequest.get("/api/apm/v1/instances",
        Map.of("service", "your-service-name")));

// 端点：keyword 过滤端点名
dispatcher.dispatch(ApmRequest.get("/api/apm/v1/endpoints",
        Map.of("service", "your-service-name", "keyword", "/api/order")));

// 链路列表与详情
dispatcher.dispatch(ApmRequest.get("/api/apm/v1/traces",
        Map.of("minutes", "30", "pageSize", "20")));
dispatcher.dispatch(ApmRequest.get("/api/apm/v1/trace/" + traceId, Map.of()));
```

**方式 C：命令行验证（不写代码）**

```bash
curl "http://127.0.0.1:8080/api/apm/v1/capabilities"
# {"code":"00000","ok":true,"data":{"engine":"hotspot","capabilities":["services","instances","endpoints","traces"]}}
```

**关于链式 API**：本模块无链式 API。`HotspotApmProvider` 只有 `name()` / `order()` / `engine()` 三个显式方法（其余靠接口默认实现），返回值都是 `String` / `int` / `OtnlEngine` / `void` / `Map`；没有 Builder，也没有 `return this`。

### 5.2 SPI 扩展示例

本模块的「全部内容」就是一次 `ApmProvider` SPI 实现，可直接作为模板。

**① 实现**：`com.chua.starter.apm.hotspot.HotspotApmProvider`

```java
@Spi("hotspot")
public class HotspotApmProvider implements ApmProvider {

    private static final String ENGINE_NAME = "hotspot";
    private volatile OtnlEngine engine;

    @Override
    public String name() {
        return ENGINE_NAME;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public OtnlEngine engine() {
        OtnlEngine current = engine;
        if (current == null) {
            synchronized (this) {
                current = engine;
                if (current == null) {
                    try {
                        current = ServiceProvider.of(OtnlEngine.class).getExtension(ENGINE_NAME);
                    } catch (Throwable e) {
                        log.debug("[APM] 获取 hotspot 引擎失败: {}", e.getMessage());
                        return null;
                    }
                    engine = current;
                }
            }
        }
        return current;
    }
}
```

**② 登记**：`src/main/resources/META-INF/extensions/com.chua.starter.apm.support.spi.ApmProvider`

```text
# ApmProvider SPI 注册：名称=实现类
hotspot=com.chua.starter.apm.hotspot.HotspotApmProvider
```

被适配的引擎在 utils 包另有登记（`utils-support-runtime-apm` 的 `META-INF/extensions/com.chua.common.support.apm.OtnlEngine`）：

```text
# OtnlEngine SPI 注册：名称=实现类
hotspot=com.chua.runtime.apm.engine.HotspotOtnlEngine
```

**③ 生效**：`ApmRegistry#detect()` 用 `ServiceProvider.of(ApmProvider.class)` 扫到本实现 → 按 `order=100` 排序 → 只在没有更优先且可用的实现时才被选中。选中后 `GET /status`、`/capabilities`、`/services` 等接口立即以 hotspot 数据作答。

**本模块不提供其它 SPI 扩展点**：没有 `META-INF/services/*`，没有 `META-INF/spring/*`（无自动配置类），也没有除 `ApmProvider` 之外的扩展接口。

## 六、与其它 starter 的关系

| 关系 | artifactId | 方向与说明 |
| --- | --- | --- |
| 依赖 | `spring-support-apm-starter`（`4.0.0.51`） | 本模块 → 基础模块：`ApmProvider` 契约、注册表、统一路由与全部 REST 接口 |
| 依赖 | `utils-support-runtime-apm`（`4.0.0.50`） | 本模块 → utils 层：`HotspotOtnlEngine`（数据面）、`TraceHandler` 等处理器、`ApmBootstrap` |
| 依赖 | `utils-support-common-starter`（`4.0.0.50`） | `OtnlEngine` / `ServiceProvider` / `@Spi` |
| 运行时依赖 | `utils-support-runtime-agent`（`4.0.0.50`） | 部署时以 `-javaagent` 挂载，`premain` 里 `SpyBootstrap.init` + `ApmBootstrap#start` 才让引擎真正有数据 |
| 被依赖 | `spring-support-apm-starter` | 基础模块通过 SPI 发现本实现；业务代码不直接 import 本模块 |
| 并列 | `spring-support-apm-skywalking-starter` | `order=0` 优先；同机并存且都可用时默认选 SkyWalking，hotspot 作为「没装商业 APM 时的兜底」 |

## 七、已知限制

1. **只有 4 项能力**：`services` / `instances` / `endpoints` / `traces`。请求 `metrics`、`alarms`、`logs`、`profiles`、`topology` 等会得到 `引擎 hotspot 不支持 xxx` 的失败结果，不是空数据。
2. **未挂 agent 就不可用**：`HotspotOtnlEngine#status()` 会返回 `未检测到 hotspot agent（utils-support-runtime-apm 未以 -javaagent 方式挂载）`；agent 挂了但没跑起来时返回 `hotspot agent 已挂载但未启动（TraceHandler 未运行）`。两种情况 `isAvailable()` 都是 `false`。
3. **不能热改**：未实现 `runtimeSettings()` / `applyRuntimeSettings(Map)`，`GET /config` 的 `engine.settings` 为空表；`POST /config` 带引擎级键会抛「当前引擎不支持这些设置项」。改采集行为只能重启或改 agent 侧配置。
4. **没有自动配置类**：本模块不含 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，也不注册任何 Spring Bean。所有接口都由 `spring-support-apm-starter` 装配，本模块只贡献 SPI 文件。
5. **数据只在内存**：Span 存在 `TraceHandler` 的列表里，上限 `MAX_SPANS = 10000`，超出后按插入顺序淘汰最旧的（`spans.remove(0)`）。进程重启即清空，没有落库、没有跨进程聚合。
6. **只能看到自己**：`services()` 固定返回当前进程这 1 个服务，`instances()` 固定返回当前实例；它不是分布式追踪后端，无法把多台机器的调用串起来。
7. **attach 场景可能漏采早期类**：`RuntimeAgent#premain` 里对已加载类补做了 `retransformLoadedClasses`，但 attach 之前就已完成的调用不会被回放；启动即挂载（`-javaagent`）才能覆盖全量。
8. **版本号不可配**：`/status` 里的 `version` 来自 `TraceHandler#version()`，不是 `pom.xml` 的版本。
9. **`close()` 不释放采集资源**：`HotspotOtnlEngine#close()` 明确「agent 生命周期由 `ApmBootstrap` 管理，引擎不持有资源」，所以关掉 Provider 不会停止插桩。
10. **本模块无 `META-INF/services` 扩展点**：只有一个 `META-INF/extensions/com.chua.starter.apm.support.spi.ApmProvider` 文件；需要自定义采集或新增能力，应改 utils 层的 `HotspotOtnlEngine`（以代码为准）。
