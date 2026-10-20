# 设备资产监控（pages/device）产品与技术方案

> 范围：设备资产台账、厂商接入、设备同步、实时状态、视频上墙、组织/门禁/停车场、任务调度、设备大屏。
>
> 代码基线：`spring-support-device-starter` + `vue-support-parent-starter/pages/device`。
>
> 本文先给结论与设计，再给接口、数据模型、实施顺序和可扩展功能清单。

---

## 0. 结论摘要

| # | 决策 | 理由 |
|---|---|---|
| 1 | **复用全局 SSE**，设备模块只定义 topic，页面只做订阅/退订 | 全局 socket 已由 `createApp` 装配，统一鉴权、重连、多地址降级；页面各建连接会撞浏览器同源连接上限，多标签页还会放大 |
| 2 | 厂商能力改为 **“SPI 描述符 + 可选能力接口”** | 目前 `DeviceProvider` 只有连接/组织，无法声明“支持哪些平台、需要哪些凭据字段、能不能同步设备/门禁/停车场” |
| 3 | 同步采用 **本地台账为唯一真源 + 厂商目录为外部源**，拉取用 diff、推送用 outbox | 厂商平台普遍限流、字段不全；直接双向实时写会把本地台账拖成厂商镜像，且删除/改名无法追溯 |
| 4 | 视频 **网格默认子码流、全屏切主码流**；RTSP 必须服务端转封装/转码，WASM 只做前端解封装兜底 | 浏览器不能直连 RTSP；H.265 在 Chrome 仍不可用；ffmpeg.wasm 做全量转码代价高 |
| 5 | 地图用 **`@repo/ui/re-map`（Leaflet）**，替换现在的 SVG 假地图 | 现 `ReMap.vue` 只是按经纬度画点，没有底图/缩放/聚合/工具栏 |
| 6 | 定时任务统一进 **任务配置表 + 时间轮**，不再每秒全表扫 | 现 `DeviceScheduler` 每秒 `listCheckableDevices()`，设备一多就是持续 DB 压力 |
| 7 | **P0 先修 4 个现网问题**：密码/SK 明文回传、添加设备丢字段、组织同步重复插入、离线只降不升 | 都会直接影响安全或数据正确性 |

---

## 1. 现状盘点

### 1.1 已有的

| 能力 | 位置 | 现状 |
|---|---|---|
| 厂商 SPI | `spi/DeviceProvider`、`spi/DeviceSession` | 海康、大华各 4 种接入（云耀/云眸/安防/SDK、云睿/乐橙/SDK） |
| 设备 REST | `controller/DeviceController` | 增删改查、组织树、导出、阈值、重连、测连接 |
| 凭据 | `ProviderCredentialController` + `device_provider_credential` | 同厂商多套、默认套、启用、阈值 |
| 通道/预置位/门禁/事件 | `ChannelController`、`PtzController`、`AccessController`、`EventController` | 基础 CRUD 与查询 |
| 视频 | `VideoController` | 返回厂商 HLS/FLV 地址、回放地址 |
| 定时 | `scheduler/DeviceScheduler` | 每秒触发，按设备阈值判离线 |
| 前端 | `pages/device` | 设备树 + 概览/地图/通道/视频/云台/门禁/事件 8 个页签 |
| 前端播放 | `ReVideo.vue` | hls.js + flv.js，原生 video |

### 1.2 关键缺口

| 缺口 | 影响 |
|---|---|
| `DeviceController` 直接返回 `DeviceInfo`，含 `deviceInfoPassword`；凭据接口返回 `deviceCredentialAppSecret` | 密码/SK 明文进浏览器、进日志、进前端缓存 |
| `addDevice()` 只落 vendor/ip/port/username/password，**名称/型号/大类/组织/经纬度全丢** | 前端“添加设备”填的名称在列表里是空的 |
| `syncOrganization()` 直接 `insert`，主键是 `vendor:orgId` | 第二次同步必然主键冲突 |
| 离线检测只把 `online` 置 false，从不置回 true；无 `last_online_at` | 设备恢复后永远显示离线；无法做“最后在线时间” |
| 每 1 秒全表查候选设备 | 设备量大后 DB 持续空转 |
| 无任何 SSE / WebSocket 推送 | 在线状态、经纬度、事件都靠手动刷新 |
| 无设备同步、无设备编码、无负责人、无主/副码流、无最后在线 | 需求 2、2.5 无法满足 |
| `ReMap.vue` 是 SVG 坐标点 | 不能缩放/聚合/打开外部地图 |
| 前端 `pollTimer` 只 `console.log` | 实时事件是占位实现 |
| 无门禁记录菜单、无停车场、无大屏 | 需求 2.6、3、3.1、4 缺失 |
| 无任务配置（时间/间隔/开关） | 需求 3.2–3.4 的“检查时间、后台时间轮”缺失 |

> 补充：`DeviceProvider` 的 Javadoc 写“由 `META-INF/services/...` 注册”，实际实现是 Spring `@Component`。两者混用会让第三方 jar 接入方式不明确，方案里统一为 **Spring Bean 注册**，并在文档中改掉错误注释。

---

## 2. 需求逐条对照

| 需求 | 落点 | 做法 |
|---|---|---|
| 1. 配置厂家和 AK/SK，同厂家多套，厂家以 SPI 为准 | 厂商接入页 + `DeviceVendorDescriptor` | 后端 `/provider/vendors` 返回厂家/平台/凭据字段/能力；前端动态渲染表单 |
| 2. 从厂家同步设备，增删改查到厂家 | 同步中心 + `DeviceDirectoryProvider` | 拉取 diff、推送 outbox、失败重试、预览与冲突处理 |
| 2.1 页面 SSE 实时在线/经纬度/推送 | 全局 socket + `device.*` topic | REST 快照 + SSE 增量 + `seq` 补拉；socket 关闭时轮询兜底 |
| 2.2 打开视频流（WASM 转码直接预览） | 视频墙 + 转码服务 | 服务端转 HLS/fMP4；WASM 只做 transmux/解密兜底 |
| 2.3 多选三步弹窗（调设备/布局/打开方式） | `DeviceWallWizard` | 3 步向导，URL 参数化，`remind=true` |
| 2.4 预览页每路调主/副码流 | 视频墙每格工具条 | `streamType=main|sub`，切换即重拉 |
| 2.5 列表字段与交互 | 设备资产列表 | 名称+编码复制、负责人上下排、主副码流、经纬度、在线脉冲、最后在线、创建/更新时间 |
| 2.6 门禁出入记录等更多菜单 | 门禁记录、事件中心 | 独立菜单，数据抽取任务 |
| 3. 组织管理弹按钮，不单开菜单 | 厂商接入页/组织树 | `管理组织` 抽屉，按厂商能力显示 |
| 3.1 停车场单独菜单 | `/device/parking` | 车场/车道/道闸/过车记录 |
| 3.2 手动同步 + 检查时间 + 时间轮 | 同步中心 + 任务配置 | `device_task_config` + `TimeScheduler` |
| 3.3 离线检查配置 | 任务配置 | 开关、cron/间隔、厂商/设备阈值 |
| 3.4 门禁数据抽取 | 任务配置 + 门禁记录 | 增量游标、去重、失败重试 |
| 4. 大屏地图 + 工具栏显隐/分类过滤 | `/device/screen` | Leaflet 聚合、分类过滤、统计、告警联动 |

