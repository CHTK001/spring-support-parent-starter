# spring-support-device-starter

设备接入 Starter：把海康、大华、宇视、精讯畅通、GB28181、ONVIF 等厂商/协议收敛成一套
`DeviceProvider` + `DeviceSession` SPI，业务侧只依赖 SPI，不直接碰各家的鉴权与报文差异。

- 包名：`com.chua.starter.device`
- 版本：`4.0.0.51`
- 能力口径：接口登记在 `provider/sdk/<vendor>/*Apis.java`，支持度登记在
  `provider/sdk/DeviceCapabilityMatrix.java`

## 1. SPI 契约

### 1.1 DeviceProvider（厂商入口）

| 方法 | 说明 |
|---|---|
| `getVendor()` | 厂商标识，如 `hikvision`、`dahua`、`uniview`、`standard` |
| `support(DeviceConnectInfo)` | 是否接管该连接信息 |
| `connect(DeviceConnectInfo)` | 建立会话，返回 `DeviceSession` |
| `descriptor()` | 厂商描述（名称、网关、能力矩阵），默认由 `getVendor()` 推导 |
| `fetchDevices()` / `fetchDevices(session)` | 设备同步（平台级） |
| `fetchOrganizations()` / `fetchPersons(session)` | 组织、人员同步（平台有则实现） |
| `upsertRemote` / `removeRemote` | 设备写操作（平台有则实现） |
| `upsertOrganization` / `removeOrganization` | 组织写操作 |
| `upsertPerson` / `removePerson` | 人员写操作 |
| `remoteUnlock(doorSerial)` | 平台级远程开门 |

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
| 门禁 | `listDoors()`、`remoteUnlock(doorId)`、`queryAccessRecords(range)` |

约定：

- 直播以 **URL 取流**为主（HLS/FLV/RTSP），`preview`/`playback` 这类字节流接口只在
  厂商 SDK 能直接出流时实现；HTTP 平台不覆写时沿用基类行为，**不代表已实现**。
- 平台没有的能力必须抛 `UnsupportedOperationException` 并写明原因，不返回空列表/
  空字符串冒充成功。

## 2. 平台覆盖

状态口径：

| 状态 | 含义 |
|---|---|
| `yes` | 已接通并有真实调用验证 |
| `unverified` | 已按官方文档或既有网关契约实现，缺环境/凭据，未做真机验证 |
| `todo` | 官方接口已核实，代码未实现 |
| `no` | 官方开放接口目录无此能力 |

当前统计（12 个平台 × 15 个能力位 = 180）：

| 平台 | platformType | yes | unverified | todo | no |
|---|---|---:|---:|---:|---:|
| 海康云曜 | `cloud-yao` | 1 | 10 | 0 | 4 |
| 海康云眸 | `cloud-eye` | 1 | 12 | 0 | 2 |
| 海康综合安防 | `secure-platform` | 1 | 13 | 0 | 1 |
| 海康萤石 | `ezviz` | 6 | 7 | 0 | 2 |
| 海康 ISUP | `isup` | 1 | 4 | 0 | 10 |
| 大华云平台 | `dahua-cloud` | 0 | 14 | 0 | 1 |
| 大华乐橙 | `lechange` | 1 | 8 | 0 | 6 |
| 大华 ICC | `dahua-icc` | 1 | 12 | 0 | 2 |
| 宇视云开放平台 | `uniview` | 0 | 8 | 0 | 7 |
| GB28181 | `gb28181` | 1 | 4 | 0 | 10 |
| ONVIF | `onvif` | 0 | 5 | 0 | 10 |
| 精讯云 | `sennor-cloud` | 0 | 1 | 0 | 14 |
| **合计** | | **13** | **98** | **0** | **69** |

已标 `yes` 的能力位：

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

> `yes` 只覆盖上表能力位，其余能力位按 `unverified` / `todo` / `no` 计，不能外推。
> 当前 `todo` 为 0：已取证的接口全部落到会话层，未公开字段的能力明确标 `no`。

## 3. 各平台实现位置

| 平台 | Provider | Session |
|---|---|---|
| 海康（云曜/云眸/综合安防/萤石/ISUP） | `provider/hikvision/HikvisionProvider` | `provider/hikvision/open/*Session`、`provider/hikvision/sdk/*` |
| 大华（云平台/乐橙/ICC） | `provider/dahua/DahuaProvider` | `provider/dahua/open/*Session` |
| 宇视 | `provider/uniview/UniviewProvider` | `provider/uniview/open/UniviewSession` |
| 精讯畅通（Sennor） | `provider/sennor/SennorProvider` | `provider/sennor/SennorSession` |
| ONVIF | `provider/standard/StandardProtocolProvider` | `provider/standard/*Session` |
| GB28181 | `provider/sdk/protocol/Gb28181Sdk` 门面 + `provider/standard/Gb28181Session`（WVP REST 网关） |

接口目录：

