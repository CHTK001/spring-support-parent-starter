# spring-support-apm-skywalking-starter

`spring-support-apm-starter` 的 SkyWalking 扩展：实现 `ApmProvider`（SPI 名 `skywalking`，`order=0`），把 utils 层 `utils-support-skywalking-starter` 提供的 `OtnlEngine`（SPI 名同样是 `skywalking`）交给上层统一路由；同时把 `plugin.apm.skywalking.*` 桥接到进程级 `SkywalkingSettings`。

- 本模块**只做「适配 + 配置桥接」**：OAP 的 GraphQL 查询全部在 `utils-support-skywalking-starter` 的 `SkywalkingOtnlEngine` 里。
- 所有 REST 接口来自 `spring-support-apm-starter`，本模块不声明任何 Controller。

> 当前版本：`4.0.0.51`（跟随父 POM `spring-support-parent-starter`）

## 一、模块能力

| 能力域 | 关键类 | 入口 |
| --- | --- | --- |
| SPI 实现 | `com.chua.starter.apm.skywalking.SkywalkingApmProvider` | `META-INF/extensions/com.chua.starter.apm.support.spi.ApmProvider` |
| 引擎适配 | `SkywalkingApmProvider#engine()` | `ServiceProvider.of(OtnlEngine.class).getExtension("skywalking")` |
| 优先级声明 | `SkywalkingApmProvider#order()` = `0` | `ApmRegistry#detect()` 按 `order` 升序排序 |
| 可热改项声明 | `SkywalkingApmProvider#runtimeSettings()` | 键 `oapUrl` / `oapTimeout`，供 `GET /config` 的 `engine.settings` 展示 |
| 可热改项应用 | `SkywalkingApmProvider#applyRuntimeSettings(Map)` | `POST /config` 带 `oapUrl` / `oapTimeout` 时调用 |
| 配置桥接 | `com.chua.starter.apm.skywalking.config.SkywalkingApmAutoConfiguration`（`InitializingBean#afterPropertiesSet`） | `plugin.apm.skywalking.url` 非空时装配 |
| 配置属性 | `SkywalkingApmProperties`（`@ConfigurationProperties(prefix = "plugin.apm.skywalking")`） | `url` / `username` / `password` / `timeout` |
| 进程级设置 | `com.chua.skywalking.support.client.SkywalkingSettings`（utils 层） | `apply(...)` / `url()` / `username()` / `password()` / `timeout()` / `enabled()` |
| 自动配置登记 | `SkywalkingApmAutoConfiguration` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| 引擎 SPI 登记（间接） | `com.chua.skywalking.support.engine.SkywalkingOtnlEngine` | utils 包的 `META-INF/extensions/com.chua.common.support.apm.OtnlEngine` |

`SkywalkingApmProvider` 上的两个公开常量就是运行期键名：

| 常量 | 值 | 含义 |
| --- | --- | --- |
| `SkywalkingApmProvider.KEY_OAP_URL` | `"oapUrl"` | OAP 地址，如 `http://127.0.0.1:12800` |
| `SkywalkingApmProvider.KEY_OAP_TIMEOUT` | `"oapTimeout"` | 单次查询超时（毫秒） |

### 引擎能力与状态缓存

本模块把 `SkywalkingOtnlEngine#capabilities()` 原样上抛给 `/capabilities` 与 `ApmDescriptor#capabilities()`，因此经本模块接入时，统一接口**全部能力都声明支持**：

