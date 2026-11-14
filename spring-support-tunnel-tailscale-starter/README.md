# spring-support-tunnel-tailscale-starter

基于官方 `tailscale` 命令行子进程的 Tailscale Funnel 内网穿透 starter：Spring Boot 启动后自动把本机服务（`server.port`）通过 Tailscale Funnel 暴露为公网 HTTPS 地址。
默认开启（`plugin.tailscale.enable` 缺省为 `true`），但**前提是宿主机已安装 `tailscale` 可执行文件且设备已加入 tailnet**；未满足时启动失败默认只打日志（`fail-fast=false`）。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 自动配置与开关 | `TailscaleAutoConfiguration` | `plugin.tailscale.enable`（缺省 true） |
| 配置绑定 | `TailscaleProperties` | `plugin.tailscale.*` |
| 启动期默认隧道 | `TailscaleAutoConfiguration#onApplicationReady` | `ApplicationReadyEvent`（`server.port` 已确定） |
| 统一入口提供方 | `TailscaleTunnelProvider` | `/ms/tunnel`（由 tunnel-starter 装配） |
| 二进制定位 / 版本探测 | `TailscaleTool`（utils 层） | `TAILSCALE_BIN` → PATH → 常见安装目录 |
| 子进程管理客户端 | `TailscaleTunnelClient`（utils 层） | 链式 API，`tailscale funnel` 子进程 |
| 服务信息登记 | `tailscaleServiceInfoContributor` | 控制台模块能力 |
| 隧道模式 | `TailscaleTunnelProvider#supportedModes` | `http`（默认）/ `tcp` |

实际生效的能力：

- 启动期默认隧道：按 `server.port` 自动建一条 `http` 模式的 Funnel，输出形如 `https://<machine>.<tailnet>.ts.net` 的公网地址；
- 两种模式：`http`（默认，`tailscale funnel --yes <local>`）；`tcp`（`tailscale funnel --tcp=443 --yes <local>`，Funnel 只能暴露 443/8443/10000 三个公网 TCP 端口，本实现固定用 443）；
- 认证 Key：`plugin.tailscale.auth-key` 或环境变量 `TS_AUTHKEY`，以 `TS_AUTHKEY` 环境变量注入子进程，设备无需先 `tailscale up`；
- 映射持久化与启动恢复：每个映射一个 JSON 文件，存在 `store-dir`（默认 `~/.chua/tunnel/tailscale`），`auto-start-stored` 控制是否恢复「自动启动」标记的映射；
- 失败容错：`fail-fast=false`（默认）时启动失败只打 ERROR 日志继续启动；`true` 时抛 `IllegalStateException` 中断 Spring 启动；
- 应用关闭（`ContextClosedEvent` / `@PreDestroy`）时优雅关闭 tailscale 子进程（5 秒宽限后强杀）。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-tunnel-tailscale-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `spring-support-tunnel-starter` | `TunnelProvider` SPI 与统一入口 `/ms/tunnel`、映射编排与持久化 |
| `utils-support-tailscale-starter` | `TailscaleTool`（二进制定位/版本探测）与 `TailscaleTunnelClient`（子进程管理） |
| `spring-support-common-starter` | `TunnelRecord`、`ServiceInfoContributor`、`ServiceInfoRegistry` |
| `io.swagger.core.v3:swagger-annotations` | OpenAPI 注解 |
| `spring-boot-starter-web`（test scope） | 测试上下文需要嵌入式 Web 容器 |

`utils-support-tailscale-starter` 版本随父 pom 的 `utils.version` 管理；Tailscale 官方不提供可内嵌 Java SDK，本模块**不内置任何 tailscale 二进制**，可执行文件由宿主机负责安装。

## 三、配置项