---

## 3. 总体架构

```text
┌──────────────────────────── 浏览器（pages/device）────────────────────────────┐
│ 设备资产列表   厂商接入   同步中心   视频墙/向导   门禁记录   停车场   大屏    │
│        │            │          │           │            │          │        │
│        └────────────┴────── REST /api/device ───────────┴──────────┘        │
│                                   │                                          │
│                 全局 socket（SSE，单连接，createApp 装配）                    │
│                 订阅 device.status / device.geo / device.event ...            │
└───────────────────────────────────┬──────────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼──────────────────────────────────────────┐
│ spring-support-device-starter                                                │
│  Controller  设备 / 凭据 / 同步 / 视频 / 门禁 / 停车场 / 任务 / 大屏聚合       │
│  Service     台账、同步编排、会话管理、事件抽取、任务状态                      │
│  Provider    DeviceProvider + 可选能力接口（目录/组织/门禁/停车场/媒体/事件）  │
│  Scheduler   TimeScheduler / 时间轮 + device_task_config + 分布式锁           │
│  Realtime    DeviceRealtimePublisher → SseTemplate（存在则启用，否则 no-op）   │
└───────────────┬───────────────────────────────┬──────────────────────────────┘
                │                               │
      海康/大华 OpenAPI / SDK            转码/录像（FFmpeg/边缘网关）
```

设计原则：

1. **模块可独立运行**：设备模块不强依赖 SSE starter；实时发布做成接口 + 条件装配，没有 SSE 就退化为轮询。
2. **厂商能力可缺省**：任何能力接口默认“不支持”，前端按能力隐藏入口，不出现“点了报 501”。
3. **本地台账不被厂商字段绑死**：负责人、标签、区域、维保等本地字段永远以本地为准。
4. **所有写操作可审计**：同步、推送、远程开门、删除设备都落操作记录。

---

## 4. 厂商接入设计（需求 1）

### 4.1 描述符：让前端知道“支持哪些厂家、要填什么”

新增 `com.chua.starter.device.spi.model.DeviceVendorDescriptor`：

```java
public record DeviceVendorDescriptor(
        String vendor,                  // hikvision
        String name,                    // 海康威视
        List<AuthTypeDescriptor> authTypes,   // PLATFORM / SDK
        List<PlatformDescriptor> platforms,   // cloud-yao / cloud-eye / secure-platform
        Set<DeviceCapability> capabilities,   // DEVICE_SYNC / ORG / CHANNEL / ACCESS / PARKING / PTZ / EVENT / PLAYBACK
        int defaultOfflineThreshold,
        String docUrl
) {}

public record PlatformDescriptor(
        String code,                    // cloud-eye
        String name,                    // 海康云眸
        String defaultBaseUrl,
        List<CredentialField> credentialFields  // appKey/appSecret/...
) {}

public record CredentialField(
        String key, String label, boolean secret, boolean required, String placeholder
) {}
```

`DeviceProvider` 增加默认方法：

```java
default DeviceVendorDescriptor descriptor() {
    return new DeviceVendorDescriptor(getVendor(), getVendor(), List.of(), List.of(), Set.of(), 60, null);
}
```

海康/大华各自实现，前端“厂商接入”页即可动态渲染：
- 厂家下拉：来自 `/provider/vendors`
- 平台下拉：该厂家的 `platforms`
- 凭据字段：该平台的 `credentialFields`（AK/SK、secretId、appId…）
- 能力徽标：`DEVICE_SYNC`、`ACCESS`、`PARKING` 等

### 4.2 能力接口拆分

不要把目录、门禁、停车场全塞进 `DeviceProvider`，否则每接一个厂商都要实现一堆空方法。拆成可选接口，Spring 按类型注入，缺实现即“不支持”：

| 接口 | 方法（示意） | 能力位 |
|---|---|---|
| `DeviceDirectoryProvider` | `fetchDevices(ctx)`、`createDevice(ctx, draft)`、`updateDevice`、`deleteDevice`、`fetchChannels` | `DEVICE_SYNC` |
| `DeviceOrganizationProvider` | `fetchOrganizations(ctx)`、`create/update/delete` | `ORG` |
| `DeviceAccessProvider` | `fetchDoors`、`queryAccessRecords`、`remoteUnlock`、`fetchCardUsers` | `ACCESS` |
| `DeviceParkingProvider` | `fetchLots/Lanes`、`fetchPassRecords`、`openGate` | `PARKING` |
| `DeviceMediaProvider` | `getLiveUrl(channel, streamType, protocol)`、`getPlaybackUrl` | `MEDIA` |
| `DeviceEventProvider` | `pollEvents(cursor)`、`subscribe(listener)` | `EVENT` |
| `DevicePtzProvider` | `ptz`、`preset` | `PTZ` |

> 现有 `DeviceSession` 继续承担“已连接会话”的媒体/云台/门禁调用；上述接口承担“平台级目录/批量查询”。两者不混。

`DeviceConnectInfo` 建议增加 `credentialId`，让一台设备明确绑定哪套 AK/SK；未绑定则回退该厂商默认套（现状逻辑保留）。

### 4.3 凭据管理

- 同一厂商多套：已有表结构支持，保留。
- **加密存储**：`appKey` 可明文，`appSecret` 用 `spring-support-common-starter` 的 `AesCodec`（密钥来自配置/环境变量，不入库）。
- **脱敏返回**：列表/详情只回 `sk****abcd`；编辑时留空表示“不修改”。
- **测试连接**：`POST /provider-credential/{id}/test`，返回平台可达、认证结果、耗时。
- **默认套约束**：同厂商同平台只允许一套 `isDefault=1`，服务层事务内先清后置。
- **轮换**：保留 `device_credential_rotated_at`，旧密钥保留 N 天用于回滚（可选）。

