# spring-support-device-starter

设备接入 Starter：把海康（云曜 / 云眸 / 综合安防 / 萤石 / ISUP）、大华（云平台 / 乐橙 / ICC）、宇视、
精讯畅通、GB28181、ONVIF 收敛成一套 `DeviceProvider` + `DeviceSession` SPI；业务侧只依赖 SPI，
不直接碰各家的鉴权头、报文结构与能力差异。

- 包名：`com.chua.starter.device`
- 版本：`4.0.0.51`
- 口径：官方接口登记在 `provider/sdk/<vendor>/*Apis.java`，支持度登记在
  `provider/sdk/DeviceCapabilityMatrix`。只有 `YES`（真机验证）与 `IMPLEMENTED_UNVERIFIED`
  （已实现未验证）会派生平台能力位；`TODO` / `NO` 一律隐藏，原因写在能力格子的 `note` 里。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 厂商接入 SPI | `DeviceProvider`、`DeviceSession`、`DeviceVendorDescriptor` | 容器中所有 `DeviceProvider` Bean |
| 厂商实现 | `HikvisionProvider`、`DahuaProvider`、`UniviewProvider`、`SennorProvider`、`StandardProtocolProvider` | `vendor` + `platformType` |
| 平台 SDK 门面 | `DevicePlatformSdks`、`DevicePlatformSdk`、`DevicePlatformSdkProvider` | `ServiceProvider<DevicePlatformSdkProvider>` |
| 接口目录 | `HikvisionArtemisApis`、`HikvisionCloudYaoApis`、`HikvisionCloudEyeApis`、`EzvizApis`、`HikvisionIsupApis`、`DahuaCloudApis`、`LeChangeApis`、`DahuaIccApis`、`UniviewApis`、`SennorApis`、`Gb28181Apis` | 只登记核实过的官方路径 |
| 能力矩阵 | `DeviceCapabilityMatrix`（16 能力位 × 12 平台）、`DeviceCapability` | `GET /api/device/capability/matrix`、`capabilitiesOf(vendor, platform)` |
| 设备台账 | `DeviceService`、`DeviceInfo`、`DeviceSessionManager`（按凭据缓存会话） | `/api/device/**` |
| 通道 / 视频 / 云台 | `ChannelController`、`VideoController`、`PtzController` | `/api/device/channel`、`/api/device/video`、`/api/device/ptz` |
| 门禁 | `AccessController`（`/unlock`、`/grant`、`/records`）、`DeviceAccessRecordService` | `/api/device/access` |
| 事件 / 告警 | `EventController`、`DeviceAlarmController` | `/api/device/events`、`/api/device/alarms` |
| 人员 / 考勤 | `PersonController`、`AttendanceController` | `/api/device/person`、`/api/device/attendance` |
| 车辆 / 道闸 | `VehicleController`、`BarrierGateController` | `/api/device/vehicle`、`/api/device/barrier` |
| 环境指标 | `DeviceMetricService`、`DeviceMetricLatest`、`metricCollect_<credentialId>` | `/api/device/metrics/{deviceId}`、设备详情「指标」页 |
| 时序存储 | `DeviceTimeSeriesEngine`（bean/influxdb/redis）、`DeviceSeriesRecorder`、`DeviceSeriesCategory` | `/api/device/series`、传感器记录页、门禁/车闸记录页 |
| 分组 / 拓扑 | `DeviceGroupController`、`DeviceTopologyController` | `/api/device/group`、`/api/device/topology` |
| 目录同步 | `DeviceSyncServiceImpl#syncDevicesByCredential`、`#syncDevices`、`#pushDevice`、`#removeRemote` | `/api/device/sync` |
| 凭据管理 | `DeviceProviderCredentialService`、`ProviderCredentialController` | `/api/device/provider-credential` |
| webhook 入站 | `DeviceWebhookService#verify`、`DeviceWebhookDefaults` | `/api/device/webhook/{access-records,alarms,vehicle-passes,attendance-records}` |
| 生命周期回调 | `DeviceLifecycleCallback`、`DeviceLifecyclePublisher` | `/api/device/callbacks` |
| 实时推送 | `DeviceRealtimePublisher`、`SocketDeviceRealtimePublisher` | 宿主引入 socket-starter 时装配 |
| 定时任务 | `DeviceScheduleManager`、`DeviceTaskStateService` | `/api/device/tasks` |
| 密钥编解码 | `DeviceSecretCodec`（`enc:` 前缀，AES-GCM） | `device.security.secret-key` |
| 服务信息 | `ServiceInfoContributor`（`device` 标志 + 设备/通道/视频/门禁等 endpoint） | 启动横幅 |

