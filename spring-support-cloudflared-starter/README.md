# spring-support-cloudflared-starter

Spring Boot 3 自动将本机服务通过 [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/) 暴露到公网。基于官方 `cloudflared` 守护进程，零账号门槛，开箱即用。

---

## 特性

- ✅ **默认开启**：依赖引入即生效，无需配置任何 token（免账号 Quick Tunnel）
- ✅ **免账号 Quick Tunnel**：一条命令把本机服务暴露为 `*.trycloudflare.com` 公网 HTTPS 地址
- ✅ **自动获取 `server.port`**：处理 `server.port=0` 随机端口场景
- ✅ **远程管理命名隧道**：支持 Zero Trust 面板 Token（`plugin.cloudflared.token`）
- ✅ **固定自定义域名**：支持 `plugin.cloudflared.hostname`（需账户 + DNS CNAME）
- ✅ **失败容错**：`failFast` 控制是否中断 Spring 启动
- ✅ **跨平台**：Windows / macOS / Linux
- ✅ **凭据保护**：日志自动脱敏 token

---

## 1. 引入依赖

先安装 [cloudflared](https://github.com/cloudflare/cloudflared/releases)（macOS：`brew install cloudflared`；Windows：`winget install Cloudflare.cloudflared`）：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-cloudflared-starter</artifactId>
    <version>4.0.0.42</version>
</dependency>
```

---

## 2. 最小启用（最常用，免账号）

**不需要任何配置**，模块默认开启：

```yaml
# application.yml
plugin:
  cloudflared:
    enable: true
```

启动 Spring Boot，控制台会输出：

```
=========================================================
  Cloudflare Tunnel 已建立，本机服务已暴露到公网
=========================================================
  🌐 https://xxx-xxx-xxx.trycloudflare.com
=========================================================
```

通过公网 URL 即可访问你本地的 Spring 服务。

---

## 3. 配置项详细说明

所有配置项前缀为 `plugin.cloudflared`。

| 字段 | 必填 | 默认 | 说明 |
|:-----|:----:|:----:|:-----|
| `enable` | 否 | `true` | **总开关**。设为 `false` 完全禁用 |
| `binary` | 否 | 自动定位 | cloudflared 可执行文件路径；自动定位顺序：`CLOUDFLARED_BIN` 环境变量 → PATH → 常见安装目录 |
| `token` | 否 | 无 | Zero Trust 远程管理隧道 Token；为空时使用免账号 Quick Tunnel |
| `hostname` | 否 | 无 | 固定自定义域名（需账户 + DNS CNAME 路由） |
| `localUrl` | 否 | `http://127.0.0.1:server.port` | 本地待暴露的服务地址 |
| `noAutoUpdate` | 否 | `true` | 禁用 cloudflared 运行期自动更新（避免隧道中断） |
| `logLevel` | 否 | `info` | cloudflared 日志级别：`debug` / `info` / `warn` / `error` / `fatal` |
| `startTimeout` | 否 | `40s` | 启动等待公网 URL 的超时 |
| `failFast` | 否 | `false` | 启动失败时是否中断 Spring 启动 |

---

## 4. 典型场景示例

### 4.1 Quick Tunnel（默认，免账号）

```yaml
plugin:
  cloudflared:
    enable: true
```

效果：随机 `*.trycloudflare.com` 域名 + 自动转发到 `server.port`。

### 4.2 固定自定义域名

```yaml
plugin:
  cloudflared:
    hostname: tunnel.example.com
```

前提：该域名已添加到 Cloudflare 账户并配置 CNAME 路由（指向 `<隧道>.cfargotunnel.com`）。

### 4.3 Zero Trust 远程管理隧道

```yaml
plugin:
  cloudflared:
    token: ${CLOUDFLARE_TUNNEL_TOKEN}
```

Token 在 [Zero Trust 面板](https://one.dash.cloudflare.com) → Networks → Tunnels → Create tunnel 中生成。

### 4.4 指定本地服务地址

```yaml
plugin:
  cloudflared:
    localUrl: http://127.0.0.1:9090
```

### 4.5 失败快速失败

```yaml
plugin:
  cloudflared:
    failFast: true
```

默认 `failFast: false`，启动失败时只打 ERROR 日志、Spring 继续启动；设为 `true` 则直接中断启动。

---

## 5. 关闭 / 禁用

```yaml
plugin:
  cloudflared:
    enable: false
```

或通过环境变量：`PLUGIN_CLOUDFLARED_ENABLE=false`。

---

## 6. 故障排查

### 6.1 未找到 cloudflared

```
[Cloudflared] 隧道启动失败: 未找到 cloudflared 可执行文件...
```

**解决**：设置 `CLOUDFLARED_BIN` 环境变量指向可执行文件，或安装后重试。

### 6.2 进程提前退出

```
cloudflared 进程提前退出，退出码=...
```

**原因**：token 无效、端口已被占用、本地服务未启动。

### 6.3 启动超时

```
cloudflared 启动超时（40000ms），未输出公网地址
```

**原因**：网络无法访问 Cloudflare 边缘网络；固定域名场景需确认 DNS CNAME 已生效。

### 6.4 隧道变 530

通常为进程输出流被外部关闭导致——本模块持续消费输出直至进程退出，无此问题。

---

## 7. 与 utils-support-cloudflared-starter 的关系

- `utils-support-cloudflared-starter` — **通用层**（纯 Java 链式进程客户端，零 Spring 依赖）
- `spring-support-cloudflared-starter` — **Spring Boot 集成层**（依赖前者，封装自动配置）

如需在非 Spring 环境使用 CloudflareTunnelClient，直接引入 utils-support-cloudflared-starter 即可。

---

## 8. 作者

CH <ch@chua.com>