配置前缀：**`plugin.tailscale`**（`TailscaleProperties`）。

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.tailscale.enable` | `true` | 总开关；`false` 完全禁用（自动配置不装配） |
| `plugin.tailscale.binary` | 无 | tailscale 可执行文件路径；为空时按 `TAILSCALE_BIN` 环境变量 → PATH → 常见安装目录（Windows `C:\Program Files\Tailscale` 等）顺序自动定位 |
| `plugin.tailscale.auth-key` | 无 | 设备认证 Key；解析顺序「配置文件 → 环境变量 `TS_AUTHKEY`」，注入子进程环境，无登录态设备一键建隧道 |
| `plugin.tailscale.start-timeout` | `60s` | 启动后等待公网 URL 输出的超时 |
| `plugin.tailscale.fail-fast` | `false` | 启动失败时是否中断应用；`false` 只打 ERROR 日志，`true` 抛异常中断启动 |
| `plugin.tailscale.auto-start` | `true` | 启动时是否按本配置自动建立默认隧道；`false` 时模块（含统一入口与持久化映射）仍然可用，只是不在启动期自动建隧道 |
| `plugin.tailscale.auto-start-stored` | `true` | 启动时是否恢复上次标记为「自动启动」的持久化映射 |
| `plugin.tailscale.store-dir` | 无 | 映射持久化目录；缺省为 `~/.chua/tunnel/tailscale`（`TailscaleTunnelProvider#storeDir()` 返回 null 时由 tunnel-starter 按 `<plugin.tunnel.store-dir>/tailscale` 解析），一条映射一个 JSON 文件 |

## 四、快速开始

**第一步：安装 tailscale 并加入 tailnet（宿主机侧硬前提）**

```bash
# Linux / macOS / Windows 任一方式
curl -fsSL https://tailscale.com/install.sh | sh
tailscale up          # 交互式登录，加入你的 tailnet
tailscale version     # 本实现要求主版本 >= 1.50
```

Windows 默认安装位置 `C:\Program Files\Tailscale\tailscale.exe`；装了但不在 PATH 时，用 `plugin.tailscale.binary` 或环境变量 `TAILSCALE_BIN` 指向它。

**第二步：引入依赖并配置**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-tunnel-tailscale-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

```yaml
plugin:
  tailscale:
    enable: true
    # 设备已登录时可省；无登录态一键建隧道推荐走环境变量
    auth-key: ${TAILSCALE_AUTHKEY}
    # binary: /usr/local/bin/tailscale   # 不在 PATH 时显式指定
    # start-timeout: 90s
```

**第三步：启动验证**

启动成功后日志形如：

```
=========================================================
  Tailscale Funnel 已建立，本机服务已暴露到公网
=========================================================
  https://<machine>.<tailnet>.ts.net
=========================================================
```

浏览器打开该公网地址即访问本机 `server.port` 服务。也可用统一入口验证提供方是否注册：

```bash
curl http://127.0.0.1:${server.port}/ms/tunnel/providers
# data[*].name 应包含 tailscale，displayName = Tailscale Funnel
```

设备未登录 / 本地守护进程未运行 / 二进制缺失时，启动日志会打印 `failed to connect to local tailscaled process` 或 `please run tailscale up` 特征错误行并登记到映射；`fail-fast: true` 会让应用直接启动失败。

## 五、运行示例

### 5.1 调用示例

本模块**无对外链式 API**（链式客户端在 `utils-support-tailscale-starter`，见下）；对外能力由自动配置 + `TunnelProvider` 提供。

方式一：配置驱动（启动期一次性默认隧道），`http` 模式默认，`tcp` 模式固定暴露公网 443：

```yaml
plugin:
  tailscale:
    enable: true
    auth-key: ${TAILSCALE_AUTHKEY}
    auto-start: true
```

```java
// tcp 模式映射只能由统一入口创建（启动期默认隧道固定 http）：
// POST /ms/tunnel {"provider":"tailscale","name":"tcp-转发","localPort":8080,"mode":"tcp"}
```

方式二：统一入口按需创建（需要 `spring-support-tunnel-starter`）：

```java
private final TunnelService tunnelService;

public String expose() {
    TunnelRecord input = new TunnelRecord();
    input.setProvider("tailscale");
    input.setName("调试");
    input.setLocalPort(8080);
    input.setMode("http");              // http / tcp
    TunnelView created = tunnelService.create(input);   // 只保存，不启动
    TunnelView running = tunnelService.start(created.getId());
    return running.getPublicUrls().get(0);
}
```

方式三：底层客户端（`utils-support-tailscale-starter` 的链式 API，非 Spring 环境也可用）：