### 1.1 DeviceProvider（厂商入口）

| 方法 | 说明 |
|---|---|
| `getVendor()` | 厂商标识，如 `hikvision`、`dahua`、`uniview`、`standard` |
| `support(DeviceConnectInfo)` | 是否接管该连接信息 |
| `connect(DeviceConnectInfo)` | 建立会话，返回 `DeviceSession` |
| `descriptor()` | 厂商描述（名称、接入方式、平台、能力位、默认离线阈值、文档、说明） |
| `fetchDevices()` / `fetchDevices(session)` | 设备同步（平台级；带 session 版本按凭据隔离） |
| `fetchOrganizations()` / `fetchPersons(session)` | 组织、人员同步（平台有则实现，默认空列表） |
| `upsertRemote` / `removeRemote` | 设备写操作（默认 `false`；由 `DeviceSyncServiceImpl` 按设备凭据路由） |
| `upsertOrganization` / `removeOrganization` | 组织写操作（默认不支持） |
| `upsertPerson` / `removePerson` | 人员写操作（默认不支持） |
| `remoteUnlock(doorSerial)` | 平台级远程开门（不需要先建设备会话） |

### 1.2 DeviceSession（设备会话）

| 分组 | 方法 |
|---|---|
| 基础 | `getDeviceId()`、`isOnline()`、`testConnection()`、`close()` |
| 通道 | `listChannels()` |
| 直播 | `getLiveUrl(channel, protocol)`、`getLiveUrl(channel, protocol, streamType)`、`startLive(...)`、`stopLive(streamId)`、`preview(channel)` |
| 录像 | `queryRecords(channel, range)`、`getPlaybackUrl(channel, range)`、`playback(channel, range)` |
| 云台 | `ptzControl(cmd, speed)`、`setPreset`、`callPreset`、`deletePreset`、`listPresets` |
| 抓图 | `capture(channel)` |
| 事件 | `subscribeEvent`、`unsubscribeEvent`、`pollEvents(range)` |
| 门禁 | `listDoors()`、`remoteUnlock(doorId)`、`grantDoorAccess(personId, doorId)`、`queryAccessRecords(range)` |
| 车辆 / 道闸 | `listVehicles()`、`upsertVehicle`、`deleteVehicle`、`listBarriers()`、`openBarrier`、`queryVehiclePasses(range[, afterId])` |
| 环境指标 | `readMetrics()`（气象站 / 温湿度等节点实时值） |

约定：

- 直播以 **URL 取流**为主（HLS / FLV / RTSP）；`preview` / `playback` 这类字节流接口只在厂商 SDK
  能直接出流时实现。HTTP 平台不覆写时沿用基类行为，**不代表已实现**。
- 平台没有的能力必须抛 `UnsupportedOperationException` 并写明原因，不返回空列表 / 空字符串冒充成功。
- 会话由 `DeviceSessionManager` 按凭据缓存，业务侧无需手工持有。

### 1.3 平台覆盖（能力矩阵）

状态口径：

| 状态 | 含义 |
|---|---|
| `yes` | 已接通并有真实调用验证 |
| `unverified` | 已按官方文档或既有网关契约实现，缺环境 / 凭据，未做真机验证 |
| `todo` | 官方接口 / 协议已核实，代码未实现 |
| `no` | 平台公开接口 / 协议不能完整支持该能力（无接口、仅推送、仅相邻 / 部分接口或业务模型不存在；不等于接口目录零路径） |

当前统计（12 个平台 × 16 个能力位 = 192）：

| 平台 | platformType | yes | unverified | todo | no |
|---|---|---:|---:|---:|---:|
| 海康云曜 | `cloud-yao` | 1 | 10 | 0 | 5 |
| 海康云眸 | `cloud-eye` | 1 | 12 | 0 | 3 |
| 海康综合安防 | `secure-platform` | 1 | 13 | 0 | 2 |
| 海康萤石 | `ezviz` | 6 | 7 | 0 | 3 |
| 海康 ISUP | `isup` | 1 | 4 | 0 | 11 |
| 大华云平台 | `dahua-cloud` | 0 | 14 | 0 | 2 |
| 大华乐橙 | `lechange` | 1 | 8 | 0 | 7 |
| 大华 ICC | `dahua-icc` | 1 | 12 | 0 | 3 |
| 宇视云开放平台 | `uniview` | 0 | 8 | 0 | 8 |
| GB28181 | `gb28181` | 1 | 4 | 1 | 10 |
| ONVIF | `onvif` | 0 | 5 | 4 | 7 |
| 精讯云 | `sennor-cloud` | 0 | 2 | 0 | 14 |
| **合计** | | **13** | **99** | **5** | **75** |

