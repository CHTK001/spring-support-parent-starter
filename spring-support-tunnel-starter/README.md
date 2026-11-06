# spring-support-tunnel-starter

内网穿透统一入口：把 ngrok、Cloudflare Tunnel、Tailscale Funnel 等提供方的差异折叠成一套 `TunnelProvider` SPI，
所有的 REST 接口、映射持久化与 SSE 推送公共逻辑只保留一份。

---

## 特性

- ✅ **统一入口**：所有提供方共用 `/ms/tunnel/**`，页面只需维护一套 API
- ✅ **SPI 提供方**：新增提供商只需实现 `TunnelProvider` 并注册为 Bean，无需改控制器与页面
- ✅ **对应方多选**：`plugin.tunnel.providers` 白名单控制暴露哪些提供方
- ✅ **持久化台账**：`~/.chua/tunnel/<provider>/<id>.json`，一条映射一个 JSON 文件，持久化到磁盘
- ✅ **自动启动恢复**：应用启动时恢复标记为 `autoStart=true` 的映射
- ✅ **运行态内存**：会话、启动时间、最近错误只存内存，不落盘
- ✅ **SSE 实时推送**：按 1.5 秒 固定频率广播完整映射快照，页面断线自动重连
- ✅ **全局唯一映射 id**：ID 不加 provider 前缀，一个 id 走通全部接口

---

## 1. 引入依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-tunnel-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

> 需要搭配任一提供方使用：`spring-support-ngrok-starter`、`spring-support-cloudflared-starter` 或
> `spring-support-tailscale-starter`（三方实现已在 classpath 上时仅暴露白名单内的提供方）。

---

## 2. 最小启用（最常用）

引入伴随的提供方 starter 即可，**统一入口默认开启**：

```yaml
# application.yml
plugin:
  tunnel:
    enable: true
  # 可选：指定暴露的提供方白名单
  # providers: [ngrok, tailscale]
```

---

## 3. 配置项详细说明

所有配置项前缀为 `plugin.tunnel`。

### 3.1 字段总表（必填 / 选填 / 默认）

| 字段 | 必填 | 默认 | 说明 |
|:-----|:----:|:----:|:-----|
| `enable` | 否 | `true` | **总开关**。设为 `false` 既不注册 REST 接口，也不恢复自动启动的映射 |
| `storeDir` | 否 | `~/.chua/tunnel` | 映射持久化根目录；每个提供方一个子目录（`ngrok/`、`cloudflared/`、`tailscale/`） |
| `providers` | 否 | 全部注册提供方 | 对外暴露的提供方白名单，为空表示「容器里有几个就暴露几个」 |
| `restoreOnStartup` | 否 | `true` | 是否在启动时恢复标记为 `autoStart=true` 的持久化映射 |
| `sseInterval` | 否 | `1500ms` | SSE 状态推送间隔 |

### 3.2 白名单示例（生产只留 Tailscale）

```yaml
plugin:
  tunnel:
    providers: [tailscale]
```

这样即使 classpath 同时有 ngrok / Cloudflare 实现，也不会出现在接口与页面里。

---

## 4. 提供方 SPI：新增提供商只需 1 个类

```java
public class MyTunnelProvider implements TunnelProvider {

    @Override public String name() { return "mytunnel"; }

    @Override public String displayName() { return "My Tunnel"; }

    @Override public boolean isAvailable() { return new MyTool().isAvailable(); }

    @Override public List<String> supportedModes() { return List.of("http", "tcp"); }

    @Override public String defaultMode() { return "http"; }

    @Override public String storeDir() { return null; }   // 继承 plugin.tunnel.storeDir

    @Override public boolean autoStartStored() { return true; }

    @Override public Map<String, Object> capabilities() { return Map.of(); }

    @Override public void applyInput(TunnelRecord target, TunnelRecord input) { /* 填写厂商专属字段 */ }

    @Override public TunnelSession open(TunnelRecord record) { /* 建立隧道 */ }
}
```

注册为 Bean 后由 `TunnelProviderRegistry` 自动收集，统一入口 `/ms/tunnel` 即刻可用。

### 4.1 字段约定