| 能力常量 | 值 | 对应接口 |
| --- | --- | --- |
| `OtnlEngine.CAP_SERVICES` | `services` | `GET /services` |
| `OtnlEngine.CAP_INSTANCES` | `instances` | `GET /instances?service=` |
| `OtnlEngine.CAP_ENDPOINTS` | `endpoints` | `GET /endpoints?service=` |
| `OtnlEngine.CAP_TRACES` | `traces` | `GET /traces`、`GET /trace/{traceId}` |
| `OtnlEngine.CAP_TOPOLOGY` | `topology` | `GET /topology`、`/topology/multi`、`/infra-3d` |
| `OtnlEngine.CAP_METRICS` | `metrics` | `GET /metrics`、`/metric-definitions`、`/metrics-batch` |
| `OtnlEngine.CAP_RUNTIME` | `runtime` | JVM/运行时指标（随指标通道取数） |
| `OtnlEngine.CAP_ALARMS` | `alarms` | `GET /alarms` |
| `OtnlEngine.CAP_ALERTS` | `alerts` | `GET /alerts` |
| `OtnlEngine.CAP_LOGS` | `logs` | `GET /logs` |
| `OtnlEngine.CAP_PROFILES` | `profiles` | `GET /profiles`、`/profiles/{taskId}/segments`、`/profiles/{taskId}/analysis` |

状态探测由 `SkywalkingOtnlEngine#status()` 完成，且自带两级缓存（常量在 utils 类里）：

| 常量 | 值 | 含义 |
| --- | --- | --- |
| `STATUS_OK_TTL` | `15000` 毫秒 | 探活成功后的状态缓存时长 |
| `STATUS_FAIL_TTL` | `5000` 毫秒 | 探活失败后的状态缓存时长 |
| `SERVICE_CACHE_TTL` | `30000` 毫秒 | 服务目录缓存时长 |

因此 `GET /status` 不会每次都打 OAP：它的实际请求频率同时受本地状态缓存（上面两个常量）与 `plugin.apm.detect-interval`（默认 10000 毫秒）约束。

## 二、依赖与引入

```xml
<!-- 通常只需这一个：它会传递 spring-support-apm-starter 与 utils-support-skywalking-starter -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-apm-skywalking-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 版本 | 作用 | 作用域 |
| --- | --- | --- | --- |
| `spring-support-apm-starter` | `4.0.0.51`（`${project.version}`） | `ApmProvider` SPI、`ApmRegistry`、`ApmDispatcher`、全部 REST 接口 | 编译 |
| `utils-support-skywalking-starter` | `4.0.0.50`（`${utils.version}`） | `SkywalkingOtnlEngine`（OAP GraphQL 数据面）、`SkywalkingSettings`、`SkywalkingAutoConfiguration` | 编译 |
| `utils-support-common-starter` | `4.0.0.50`（`${utils.version}`） | `OtnlEngine` / `ServiceProvider` / `@Spi` | 编译 |
| `spring-boot-configuration-processor` | 父 POM 管理 | 生成 `plugin.apm.skywalking.*` 配置元数据 | `provided` |
| `lombok` | 父 POM 管理 | `@Slf4j` / `@Getter` / `@Setter` | `provided` |

引入后无需任何额外装配：`spring-support-apm-starter` 通过 `META-INF/extensions` 扫到本实现，`/api/apm/v1/status` 即可看到 `skywalking` 候选。

## 三、配置项

`SkywalkingApmProperties` 使用 `@ConfigurationProperties(prefix = "plugin.apm.skywalking")`：

| key | 默认值 | 说明 |
| --- | --- | --- |
| `plugin.apm.skywalking.url` | `""`（空） | OAP 地址，如 `http://127.0.0.1:12800`。**非空才会装配桥接类** |
| `plugin.apm.skywalking.username` | `""`（空） | OAP Basic 鉴权用户名（注意事项见「七、已知限制」第 2 条） |
| `plugin.apm.skywalking.password` | `""`（空） | OAP Basic 鉴权密码（同上） |
| `plugin.apm.skywalking.timeout` | `0` | 单次查询超时（毫秒），`<=0` 表示不覆盖 utils 层的 `skywalking.timeout` |

桥接类的生效条件来自注解，不是配置项：

| 条件 | 来源 |
| --- | --- |
| `@ConditionalOnClass(SkywalkingSettings.class)` | 必须能加载 utils 层设置类 |
| `@ConditionalOnProperty(prefix = "plugin.apm.skywalking", name = "url")` | **必须配置 `url`**，否则整个桥接类不装配 |
| `@AutoConfiguration(after = SkywalkingAutoConfiguration.class)` | 在 utils 层自动配置之后执行，同名项以 `plugin.apm.skywalking.*` 为准 |