```java
try (TailscaleTunnelClient client = TailscaleTunnelClient.create()
        .url("http://127.0.0.1:8080")
        .authKey("tskey-...")     // 可选，注入子进程 TS_AUTHKEY
        .start()) {
    System.out.println(client.getUrl());   // https://<machine>.<tailnet>.ts.net
}
```

### 5.2 SPI 扩展示例

本模块**提供** `TunnelProvider` 实现（`TailscaleTunnelProvider`），而不是定义新扩展点；登记动作在自动配置里完成：

```java
@Bean
@ConditionalOnMissingBean
public TailscaleTunnelProvider tailscaleTunnelProvider() {
    return new TailscaleTunnelProvider(properties);
}
```

生效链路：Bean 注册 → tunnel-starter 的 `TunnelProviderRegistry` 收集 → `plugin.tunnel.providers`
白名单裁剪 → `/ms/tunnel/providers` 可见 → 创建映射时 `provider=tailscale` 可选中。
提供方名称 `tailscale` 全局唯一；`capabilities()` 回填 `binaryAvailable` / `binaryPath` / `authKeyConfigured` / `autoStart` / `autoStartStored` 供页面渲染可用性。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
|:-----|:-----|:-----|
| `spring-support-tunnel-starter` | 本模块依赖 | `TunnelProvider` SPI、统一 REST `/ms/tunnel`、映射持久化与启动恢复，本模块不重复实现 |
| `utils-support-tailscale-starter` | 本模块依赖 | tailscale CLI 定位与子进程管理（`TailscaleTool` / `TailscaleTunnelClient`），无 Spring 依赖 |
| `spring-support-common-starter` | 本模块依赖 | `TunnelRecord`、服务信息登记 |
| `spring-support-tunnel-ngrok-starter` | 兄弟模块，无依赖 | 同为 `TunnelProvider` 实现，可共存，用 `plugin.tunnel.providers` 决定暴露哪个 |
| `spring-support-tunnel-cloudflared-starter` | 兄弟模块，无依赖 | 同上 |

注意区分：

- **本模块 = Tailscale Funnel**：基于宿主机已安装的 `tailscale` 命令行子进程，公网地址由 Tailscale 分配；
- **ngrok = 进程内 SDK**（`ngrok-java` native 内嵌，无需装二进制）、cloudflared = 外部子进程 —— 三家互相独立，可同时引入。

## 七、已知限制

- **宿主机必须预装 tailscale**：本模块不内置二进制，`isAvailable()` 只探测 `tailscale` 可执行文件是否存在；守护进程是否在跑、设备是否已登录，由 `funnel` 子进程报错并登记到映射（`failed to connect to local tailscaled process` / `please run tailscale up` 特征行）。
- **`server.port=0` 不会自动探测实际端口**：`resolveLocalPort` 读到 `0` 返回 `-1`，只打 WARN「无法解析本地 server.port，跳过隧道建立」。随机端口场景请显式配置端口或走 `/ms/tunnel` 指定 `localPort`。
- **`start-timeout` 超时时隧道视为失败**：`TailscaleTunnelClient#waitForUrl` 在超时（默认 60s）内未解析到公网地址会终止子进程并抛 `IllegalStateException`；默认 `fail-fast=false` 只打日志继续启动。
- **`tcp` 模式固定公网 443**：Funnel 只允许非 root 设备暴露 443/8443/10000 三个公网 TCP 端口，本实现写死 `TCP_PORTS.get(0)`（443），不提供端口选择。
- **认证 Key 是设备级凭据，不是隧道级**：`TailscaleTunnelProvider#applyInput` 会清空 ngrok/cloudflared 专属字段（`authtoken`/`remote-address`/`basicAuth`/`metadata`/`token`），每条约数据源映射都共用同一个 Key。
- **`auto-start` 默认 true**：同时使用管理台页面时建议设 `false`，否则启动期默认隧道与页面映射会各建一条。
- **映射内容是明文 JSON**：`store-dir` 下每条映射一个 JSON 文件，注意目录权限（同 ngrok 的 BasicAuth 明文问题）。
- **进程退出细节**：`close()` 先 `destroy()`，5 秒未退出再 `destroyForcibly()`；输出必须持续消费直到进程退出，否则管道满会挂起（客户端已内部处理，此处仅供排查理解）。