---

## 5. 设备同步设计（需求 2、3.2）

### 5.1 同步模型

```text
厂商平台 ──pull(diff)──▶ 本地台账 device_info ──push(outbox)──▶ 厂商平台
                              ▲
                              └── 人工新增/编辑/删除
```

- **拉取**：全量或按组织/时间增量；以 `(vendor, externalId)`（无则 `(vendor, serial)`）做幂等 upsert。
- **推送**：本地变更写 `device_sync_outbox`，后台按顺序推送；成功标记，失败指数退避，超限进死信。
- **删除**：默认“本地停用，不删厂商”；只有显式勾选“同步删除”才推送，避免误删。
- **冲突**：字段级策略
  - `vendor`：名称、型号、固件、通道、经纬度以厂商为准
  - `local`：负责人、标签、区域、维保、备注永远本地
  - `newer`：按 `updated_at` 取新
  - `manual`：进冲突列表人工选
- **预览**：`POST /sync/preview` 返回 `added/updated/removed/conflicts`，不落库。
- **幂等键**：`uk_device_vendor_external (device_info_vendor, device_info_external_id)`。
- **进度**：每个阶段发 `device.sync.progress`，前端进度条 + 日志。

### 5.2 同步任务

| 任务 | 说明 | 默认 |
|---|---|---|
| `DEVICE_SYNC_PULL` | 拉设备/通道 | 每天 02:00 |
| `DEVICE_SYNC_PUSH` | 推送 outbox | 每 30 秒 |
| `ORG_SYNC` | 拉组织树 | 每天 02:10 |
| `ACCESS_EXTRACT` | 拉门禁记录 | 每 5 分钟 |
| `PARKING_EXTRACT` | 拉过车记录 | 每 5 分钟 |
| `ONLINE_CHECK` | 在线检测 | 每 30 秒（可配） |
| `EVENT_POLL` | 事件轮询 | 每 10 秒（可配） |

### 5.3 组织同步修复

现状 `syncOrganization()` 直接 insert，第二次必冲突。改为：

1. 先按 `(vendor, externalId)` 查本地映射；
2. 存在则 update 名称/父级/路径；
3. 不存在则 insert；
4. 厂商已删除的节点：本地标记 `device_organization_deleted=1`，不物理删（设备还挂着）。

同时补充 `device_organization_external_id`、`device_organization_sort`、`device_organization_updated_at`。

---

## 6. SSE 设计（需求 2.1）

### 6.1 复用全局还是页面独立？

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| 复用全局 `createApp` 的 socket | 单连接、统一鉴权/解密/重连/多地址降级；多页面共享 | 需要全局 socket 开启 | **采用** |
| 设备页自建 `createSseService` | 页面自治、可按视图传参 | 每标签页多一条长连接；HTTP/1.1 同源 6 连接很容易被多页签耗尽；重连风暴 | 不采用 |
| 设备页命名实例 | 隔离设备 topic | 仍是额外连接 | 仅当全局 socket 被禁用时兜底 |

**落地方式**：

```ts
// pages/device/src/composables/useDeviceRealtime.ts
const service = useSocketService();           // 全局实例
const off1 = service?.subscribe("device", "status", onStatus);
const off2 = service?.subscribe("device", "geo", onGeo);
// 页面卸载只退订，不 close 连接
```

若 `socket.enable=false`：自动降级为 15s 轮询 `/api/device/changes?sinceSeq=`，UI 显示“实时通道未启用”。

### 6.2 Topic 与消息

| topic | 载荷 | 用途 |
|---|---|---|
| `device.status` | `{ deviceId, online, lastOnlineAt, seq, ts }` | 在线/离线、脉冲灯、最后在线 |
| `device.geo` | `{ deviceId, lat, lng, address, seq }` | 经纬度变更、地图移动 |
| `device.event` | `{ deviceId, channelNo, eventType, occurredAt, detail, seq }` | 移动侦测/报警/门禁事件 |
| `device.sync.progress` | `{ taskId, phase, total, done, failed }` | 同步进度 |
| `device.access` | `{ deviceId, doorId, cardNo, userName, result, occurredAt }` | 门禁实时记录 |
| `device.alarm` | `{ level, deviceId, title, occurredAt }` | 大屏/顶栏告警 |

约定：
- 消息统一 `{ module: "device", event: "status", data, seq }`，与现有 `WsMessage` 对齐。
- 首屏 REST 快照带 `seq`；断线重连带 `sinceSeq` 补拉；服务端保留最近 N 分钟变更。
- 页面 `visibilitychange` 隐藏时降频/暂停视频，恢复时补拉。

### 6.3 后端发布

新增 `DeviceRealtimePublisher` 接口：

```java
public interface DeviceRealtimePublisher {
    void publish(String event, Object payload);
}
```

- 默认实现 `NoopDeviceRealtimePublisher`（模块独立运行）。
- 检测到 `SseTemplate` Bean 时装配 `SseDeviceRealtimePublisher`，按 topic 广播。
- 若现有 SSE 总线只支持按 clientId 发，则在设备侧维护 clientId 集合，或扩展 `DefaultSseEventBusConfigurer.getEmitter(event)` 广播（该方法已存在，可直接用）。

> 不建议在设备模块里再写一套 SSE 端点；复用 `/sse/connect` 与既有事件总线，避免两套连接管理。

---

## 7. 视频设计（需求 2.2、2.4）

### 7.1 现实约束

- 浏览器不能播 RTSP。
- Chrome/Edge 对 H.265 支持仍不完整；海康/大华主码流常是 H.265。
- `ffmpeg.wasm` 在浏览器里做**全量转码**成本高（CPU、内存、发热），不适合多路。
- 低延迟首选 WebSocket + fMP4；兼容性首选 HLS；FLV 适合桌面端。

### 7.2 推荐链路

```text
设备 RTSP/私有协议
   │
   ├─ 厂商平台已给 HLS/FLV ──────────────▶ 直接播放（现状）
   │
   └─ 仅 RTSP/PS 流
         │
         ├─ 服务端 FFmpeg：转封装 H.264 → fMP4/WS 或 HLS（推荐，低延迟）
         ├─ 服务端转码 H.265 → H.264（仅主码流/大屏需要时）
         └─ 浏览器 WASM：仅做 fMP4 transmux / 解密 / 私有头剥离（兜底，不转码）
```