已标 `yes` 的能力位（共 13 格）：

| 平台 | 能力 |
|---|---|
| 海康云曜 | DEVICE |
| 海康云眸 | DEVICE |
| 海康综合安防 | DEVICE |
| 海康萤石 | DEVICE、CHANNEL、PERSON、PERSON_WRITE、EVENT、ACCESS |
| 海康 ISUP | DEVICE |
| 大华乐橙 | DEVICE |
| 大华 ICC | DEVICE |
| GB28181 | DEVICE |

> `yes` 只覆盖上表能力位，其余按 `unverified` / `todo` / `no` 计，不能外推。
> 当前 `todo` 为 5：GB28181 `EVENT`，ONVIF `ACCESS` / `ACCESS_WRITE` / `EVENT` / `RECORD`；
> 均为官方接口 / 协议已核实、会话层未实现。ONVIF 门禁与录像目前只到 Profile 级。
> `no` 中仍可能保留 1~2 个仅部分 / 相邻接口，原因写在能力矩阵的 `note` 里。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-device-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 说明 |
|---|---|---|
| `spring-boot-starter-web` | 控制器 / Jackson | 必需 |
| `com.baomidou:mybatis-plus-spring-boot4-starter` | ORM | 必需 |
| `spring-support-mybatis-starter` | `PageResult`、分页 | 必需 |
| `spring-support-common-starter` | `ServiceProvider` SPI 注册表、`ServiceInfoContributor` | 必需 |
| `spring-support-swagger-starter` | OpenAPI 注解 | 必需 |
| `com.hikvision.ga:artemis-http-client:1.1.3` | 综合安防 Artemis 签名（`x-ca-*`） | 必需；官方唯一安防类 SDK |
| `org.apache.poi:poi-ooxml:5.5.1` | Excel 导出 | 必需 |
| `jna` / `jna-platform:5.19.1` | 海康 / 大华本地 SDK 直连 | 必需 |
| `spring-support-socket-starter` | 设备事件实时推送 | `optional`，宿主引入后按 `@ConditionalOnClass` 装配 |
| `spring-support-oauth-client-starter` | `@TokenForIgnore`（webhook 入站免登录态） | `optional`，注解缺失时静默失效 |

## 三、配置项

本模块有一组 `@ConfigurationProperties`（时序引擎）与一处 `@Value`：

| key | 默认值 | 说明 |
|---|---|---|
| `device.security.secret-key` | 空 | 设备密码 / 厂商 AppSecret 落库前的 AES-GCM 密钥。未配置时保持明文并打印 WARN；配置后新写入值带 `enc:` 前缀，历史明文仍可读（下次写入转密文） |

### 3.1 设备时序引擎（`plugin.device.timeseries.*`）

传感器历史、门禁通行、车闸过车三类记录都写进同一个时序契约
（`DeviceTimeSeriesEngine`：分类 + 设备 + 序列键 + 值 + 时间），
存储后端由 `engine` 切换，**业务代码与页面接口不变**：

```yaml
plugin:
  device:
    timeseries:
      engine: bean            # bean（默认）/ influxdb / redis / none
      retention-days: 30       # 保留天数，每天 03:30 清理；<=0 表示不清理
      table: device_series_point   # bean 引擎明细表（只允许字母/数字/下划线）
      url: http://172.16.0.40:8086 # influxdb 服务地址
      database: device             # influxdb 库名 / redis 逻辑库下标（redis 实际以 spring.data.redis.database 为准）
      username: admin              # influxdb 1.x（可空）
      password: secret
      measurement: device_series   # influxdb measurement
      key-prefix: device:series    # redis 键前缀
      max-points-per-series: 100000
```

| engine | 存储形态 | 额外依赖 |
|---|---|---|
| `bean`（默认） | MySQL 明细表 `device_series_point`（`datetime(3)` + 分类/设备/时间索引） | 无，随宿主数据源 |
| `influxdb` | InfluxDB 1.x，`measurement + tag(category/device/key/channel) + field(name/value/unit/extra)`，JDK `HttpClient` 直连 `/write` `/query`，不引客户端 jar | InfluxDB 服务 |
| `redis` | ZSET，`score=毫秒时间戳`、`member=时序点 JSON`；设备/序列键走 SET 索引，不用 `KEYS/SCAN` 全库扫 | 宿主引入 `spring-data-redis`（本模块依赖为 `optional`） |
| `none` | 不存储，读写走空语义 | 无 |

