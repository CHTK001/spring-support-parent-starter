# spring-support-tunnel-cloudflared-starter

Spring Boot 3 集成 [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/)：
管理外部 `cloudflared` 进程，把本机服务暴露为公网 HTTPS 地址。免账号 Quick Tunnel 开箱可用。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 自动配置与开关 | `CloudflaredAutoConfiguration` | `plugin.cloudflared.enable` |
| 配置绑定 | `CloudflaredProperties` | `plugin.cloudflared.*` |
| 启动期默认隧道 | `CloudflaredAutoConfiguration#onApplicationReady` | `ApplicationReadyEvent` |
| 统一入口提供方 | `CloudflaredTunnelProvider` | `/ms/tunnel`（由 tunnel-starter 装配） |
| 二进制定位 | `CloudflaredTool#locate` | `capabilities.binaryPath` |
| 服务信息登记 | `cloudflaredServiceInfoContributor` | 控制台模块能力 |

能力说明：

- **Quick Tunnel（默认，免账号）**：`cloudflared tunnel --url ...`，得到随机 `*.trycloudflare.com`；
- **命名隧道（named）**：Zero Trust 面板生成 Token，由本模块以 `token` 参数启动；
- 固定自定义域名：映射记录里的 `domain`（启动期配置为 `hostname`）；
- 每个映射独占一个 `cloudflared` 子进程，停止/更新/删除时关闭；
- 凭据不写日志，启动失败由 `fail-fast` 决定是否中断应用。

## 二、依赖与引入

先安装 `cloudflared` 二进制（本模块不自带）：

```bash
# macOS
brew install cloudflared
# Windows
winget install Cloudflare.cloudflared
```

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-tunnel-cloudflared-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `spring-support-tunnel-starter` | `TunnelProvider` SPI 与统一入口 `/ms/tunnel` |
| `utils-support-cloudflared-starter` | `CloudflareTunnelClient` 子进程管理 |
| `spring-support-common-starter` | `TunnelRecord` 与服务信息登记 |
| `spring-boot-starter-web` | 统一入口 REST（optional，由 tunnel-starter 侧需要） |

二进制定位顺序：`CLOUDFLARED_BIN` 环境变量 → `PATH` → 常见安装目录；
也可以用 `plugin.cloudflared.binary` 指定绝对路径。定位结果可在
`GET /ms/tunnel/providers` 的 `capabilities.binaryPath` 查看。

## 三、配置项

配置前缀：`plugin.cloudflared`（`CloudflaredProperties`）。

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.cloudflared.enable` | `true` | 总开关，`false` 完全禁用 |
| `plugin.cloudflared.binary` | 自动定位 | `cloudflared` 可执行文件路径 |
| `plugin.cloudflared.token` | 无 | 命名隧道 Token；为空时走免账号 Quick Tunnel |
| `plugin.cloudflared.hostname` | 无 | 启动期固定域名（需账户 + DNS CNAME） |
| `plugin.cloudflared.local-url` | `http://127.0.0.1:server.port` | 启动期本地待暴露地址 |
| `plugin.cloudflared.no-auto-update` | `true` | 关闭 cloudflared 运行期自更新，避免隧道中断 |
| `plugin.cloudflared.log-level` | `info` | `debug` / `info` / `warn` / `error` / `fatal` |
| `plugin.cloudflared.start-timeout` | `40s` | 等待公网 URL 的超时 |
| `plugin.cloudflared.fail-fast` | `false` | 启动失败是否中断应用 |
| `plugin.cloudflared.auto-start` | `true` | 启动期是否用 `server.port` 自动建默认隧道 |
| `plugin.cloudflared.auto-start-stored` | `true` | 启动时是否恢复 `autoStart=true` 的持久化映射 |
| `plugin.cloudflared.store-dir` | `~/.chua/tunnel/cloudflared` | 映射持久化目录 |

映射维度的字段（`TunnelRecord`）：

| 字段 | 说明 |
|:-----|:-----|
| `localPort` | 本地端口，提供方固定转发到 `http://127.0.0.1:<localPort>` |
| `mode` | `quick` / `named`，默认 `quick` |
| `token` | named 模式必填；为空时回退 `plugin.cloudflared.token` |
| `domain` | 固定域名，映射到 client 的 `hostname` |

模式规则：

- `quick`：不传 token，得到随机 `trycloudflare.com` 域名；
- `named`：映射 token 或全局 token 至少有一个，否则创建时抛
  `named 模式必须填写 Cloudflare 隧道 Token`。

## 四、快速开始

**第一步：安装 `cloudflared` 并确认可执行**

```bash
cloudflared --version
```

**第二步：确认模块开启（默认即开启）**

```yaml
plugin:
  cloudflared:
    enable: true
```

**第三步：启动应用，读取公网地址**

```
=========================================================
  Cloudflare Tunnel 已建立，本机服务已暴露到公网
=========================================================
  🌐 https://xxx-xxx-xxx.trycloudflare.com
=========================================================
```

Quick Tunnel 的域名每次启动都会变化；需要稳定地址时使用固定域名或命名隧道。

禁用：`plugin.cloudflared.enable=false`，或环境变量 `PLUGIN_CLOUDFLARED_ENABLE=false`。

## 五、运行示例

### 5.1 调用示例

本模块**无链式 API**：对外是自动配置 + `TunnelProvider` 实现，调用方式是配置或 REST。

