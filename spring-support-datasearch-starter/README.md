# Spring Support DataSearch Starter

数据检索服务化：把 `utils-support-datasearch-starter` 的数据源 SPI 归一成前端唯一契约，并以 `/v2/**` 暴露。

## 提供的接口

| 接口 | 说明 | 前端消费方 |
| --- | --- | --- |
| `GET /v2/weather/overview` | 天气总览（实况 + 逐小时 + 逐日），参数 `latitude/longitude/city/days/hours` 全可选 | `@repo/core` `getWeatherOverview()` |
| `GET /v2/location/resolve` | 按请求来源 IP 解析展示位置（内网退回服务器自身定位） | `@repo/core` `resolveGeoLocation()` |
| `GET /v2/typhoon/active` | 当前活跃台风列表（无活跃台风返回空数组） | `@repo/core` `getActiveTyphoonList()` |
| `GET /v2/typhoon/detail?id=` | 单个台风详情（历史路径 + 预报路径） | `@repo/core` `getTyphoonDetail()` |
| `GET /v2/exchange-rate/overview` | 汇率概览（最新价 + 周期涨跌 + 周期极值），参数 `symbols`（逗号分隔，可选）、`period`（1m/3m/6m/12m，可选） | `@repo/core` `getExchangeRateOverview()` |
| `GET /v2/holiday/get?year=` | 某年法定节假日与调休补班，按日期逐行；`year` 缺省当年 | `@repo/core` `fetchHolidays()` |
| `GET /v2/ip/lookup?ip=` | IP 归属地（国家/省/市/运营商 + 经纬度/邮编/时区） | `@repo/ui` `ReIpText` |

七个接口均 `@TokenForIgnore`：**顶栏与日历在登录前就可能渲染**，必须匿名可访问。参数全部可选，
启动期的 HEAD 探测不带查询串也要能命中映射（前端 `probeBackend` 用 HEAD 判定能力是否存在）。

## 契约是硬约束

前端对后台数据**原样透传、不做字段补齐**（`packages/core/src/api/weather.ts`、`typhoon.ts`），所以：

- 数据源给出的字段必须**全部映射**到出参，缺一项 UI 对应位置就显示 `-`；
- 天气现象一律用 WMO 4677 数字码，文案与图标由前端按码推导；
- 时间统一 `yyyy-MM-ddTHH:mm`（与前端 `canonTime` 同口径，带偏移的报文折算到本地时区）；
- 风圈报文 `"250|150|250|150"` 在此解析成四方位数组 `[东北, 东南, 西南, 西北]`（公里）；
- 无可用数据源时返回 `ReturnResult.error(...)`（HTTP 200 + 失败码），前端据此回落三方，**不要**返回半截数据；
- 唯一的例外是节假日：**「这一年没有放假数据」返回空数组**，空是权威结论，失败（数据源整体不可用）
  才返回失败码 —— 前端把两者区分对待（空数组 → 不重复探测，失败 → 退回内置表）。

新增数据源字段时，务必同步前端契约（`@repo/core` 的 TS 接口）与本模块出参。

## 数据源（SPI）

来自 `utils-support-datasearch-starter`，按 SPI 注册顺序取用，全部不可达时返回空交由前端降级：

- 天气：`OpenMeteoWeatherProvider`、`WttrInWeatherProvider`
- 台风：`ZhejiangTyphoonProvider`（浙江省水利厅台风网）
- 定位：`IpApiLocationProvider`
- 反查地名：`BigDataCloudGeocodeProvider`
- 节假日：`OnlineHolidayProvider`（holiday-cn 年度 JSON，内置 2026 官方安排兜底）
- 汇率：`OpenExchangeRateProvider`（open.er-api.com，USD 基准全量表，6 小时内存缓存）

汇率与节假日另有两处**非 SPI 兜底**，目的是让「宿主没引 utils 数据检索模块」或「SPI 抓取失败」
时顶栏不至于整块空白：

- 汇率最新价回落到 frankfurter `/latest`，周期序列走 frankfurter 日期区间接口（一次拿整段，
  不逐日外呼），历史序列按「base + quotes + 天数」缓存 10 分钟；
- 节假日的年份解析失败时返回空数组而非异常，日历仍有内置表可渲染。

IP 归属地不走 `IptablesService`，而是直接消费 `IpPosition` SPI（ip2region / geoip2 本地库）：
`IptablesService` 在接口上钉了 `@Cacheable(cacheManager = "redis-cache-600")`，该管理器由
redis + ai 侧的配置提供，没有它的宿主首次调用就会抛 `Cannot find cache named 'redis-cache-600'`。
IP 归属地在本模块还需要「同一 IP 被页面上多处渲染」的去重，进程内缓存比走 Redis 更合适。

## 装配方式

以**自动配置**注册（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`），宿主引入依赖即生效：

- 生效条件：Servlet Web 应用；`plugin.datasearch.enabled` 不为 `false`（默认开启）；
- 宿主已自建同名 Bean 时逐个退让（`@ConditionalOnMissingBean`）；
- **不要求** `@ComponentScan` 覆盖 `com.chua.starter.datasearch`（多数宿主只扫自己的子包，扫不到这里）。

## 依赖方向

放在 spring 层的原因：这套能力是"平台可选增强"，`ai` / `system` / `monitor` 等各类宿主都可能要，而 spring 层不得反向依赖 api 层。

- 本模块是**唯一实现**：`spring-api-support-common-service-starter`（api 业务层）已移除原先的同名类，改为依赖本模块，避免两份代码分叉；
- 其余 api 侧宿主（`-system` / `-oauth` / `-monitor` 等）经由 common-service-starter 传递获得同一份实现。

## 前端配合

前端用「业务能力开关」控制是否探测（`app.yaml` → `other.capabilities`）：

```yaml
other:
  capabilities:
    weather: true       # 天气总览
    location: true      # 定位解析
    typhoon: true       # 活跃台风
    exchangeRate: true  # 汇率概览
    holiday: true       # 节假日
    ipLookup: true      # IP 归属地
```

- `false` = 后台不提供该能力：前端**不发探测请求**（避免每次启动的 404 噪音），对应入口一并隐藏；
- 能力清单与探测路径见 `packages/core/src/api/capability.ts`，新增能力在此登记一条即可，探测、显隐、开关、app.yaml 自动全链路生效。

## 不含的能力

- `/v2/holiday/sync`（从三方同步到本地库）：本模块**不读也不写** `sys_holiday` 表，没有可同步的
  落地目标，同步仍由 system 模块的 `SysHolidayController` 负责，前端据其返回值提示「尚未实现」。
- `/v2/ip/lookup` **已在本模块实现**（原 README 写「不在本模块」的原因见上：当时依赖的是带
  `redis-cache-600` 的 `IptablesService`，现已改为直连 `IpPosition` SPI）。若宿主同时引入了
  api 侧 `SysIpController`，两边路径相同 —— Spring MVC 会因模糊映射在启动时失败，
  部署侧只需保留一处（本模块的 Bean 带 `@ConditionalOnMissingBean`，宿主自建 `IpLookupV2Controller`
  时自动退让）。
