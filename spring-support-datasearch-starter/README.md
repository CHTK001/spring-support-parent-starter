# spring-support-datasearch-starter

把 `utils-support-datasearch-starter` 的数据源 SPI 归一成前端唯一契约，并以 `/v2/**` 暴露：天气 / 定位 / 台风 / 汇率 / 节假日 / IP 归属地。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 自动配置 | `DataSearchServiceConfiguration` | `META-INF/spring/...AutoConfiguration.imports` |
| 天气总览 | `WeatherV2Controller` + `WeatherDataService` / `WeatherDataServiceImpl` | `GET /v2/weather/overview` |
| 定位解析 | `LocationV2Controller`（复用 `WeatherDataService#resolveLocation`） | `GET /v2/location/resolve` |
| 台风列表 / 详情 | `TyphoonV2Controller` + `TyphoonDataService` | `GET /v2/typhoon/active`、`GET /v2/typhoon/detail` |
| 汇率概览 | `ExchangeRateV2Controller` + `ExchangeRateDataService` | `GET /v2/exchange-rate/overview` |
| 节假日 | `HolidayV2Controller` + `HolidayDataService` | `GET /v2/holiday/get` |
| IP 归属地 | `IpLookupV2Controller` + `IpLookupDataService` | `GET /v2/ip/lookup` |
| SPI 装载 | `SpiProviders#list(Class)`（按实现类去重、保持注册顺序、失败返回空列表） | 各 `*DataServiceImpl` 内部调用 |
| 响应包装 | `ReturnResult.ok/error`（HTTP 200 + 业务码） | 六个控制器 |

七个接口全部标注 `@TokenForIgnore`：**顶栏与日历在登录前就可能渲染**，必须匿名可访问。除台风详情的 `id` 外参数全部可选，启动期的 HEAD 探测不带查询串也要能命中映射（前端 `probeBackend` 用 HEAD 判定能力是否存在）。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-datasearch-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 说明 |
|---|---|---|
| `spring-boot-starter-web` | `@RestController` / `@GetMapping` / MVC 绑定 | 必需 |
| `spring-boot-autoconfigure` | `@AutoConfiguration`、条件注解 | 必需 |
| `utils-support-datasearch-starter` | `WeatherProvider` / `TyphoonProvider` / `LocationProvider` / `GeocodeProvider` / `HolidayProvider` / `ExchangeRateProvider` SPI 与模型 | 必需 |
| `utils-support-common-starter` | `ReturnResult`、`ServiceProvider`、`IpPosition` | 必需 |
| `spring-support-oauth-client-starter` | `@TokenForIgnore` | 必需（匿名放行） |
| `io.swagger.core.v3:swagger-annotations` | `@Tag` / `@Operation` / `@Schema` | 仅文档用途 |
| `jakarta.servlet-api` | `HttpServletRequest`（定位接口按来源 IP 解析） | `provided` |
| `lombok` | 构造注入 / 日志 | `provided` |

## 三、配置项

模块内无 `@ConfigurationProperties` 类；唯一的配置项由 `@ConditionalOnProperty` 直接读取：

| key | 默认值 | 说明 |
|---|---|---|
| `plugin.datasearch.enabled` | `true`（`matchIfMissing = true`） | 整体开关。`false` 时六个控制器与五个数据服务都不注册 |

生效条件（三个同时满足）：Servlet Web 应用（`@ConditionalOnWebApplication(SERVLET)`）、开关不为 `false`、宿主未自建同名 Bean（逐个 `@ConditionalOnMissingBean`）。

## 四、快速开始

**第一步**：引入依赖。宿主需是 Servlet Web 应用，并已装配 `spring-support-oauth-client-starter` 的 `@TokenForIgnore` 支持（否则接口会被鉴权拦下）。

**第二步**：确认开关（默认已开，可不写）：

```yaml
plugin:
  datasearch:
    enabled: true
```

**第三步**：直接访问任一接口验证，例如：

```bash
curl -s "http://localhost:8080/v2/weather/overview?city=杭州&days=3&hours=12"
```