**WASM 的定位**（回应“wasm 直接支持转码成浏览器直接预览”）：
- 可以做：TS → fMP4 转封装、AES 解密、私有帧头处理、软解 H.265（性能差，单路可用）。
- 不建议做：多路实时转码、分辨率缩放、码率转码。
- 若必须前端软解，限制“单路 + 主码流 + 提示 CPU 占用”，并在 UI 标注。

### 7.3 码流与接口

`GET /api/device/video/stream?deviceId=&channel=&streamType=sub|main&protocol=hls|flv|fmp4&token=`

- 网格/大屏默认 `sub`；单画面/全屏切 `main`。
- 返回结构建议从裸字符串升级为：

```json
{
  "url": "https://...",
  "protocol": "hls",
  "streamType": "sub",
  "expiresAt": "2026-10-17T12:00:00Z",
  "transcoded": false
}
```

- 兼容旧前端：保留字符串返回一段时间，或新老字段并存。
- 按需拉流：页面不可见/格子关闭时调用 `POST /video/stop` 释放转码资源（有会话概念时）。

### 7.4 播放器组件

- 新增 `ReVideoPlayer`（统一封装 HLS/FLV/fMP4/WebRTC），`ReVideo` 保留兼容。
- 能力：自动重连、首帧超时、断流角标、静音、截图、录制（可选）、全屏、画中画。
- 网格性能：`IntersectionObserver` 只播可见格；最多 16 路；超过提示。

---

## 8. 地图与坐标（需求 2.5、4）

- 组件：`@repo/ui/re-map`（Leaflet），支持工具栏、图层、聚合、测距。
- 设备点：按在线状态着色 + 脉冲；离线灰；未知黄。
- 聚合：`leaflet.markercluster`（dist 已有）。
- 经纬度列：显示 `lat, lng`，点击“定位”打开地图弹窗；“打开地图”跳转高德/百度/腾讯。
- **坐标系**：厂商多返回 WGS84；高德/腾讯底图是 GCJ02，百度是 BD09。统一在 `utils/coord.ts` 做转换，落库保留 WGS84，展示时按底图转换。
- 无坐标设备：列表显示“未配置”，提供批量地图选点。
- 大屏：分类过滤（摄像头/门禁/报警/停车场）、工具栏显隐、全屏、暗色。

---

## 9. 定时任务与时间轮（需求 3.2–3.4）

### 9.1 现状问题

- `DeviceScheduler` 每秒执行，内部 `listCheckableDevices()` 全表查。
- 只有离线检测，事件轮询在 `DeviceEventService` 的 `@Scheduled`，两套调度并存。
- 无开关、无检查时间、无执行日志。

### 9.2 目标模型

新增 `device_task_config`：

| 字段 | 说明 |
|---|---|
| `task_code` | `ONLINE_CHECK` / `DEVICE_SYNC_PULL` / `ACCESS_EXTRACT` … |
| `task_enabled` | 开关 |
| `task_cron` | cron 表达式（优先） |
| `task_interval_sec` | 固定间隔（无 cron 时） |
| `task_next_run_at` | 下次执行 |
| `task_last_run_at` / `task_last_status` / `task_last_message` | 运行结果 |
| `task_timeout_sec` | 超时 |
| `task_params` | JSON（厂商、组织、批量大小） |
| `task_lock_owner` / `task_lock_until` | 集群抢占 |

调度：
- 用 `common-starter` 的 `TimeScheduler` / `HashedWheelTimer`，启动时把每个启用任务按 `nextRunAt` 投递到时间轮；执行完重算下次时间再投递。
- 不再每秒全表扫；只有“到期任务”才查库。
- 集群：`UPDATE ... SET lock_owner=? WHERE task_code=? AND (lock_until IS NULL OR lock_until < NOW())` 抢占。
- 前端“系统设置 > 任务配置”：启用、cron/间隔、立即执行、查看最近 50 次日志。

### 9.3 在线检测修复

- 会话在线 → `online=true`，更新 `last_online_at`；离线 → `online=false`，更新 `last_offline_at`。
- 状态变化才发 SSE，避免每秒刷。
- 无会话设备：按厂商能力主动探测（平台查设备状态），失败则判离线。
- 设备恢复：下一次检测或事件推送时置回在线，并清离线时长。

---

## 10. 数据模型

### 10.1 `device_info` 扩列

```sql
ALTER TABLE device_info
    ADD COLUMN device_info_code               VARCHAR(64)  COMMENT '设备编码（资产编码/厂商序列号）',
    ADD COLUMN device_info_external_id        VARCHAR(128) COMMENT '厂商侧设备唯一标识',
    ADD COLUMN device_info_credential_id      VARCHAR(32)  COMMENT '绑定的提供商凭据 ID',
    ADD COLUMN device_info_owner_id           VARCHAR(32)  COMMENT '负责人用户 ID',
    ADD COLUMN device_info_owner_name         VARCHAR(64)  COMMENT '负责人姓名（冗余，列表直出）',
    ADD COLUMN device_info_owner_dept         VARCHAR(128) COMMENT '负责人部门',
    ADD COLUMN device_info_owner_phone        VARCHAR(32)  COMMENT '负责人电话',
    ADD COLUMN device_info_source             VARCHAR(16)  DEFAULT 'local' COMMENT 'local/vendor',
    ADD COLUMN device_info_stream_main_url    VARCHAR(1024) COMMENT '主码流地址（平台返回，可空）',
    ADD COLUMN device_info_stream_sub_url     VARCHAR(1024) COMMENT '子码流地址',
    ADD COLUMN device_info_last_online_at     DATETIME     COMMENT '最后在线时间',
    ADD COLUMN device_info_last_offline_at    DATETIME     COMMENT '最后离线时间',
    ADD COLUMN device_info_install_at         DATE         COMMENT '安装日期',
    ADD COLUMN device_info_warranty_end       DATE         COMMENT '维保到期',
    ADD COLUMN device_info_area               VARCHAR(128) COMMENT '区域/楼层',
    ADD COLUMN device_info_tags               VARCHAR(255) COMMENT '标签，逗号分隔',
    ADD COLUMN device_info_status             VARCHAR(16)  DEFAULT 'normal' COMMENT 'normal/disabled/maintenance/scrapped',
    ADD COLUMN device_info_remark             VARCHAR(500),
    ADD COLUMN device_info_version            INT          DEFAULT 0 COMMENT '乐观锁/增量序号';

-- 加密后的密码/密钥会变长，原 100 不够
ALTER TABLE device_info MODIFY COLUMN device_info_password VARCHAR(512);

CREATE UNIQUE INDEX uk_device_vendor_external
    ON device_info (device_info_vendor, device_info_external_id);
CREATE INDEX idx_device_online_last ON device_info (device_info_online, device_info_last_online_at);
CREATE INDEX idx_device_owner ON device_info (device_info_owner_id);
```

