# spring-support-tunnel-starter

内网穿透统一入口：把 ngrok、Cloudflare Tunnel 等提供方的差异折叠成一套 `TunnelProvider` SPI，
REST 接口、映射持久化、启动恢复与 SSE 推送只保留一份。本模块**不含任何厂商客户端**，引入哪个提供方 jar 就具备哪个能力。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 提供方契约 | `TunnelProvider` / `TunnelSession` | 实现类声明为 Spring Bean 即被收集 |
| 提供方注册与白名单 | `TunnelProviderRegistry` | `TunnelAutoConfiguration#tunnelProviderRegistry` |
| 映射编排（增删改查/启停） | `TunnelService` | `/ms/tunnel/**` |
| 映射持久化台账 | `TunnelStore` | `<store-dir>/<provider>/<id>.json` |
| 统一 REST 接口 | `TunnelController` | `/ms/tunnel` |
| SSE 实时状态 | `TunnelStatusStream`（common-starter） | `GET /ms/tunnel/stream` |
| 启动自动恢复 | `TunnelService#restoreAutoStart` | `ApplicationReadyEvent` |
| 公共字段校验与归一 | `TunnelInputs` | 端口、名称、备注、开关 |

设计要点：

- 映射 id 全局唯一，不带 provider 前缀，一个 id 走通全部接口；
- 运行态（会话、启动时间、最近错误）只存内存，不落盘，避免把过期公网地址当成事实；
- 新增提供方只需实现 `TunnelProvider` 并注册为 Bean，控制器与前端不需要改动。

SSE 推送（`GET /ms/tunnel/stream`）连接时即推当前快照，之后按 `sse-interval`（默认 1.5 秒）
广播完整快照，页面断线自动重连：

```
event: tunnels
data: [{ "id": "...", "name": "测试映射", "localPort": 8080, "mode": "http",
         "running": true, "publicUrls": ["https://abc.ts.net"], "error": "" }]
```

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-tunnel-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 | 说明 |
|:-----|:-----|:-----|
| `spring-support-common-starter` | `TunnelRecord` / `TunnelView` / `TunnelStatusStream` | 必选 |
| `spring-boot-starter` | 自动配置与容器 | 必选 |
| `spring-boot-starter-web` | `TunnelController` 与 SSE | optional，非 Web 应用可不引入 |
| `swagger-annotations` | 接口文档注解 | 必选 |
| `spring-boot-configuration-processor` | 配置元数据 | optional |

> 本模块自身不会建立任何隧道。必须额外引入至少一个提供方：
> `spring-support-tunnel-ngrok-starter`、`spring-support-tunnel-cloudflared-starter` 或 `spring-support-tunnel-tailscale-starter`。

## 三、配置项

配置前缀：`plugin.tunnel`（`TunnelProperties`）。

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.tunnel.enable` | `true` | 总开关；`false` 时不注册 REST 接口，也不恢复自动启动映射 |
| `plugin.tunnel.store-dir` | `~/.chua/tunnel` | 映射持久化根目录，每个提供方一个子目录 |
| `plugin.tunnel.providers` | 空（全部） | 对外暴露的提供方白名单，如 `[ngrok]` |
| `plugin.tunnel.restore-on-startup` | `true` | 启动时是否恢复 `autoStart=true` 的映射 |
| `plugin.tunnel.sse-interval` | `1500ms` | SSE 快照推送间隔 |

白名单示例（生产只允许 cloudflared，即使 classpath 里有 ngrok 也不暴露）：

```yaml
plugin:
  tunnel:
    providers: [cloudflared]
```

字段职责边界：本配置只管「入口开不开、映射存哪、暴露哪些提供方」；
authtoken、二进制路径、域名等厂商细节仍在 `plugin.ngrok.*` / `plugin.cloudflared.*` 中。

持久化目录布局（一条映射一个 JSON，升级后历史文件无需搬迁）：

```
~/.chua/tunnel
├── ngrok
│   └── <id>.json
├── cloudflared
│   └── <id>.json
└── tailscale
    └── <id>.json