**不需要** `@ComponentScan` 覆盖 `com.chua.starter.datasearch`——多数宿主只扫自己的子包，扫不到这里，所以本模块用自动配置注册 Bean。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API：对外是 HTTP 契约，对内是普通 Bean 方法（返回 VO / `List`），没有 Builder 或 `return this`。以下为步骤式用法。

**HTTP 契约**：

| 方法与路径 | 参数（全可选，除注明外） | 出参 |
|---|---|---|
| `GET /v2/weather/overview` | `latitude`、`longitude`、`city`、`days`（默认 7，上限 16）、`hours`（默认 24，上限 72） | `WeatherOverviewVO`（实况 + 逐小时 + 逐日） |
| `GET /v2/location/resolve` | 无（取请求头 `X-Forwarded-For` / `X-Real-IP` / `Proxy-Client-IP` / `WL-Proxy-Client-IP`） | `LocationVO` |
| `GET /v2/typhoon/active` | 无 | `List<TyphoonItemVO>`；无活跃台风时为空数组 |
| `GET /v2/typhoon/detail` | `id`（**必填**，如 `202618`） | `TyphoonDetailVO`（历史路径 + 预报路径） |
| `GET /v2/exchange-rate/overview` | `symbols`（逗号分隔或重复传参，如 `USD/EUR,USD/CNY`）、`period`（`1m`/`3m`/`6m`/`12m`，默认 `1m`） | `ExchangeRateOverviewVO`（最新价 + 周期涨跌 + 周期极值） |
| `GET /v2/holiday/get` | `year`（缺省当年） | `List<HolidayVO>`，按日期逐行 |
| `GET /v2/ip/lookup` | `ip`（支持 `1.2.3.4:8080`，端口忽略） | `IpLookupVO`（国家/省/市/运营商 + 经纬度/邮编/时区） |

`symbols` 同时接受两种前端序列化方式：`symbols=USD/EUR,USD/CNY` 与 axios 默认的 `symbols[]=USD/EUR&symbols[]=USD/CNY`（`ExchangeRateV2Controller#flatten`）。

**几个可直接粘贴的请求**：

```bash
# 台风：先取活跃列表，再按编号取详情
curl -s "http://localhost:8080/v2/typhoon/active"
curl -s "http://localhost:8080/v2/typhoon/detail?id=202618"

# 汇率：默认周期 1m
curl -s "http://localhost:8080/v2/exchange-rate/overview?symbols=USD/CNY,USD/EUR&period=3m"

# 节假日 / IP 归属地
curl -s "http://localhost:8080/v2/holiday/get?year=2026"
curl -s "http://localhost:8080/v2/ip/lookup?ip=114.114.114.114"
```

**服务端直接调用**（跳过 HTTP，适合内部聚合）：

```java
@Service
@RequiredArgsConstructor
public class TopBarService {

    private final WeatherDataService weatherDataService;
    private final HolidayDataService holidayDataService;

    public WeatherOverviewVO weather(String city) {
        WeatherQuery query = new WeatherQuery();
        query.setCity(city);
        query.setDays(3);
        query.setHours(12);
        return weatherDataService.overview(query);   // 无可用数据源时返回 null
    }

    public List<HolidayVO> holidays(Integer year) {
        return holidayDataService.list(year);        // 无数据源时返回 null
    }
}
```

**契约是硬约束**（前端对后台数据原样透传、不做字段补齐，见 `packages/core/src/api/weather.ts`、`typhoon.ts`）：

- 数据源给出的字段必须**全部映射**到出参，缺一项 UI 对应位置就显示 `-`；
- 天气现象一律用 WMO 4677 数字码，文案与图标由前端按码推导；
- 时间统一 `yyyy-MM-ddTHH:mm`（与前端 `canonTime` 同口径，带偏移的报文折算到本地时区）；
- 风圈报文 `"250|150|250|150"` 在此解析成四方位数组 `[东北, 东南, 西南, 西北]`（公里）；
- 无可用数据源时返回 `ReturnResult.error(...)`（HTTP 200 + 失败码），前端据此回落三方，**不要**返回半截数据；
- 唯一例外是节假日：**「这一年没有放假数据」返回空数组**，空是权威结论，失败（数据源整体不可用）才返回失败码——前端把两者区分对待（空数组 → 不重复探测，失败 → 退回内置表）。