### 10.2 新增表

```sql
-- 同步状态（每厂商/每资源一条）
CREATE TABLE device_sync_state (
    device_sync_state_id        VARCHAR(32) PRIMARY KEY,
    device_sync_state_vendor    VARCHAR(32) NOT NULL,
    device_sync_state_resource  VARCHAR(32) NOT NULL COMMENT 'device/channel/org/access/parking',
    device_sync_state_cursor    VARCHAR(255) COMMENT '增量游标',
    device_sync_state_last_at   DATETIME,
    device_sync_state_last_ok   TINYINT DEFAULT 1,
    device_sync_state_message   VARCHAR(500),
    UNIQUE KEY uk_sync (device_sync_state_vendor, device_sync_state_resource)
) COMMENT '厂商同步游标状态';

-- 同步执行记录
CREATE TABLE device_sync_record (
    device_sync_record_id       VARCHAR(32) PRIMARY KEY,
    device_sync_record_task_id  VARCHAR(32) NOT NULL,
    device_sync_record_vendor   VARCHAR(32),
    device_sync_record_mode     VARCHAR(16) COMMENT 'pull/push/preview',
    device_sync_record_phase    VARCHAR(32),
    device_sync_record_total    INT DEFAULT 0,
    device_sync_record_added    INT DEFAULT 0,
    device_sync_record_updated  INT DEFAULT 0,
    device_sync_record_removed  INT DEFAULT 0,
    device_sync_record_failed   INT DEFAULT 0,
    device_sync_record_status   VARCHAR(16) COMMENT 'running/success/partial/failed',
    device_sync_record_message  VARCHAR(1000),
    device_sync_record_started_at DATETIME,
    device_sync_record_finished_at DATETIME,
    KEY idx_sync_record_time (device_sync_record_started_at)
) COMMENT '同步执行记录';

-- 推送 outbox（本地→厂商最终一致）
CREATE TABLE device_sync_outbox (
    device_sync_outbox_id       VARCHAR(32) PRIMARY KEY,
    device_sync_outbox_device_id VARCHAR(32),
    device_sync_outbox_vendor   VARCHAR(32),
    device_sync_outbox_action   VARCHAR(16) COMMENT 'create/update/delete',
    device_sync_outbox_payload  TEXT,
    device_sync_outbox_status   VARCHAR(16) DEFAULT 'pending' COMMENT 'pending/sent/failed/dead',
    device_sync_outbox_retry    INT DEFAULT 0,
    device_sync_outbox_next_at  DATETIME,
    device_sync_outbox_message  VARCHAR(500),
    device_sync_outbox_created_at DATETIME,
    KEY idx_outbox_due (device_sync_outbox_status, device_sync_outbox_next_at)
) COMMENT '设备变更推送队列';

-- 在线状态流水（用于在线率/最后在线）
CREATE TABLE device_online_log (
    device_online_log_id        VARCHAR(32) PRIMARY KEY,
    device_online_log_device_id VARCHAR(32) NOT NULL,
    device_online_log_online    TINYINT NOT NULL,
    device_online_log_at        DATETIME NOT NULL,
    KEY idx_online_log_device (device_online_log_device_id, device_online_log_at)
) COMMENT '设备在线状态流水';

-- 任务配置
CREATE TABLE device_task_config (
    device_task_config_id       VARCHAR(32) PRIMARY KEY,
    device_task_config_code     VARCHAR(64) NOT NULL,
    device_task_config_name     VARCHAR(100),
    device_task_config_enabled  TINYINT DEFAULT 1,
    device_task_config_cron     VARCHAR(64),
    device_task_config_interval INT,
    device_task_config_next_at  DATETIME,
    device_task_config_last_at  DATETIME,
    device_task_config_last_status VARCHAR(16),
    device_task_config_last_message VARCHAR(1000),
    device_task_config_timeout  INT DEFAULT 300,
    device_task_config_params   TEXT,
    device_task_config_lock_owner VARCHAR(64),
    device_task_config_lock_until DATETIME,
    UNIQUE KEY uk_task_code (device_task_config_code)
) COMMENT '设备域定时任务配置';

-- 门禁记录（抽取落库）
CREATE TABLE device_access_record (
    device_access_record_id     VARCHAR(32) PRIMARY KEY,
    device_access_record_device_id VARCHAR(32),
    device_access_record_door_id VARCHAR(32),
    device_access_record_door_name VARCHAR(100),
    device_access_record_card_no VARCHAR(64),
    device_access_record_user_name VARCHAR(100),
    device_access_record_user_id VARCHAR(32),
    device_access_record_type   VARCHAR(32) COMMENT 'card/face/fingerprint/qr/password/remote',
    device_access_record_result VARCHAR(16) COMMENT 'success/fail',
    device_access_record_direction VARCHAR(8) COMMENT 'in/out',
    device_access_record_photo_url VARCHAR(500),
    device_access_record_occurred_at DATETIME,
    device_access_record_raw     TEXT,
    UNIQUE KEY uk_access_vendor_event (device_access_record_device_id, device_access_record_occurred_at, device_access_record_card_no),
    KEY idx_access_time (device_access_record_occurred_at)
) COMMENT '门禁出入记录';

-- 停车场（最小集）
CREATE TABLE device_parking_lot (
    device_parking_lot_id       VARCHAR(32) PRIMARY KEY,
    device_parking_lot_vendor   VARCHAR(32),
    device_parking_lot_external_id VARCHAR(128),
    device_parking_lot_name     VARCHAR(100),
    device_parking_lot_total    INT,
    device_parking_lot_free     INT,
    device_parking_lot_status   VARCHAR(16)
) COMMENT '停车场';

CREATE TABLE device_parking_record (
    device_parking_record_id    VARCHAR(32) PRIMARY KEY,
    device_parking_record_lot_id VARCHAR(32),
    device_parking_record_lane  VARCHAR(64),
    device_parking_record_plate VARCHAR(32),
    device_parking_record_direction VARCHAR(8) COMMENT 'in/out',
    device_parking_record_result VARCHAR(16),
    device_parking_record_occurred_at DATETIME,
    KEY idx_parking_time (device_parking_record_occurred_at)
) COMMENT '过车记录';
```

> 命名遵循现有约定：表前缀 `device_`，字段前缀 `device_*_`；DDL 放 `src/main/resources/db/init/` 增量脚本，别改已发布的 `V1.0`。