- `provider/sdk/hikvision/HikvisionArtemisApis.java`（综合安防 Artemis 网关）
- `provider/sdk/uniview/UniviewApis.java`（宇视云开放平台）
- `provider/sdk/sennor/SennorApis.java`（精讯云 HTTP 取数接口）
- 其余厂商接口常量随各自 Session/Provider 维护

## 4. 接入示例

`DeviceConnectInfo` 字段：`vendor`、`authType`、`ip`、`port`、`username`、`password`、
`platformType`、`appKey`、`appSecret`、`deviceSerial`、`extra`。

```java
// 宇视云开放平台：appId/secretKey + 设备序列号
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

精讯云鉴权有两种模式，复用凭据的 `appKey` / `appSecret` 两列：

- 账号模式：`appKey` 填控制台账号，`appSecret` 填密码，走 `POST /index/login`
  建立 `JSESSIONID` 会话，再用 `/deviceManage/getDeviceList` 取设备。
- HTTP 取数模式：`appKey` 填 16 位 `secretKey`，`appSecret` 留空，调用
  `/device/*`、`/relay/*` 官方接口。`secretKey` 需联系厂商获取，本仓库不带任何凭据。

平台地址默认 `http://www.sennor.net`，可由 `extra.baseUrl` 覆盖。精讯云只声明
`DEVICE` 能力，设备统一映射为 `category=other`，不提供视频、门禁、组织、人员能力。

## 5. 已知缺口

| 平台 | 缺口 |
|---|---|
| 宇视 | 设备写操作已接入但未真机校准；官方 openapi 目录无组织、人员、门禁、停车场接口；无回放地址接口（仅录像检索） |
| 萤石 | 停车记录仅有查询接口（parking/msgRecord、parking/parkingRecord），无车场、车道与开闸接口 |
| 大华云平台 | 14 项 `unverified`：视频/录像/云台/抓图/事件/设备写/组织/人员/门禁均按官方文档实现，缺设备与凭据真机验证。组织写需 `templateId`（当前用户或 `extra.orgTemplateId`）、新增用户需 `extra.defaultPersonPassword`、新增设备需 `extra.devCode` |
| 大华 ICC | 设备公开目录只有详情/分页/树/通道分页，无增删改；组织/人员写已接入，缺环境验证 |
| 海康综合安防 | `ACCESS_WRITE` 部分接入：单人员单门权限同步下发走 iSC ACPS `/api/acps/v1/authDownload/task/simpleDownload`；门禁点增删改与批量下载任务无公开字段文档，未实现 |
| 海康 ISUP / GB28181 | 各 4 项 `unverified`：会话已实现（ISUP `/isup/*` 网关、国标 WVP `/api/v1/*`），缺服务端环境联调 |
| ONVIF | 5 项 `unverified`、10 项 `no`：标准本身无对应服务 |
| 精讯云 | `DEVICE` 已实现设备目录与在线状态；账号名下 0 台设备，字段映射未验证。官方只有设备改名，没有新增/删除；传感器/继电器数据接口已登记但本模块暂无对应能力位与页面 |

真机验证缺口：综合安防无网关、ICC 无环境、宇视无 appId/secretKey、大华云无设备与凭据、精讯云账号下无设备。

## 6. 验证

编译：

```powershell
mvn -o -DskipTests -f spring-support-device-starter/pom.xml compile
```

统计能力矩阵（PowerShell）：

```powershell
cd spring-support-parent-starter\spring-support-device-starter\src\main\java\com\chua\starter\device\provider\sdk
$t = Get-Content DeviceCapabilityMatrix.java -Raw
$ms = [regex]::Matches($t, 'static DeviceCapabilityMatrix (\w+)\(\)\s*\{(.*?)\n    \}', 'Singleline')
foreach ($m in $ms) {
    $b = $m.Groups[2].Value
    $p = [regex]::Match($b, 'builder\("([^"]+)"').Groups[1].Value
    $y = ([regex]::Matches($b, '\.yes\(')).Count
    $u = ([regex]::Matches($b, '\.unverified\(')).Count
    $d = ([regex]::Matches($b, '\.todo\(')).Count
    $n = ([regex]::Matches($b, '\.no\(')).Count
    "{0,-26} {1,3} {2,3} {3,3} {4,3}" -f $p, $y, $u, $d, $n
}
```

## 7. 新增平台

1. 新建 `provider/<vendor>/<Vendor>Provider`，`getVendor()` 返回厂商标识，
   `support()` 按 `platformType` 判断。
2. 在 `provider/sdk/<vendor>/` 登记官方接口路径（`*Apis.java`），只写核实过的路径。
3. 新建 `provider/<vendor>/open/<Vendor>Session`，继承 `AbstractOpenApiSession`，
   覆写 `doRequest`（鉴权头 + 错误码）与真实支持的 SPI 方法；不支持的显式抛异常。
4. 在 `DeviceCapabilityMatrix` 增加 `builder(...)` 条目，按能力位如实标注。
5. 编译 + 真机验证后再把对应能力位从 `unverified` 改为 `yes`。