新增数据源字段时，务必同步前端契约（`@repo/core` 的 TS 接口）与本模块出参。

### 5.2 SPI 扩展示例

扩展点在 `utils-support-datasearch-starter`，本模块通过 `SpiProviders.list(...)` 消费。以新增天气源为例：

**实现**（接口只有 `name()`、`getWeather(String city)`、`getWeather(Double latitude, Double longitude, Integer days)`；坐标重载有默认实现返回 `null`，不支持坐标时可只覆盖城市查询）：

```java
@Spi("my-weather")
public class MyWeatherProvider implements WeatherProvider {

    @Override
    public String name() { return "my-weather"; }

    @Override
    public WeatherInfo getWeather(String city) {
        // 拉取并转成 WeatherInfo（天气现象填 WMO 4677 数字码）
        return null;
    }

    @Override
    public WeatherInfo getWeather(Double latitude, Double longitude, Integer days) {
        // 支持坐标查询时覆盖此方法
        return null;
    }
}
```

**登记**：在自有 jar 下新建 `META-INF/extensions/com.chua.common.support.datasearch.weather.spi.WeatherProvider`，一行一条 `名称=实现类全限定名`：

```text
my-weather=com.example.weather.MyWeatherProvider
```

**生效**：把 jar 放进宿主 classpath 即可，无需改本模块。装配顺序即 SPI 注册顺序（`SpiProviders#list` 用 `LinkedHashMap` 去重保序），天气侧逐个尝试、**优先采用带 WMO 天气码的结果**，全部缺码时才退回首个结果。

### 5.3 余额查询内核（credit/）

`credit/` 目录是「上游余额 / 积分读数」的共用内核，账户页与 AI 供应商页都通过
`CreditQueries.query(CreditQuery)` 消费，差异全在 `CreditQueryProvider` SPI 实现里：

| 实现（`kind`） | 用途 |
|---|---|
| `expression` | 通用「地址 + 字段表达式」，没专用实现的上游走这里 |
| `new-api` / `sub2api` / `openai` | 中转站 / OpenAI 官方 |
| `host` | 厂商官方端点，按 `providerId` 取内置地址与表达式 |

- **收录口径**：只登记「官方文档明确写了余额 / 额度端点」的厂商，不猜地址、不标「支持」。
  现有 11 家 Bearer 预设 + 4 家 AK/SK 签名预设，清单与逐家依据见
  `HostCreditQueryProvider.HOSTS` 与各 README 的「已收录 / 不收录」小节。
- **字段表达式**（`CreditExpressions`）：`$ ¥ ￥ € £` 币种前缀、点路径 / 数组下标 / `*` 通配、
  空格包围的 `* N` / `/ N` 换算、`%` 比例、`!` 已用占比翻转、`;` 多段。
- **AK/SK 签名**（`CreditSigner`）：阿里云 BSS、火山引擎、百度智能云、七牛四家要按各家规范
  对请求做 HMAC 签名，只发 Bearer 查不到。签名器直接产出「完整 URL + 最终请求头 + body」，
  `CreditHttp` 原样发出、不二次编码，避免签名字节与实际请求错位（这类错位只表现为上层 401）。
  `signType` 由 host 预设按厂商注入，AK/SK 由账户页「余额查询」配置加密落库、查询前解密传入；
  缺 AK/SK 时返回明确提示，不发必然 401 的请求。四种方案的文档链接与字节级口径见
  `CreditSigner` 类注释。
- **失败语义**：查不到一律返回 `CreditResult.fail(...)`（不抛异常），页面按原文提示；
  「N 个上游逐个查」不会因为某一家超时把整页带崩。

## 六、与其它 starter 的关系

