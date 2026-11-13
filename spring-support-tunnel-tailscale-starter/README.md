# spring-support-tailscale-starter

Spring Boot 集成 [Tailscale](https://tailscale.com) Funnel，把本机服务暴露到公网 Tailnet。Tailscale 不提供可内嵌的 Java SDK，本模块基于官方 `tailscale` 命令行客户端，通过 `funnel` 子进程建立隧道。

---

## 特性

- ✅ **默认开启**：引入即生效，只需配置认证 Key（或设备已 `tailscale up` 登录）
- ✅ **统一入口**：作为 `TunnelProvider` 接入 `spring-support-tunnel-starter`，`/ms/tunnel/**` 统一管理
- ✅ **两种模式支持**：`http`（Funnel HTTPS 地址，默认）/ `tcp`（暴露 443 / 8443 / 10000）
- ✅ **持久化映射**：`~/.chua/tunnel/tailscale/` 下一个 JSON 文件存一条映射，重启恢复
- ✅ **自动启动**：应用就绪后自动建默认隧道，容器关闭时自动关闭 `funnel` 子进程
- ✅ **失败容错**：`failFast` 控制是否中断 Spring 启动
- ✅ **跨平台**：Windows（`C:\Program Files\Tailscale\tailscale.exe`）/ macOS / Linux，也可通过 `binary` 配置指定路径
- ✅ **设备级凭据**：Tailscale 没有单条隧道 Token，通过 `plugin.tailscale.authKey` 或环境变量 `TS_AUTHKEY` 注入设备

---

## 1. 引入依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-tailscale-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

> 该模块同时依赖 `spring-support-tunnel-starter`（统一 REST 入口与编排），无需自行引入。

---

## 2. 最小启用（最常用）

只需配置认证 Key，**模块默认开启**：

```yaml
# application.yml
plugin:
  tailscale:
    authKey: ts.authkey=2acE1b4Tbt5PMDiJNk39A3
```

或者通过环境变量（推荐 — Key 不入 git）：

```bash
# Linux / macOS
export TS_AUTHKEY=ts.authkey=xxxxxxxx

# Windows PowerShell
$env:TS_AUTHKEY="ts.authkey=xxxxxxxx"
```

启动 Spring Boot，控制台会输出：

```
=========================================================
  Tailscale Funnel 已建立，本机服务已暴露到公网
=========================================================
  https://<machine>.<tailnet>.ts.net
=========================================================
```

即可通过公网地址访问本机 Spring 服务。

---

## 3. 配置项详细说明

所有配置项前缀为 `plugin.tailscale`。

### 3.1 字段总表（必填 / 选填 / 默认）

| 字段 | 必填 | 默认 | 说明 |
|:-----|:----:|:----:|:-----|
| `enable` | 否 | `true` | **总开关**。设为 `false` 完全禁用。模块默认开启 |
| `binary` | 否 | 自动定位 | tailscale 可执行文件路径；为空时自动定位（`TAILSCALE_BIN` 环境变量 → PATH → 常见安装目录） |
| `authKey` | 否 | 无 | 设备认证 Key（同 `TS_AUTHKEY`），用于无登录态建隧道；非键入时设备需先 `tailscale up` 加入 tailnet |
| `startTimeout` | 否 | `60s` | 启动等待公网 URL 的超时 |
| `failFast` | 否 | `false` | 隧道启动失败时是否中断应用；`true` 直接抛出异常退出，`false` 只打 ERROR 日志 |
| `autoStart` | 否 | `true` | 启动时是否按本配置自动建立默认隧道（即 `server.port` 的 HTTP 隧道） |
| `autoStartStored` | 否 | `true` | 启动时是否恢复上次标记为「自动启动」的持久化映射 |
| `storeDir` | 否 | `~/.chua/tunnel/tailscale` | 映射持久化目录；一条映射一个 JSON 文件，不使用数据库 |

### 3.2 必填项总结

| 场景 | 必填项 |
|:-----|:-------|
| 已登录设备（默认） | （无需 Key） |
| 无登录态一键建隧道 | `authKey`（或 `TS_AUTHKEY`） |
| 指定本地二进制 | `binary` |

### 3.3 TCP 模式说明

Tailscale Funnel 只能暴露 `443 / 8443 / 10000` 三个公网 TCP 端口（未提权的设备），因此 `tcp` 模式下映射固定暴露 `443`，一律使用 `tailscale funnel --tcp=443 --yes <target>` 发起；本地服务端口由 `localPort` 决定。

---

## 4. 典型场景示例

### 4.1 指定二进制路径

```yaml
plugin:
  tailscale:
    binary: /usr/local/bin/tailscale
```

### 4.2 不自动建隧道（管理接口仍可用）

```yaml
plugin:
  tailscale:
    autoStart: false
```

适合「从页面按需映射端口」的部署方式。

### 4.3 启动失败中断应用

```yaml
plugin:
  tailscale:
    failFast: true
```

### 4.4 长连接超时调大

```yaml
plugin:
  tailscale:
    startTimeout: 90s
```

### 4.5 映射持久化目录指向挂载盘

```yaml
plugin:
  tailscale:
    storeDir: /data/tunnel/tailscale
```

---

## 5. 关闭 / 禁用

```yaml
plugin:
  tailscale:
    enable: false
```

也可以通过环境变量 `PLUGIN_TAILSCALE_ENABLE=false`，或 Spring Profile：

```yaml
# application-prod.yml
plugin:
  tailscale:
    enable: false
```

---

## 6. 故障排查

### 6.1 `authKey 未配置`

```
[Tailscale] 缺少认证 Key
```

**解决**：设置 `plugin.tailscale.authKey` 或环境变量 `TS_AUTHKEY`。

### 6.2 `failed to connect to local tailscaled process`

```
[Tailscale] 隧道启动失败: tailscale: exit status 1: failed to connect to local tailscaled process
```

**原因**：Tailscale 守护进程未在运行。

**解决**：
- 在创建隧道前，先运行 `tailscale up` 登录设备；
- 配置 `plugin.tailscale.authKey`（或 `TS_AUTHKEY`），隧道启动时子进程会注入 `--auth-key`。

### 6.3 `please run tailscale up`

```
[Tailscale] 隧道启动失败: tailscale: exit status 1: please run tailscale up
```

**原因**：设备未加入 tailnet。

**解决**：运行 `tailscale up --auth-key=<key>` 完成设备认证。

### 6.4 `binary not found`

```
[Tailscale] 无法定位 tailscale 可执行文件
```

**解决**：确认 `PATH` 上有 `tailscale`，或设置 `plugin.tailscale.binary` 为绝对路径。

---

## 7. 与 utils-support-tailscale-starter 的关系

- `utils-support-tailscale-starter` — **通用层**（`TailscaleTool` CLI 定位 + `TailscaleTunnelClient` 链式调启，零 Spring 依赖）
- `spring-support-tailscale-starter` — **Spring Boot 集成层**（依赖前者，封装自动配置 + `TunnelProvider` 实现）

如需在非 Spring 环境直接调用 Tailscale CLI，直接引入 utils-support-tailscale-starter 即可。

---

## 8. 版本对应

| Tailscale CLI | spring-support-tailscale-starter | utils-support-tailscale-starter |
|:-------------:|:--------------------------------:|:------------------------------:|
| 1.102.4       | 4.0.0.51                         | 4.0.0.50                       |

---

## 9. 隧道管理工作台

引入 `spring-support-tunnel-starter` 后，`GET /ms/tunnel/providers` 会列出 `Tailscale Funnel`：

```json
{
  "code": "00000",
  "data": [
    {
      "name": "tailscale",
      "displayName": "Tailscale Funnel",
      "available": true,
      "modes": ["http", "tcp"],
      "defaultMode": "http",
      "capabilities": {
        "provider": "tailscale",
        "binaryAvailable": true,
        "binaryPath": "C:\\Program Files\\Tailscale\\tailscale.exe",
        "authKeyConfigured": true,
        "autoStart": true,
        "autoStartStored": true
      }
    }
  ]
}
```

所有映射的增删改查、启停恢复、SSE 实时推送统一走 `/ms/tunnel` 前缀（详见 [spring-support-tunnel-starter/README.md](spring-support-tunnel-starter/README.md)），无需为每个提供商维护独立页面。

---

## 10. 作者

CH <ch@chua.com>