```

启动恢复由 `TunnelService#restoreAutoStart` 在 `ApplicationReadyEvent` 执行：
`restore-on-startup=false` 时跳过；只恢复 `enabled` 且 `autoStart=true`、
且提供方 `autoStartStored()` 为 true 的映射；单条失败只记 warn，不影响其余映射与应用启动。

## 四、快速开始

**第一步：引入统一入口与一个提供方**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-tunnel-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-tunnel-cloudflared-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

**第二步：确认入口开启**

```yaml
plugin:
  tunnel:
    enable: true          # 默认 true，可省略
```

**第三步：调用接口创建并启动映射**

```bash
curl -X POST http://127.0.0.1:8080/ms/tunnel \
  -H 'Content-Type: application/json' \
  -d '{"name":"本地调试","provider":"cloudflared","localPort":8080,"mode":"quick","autoStart":false}'

curl -X POST http://127.0.0.1:8080/ms/tunnel/<id>/start
curl http://127.0.0.1:8080/ms/tunnel
```

启动日志会打印已注册提供方：

```
[Tunnel] 注册提供方 cloudflared（CloudflaredTunnelProvider），可用=true
```

## 五、运行示例

### 5.1 调用示例

本模块**无链式 API**，`TunnelService` 的方法直接返回 `TunnelView` / `boolean`，按步骤调用：

```java
@Service
public class TunnelDemo {

    private final TunnelService tunnelService;

    public TunnelDemo(TunnelService tunnelService) {
        this.tunnelService = tunnelService;
    }

    public void expose() {
        // 1. 新建映射（不会自动启动）
        TunnelRecord input = new TunnelRecord();
        input.setName("本地调试");
        input.setProvider("cloudflared");
        input.setLocalPort(8080);
        input.setMode("quick");
        input.setEnabled(true);
        input.setAutoStart(true);
        TunnelView created = tunnelService.create(input);

        // 2. 启动（失败信息会写入 error 字段并抛出 IllegalStateException）
        TunnelView running = tunnelService.start(created.getId());
        List<String> urls = running.getPublicUrls();

        // 3. 停止 / 删除
        tunnelService.stop(created.getId());
        tunnelService.delete(created.getId());
    }
}
```

单提供方部署下 `provider` 可省略（`resolveCreateProvider` 只有一个实现时自动选中）；
多实现时必须显式指定，否则抛 `请指定提供方（provider）：当前可选 [...]`。

REST 接口：

| 方法 | 路径 | 说明 |
|:-----|:-----|:-----|
| GET | `/ms/tunnel/providers` | 提供方列表（能力、支持模式、可用性） |
| GET | `/ms/tunnel` | 映射列表（台账 + 运行状态），可接 `?provider=xxx` |
| GET | `/ms/tunnel/{id}` | 映射详情 |
| POST | `/ms/tunnel` | 新建映射（请求体 `TunnelRecord`） |
| PUT | `/ms/tunnel/{id}` | 更新映射（运行中先停止再重启） |
| DELETE | `/ms/tunnel/{id}` | 删除映射（运行中先停止） |
| POST | `/ms/tunnel/{id}/start` | 启动映射 |
| POST | `/ms/tunnel/{id}/stop` | 停止映射 |
| GET | `/ms/tunnel/stream` | SSE 实时状态推送 |

请求体字段（`TunnelRecord`）：

```json
{
  "name": "测试映射",
  "provider": "ngrok",
  "localPort": 8080,
  "mode": "http",
  "domain": "",
  "remoteAddress": "",
  "authtoken": "",
  "token": "",
  "metadata": "",
  "enabled": true,
  "autoStart": true,
  "remark": ""
}
```

### 5.2 SPI 扩展示例

实现 → 登记 → 生效三步：