- **上游**：`utils-support-datasearch-starter`（全部数据源 SPI 与 `WeatherInfo` 等模型）、`utils-support-common-starter`（`ReturnResult`、`ServiceProvider`、`IpPosition`）、`spring-support-oauth-client-starter`（`@TokenForIgnore`）。
- **本模块是唯一实现**：`spring-api-support-common-service-starter`（api 业务层）已移除原先的同名类，改为依赖本模块，避免两份代码分叉；其余 api 侧宿主（`-system` / `-oauth` / `-monitor` 等）经由 common-service-starter 传递获得同一份实现。
- **`spring-support-ai-starter`**：依赖本模块以获取 utils 层的 `UsageParser`、`ModelMetricsProvider`、`UsagePricingReconciler` 契约（会同时带上本模块的 `/v2/**` 接口）。
- **`spring-support-monitor-starter`**：`MonitorAutoConfiguration` 注册 `DatasearchConflictResolve`，把宿主里旧的 `/v2/holiday/get`、`/v2/ip/lookup` 控制器移走，避免与本模块同路径双映射导致 `Ambiguous handler methods mapped`。
- **前端**：能力开关在 `app.yaml` → `other.capabilities`（`weather` / `location` / `typhoon` / `exchangeRate` / `holiday` / `ipLookup`）。`false` 表示后台不提供该能力：前端**不发探测请求**（避免每次启动的 404 噪音），对应入口一并隐藏；能力清单与探测路径见 `packages/core/src/api/capability.ts`，新增能力在此登记一条即可。

放在 spring 层的原因：这套能力是「平台可选增强」，`ai` / `system` / `monitor` 等各类宿主都可能要，而 spring 层不得反向依赖 api 层。

## 七、已知限制

1. **契约不可协商**：出参字段、时间格式、WMO 天气码、风圈数组顺序都是前端硬依赖；改字段必须同步前端 `@repo/core`，否则 UI 直接缺项。
2. **失败返回 200**：数据源不可用时是 `ReturnResult.error(...)`（HTTP 200 + 失败码），不是 5xx；调用方必须看业务码，不能只看 HTTP 状态。
3. **空的语义分两种**：台风 `active` 与节假日「该年无数据」返回空集合（权威结论），数据源整体不可用才返回失败码；两者不可混用。
4. **定位不猜 IP**：客户端来源 IP 无法确认（内网 / 回环）或查不到归属地时直接返回 `null`（控制器回失败码），**不会退回服务器自身定位**——把服务器地址当客户端地址会导致顶栏显示错城市。
5. **IP 归属地不走 `IptablesService`**：直接消费 `IpPosition` SPI（ip2region / geoip2 本地库）。`IptablesService` 在接口上钉了 `@Cacheable(cacheManager = "redis-cache-600")`，该管理器由 redis + ai 侧配置提供，没有它的宿主首次调用会抛 `Cannot find cache named 'redis-cache-600'`；本模块还需要「同一 IP 被页面多处渲染」的去重，进程内缓存更合适。
6. **对外部数据源有兜底但不是万能**：汇率最新价与历史序列会回落到 frankfurter（历史序列按「base + quotes + 天数」缓存 10 分钟），节假日年份解析失败返回空数组；天气/台风没有额外的三方兜底，SPI 全不可用即为空。
7. **不含 `/v2/holiday/sync`**：本模块**不读也不写** `sys_holiday` 表，没有可同步的落地目标；同步仍由 system 模块的 `SysHolidayController` 负责，前端据其返回值提示「尚未实现」。
8. **可能与 api 侧接口撞路径**：若宿主同时引入 api 侧 `SysIpController`（同样映射 `/v2/ip/lookup`），Spring MVC 会因模糊映射在启动时失败。部署侧只需保留一处；本模块的 Bean 带 `@ConditionalOnMissingBean`，宿主自建 `IpLookupV2Controller` 时自动退让。
9. **仅 Servlet Web**：`@ConditionalOnWebApplication(type = SERVLET)`，WebFlux 宿主不装配任何控制器。