运行期可热改项（`SkywalkingApmProvider#runtimeSettings()` 声明的两个键）：

| key | 说明 | 非法值行为 |
| --- | --- | --- |
| `oapUrl` | 新 OAP 地址 | 不以 `http://` / `https://` 开头 → `IllegalArgumentException` |
| `oapTimeout` | 新超时毫秒数 | 非整数或 `<=0` → `IllegalArgumentException` |

> 部署侧还可以用 `-Dskywalking.url`、`-Dplugin.apm.skywalking.url`、环境变量 `SW_OAP_ADDRESS` 提供地址；`spring-support-apm-starter` 的 `ApmRuntimeConfig#restore()` 发现这些来源存在时，会忽略设置文件里保存的 `oapUrl`。

## 四、快速开始

**第 1 步：加依赖**（见上）。它是一个传递依赖包：引入它就不必再单独写 `spring-support-apm-starter`。

**第 2 步：配置 OAP 地址**

```yaml
plugin:
  apm:
    enabled: true
    type: skywalking          # 可留空（自动探测时本实现 order=0，优先被选中）
    skywalking:
      url: http://127.0.0.1:12800
      timeout: 5000
```

**第 3 步：启动后验证**

```bash
curl http://127.0.0.1:8080/api/apm/v1/status
```

`data.candidates` 里应出现 `"name":"skywalking"`；`data.present=true` 且 `data.mode` 为 `remote` 时说明已连上 OAP。再查 `GET /api/apm/v1/config`，`engine.settings` 应回显当前 `oapUrl` / `oapTimeout`。

也可以直接看能力清单，确认 profiles 等高级能力是否被声明：

```bash
curl http://127.0.0.1:8080/api/apm/v1/capabilities
# {"code":"00000","ok":true,"data":{"engine":"skywalking","capabilities":["services","instances",...]}}
```

**地址的三种来源**（都能把地址送进进程级设置，但进入路径不同）：

| 来源 | 示例 | 生效路径 |
| --- | --- | --- |
| 本模块配置 | `plugin.apm.skywalking.url` | Spring 绑定后由 `SkywalkingApmAutoConfiguration` 调 `SkywalkingSettings.apply(...)` 写入 |
| 系统属性 `skywalking.url` | `-Dskywalking.url=http://10.0.0.5:12800` | `SkywalkingSettings` 静态初始化时由 `resolveUrl()` 优先读取（`PROP_URL`） |
| 环境变量 `SW_OAP_ADDRESS` | `export SW_OAP_ADDRESS=http://10.0.0.5:12800` | 系统属性缺失时才读；允许 `host:port` 形式，会自动补 `http://` |

静态初始化的顺序是「系统属性 → 环境变量 → `DEFAULT_URL`（`http://127.0.0.1:12800`）」；随后 Spring 侧的 `apply(...)` 会覆盖它，所以**最终生效值以 `apply` 写入的为准**（本模块配置，或 `/config` 热改）。

## 五、运行示例

### 5.1 调用示例

**方式 A：直接使用 Provider（本模块无链式 API）**

```java
// 1) 构造实现类即可，无构造参数
SkywalkingApmProvider provider = new SkywalkingApmProvider();

// 2) 元信息：name 与 plugin.apm.type 对应；order 越小越优先
String name = provider.name();     // "skywalking"
int order = provider.order();      // 0

// 3) 取数据面引擎：惰性创建并缓存；utils 包缺失或 SPI 未登记时返回 null
OtnlEngine engine = provider.engine();
if (engine != null && provider.isAvailable()) {
    OtnlQuery query = OtnlQuery.empty();
    engine.services(query);
}

// 4) 读取/修改可热改项
Map<String, String> current = provider.runtimeSettings();   // {oapUrl=..., oapTimeout=...}
provider.applyRuntimeSettings(Map.of(
        "oapUrl", "http://10.0.0.5:12800",
        "oapTimeout", "8000"));

// 5) 释放（默认实现释放底层引擎）
provider.close();
```