```java
public class MyTunnelProvider implements TunnelProvider {

    @Override
    public String name() {
        return "mytunnel";
    }

    @Override
    public String displayName() {
        return "My Tunnel";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<String> supportedModes() {
        return List.of("http");
    }

    @Override
    public String defaultMode() {
        return "http";
    }

    @Override
    public Map<String, Object> capabilities() {
        return Map.of("provider", name());
    }

    @Override
    public void applyInput(TunnelRecord target, TunnelRecord input) {
        target.setMode(defaultMode());
        TunnelInputs.applyCommon(target, input, name());
    }

    @Override
    public TunnelSession open(TunnelRecord record) {
        return new MySession(record.getLocalPort());
    }
}
```

```java
@AutoConfiguration
public class MyTunnelAutoConfiguration {

    @Bean
    public MyTunnelProvider myTunnelProvider() {
        return new MyTunnelProvider();
    }
}
```

登记后由 `TunnelProviderRegistry` 自动收集。若配置了 `plugin.tunnel.providers`，
`name()` 必须出现在白名单里才会暴露；名称重复（大小写不敏感）会在启动时直接报错。
`TunnelProvider#storeDir()` 返回非空时覆盖 `<store-dir>/<name>`，用于兼容历史目录。

## 六、与其它 starter 的关系

三个隧道模块的依赖方向与差异：

| 模块 | 角色 | 依赖方向 | 需要外部进程 | 建立隧道的方式 |
|:-----|:-----|:---------|:-------------|:---------------|
| `spring-support-tunnel-starter` | 编排 + SPI + 统一入口 | 被两个提供方依赖 | 否，自身不建隧道 | 只调用 `TunnelProvider#open` |
| `spring-support-tunnel-ngrok-starter` | ngrok 提供方 | 依赖本模块 + `utils-support-ngrok-starter` | 否，内嵌 Rust Agent（`ngrok-java-native`） | 进程内客户端会话 |
| `spring-support-tunnel-cloudflared-starter` | Cloudflare 提供方 | 依赖本模块 + `utils-support-cloudflared-starter` | 是，需要 `cloudflared` 二进制 | 每个映射一个子进程 |

- 本机隧道与云隧道的差别在提供方内部消化，本模块只持有 `TunnelSession`，不关心是长连接还是子进程。
- 两个提供方可以同时存在，用 `plugin.tunnel.providers` 按环境裁剪；另有
  `spring-support-tunnel-tailscale-starter` 提供 Tailscale Funnel 实现，契约相同。
- 提供方自己的「启动期一次性隧道」（`auto-start`）与本模块的持久化映射是两条独立路径。

## 七、已知限制

### 7.1 行为限制

- 更新映射时**不允许变更提供方**：`TunnelService#update` 会强制沿用记录里的 `provider`，换提供方等价于新建。
- 运行态只存内存，应用重启后公网地址与 `startedAt` 归零，需要靠 `autoStart` 重新建立。
- SSE 按固定间隔广播**完整快照**，不做增量 diff；映射很多时需自行评估带宽。
- `TunnelService#capabilities(String)` 是服务层方法，控制器未暴露对应 REST 路径。
- 本模块不提供鉴权，`/ms/tunnel/**` 的访问控制依赖宿主应用的安全链。
- 未引入任何提供方时不会启动失败，注册表为空，`/ms/tunnel/providers` 返回空列表。

### 7.2 常见报错

`未发现任何 TunnelProvider 实现`：

```
[Tunnel] 未发现任何 TunnelProvider 实现：请引入 spring-support-tunnel-ngrok-starter 或 spring-support-tunnel-cloudflared-starter
```

解决：引入至少一个提供方 starter。

`请指定提供方（provider）`：POST 时未传 `provider` 且容器内有多个提供方。
解决：显式传 `provider`，或用 `plugin.tunnel.providers` 收敛为一个。

`未知的内网穿透提供方`：`provider` 拼写错误或已被白名单裁掉。
解决：先查 `GET /ms/tunnel/providers` 的 `name` 列表。

`TunnelProvider 名称重复`：两个实现返回了同一个 `name()`。
解决：保证提供方标识全局唯一。