三类记录的序列口径（`DeviceSeriesCategory`）：

| 分类 | key | value | extra |
|---|---|---|---|
| `metric` 传感器 | 节点标识 | 读数 | — |
| `access` 门禁 | 卡号（无卡号用人员姓名） | 事件类型 | `cardNo` / `userName` / `doorId` / `direction` / `photoUrl` |
| `vehicle` 车闸 | 车牌号 | 进出方向 | `gateId` / `vehicleType` / `photoUrl` |

**写入时机**：指标采集任务、门禁抽取任务 / webhook 推送、过车拉取 / webhook 推送，都在落明细的同时写一份时序点；
时序写入失败只记 WARN，不回滚设备入库（旁路存储不能拖垮业务）。

**查询**：传感器历史 `GET /api/device/metrics/{deviceId}/history`；
门禁 `GET /api/device/series/access`；车闸 `GET /api/device/series/vehicle`；
引擎信息 `GET /api/device/series/engine`（`available` 为 false 时带 `reason`，页面据此提示而不是显示空列表）；
保留期清理 `DELETE /api/device/series?category=&before=`。

其余运行参数不落在 yaml：

- 凭据级任务间隔（离线检测 / 事件轮询 / 设备同步 / 组织同步 / 指标采集）随凭据保存，任务名为
  `offlineDetect_<credentialId>`、`eventPoll_<credentialId>`、`deviceSync_<credentialId>`、
  `orgSync_<credentialId>`、`metricCollect_<credentialId>`，状态行在 `device_task_state`，
  可通过 `/api/device/tasks` 查看与改间隔。
- webhook 入站的四个路径由 `DeviceWebhookDefaults` 以 `@TokenForIgnore` 声明免登录态，
  **不需要写宿主 `plugin.oauth.whitelist`**；签名 / 有效期 / 主题授权仍由 `DeviceWebhookService#verify` 校验。

## 四、快速开始

**第一步**：引入依赖（见第二节），宿主需已具备数据源与 MyBatis-Plus。

**第二步**：建表。模块自带 `src/main/resources/db/init/V2.0__init_all_device.sql` 与
`V2.0__initdata_all_device.sql`，由 `spring-support-datasource-starter` 的脚本机制执行；
存量库另由 `V2.2__device_timeseries.sql` 补 `device_series_point`（时序明细表）。
未使用该能力时需手工执行这几个脚本。

**第三步**：录入平台凭据（`/api/device/provider-credential`，字段含 `vendor`、`platformType`、
`appKey`、`appSecret`、`deviceSerial`、`extra`），或为 SDK 直连设备填 IP / 端口 / 账号密码，
然后触发一次设备同步，再按需打开实时推送与定时任务。

## 五、运行示例

### 5.1 调用示例

运行期 API 是**步骤式**的：`DeviceConnectInfo`、`DeviceChannel`、`LiveHandle` 等均为 `record`，
本模块没有 fluent 的运行期 Builder；唯一的链式 API 是能力矩阵的声明式
`DeviceCapabilityMatrix.builder(platform, title).yes(...).build()`。

**直连宇视云开放平台**：

```java
// appId / secretKey + 设备序列号；extra 里放通道号等平台私有参数
DeviceConnectInfo uv = new DeviceConnectInfo(
        "uniview", "open-api", null, 443, null, null,
        "uniview", appId, secretKey, deviceSerial,
        Map.of("channelNo", 0));

DeviceProvider provider = new UniviewProvider();
DeviceSession session = provider.connect(uv);
try {
    if (!session.testConnection()) {
        throw new IllegalStateException("宇视连接失败");
    }
    session.listChannels().forEach(ch -> System.out.println(ch.channelId() + " " + ch.name()));
    String live = session.getLiveUrl(0, "hls", "sub");
} finally {
    session.close();
}
```

宇视鉴权：`POST https://ezcloud.uniview.com/openapi/user/app/token/get`
（`appId` + `secretKey` → `accessToken` + `expireTime`），后续请求头
`Authorization: <accessToken>`；token 在会话内按 `expireTime` 缓存。

**调用前先按能力矩阵判入口**（避免「点了才报不支持」）：