---

## 11. 页面与交互

### 11.1 设备资产列表 `/device`（需求 2.5）

**顶部统计**：设备总数、在线、离线、未配置坐标、今日告警。

**筛选区**：
- 组织树（左）
- 厂商、大类/小类、在线状态、设备状态、标签
- 关键字：名称 / 编码 / IP / 序列号 / 负责人
- “仅看我的”“仅看未配置坐标”

**表格列**（顺序可在列设置里调）：

| 列 | 展示 |
|---|---|
| 选择框 | 批量操作 |
| 名称 / 编码 | 名称主行；编码次行 + 复制图标（hover 出现，点击 toast） |
| 负责人 | 上下两行：姓名 / 部门·电话 |
| 厂商 / 型号 | `海康 · DS-2CD...` |
| 码流 | `主` `子` 两个 tag，点击直接预览对应码流 |
| 经纬度 | `30.123456, 120.123456`；定位图标 → 地图弹窗；外链 → 高德/百度 |
| 在线 | 脉冲点 + 文案；离线灰、未知黄；`prefers-reduced-motion` 时静态 |
| 最后在线 | 相对时间（“3 分钟前”），hover 绝对时间 |
| 创建 / 更新时间 | 统一 `YYYY-MM-DD HH:mm:ss` |
| 操作 | 预览、详情、编辑、同步、更多（删除/停用/上墙） |

**批量操作**：同步到厂商、上墙、修改组织/负责人/标签、导出、删除（二次确认 + 输入名称）。

**详情抽屉**：概览 / 通道 / 视频 / 门禁 / 事件 / 日志 / 位置；概览含健康度、在线率 7 天、最后在线、维保到期。

**体验细节**：
- 服务端分页 + 排序；列宽可拖；密度切换；全屏；保存为“我的视图”。
- 空态区分“无数据”“筛选无结果”“无权限”。
- 复制统一封装 `copyText()`，失败降级 `document.execCommand`。
- 时间统一走 `formatTime`，避免各组件自己 `toLocaleString`。

### 11.2 厂商接入 `/device/provider`（需求 1）

- 厂商卡片：Logo/名称、能力徽标（设备同步/组织/门禁/停车场/云台）、默认阈值、文档链接。
- 凭据表格：名称、平台、AK（掩码）、SK（掩码）、默认、启用、阈值、更新时间、操作。
- 新增/编辑抽屉：
  1. 选厂商 → 平台（来自 `descriptor.platforms`）
  2. 动态字段（`credentialFields`），secret 字段 `show-password`
  3. baseUrl（可空）、离线阈值、默认套、备注
  4. “测试连接”按钮，展示耗时与错误
- 空态：提示“该厂商尚未实现 SPI”或“暂无凭据”。
- 组织管理：右上“管理组织”按钮，只有 `ORG` 能力时显示；打开抽屉树，支持增删改/同步预览。

### 11.3 同步中心 `/device/sync`（需求 2、3.2）

- 任务卡：上次同步时间、耗时、成功/失败/冲突数、下次执行。
- 操作：立即同步、仅预览、选择厂商/组织、强制覆盖、查看日志。
- 预览 diff 表格：新增/更新/删除/冲突；冲突行可选“以厂商为准/以本地为准/跳过”。
- 日志：分页、状态筛选、失败原因、重试。
- 进度：SSE `device.sync.progress` 驱动，不用轮询。

### 11.4 多选上墙向导（需求 2.3）

`DeviceWallWizard` 三步：

**Step 1 选择设备**
- 左：组织树 + 设备搜索 + 分类筛选
- 中：设备列表（勾选），显示在线状态、通道数
- 右：已选列表（可拖拽排序、删除、清空）
- 每项可选：通道、主/子码流
- 支持“全选当前结果”“跨页保留”
- 离线设备默认不勾选，允许强选但标红提示

**Step 2 选择布局**
- `1x1`、`2x2`、`3x3`、`4x4`、`1+5`、`1+7`、`画中画`
- 显示缩略示意图与可容纳路数
- 选择“轮巡间隔”（0=不轮巡）
- 数量不匹配时提示“将只显示前 N 路/将留空 M 格”

**Step 3 打开方式**
- 当前页（`router.push`）
- 新标签页（`window.open`，URL 带 `remind=true`）
- 当前页弹层
- 记住本次选择（localStorage）
- 生成可复制链接

URL 约定：

```text
/device/wall?devices=dev1:1:sub,dev1:2:main,dev2:1:sub&layout=4&tour=30&remind=true
```

### 11.5 视频墙 `/device/wall`（需求 2.2、2.4）

- 布局：1/4/9/16、1+5、1+7；拖拽换位、双击全屏。
- 每格工具条：通道切换、主/副码流、清晰度、云台、对讲、截图、录制、刷新、关闭。
- 全局：布局切换、轮巡、静音、全屏、添加设备、保存预案、退出。
- 状态：连接中/播放中/断流重连/离线；断流指数退避重连。
- 性能：只播可见格；默认子码流；单页 >16 路提示；页面隐藏暂停。
- 快捷键：`1-9` 切布局、`F` 全屏、`Esc` 退出、`M` 静音。
- `remind=true` 时顶部提示“已在新标签页打开视频墙”，可关闭且记住。

### 11.6 门禁出入记录 `/device/access`（需求 2.6、3.4）

- 查询：时间范围、门、人员、卡号、事件类型、结果、方向。
- 实时开关：订阅 `device.access`，新记录插到顶部并高亮 3 秒。
- 表格：时间、门、人员、卡号、方式（刷卡/人脸/指纹/二维码/密码/远程）、结果、方向、照片（脱敏/权限控制）。
- 操作：导出、详情、远程开门（需权限 + 二次确认 + 审计）。
- 统计：今日进出、拒绝次数、异常开门。
- 数据来源：定时抽取任务 + 实时事件；抽取游标存 `device_sync_state`。

### 11.7 停车场 `/device/parking`（需求 3.1）

- 车场列表：总车位/剩余/状态；车道/道闸在线状态。
- 过车记录：时间、车场、车道、车牌、方向、放行结果、图片。
- 操作：开闸、放行、异常处理。
- 后续：月卡/临时车/无感支付/车位引导/寻车。

### 11.8 设备大屏 `/device/screen`（需求 4）

- 全屏地图 + 聚合点；在线绿/离线灰/告警红脉冲。
- 顶部统计：总数、在线率、告警数、离线 Top。
- 工具栏：显示/隐藏、分类过滤（摄像头/门禁/报警/停车场）、图层、全屏、刷新。
- 右侧：告警滚动、离线设备列表；点击定位地图并弹视频。
- 自动轮巡、无操作隐藏鼠标、暗色主题、适配 1920/2560/4K。
- URL 参数：`?org=&categories=camera,access&toolbar=0&tour=30`。

