# Spring Support DataSearch Starter

数据检索服务化：把 `utils-support-datasearch-starter` 的数据源 SPI 归一成前端唯一契约，并以 `/v2/**` 暴露。

## 提供的接口

| 接口 | 说明 | 前端消费方 |
| --- | --- | --- |
| `GET /v2/weather/overview` | 天气总览（实况 + 逐小时 + 逐日），参数 `latitude/longitude/city/days/hours` 全可选 | `@repo/core` `getWeatherOverview()` |
| `GET /v2/location/resolve` | 按请求来源 IP 解析展示位置（内网退回服务器自身定位） | `@repo/core` `resolveGeoLocation()` |
| `GET /v2/typhoon/active` | 当前活跃台风列表（无活跃台风返回空数组） | `@repo/core` `getActiveTyphoonList()` |
| `GET /v2/typhoon/detail?id=` | 单个台风详情（历史路径 + 预报路径） | `@repo/core` `getTyphoonDetail()` |

四个接口均 `@TokenForIgnore`：**顶栏在登录前就要出数**，必须匿名可访问。

## 契约是硬约束

前端对后台数据**原样透传、不做字段补齐**（`packages/core/src/api/weather.ts`、`typhoon.ts`），所以：

- 数据源给出的字段必须**全部映射**到出参，缺一项 UI 对应位置就显示 `-`；
- 天气现象一律用 WMO 4677 数字码，文案与图标由前端按码推导；
- 时间统一 `yyyy-MM-ddTHH:mm`（与前端 `canonTime` 同口径，带偏移的报文折算到本地时区）；
- 风圈报文 `"250|150|250|150"` 在此解析成四方位数组 `[东北, 东南, 西南, 西北]`（公里）；
- 无可用数据源时返回 `ReturnResult.error(...)`（HTTP 200 + 失败码），前端据此回落三方，**不要**返回半截数据。

新增数据源字段时，务必同步前端契约（`@repo/core` 的 TS 接口）与本模块出参。

## 数据源（SPI）

来自 `utils-support-datasearch-starter`，按 SPI 注册顺序取用，全部不可达时返回空交由前端降级：

- 天气：`OpenMeteoWeatherProvider`、`WttrInWeatherProvider`
- 台风：`ZhejiangTyphoonProvider`（浙江省水利厅台风网）
- 定位：`IpApiLocationProvider`
- 反查地名：`BigDataCloudGeocodeProvider`

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
    weather: true      # 天气总览
    location: true     # 定位解析
    typhoon: true      # 活跃台风
    ipLookup: false    # IP 归属地：由 api 业务层 SysIpController 提供，本模块不含
```

- `false` = 后台不提供该能力：前端**不发探测请求**（避免每次启动的 404 噪音），对应入口一并隐藏；
- 能力清单与探测路径见 `packages/core/src/api/capability.ts`，新增能力在此登记一条即可，探测、显隐、开关、app.yaml 自动全链路生效。

## 不含的能力

`/v2/ip/lookup`（IP 转物理地址）**不在本模块**：它依赖 `IptablesService` 的
`@Cacheable(cacheManager = "redis-cache-600")`，该 cacheManager 由 api 侧缓存配置提供，
下沉后宿主首次调用即失败。保持由 `spring-api-support-common-system-starter` 的
`SysIpController` 提供，对应前端开关 `other.capabilities.ipLookup`。