启动期一次性隧道（配置驱动）：

```yaml
plugin:
  cloudflared:
    hostname: tunnel.example.com   # 需先在 Cloudflare 配好 CNAME
    no-auto-update: true
    log-level: info
    fail-fast: false
```

统一入口按需创建（需要 `spring-support-tunnel-starter`）：

```java
// CloudflaredTunnelProvider 已由 CloudflaredAutoConfiguration#cloudflaredTunnelProvider 注册
private final TunnelService tunnelService;

public String expose() {
    TunnelRecord input = new TunnelRecord();
    input.setProvider("cloudflared");
    input.setName("演示");
    input.setLocalPort(8080);
    input.setMode("quick");          // quick / named
    TunnelView created = tunnelService.create(input);
    TunnelView running = tunnelService.start(created.getId());
    return running.getPublicUrls().get(0);
}
```

命名隧道（REST 等价写法）：

```json
POST /ms/tunnel
{
  "provider": "cloudflared",
  "name": "命名隧道",
  "localPort": 8080,
  "mode": "named",
  "token": "<CLOUDFLARE_TUNNEL_TOKEN>",
  "domain": "tunnel.example.com"
}
```

底层子进程客户端（属于 `utils-support-cloudflared-starter`，链式 API）：

```java
CloudflareTunnelClient client = CloudflareTunnelClient.create()
        .url("http://127.0.0.1:8080")
        .token(token)
        .hostname("tunnel.example.com")
        .noAutoUpdate(true)
        .logLevel("info")
        .startTimeout(Duration.ofSeconds(40));
client.start();
List<String> urls = client.getUrls();
client.close();
```

### 5.2 SPI 扩展示例

本模块**提供** `TunnelProvider` 实现，登记在自动配置中：

```java
@Bean
@ConditionalOnMissingBean
public CloudflaredTunnelProvider cloudflaredTunnelProvider() {
    return new CloudflaredTunnelProvider(properties);
}
```

生效链路：Bean 注册 → `TunnelProviderRegistry` 收集 → `plugin.tunnel.providers` 白名单
→ `GET /ms/tunnel/providers` 可见（含 `binaryAvailable`）→ 创建映射时 `provider=cloudflared`。
若要新增其它提供方，请实现 `TunnelProvider` 并注册 Bean（见 tunnel-starter README）。

## 六、与其它 starter 的关系

| 模块 | 层次 | 与本模块的关系 |
|:-----|:-----|:---------------|
| `utils-support-cloudflared-starter` | 通用进程客户端层 | 本模块依赖它；纯 Java 管理 `cloudflared` 子进程，可脱离 Spring 使用 |
| `spring-support-tunnel-cloudflared-starter` | Spring 集成层 | 自动配置 + 启动期隧道 + `CloudflaredTunnelProvider` |
| `spring-support-tunnel-starter` | 编排层 | 本模块依赖它；统一 REST、持久化、SSE、启动恢复由它提供 |

与 ngrok 的差异：

| 维度 | cloudflared | ngrok |
|:-----|:------------|:------|
| 运行形态 | 外部二进制子进程，每映射一个进程 | 进程内 SDK（native 库） |
| 免账号 | 是，Quick Tunnel | 否，必须 authtoken |
| 安装要求 | 需安装 `cloudflared` | 无需额外安装 |
| 模式 | `quick` / `named` | `http` / `tcp` / `tls` |
| 固定域名 | `hostname` / 映射 `domain`（需 CNAME） | `domain`（需面板预留） |
| 可用性判定 | `CloudflaredTool#isAvailable` 真实探测二进制 | 恒 true（编译期依赖） |

两者可同时引入，用 `plugin.tunnel.providers` 裁剪；cloudflared 不可用时页面应禁用该提供方，
而不是等用户点击后失败。

## 七、已知限制

- **必须先安装 `cloudflared`**：未找到时启动期日志为
  `[Cloudflared] 隧道启动失败: ...`，`isAvailable()` 返回 `false`。
- **`server.port=0` 不会自动探测实际端口**：`resolveLocalPort` 读到 `0` 返回 -1，
  日志为「无法解析本地 server.port，跳过隧道建立」。
- **启动期与提供方路径字段不完全等价**：
  - `local-url` 只在启动期生效；`CloudflaredTunnelProvider` 固定使用 `http://127.0.0.1:<localPort>`；
  - `hostname` 只在启动期生效，映射路径使用记录里的 `domain`；
  - `token` 在 quick 模式下即使配置了也不会传给进程，`named` 才使用。
- **Quick Tunnel 域名不固定**：重启即换域名，`auto-start: true` 会导致每次启动地址不同；
  要稳定地址请用 `hostname` 或 `named`。
- **`auto-start` 默认 true**：与管理台映射同时使用时建议设为 `false`，避免重复建隧道。
- **启动超时默认 40s**：网络无法访问 Cloudflare 边缘时会失败；
  固定域名场景还需确认 DNS CNAME 已生效（指向 `<隧道>.cfargotunnel.com`）。
- **进程提前退出**通常意味着 token 无效、端口被占用或本地服务未启动；
  失败原因会写入映射的 `error` 字段并出现在 SSE 快照里。
- **Token 会写入映射 JSON**：`<store-dir>/cloudflared/<id>.json` 含 `token`，注意目录权限。
- 本模块不提供 SPI 扩展点，只消费 tunnel-starter 的 `TunnelProvider` 契约。