### 11.9 其他建议入口

- 事件/报警中心 `/device/event`
- 统计报表 `/device/report`（在线率、故障率、告警趋势、资产台账）
- 运维中心（巡检、固件、配置备份、远程重启）

---

## 12. 接口清单

### 12.1 厂商与凭据

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/device/provider/vendors` | SPI 描述符列表（厂家/平台/凭据字段/能力） |
| POST | `/api/device/provider/test` | 按草稿连接信息测连通 |
| GET | `/api/device/provider-credential/list?vendor=` | 凭据列表（脱敏） |
| POST | `/api/device/provider-credential` | 新增 |
| PUT | `/api/device/provider-credential/{id}` | 修改（secret 留空不改） |
| DELETE | `/api/device/provider-credential/{id}` | 删除（被设备引用时拒绝） |
| POST | `/api/device/provider-credential/{id}/test` | 测试该凭据 |

### 12.2 设备

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/device/page` | 分页 + 过滤（替代前端全量拉取） |
| GET | `/api/device/{id}` | 详情 |
| POST | `/api/device/add` | 新增（补全名称/组织/分类/坐标等） |
| PUT | `/api/device/{id}` | 修改（不传密码表示不改） |
| DELETE | `/api/device/{id}` | 删除（有会话先断开） |
| POST | `/api/device/batch` | 批量改组织/负责人/标签/状态 |
| GET | `/api/device/changes?sinceSeq=` | SSE 断线补拉 |
| POST | `/api/device/{id}/reconnect` | 重连 |
| POST | `/api/device/{id}/refresh-status` | 刷新状态 |
| GET | `/api/device/export` | 导出 |

### 12.3 同步

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/device/sync/preview` | 预览 diff，不落库 |
| POST | `/api/device/sync/run` | 执行拉取/推送 |
| GET | `/api/device/sync/records` | 执行记录分页 |
| POST | `/api/device/sync/records/{id}/retry` | 重试 |
| GET | `/api/device/sync/conflicts` | 冲突列表 |
| POST | `/api/device/sync/conflicts/{id}/resolve` | 人工裁决 |

### 12.4 视频

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/device/video/stream` | `streamType=main|sub`、`protocol=hls|flv|fmp4` |
| GET | `/api/device/video/playback` | 回放 |
| GET | `/api/device/video/records` | 录像段 |
| POST | `/api/device/video/stop` | 释放转码资源（可选） |

### 12.5 门禁 / 停车场

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/device/access/records/page` | 门禁记录分页 |
| GET | `/api/device/access/doors` | 门列表 |
| POST | `/api/device/access/unlock` | 远程开门 |
| GET | `/api/device/parking/lots` | 车场 |
| GET | `/api/device/parking/records/page` | 过车记录 |
| POST | `/api/device/parking/gate/open` | 开闸 |

### 12.6 任务

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/device/task/config` | 任务配置列表 |
| PUT | `/api/device/task/config/{code}` | 修改启用/cron/间隔/参数 |
| POST | `/api/device/task/{code}/run` | 立即执行 |
| GET | `/api/device/task/{code}/logs` | 执行日志 |

---

## 13. 实施计划

### P0（先把地基和风险修掉，1–2 周）

1. **安全**
   - `DeviceInfo.deviceInfoPassword` 加 `@JsonIgnore`；详情单独出 `hasPassword`。
   - `DeviceProviderCredential.deviceCredentialAppSecret` 加 `@JsonIgnore`，列表返回掩码字段。
   - SK/密码入库加密（`AesCodec`，密钥配置化）；历史数据迁移脚本。
   - 门禁照片、车牌等敏感字段按权限脱敏。
2. **数据模型**
   - `device_info` 扩列 + 唯一键；新增 `device_sync_state/record/outbox`、`device_online_log`、`device_task_config`。
3. **添加/编辑设备补全字段**：名称、编码、组织、大类/小类、坐标、地址、负责人。
4. **组织同步改 upsert**，加外部 ID。
5. **在线检测修复**：双向状态、`last_online_at`、状态变化才发事件。
6. **列表页重构**：服务端分页、字段、复制、脉冲、最后在线、批量、详情抽屉。
7. **实时通道**：后端 `DeviceRealtimePublisher` + 前端 `useDeviceRealtime`（全局 socket 订阅 + 轮询兜底）。
8. **厂商接入页**：描述符接口 + 凭据 CRUD + 测试连接 + 掩码。

验收：密码/SK 不出现在任何响应；新增设备字段不丢；组织可重复同步；设备恢复能变在线；列表 1 万条不卡；状态 3 秒内可见。

### P1（核心业务闭环，2–3 周）

1. 同步中心：预览、拉取、冲突、日志、进度。
2. 视频墙 + 三步向导 + 主/副码流。
3. `ReMap` 换真实地图 + 坐标转换 + 外链。
4. 任务配置页 + 时间轮调度 + 在线/同步/事件任务。
5. 门禁记录页 + 抽取任务 + 实时推送。

验收：可从一个厂商同步 1000 台设备并预览 diff；视频墙 9 路 30 分钟不断流；上墙链接可分享；门禁记录 5 分钟内入库。

### P2（扩展与运营，3–4 周）

1. 停车场菜单与过车记录。
2. 设备大屏（地图、过滤、告警联动）。
3. 推送 outbox + 厂商侧增删改。
4. 组织管理抽屉。
5. 统计报表、事件中心、操作审计。

### P3（增值）

AI 事件、视频质量诊断、资产全生命周期、移动端、开放平台。

---

## 14. 更多功能建议（“帮我想一下”）

按“价值 / 成本”排序，前 12 个建议优先做：