```java
Set<DeviceCapability> caps =
        DeviceCapabilityMatrix.capabilitiesOf("uniview", "uniview");
if (!caps.contains(DeviceCapability.DEVICE_WRITE)) {
    // 能力矩阵未给出该位，前端隐藏「新增设备」
}
```

**精讯云鉴权两种模式**（复用凭据的 `appKey` / `appSecret` 两列）：

- 账号模式：`appKey` 填控制台账号，`appSecret` 填密码，走 `POST /index/login`
  建立 `JSESSIONID` 会话，再用 `/deviceManage/getDeviceList` 取设备。
- HTTP 取数模式：`appKey` 填 16 位 `secretKey`，`appSecret` 留空，调用
  `/device/*`、`/relay/*` 官方接口。`secretKey` 需联系厂商获取，本仓库不带任何凭据。

平台地址默认 `http://www.sennor.net`，可由 `extra.baseUrl` 覆盖。精讯云声明 `DEVICE` 与 `METRIC`
能力，设备统一映射为 `category=other`；不提供视频、门禁、组织、人员能力。

**环境指标**（气象站 / 温湿度传感器）：

- 账号模式读取 `GET /deviceManage/getSensorList?deviceNumber=<设备编号>`；
  HTTP 取数模式读取 `GET /device/selSensorData`，字段与官方 `/device/*` 一致。
- 节点以 `sensorId` / `templateId` 为稳定标识；名称为温度、湿度、风速、雨量等，
  值保留字符串以兼容风向与天气现象。
- 凭据高级配置可开启「环境指标采集」，任务名 `metricCollect_<credentialId>`，默认 60 秒；
  设备详情「指标」页每 10 秒读取最新快照，也可手动触发（`POST /api/device/metrics/{deviceId}/collect`）。
- 快照表 `device_metric_latest` 按设备 + 节点覆盖更新，**不保留历史趋势**。

### 5.2 SPI 扩展示例

新增一个厂商按「实现 → 登记 → 生效」三步：

**① 实现**：新建 `provider/<vendor>/<Vendor>Provider`（`getVendor()` 返回厂商标识，
`support()` 按 `platformType` 判断），会话继承 `provider/platform/AbstractOpenApiSession`
并覆写 `doRequest`（鉴权头 + 错误码）与真实支持的 SPI 方法，不支持的方法显式抛异常：

```java
@Component
public class AcmeProvider implements DeviceProvider {

    @Override
    public String getVendor() {
        return "acme";
    }

    @Override
    public boolean support(DeviceConnectInfo info) {
        return "acme".equals(info.vendor()) && "acme-cloud".equals(info.platformType());
    }

    @Override
    public DeviceSession connect(DeviceConnectInfo info) {
        return new AcmeSession(info);
    }

    @Override
    public DeviceVendorDescriptor descriptor() {
        return new DeviceVendorDescriptor(
                "acme", "Acme 安防",
                List.of(), List.of(),
                DeviceCapabilityMatrix.capabilitiesOf("acme", "acme-cloud"),
                60, null, "Acme 云开放平台");
    }
}
```

**② 登记**：

- 厂商 Provider 标注 `@Component` 即可：`DeviceAutoConfiguration` 会 `@ComponentScan` 到
  `com.chua.starter.device.provider`，并把容器中所有 `DeviceProvider` 按 `getVendor()` 注册进
  `ServiceProvider<DeviceProvider>`。**本模块不再使用 `META-INF/services` 文件**。
- 官方接口路径登记到 `provider/sdk/acme/AcmeApis.java`（`PlatformApiSpec.global(...)`），只写核实过的路径。
- 在 `DeviceCapabilityMatrix` 增加 `builder("acme.acme-cloud", "...")` 条目，按能力位如实标注
  `yes` / `unverified` / `todo` / `no`。

**③ 生效**：重启后 `GET /api/device/capability/matrix/acme.acme-cloud` 能查到矩阵条目，
前端「厂商接入」下拉与凭据表单按 `descriptor()` 渲染；同步入口
`DeviceSyncServiceImpl#syncDevicesByCredential` 会用该 Provider 的 `fetchDevices(session)`。

外部模块还可以只扩展**平台 SDK 门面**（不改本模块代码）：提供一个 `DevicePlatformSdkProvider`
Bean，`DeviceAutoConfiguration#devicePlatformSdkProviders` 会自动 `DevicePlatformSdks.register(...)`，
`/api/device/platform/**` 即可调用新平台接口。

## 六、与其它 starter 的关系