- `name()`：提供方标识（小写），也是持久化目录名，必须全局唯一，两个实现争抢同一名称启动直接报错。
- `supportedModes()`：`http` / `tcp`（由各提供方 Fnuel/`tunnel`/`funnel` 决定）。
- `defaultMode()`：创建映射时未传 `mode` 使用的默认模式。
- `capabilities()`：页面渲染提供方状态（`binaryAvailable`、`authKeyConfigured` …），`provider` 为 `name()`。
- `applyInput()`：把页面提交的 `TunnelRecord` 翻译成厂商专属配置（如 ngrok 的 `authtoken`、tailscale 的 `--tcp=443`）。
- `open()`：唯一执行 DOS（协议、命令、Token ），错误记入映射错误信息。

---

## 5. REST 接口

| 方法 | 路径 | 说明 |
|:-----|:----:|:-----|
| GET | `/ms/tunnel/providers` | 提供方列表（能力、支持模式、可用性） |
| GET | `/ms/tunnel` | 映射列表（台账 + 运行状态），可接 `?provider=xxx` |
| GET | `/ms/tunnel/{id}` | 映射详情 |
| POST | `/ms/tunnel` | 新建映射（请求体 `TunnelRecord`，`provider` 必填；单提供方可省略） |
| PUT | `/ms/tunnel/{id}` | 更新映射（运行中先停止再重启） |
| DELETE | `/ms/tunnel/{id}` | 删除映射（运行中先停止） |
| POST | `/ms/tunnel/{id}/start` | 启动映射 |
| POST | `/ms/tunnel/{id}/stop` | 停止映射 |
| GET | `/ms/tunnel/stream` | SSE 实时状态推送 |

`POST`/`PUT` 请求体见 `TunnelRecord`：

```json
{
  "id": "1a2b3c4d-1234-5678-9abc-def012345678",
  "name": "测试映射",
  "provider": "tailscale",
  "localPort": 8080,
  "mode": "http",
  "domain": "",
  "enabled": true,
  "autoStart": true,
  "remark": ""
}
```

> 映射 id 由服务端生成 UUID；单提供方部署下无需传 `provider`，系统自动选中已注册的唯一实现。

---

## 6. SSE 实时状态

`/ms/tunnel/stream` 是 `TunnelStatusStream` 产出的 SSE 端点：连接时即推送当前快照，后续按 **1.5 秒** 固定频率广播完整的最新（所有映射）快照：

```
event: tunnels
data: [{ "id": "...", "name": "测试映射", "localPort": 8080, "mode": "http",
         "running": true, "publicUrls": ["https://abc.ts.net"], "error": "" }]
```

---

## 7. 持久化存储

目录布局与改造前完全一致，历史映射无需搬迁：

```
~/.chua/tunnel
├── ngrok
│   └── 1a2b3c4d-1234-5678-9abc-def012345678.json
├── cloudflared
│   └── 2b3c4d5e-...json
└── tailscale
    └── 3c4d5e6f-...json
```

每个文件包含该映射的完整台账信息（如 `id`、`name`、`localPort`、`mode`、`domain`、`provider`、`enabled`、`autoStart`、`remark`）。创建会新建文件，更新回写，删除删除文件。

- 通过 `TunnelProvider#storeDir()` 可覆盖自己的目录。
- 页面通过 SSE 实时接收后端广播的变化，看到的永远是磁盘上的实际状态。

---

## 8. 自动启动恢复

`TunnelService#restoreAutoStart()` 在 `ApplicationReadyEvent` 时执行：

- `plugin.tunnel.restoreOnStartup=false` 时跳过；
- 只恢复 `enabled` + `autoStart=true` 的映射，且提供方支持 `autoStartStored()`；
- 单条失败（凭据过期、端口占用）只记错误日志，不影响其余映射，也不阻塞应用启动。

---

## 9. 故障排查

### 9.1 `未发现任何 TunnelProvider 实现`

```
[Tunnel] 未发现任何 TunnelProvider 实现：请引入 spring-support-ngrok-starter 或 spring-support-cloudflared-starter
```

**解决**：引入至少一个提供方 starter。

### 9.2 `未指定提供方：当前可选 []`

POST 映射时未传 `provider` 且容器内有多个提供方。

**解决**：显式传 `provider`，或把白名单 `plugin.tunnel.providers` 限制为一个。

---

## 10. 页面集成（参考）

监控管理应用 (`vue-support-monitor-starter`) 的 `pages/tunnel` 已完成接入：下拉列表来自
`GET /ms/tunnel/providers`，映射列表、创建/更新/删除/启停、SSE 推送统一走 `/ms/tunnel` 前缀。`provider` 字段决定由哪个提供商承载，所以新增提供方后页面无需改动即可使用。

---

## 11. 作者

CH <ch@chua.com>