**方式 B：走统一入口（推荐）**——由 `spring-support-apm-starter` 的 `ApmDispatcher` 调用，等价于前端行为：

```java
ApmResult status = dispatcher.dispatch(ApmRequest.get("/api/apm/v1/status", Map.of()));
ApmResult cfg = dispatcher.dispatch(ApmRequest.get("/api/apm/v1/config", Map.of()));
ApmResult updated = dispatcher.dispatch(new ApmRequest("POST", "/api/apm/v1/config",
        Map.of("oapUrl", "http://10.0.0.5:12800")));
```

**方式 C：只做配置桥接（不涉及 APM 接口）**

```java
// 与 SkywalkingApmAutoConfiguration#afterPropertiesSet 做的事一致
SkywalkingSettings.apply("http://127.0.0.1:12800", "user", "pwd", 5000L, SkywalkingSettings.enabled());
String url = SkywalkingSettings.url();
long timeout = SkywalkingSettings.timeout();
```

**关于链式 API**：本模块无链式 API。`SkywalkingApmProvider` 的方法返回 `String` / `int` / `OtnlEngine` / `Map` / `void`；`SkywalkingSettings#apply` 是 `void` 静态方法；没有 Builder，也没有 `return this`。

### 5.2 SPI 扩展示例

本模块**已经是**一次 SPI 实现，它示范了扩展 `ApmProvider` 的完整三件套。

**① 实现**：`com.chua.starter.apm.skywalking.SkywalkingApmProvider`，类上有 `@Spi("skywalking")`，`name()` 返回 `"skywalking"`，`order()` 返回 `0`。

**② 登记**：`src/main/resources/META-INF/extensions/com.chua.starter.apm.support.spi.ApmProvider`

```text
# ApmProvider SPI 注册：名称=实现类
skywalking=com.chua.starter.apm.skywalking.SkywalkingApmProvider
```

同时，它依赖的数据面引擎在 utils 包里另有登记（`utils-support-skywalking-starter` 的 `META-INF/extensions/com.chua.common.support.apm.OtnlEngine`）：

```text
# OtnlEngine SPI 注册：名称=实现类
skywalking=com.chua.skywalking.support.engine.SkywalkingOtnlEngine
```

**③ 生效**：`spring-support-apm-starter` 的 `ApmRegistry#detect()` 用 `ServiceProvider.of(ApmProvider.class)` 扫描到本实现 → 按 `order=0` 排在最前 → 未配置 `plugin.apm.type` 时取第一个可用者，即 skywalking。`plugin.apm.type=skywalking` 可显式指定；写错（例如 `skywalkin`）会在 `/config` 保存时被 `validateType` 拒绝并列出可选值。

本模块**不提供额外的 SPI 扩展点**（除上述 `META-INF/extensions` 登记外，没有 `META-INF/services`，也没有 `META-INF/extensions` 之外的扩展接口）。

### 5.3 排障清单

按顺序看这四处，基本能定位「装了但没数据」的原因：

| 步骤 | 检查点 | 判读方式 |
| --- | --- | --- |
| 1 | `GET /api/apm/v1/status` 的 `candidates` | 有 `skywalking` 条目但 `available=false` → 实现已加载，问题在后端连通性或配置 |
| 2 | 同响应的 `candidates[].message` | `SkyWalking OAP 不可达: ...` 说明 `SkywalkingSettings.url()` 指的地址连不上 |
| 3 | `GET /api/apm/v1/config` 的 `engine.settings.oapUrl` | 与实际 OAP 不一致时，检查是否被设置文件、`-Dskywalking.url` 或 `SW_OAP_ADDRESS` 覆盖 |
| 4 | 启动日志 `[APM] SkyWalking OAP 地址（plugin.apm.skywalking.url）: ...` | 没这行说明 `plugin.apm.skywalking.url` 没配，桥接类根本没装配 |