- **上游**：`spring-support-common-starter`（`ServiceProvider` SPI 注册表、`ServiceInfoContributor`、
  `@TokenForIgnore` 的注解语义）、`spring-support-mybatis-starter`（分页与 `PageResult`）。
- **`spring-support-oauth-client-starter`（optional）**：只用于 `@TokenForIgnore` 声明 webhook 入站免登录态；
  宿主不引入时注解不存在，webhook 需自行在 `plugin.oauth.whitelist` 放行。
- **`spring-support-socket-starter`（optional）**：`DeviceRealtimeConfiguration` 在有
  `SocketSessionTemplate` 时注入 `SocketDeviceRealtimePublisher`，否则用空实现兜底，模块仍可独立运行。
- **`spring-support-datasource-starter`**：执行 `db/init` 下的建表与种子脚本；未引入时需手工建表。
- **`spring-support-swagger-starter`**：`DeviceGroupController` 等使用其 OpenAPI 注解。

## 七、已知限制

1. **能力位不能外推**：`yes` 仅 13 格（上表），其余全部按 `unverified` / `todo` / `no` 处理；
   未标 `yes` 的能力不得对外描述为「已支持」。
2. **真机验证缺口**：综合安防无网关、ICC 无环境、宇视无 `appId` / `secretKey`、大华云无设备与凭据、
   精讯云账号名下 0 台设备；这些平台的能力位只能停在 `unverified`。
3. **待实现（`todo` 5 格）**：GB28181 `EVENT`（`gb.alarm.subscribe` 已登记但会话未实现）；
   ONVIF `ACCESS` / `ACCESS_WRITE` / `EVENT` / `RECORD`（仅到 Profile 级）。
4. **设备写回范围有限**：宇视、萤石、云眸、云曜、综合安防、乐橙、大华云的 `DEVICE_WRITE` 均为
   `unverified`，且都依赖 `DeviceSyncServiceImpl.pushDevice/removeRemote` 按凭据路由；
   萤石 / 云眸新增设备需验证码，云眸另需 `groupNo`（凭据实体无该字段，当前未透传）。
5. **组织 / 人员能力缺口**：宇视官方目录无组织、人员、门禁、停车场接口；大华云组织写需
   `templateId`（当前用户或 `extra.orgTemplateId`），新增用户需 `extra.defaultPersonPassword`；
   乐橙无组织、人员、停车场；ICC 设备目录只有详情 / 分页 / 树 / 通道分页，无增删改。
6. **门禁写能力**：乐橙 `ACCESS_WRITE` 仅远程开门，无门点增删改与授权下发；综合安防
   `ACCESS_WRITE` 只接入 iSC ACPS `/api/acps/v1/authDownload/task/simpleDownload` 单人员单门下发，
   门禁点增删改与批量下载任务无公开字段文档，未实现。
7. **停车场**：萤石只有停车消息 / 停车记录两个查询接口，无车场、车道与开闸接口；`PARKING` 位无平台为 `yes`。
8. **密钥加密是「可选」的**：未配置 `device.security.secret-key` 时密码与厂商 SK 明文入库；
   已产生 `enc:` 数据后再移除该配置会导致解密直接抛 `IllegalStateException`。
9. **「登记接口」不等于「实现能力」**：`*Apis.java` 只描述官方接口目录，能力是否可用以
   `DeviceCapabilityMatrix` 的格子和会话实现为准；新增平台必须同时补 `*Apis.java` 与
   `DeviceSession` 实现，仅在接口目录登记不会让任何能力位从 `todo` 变成 `yes`。
10. **平台名称**：Dahua 侧文档常写作「大华云睿 / 大华云平台」，本仓库标识统一为 `dahua-cloud`；
    海康侧写作「云曜」（`cloud-yao`），与代码中的 `DeviceAutoConfiguration` 日志文案用词不完全一致。
11. **时序引擎的验证范围**：`bean` 引擎已在本机 MySQL 上跑通写入 / 查询 / 计数 / 时间窗 / 保留期清理；
    `influxdb` 与 `redis` 按官方协议实现（InfluxDB 1.x `/write`+`/query`、Redis ZSET + 索引 SET），
    本机没有这两套服务，**尚未对真实服务做端到端验证**。`redis` 还需要宿主持有 `spring-data-redis`，
    缺失时引擎不可用并在页面上给出原因（不会静默当成「没有数据」）。
12. **时序是旁路**：明细表（`device_access_record`、`device_vehicle_pass`、`device_metric_latest`）仍在写，
    时序存储服务历史/趋势与跨后端切换；明细表继续承载考勤归集等需要关系语义的读取。
