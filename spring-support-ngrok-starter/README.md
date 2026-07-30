# spring-support-ngrok-starter

Spring Boot 3 自动将本机服务通过 [ngrok](https://ngrok.com) 暴露到公网。基于官方 [ngrok-java](https://github.com/ngrok/ngrok-java) Agent SDK，零外部进程，开箱即用。

---

## 特性

- ✅ **默认开启**：依赖引入即生效，只需配置 authtoken
- ✅ **零外部进程**：内嵌 Rust Agent，无需启动独立 ngrok CLI
- ✅ **自动获取 `server.port`**：处理 `server.port=0` 随机端口场景
- ✅ **支持三种隧道**：`http` / `tcp` / `tls`
- ✅ **支持两种模式**：`forward`（自动转发到本机 server.port）/ `listen`（仅暴露 URL）
- ✅ **多端口同时暴露**：`extraLocalPorts` 同时转发多个端口
- ✅ **HTTP 鉴权**：内置 BasicAuth 支持
- ✅ **私有部署**：支持自建 ngrok 服务端 + CA 证书
- ✅ **失败容错**：`failFast` 控制是否中断 Spring 启动
- ✅ **跨平台**：Windows / macOS / Linux（x86_64、aarch64、armv7、i686）

---

## 1. 引入依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-ngrok-starter</artifactId>
    <version>4.0.0.42</version>
</dependency>
```

---

## 2. 最小启用（最常用）

只需配置 authtoken，**模块默认开启**：

```yaml
# application.yml
plugin:
  ngrok:
    authtoken: 2abc...xyz
```

或者通过环境变量（推荐 — token 不入 git）：

```bash
# Linux / macOS
export NGROK_AUTHTOKEN=2abc...xyz

# Windows PowerShell
$env:NGROK_AUTHTOKEN="2abc...xyz"
```

启动 Spring Boot，控制台会输出：

```
=========================================================
  Ngrok 隧道已建立，本机服务已暴露到公网
=========================================================
  🌐 https://xxxx-xxx-xxx-xxx.ngrok-free.app
=========================================================
```

通过公网 URL 即可访问你本地的 Spring 服务。

---

## 3. 配置项详细说明

所有配置项前缀为 `plugin.ngrok`。

### 3.1 字段总表（必填 / 选填 / 默认）

| 字段 | 必填 | 默认 | 说明 |
|:-----|:----:|:----:|:-----|
| `enable` | 否 | `true` | **总开关**。设为 `false` 完全禁用。模块默认开启 |
| `authtoken` | **是** | 无 | ngrok authtoken。空时回退读取环境变量 `NGROK_AUTHTOKEN`；两者都为空则启动期**硬错误**中断 |
| `metadata` | 否 | 无 | 会话元数据，ngrok 控制台可见 |
| `serverAddr` | 否 | 无 | 私有部署 ngrok 服务端地址，例：`tunnel.example.com:443` |
| `caCert` | 否 | 无 | 私有部署 CA 证书（classpath 资源路径），例：`classpath:ngrok-ca.crt` |
| `heartbeatInterval` | 否 | 30s | 心跳间隔 |
| `heartbeatTolerance` | 否 | 60s | 心跳容忍 |
| `type` | 否 | `http` | 隧道类型：`http` / `tcp` / `tls` |
| `mode` | 否 | `forward` | 转发模式：`forward` 自动转发 / `listen` 仅暴露 URL |
| `domain` | 否 | 无 | 固定域名（http/tls），需 ngrok 面板预留 |
| `subdomain` | 否 | 无 | 子域名（已部分下线，建议改用 `domain`） |
| `remoteAddress` | **tcp 模式必填** | 无 | TCP 远程地址，例：`1.tcp.ngrok.io:20000` |
| `scheme` | 否 | `HTTPS` | http 模式协议方案：`HTTP` / `HTTPS` |
| `compression` | 否 | `false` | 启用 HTTP 响应压缩 |
| `basicAuth` | 否 | 无 | BasicAuth，格式 `user:password` |
| `basicAuthUsername` | 否 | 无 | BasicAuth 用户名（与 `basicAuthPassword` 配合） |
| `basicAuthPassword` | 否 | 无 | BasicAuth 密码 |
| `failFast` | 否 | `false` | ngrok 启动失败时是否中断 Spring 启动 |
| `extraLocalPorts` | 否 | 无 | 额外本地端口（逗号分隔），例：`8081,8082` |

### 3.2 必填项总结

| 场景 | 必填项 |
|:-----|:-------|
| HTTP 转发（默认） | `authtoken` |
| HTTP 固定域名 | `authtoken` + `domain` |
| HTTP BasicAuth | `authtoken` + `basicAuth`（或 username + password） |
| TCP 隧道 | `authtoken` + `remoteAddress` |
| TLS 隧道 | `authtoken` + `domain` |
| 私有部署 | `authtoken` + `serverAddr` + `caCert` |

---

## 4. 典型场景示例

### 4.1 HTTP 转发（默认）

```yaml
plugin:
  ngrok:
    authtoken: ${NGROK_AUTHTOKEN}
```

效果：随机域名 + 自动转发到 `server.port`（默认 8080）。

### 4.2 固定域名

```yaml
plugin:
  ngrok:
    authtoken: ${NGROK_AUTHTOKEN}
    domain: my-app.ngrok-free.app
```

前提：先在 <https://dashboard.ngrok.com/cloud-edge/domains> 预留该域名。

### 4.3 HTTP BasicAuth 鉴权

```yaml
plugin:
  ngrok:
    authtoken: ${NGROK_AUTHTOKEN}
    domain: my-app.ngrok-free.app
    basicAuth: admin:secret123
```

或者：

```yaml
plugin:
  ngrok:
    authtoken: ${NGROK_AUTHTOKEN}
    basicAuthUsername: admin
    basicAuthPassword: secret123
```

### 4.4 同时暴露多个本地端口

```yaml
plugin:
  ngrok:
    authtoken: ${NGROK_AUTHTOKEN}
    extraLocalPorts: 8081,8082
```

效果：会同时为 8080（server.port）、8081、8082 建立 HTTP 转发隧道，公网 URL 会有 3 个。

### 4.5 TCP 隧道

```yaml
plugin:
  ngrok:
    authtoken: ${NGROK_AUTHTOKEN}
    type: tcp
    remoteAddress: 1.tcp.ngrok.io:20000
```

前提：先在 ngrok 面板 reserve 一个 TCP 地址。

### 4.6 TLS 隧道

```yaml
plugin:
  ngrok:
    authtoken: ${NGROK_AUTHTOKEN}
    type: tls
    domain: my-app.example.com
    mode: forward
```

### 4.7 私有部署

```yaml
plugin:
  ngrok:
    authtoken: ${NGROK_AUTHTOKEN}
    serverAddr: tunnel.example.com:443
    caCert: classpath:ngrok-ca.crt
```

把 `ngrok-ca.crt` 放到 `src/main/resources/` 即可。

### 4.8 listen 模式（仅暴露 URL，不自动转发）

```yaml
plugin:
  ngrok:
    authtoken: ${NGROK_AUTHTOKEN}
    mode: listen
```

适用场景：需要手动 accept 连接、处理字节流时使用。

### 4.9 失败快速失败

```yaml
plugin:
  ngrok:
    authtoken: ${NGROK_AUTHTOKEN}
    failFast: true
```

默认 `failFast: false`，ngrok 启动失败时只打 ERROR 日志，Spring 继续启动；设为 `true` 则直接中断启动。

> 注意：**authtoken 为空**导致的错误**不受** `failFast` 控制 — 必填项会直接中断启动。

---

## 5. 关闭 / 禁用

### 5.1 临时关闭（不改代码）

```yaml
plugin:
  ngrok:
    enable: false
```

### 5.2 生产环境彻底禁用

通过环境变量：

```bash
PLUGIN_NGROK_ENABLE=false
```

或者用 Spring Profile：

```yaml
# application-prod.yml
plugin:
  ngrok:
    enable: false
```

---

## 6. 故障排查

### 6.1 `authtoken 未配置`

```
[Ngrok] authtoken 未配置：请设置 plugin.ngrok.authtoken 或环境变量 NGROK_AUTHTOKEN
```

**解决**：设置 `plugin.ngrok.authtoken` 或 `NGROK_AUTHTOKEN` 环境变量。

### 6.2 `连接被拒绝` / `Connection refused`

```
io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: connect
```

**原因**：本机服务未启动，或 `server.port` 配置与实际不一致。

**解决**：
- 确认 Spring 服务已正常启动（看启动 banner）
- 确认 `server.port` 与实际监听端口一致
- 默认模块监听 `WebServerInitializedEvent`，能正确处理 `server.port=0` 随机端口

### 6.3 `认证失败` / `authtoken 无效`

```
[Ngrok] Ngrok Session 连接失败: ...
```

**解决**：
- 检查 authtoken 是否正确（不要有多余空格）
- 重新到 <https://dashboard.ngrok.com/get-started/your-authtoken> 复制

### 6.4 `unsatisfiedlinkerror`

```
java.lang.UnsatisfiedLinkError: no ngrok-java-native-linux-x86_64 in java.library.path
```

**原因**：`ngrok-java-native` 平台不匹配。

**解决**：本模块已通过 `os-maven-plugin` 自动选择 native 分类器，确认父 pom 启用了 `kr.motd.maven:os-maven-plugin`。

### 6.5 域名绑定失败

```
[Ngrok] ... domain not reserved
```

**原因**：自定义域名未在 ngrok 面板预留。

**解决**：到 <https://dashboard.ngrok.com/cloud-edge/domains> 预留并配置 DNS CNAME。

---

## 7. 与 utils-support-ngrok-starter 的关系

- `utils-support-ngrok-starter` — **通用层**（纯 Java 链式客户端，零 Spring 依赖）
- `spring-support-ngrok-starter` — **Spring Boot 集成层**（依赖前者，封装自动配置）

如需在非 Spring 环境使用 NgrokClient，直接引入 utils-support-ngrok-starter 即可。

---

## 8. 版本对应

| ngrok-java | spring-support-ngrok-starter | utils-support-ngrok-starter |
|:----------:|:---------------------------:|:--------------------------:|
| 1.1.1      | 4.0.0.42                    | 4.0.0.42                   |

---

## 9. 作者

CH <ch@chua.com>