补充：`candidates` 里**没有** `skywalking` 条目时，问题不在本模块，而是依赖没进来（`spring-support-apm-skywalking-starter` 缺失或被打包裁剪）。

## 六、与其它 starter 的关系

| 关系 | artifactId | 方向与说明 |
| --- | --- | --- |
| 依赖 | `spring-support-apm-starter`（`4.0.0.51`） | 本模块 → 基础模块：借用 `ApmProvider`、统一路由与全部 REST 接口 |
| 依赖 | `utils-support-skywalking-starter`（`4.0.0.50`） | 本模块 → utils 层：`SkywalkingOtnlEngine`（数据面）、`SkywalkingSettings`（进程级设置）、`SkywalkingAutoConfiguration` |
| 依赖 | `utils-support-common-starter`（`4.0.0.50`） | 本模块 → `OtnlEngine` 契约与 `ServiceProvider` |
| 被依赖 | `spring-support-apm-starter` | 基础模块通过 `ApmProvider` SPI 发现本实现，业务侧不直接 import 本模块 |
| 并列 | `spring-support-apm-hotspot-starter` | 另一个 `ApmProvider` 实现（`order=100`）；同机并存时默认选 skywalking |
| 配置优先级 | utils 层 `skywalking.*` | `plugin.apm.skywalking.*` 覆盖 `skywalking.*`（自动配置 `after` 声明保证顺序） |

## 七、已知限制

1. **桥接只在配了 `url` 时生效**：`SkywalkingApmAutoConfiguration` 的 `@ConditionalOnProperty(prefix = "plugin.apm.skywalking", name = "url")` 决定了「只配 `timeout` 或只配 `username`」不会触发桥接，那些值会被忽略。需要改超时又不写 `url` 时，请直接配 utils 层的 `skywalking.timeout`。
2. **`username` / `password` 的空串会覆盖而非回落**：`SkywalkingSettings#apply` 对非 null 的 `username` / `password` 无条件赋值，而 `SkywalkingApmProperties` 的默认值是空串。因此桥接生效（`url` 非空）且未填鉴权信息时，会把进程内的用户名/密码置为空串，**不会**沿用 `skywalking.username` / `skywalking.password`。需要鉴权请在本前缀下显式填写（以代码为准）。
3. **鉴权信息不参与热改**：`runtimeSettings()` 刻意只暴露 `oapUrl` 与 `oapTimeout`，用户名/密码属于部署机密，界面不回显、也不能通过 `POST /config` 修改。
4. **`oapTimeout` 校验严格**：必须是正整数毫秒，`0` 或负数或非数字都会抛 `IllegalArgumentException`，`POST /config` 会把它包成失败结果返回。
5. **`oapUrl` 必须带协议**：仅接受 `http://` / `https://` 前缀（大小写不敏感），否则拒绝。
6. **引擎惰性创建并缓存**：`engine()` 第一次调用才去 `ServiceProvider.getExtension("skywalking")`；失败时返回 `null` 并只打 `debug` 日志，此时 `ApmRegistry` 会把本实现视为不可用并跳过。排障请开 `logging.level.com.chua.starter.apm=DEBUG`。
7. **重置靠 `close()`**：`applyRuntimeSettings` 之后调用 `engine.close()` 丢弃状态缓存与 OAP 连接，下一次请求按新的 `SkywalkingSettings.url()` 重建连接；`SkywalkingOtnlEngine` 的 client / executor 是惰性创建的，所以关闭后同一实例可以继续用。
8. **不做连通性预检**：`/status` 的可用性来自 `SkywalkingOtnlEngine#status()`，本模块不额外探活；OAP 挂掉的原因由引擎回填到 `ApmDescriptor#message()`。
9. **本模块不新增 REST 路径**：所有接口都在 `spring-support-apm-starter` 的 `{plugin.apm.path}` 下（默认 `/api/apm/v1`），本模块也没有 `META-INF/services` 形式的 SPI。