| 优先级 | 功能 | 价值 | 成本 |
|---|---|---|---|
| ★★★ | **在线率与健康度**：7/30 天在线率、离线时长 Top、心跳曲线、健康分 | 运维最关心，数据已有 | 低 |
| ★★★ | **上墙预案与轮巡计划**：保存“某区域 9 路”预案，一键上墙/定时轮巡 | 指挥中心高频 | 低 |
| ★★★ | **告警降噪与联动**：去重/聚合/抑制，短信/电话/企微/钉钉/声光/上墙 | 避免告警疲劳 | 中 |
| ★★★ | **视频质量诊断**：遮挡、模糊、偏色、雪花、亮度异常、离线检测 | 主动运维，减少巡检 | 中 |
| ★★☆ | **资产全生命周期**：采购/入库/领用/维修/报废 + 二维码标签 + 扫码报修 | 资产台账闭环 | 中 |
| ★★☆ | **录像证据链**：时间轴回放、倍速、片段下载、水印、分享审批 | 事件取证刚需 | 中 |
| ★★☆ | **电子地图增强**：楼层/室内图、电子围栏、移动设备轨迹、离线瓦片 | 大屏与调度 | 中 |
| ★★☆ | **门禁深化**：人员/卡/人脸下发、权限组、访客预约、考勤联动、尾随/反潜回 | 门禁真正可用 | 高 |
| ★★☆ | **停车场深化**：车牌识别、无感支付、车位引导、寻车 | 商业价值 | 高 |
| ★★☆ | **设备运维**：远程重启、配置备份/恢复、固件升级、日志抓取、NTP 校时 | 降低上门 | 中 |
| ★★☆ | **数据权限与审计**：组织/区域/设备级数据权限，开门/删除/导出审计 | 合规 | 中 |
| ★★☆ | **开放平台**：OpenAPI、Webhook、MQTT 物模型、第三方对接 | 生态 | 中 |
| ★☆☆ | 移动端/小程序巡检、扫码 | 现场效率 | 中 |
| ★☆☆ | 多租户与设备共享 | SaaS 化 | 高 |
| ★☆☆ | 带宽自适应/按需拉流/边缘转码 | 降本 | 中 |
| ★☆☆ | 网络拓扑、链路监控、PoE/交换机端口 | 排障 | 高 |
| ★☆☆ | 能耗管理、机柜/机房 | 扩展场景 | 高 |
| ★☆☆ | 大屏指挥模式：一键上墙、预案、多屏联动 | 演示/指挥 | 中 |
| ★☆☆ | 国际化、暗色、无障碍 | 体验 | 低 |
| ★☆☆ | AI：人形/车辆/越界/离岗/烟火/客流 | 增值 | 高 |

另外几个“低成本高感知”的点：
- 列表页“最后在线”相对时间 + 离线时长排序。
- 设备详情“一键复制台账信息”（名称/编码/IP/序列号）。
- 离线设备一键“批量重连/批量检测”。
- 上墙时离线设备置灰并提示，避免黑屏。
- 视频墙“断流自动重连 + 重连次数/下次重试倒计时”。
- 所有长操作（同步、批量、抽取）都有进度与取消。
- 大屏“无操作自动隐藏鼠标和工具栏”。
- 设备变更订阅：负责人变更、组织变更发站内信。

---

## 15. 风险与待确认

| 风险 | 说明 | 对策 |
|---|---|---|
| 厂商 SDK 授权/动态库 | 海康/大华 SDK 需 dll/so，跨平台受限 | 平台模式优先；SDK 作为可选部署 |
| 平台 API 限流/配额 | 全量同步可能触发限流 | 增量 + 分页 + 退避 + 夜间执行 |
| 转码成本 | 多路 H.265 转码吃 CPU/GPU | 子码流优先、按需拉流、边缘转码 |
| 浏览器兼容 | H.265、fMP4、WebRTC 差异 | 能力探测 + 降级 HLS/FLV |
| 坐标系 | WGS84/GCJ02/BD09 混用 | 统一转换工具，落库 WGS84 |
| 数据合规 | 人脸、车牌、门禁记录 | 权限、脱敏、审计、留存策略 |
| 集群并发 | 多实例重复执行任务 | DB 行锁/Redis 锁 + 幂等 |
| 时钟 | 设备/平台/服务器时间不一致 | NTP + 以服务器时间为准 + 记录原始时间 |

**需要拍板的问题：**

1. 首批必须打通的厂商与平台？（海康云眸/安防、 大华云睿/乐橙、宇视、萤石、华为好望…）
2. 视频部署形态：服务端 FFmpeg 转码 / 边缘网关 / 仅厂商 HLS？是否接受 WebRTC？
3. 大屏地图：高德/天地图/离线瓦片？是否有商用 key？
4. 是否多租户/数据权限？门禁照片、车牌是否属于敏感数据？
5. 任务中心是否复用现有 `pages/job`，还是设备域独立配置页？
6. 上墙默认“当前页”还是“新标签页”？
7. 门禁与停车场是否同一期交付？

---

## 16. 附：本次建议改动的文件

后端：

```text
spring-support-device-starter/
  src/main/java/com/chua/starter/device/spi/
    model/DeviceVendorDescriptor.java       新增
    model/PlatformDescriptor.java           新增
    model/CredentialField.java              新增
    DeviceProvider.java                     加 descriptor() 默认方法
    DeviceDirectoryProvider.java            新增（可选能力）
    DeviceOrganizationProvider.java         新增
    DeviceAccessProvider.java               新增
    DeviceParkingProvider.java              新增
    DeviceMediaProvider.java                新增
  support/controller/
    ProviderController.java                 新增（vendors/test）
    DeviceController.java                   分页、补字段、脱敏
    ProviderCredentialController.java       脱敏、测试、默认套约束
    SyncController.java                     新增
    TaskController.java                     新增
    AccessRecordController.java             新增
    ParkingController.java                  新增
  support/service/impl/
    DeviceServiceImpl.java                  补字段、upsert、状态双向
    DeviceSyncService.java                  新增
    DeviceTaskService.java                  新增
    DeviceRealtimePublisher.java            新增
  support/scheduler/DeviceScheduler.java    改为任务配置 + 时间轮
  resources/db/init/V1.1__device_asset_upgrade.sql  新增增量脚本
```

前端：

```text
pages/device/src/
  views/DeviceView.vue              重构为资产列表 + 详情抽屉
  views/ProviderView.vue            新增
  views/SyncView.vue                新增
  views/WallView.vue                新增
  views/AccessRecordView.vue        新增
  views/ParkingView.vue             新增
  views/ScreenView.vue              新增
  components/DeviceWallWizard.vue   新增
  components/ReVideoPlayer.vue      新增
  components/DeviceStatusDot.vue    新增
  components/DeviceLocationCell.vue 新增
  composables/useDeviceRealtime.ts  新增
  api/deviceApi.ts                  补接口
  utils/coord.ts                    新增
  router/index.ts                   新增路由与菜单
```

---

**下一步建议**：先落 P0 的 1–5（安全 + 数据模型 + 添加字段 + 组织 upsert + 在线双向），这些不改页面结构也能立刻消除现网风险；再做列表重构和实时通道。
