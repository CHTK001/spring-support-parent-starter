# spring-support-tunnel-ngrok-starter

Spring Boot 3 集成 ngrok：基于官方 `ngrok-java` Agent SDK（内嵌 Rust Agent），
把本机服务暴露到公网，**不需要启动独立的 ngrok CLI**。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 自动配置与开关 | `NgrokAutoConfiguration` | `plugin.ngrok.enable` |
| 配置绑定 | `NgrokProperties` | `plugin.ngrok.*` |
| 启动期默认隧道 | `NgrokAutoConfiguration#onApplicationReady` | `ApplicationReadyEvent` |
| 统一入口提供方 | `NgrokTunnelProvider` | `/ms/tunnel`（由 tunnel-starter 装配） |
| 能力探测 | `NgrokTunnelProvider#capabilities` | `GET /ms/tunnel/providers` |
| 服务信息登记 | `ngrokServiceInfoContributor` | 控制台模块能力 |

支持的模式与能力：

- 隧道类型：`http` / `tcp` / `tls`（`TunnelProvider` 侧同一套模式）；
- HTTP 转发：`forward`（转发到本机端口）/ `listen`（仅暴露 URL，启动期路径）；
- HTTP BasicAuth、固定域名、私有部署（`server-addr` + CA 证书）、心跳参数；
- 多端口同时暴露：`extra-local-ports`（仅启动期 http 类型生效）；
- 失败容错：`fail-fast` 控制是否中断 Spring 启动。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-tunnel-ngrok-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `spring-support-tunnel-starter` | `TunnelProvider` SPI 与统一入口 `/ms/tunnel` |
| `utils-support-ngrok-starter` | 纯 Java 客户端 `NgrokClient`（链式 API） |
| `com.ngrok:ngrok-java:1.1.1` | 官方 Agent SDK |
| `com.ngrok:ngrok-java-native:1.1.1` | 平台 native，classifier 由 `os-maven-plugin` 生成 |
| `spring-support-common-starter` | `TunnelRecord` 与服务信息登记 |

`ngrok-java-native` 按 `os.detected.classifier` 选择平台包，pom 已启用
`kr.motd.maven:os-maven-plugin:1.7.1`，无需手工指定。

## 三、配置项

配置前缀：`plugin.ngrok`（`NgrokProperties`）。

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.ngrok.enable` | `true` | 总开关，`false` 完全禁用 |
| `plugin.ngrok.authtoken` | 无 | 为空时回退环境变量 / 系统属性 `NGROK_AUTHTOKEN`；仍为空则隧道建立失败并记错误 |
| `plugin.ngrok.metadata` | 无 | 会话元数据，ngrok 控制台可见 |
| `plugin.ngrok.server-addr` | 无 | 私有部署服务端地址，如 `tunnel.example.com:443` |
| `plugin.ngrok.ca-cert` | 无 | 私有部署 CA，classpath 资源路径 |
| `plugin.ngrok.heartbeat-interval` | ngrok 默认 | 心跳间隔 |
| `plugin.ngrok.heartbeat-tolerance` | ngrok 默认 | 心跳容忍 |
| `plugin.ngrok.type` | `http` | `http` / `tcp` / `tls` |
| `plugin.ngrok.mode` | `forward` | `forward` 自动转发 / `listen` 仅暴露 URL |
| `plugin.ngrok.domain` | 无 | 固定域名（需面板预留） |
| `plugin.ngrok.subdomain` | 无 | 已下线能力，仅打印告警，不生效 |
| `plugin.ngrok.remote-address` | 无 | tcp 模式必填，如 `1.tcp.ngrok.io:20000` |
| `plugin.ngrok.scheme` | `HTTPS` | 启动期 http 模式协议方案 |
| `plugin.ngrok.compression` | `false` | 启动期 http 响应压缩 |
| `plugin.ngrok.basic-auth` | 无 | `user:password`，优先于用户名/密码两个字段 |
| `plugin.ngrok.basic-auth-username` | 无 | BasicAuth 用户名 |
| `plugin.ngrok.basic-auth-password` | 无 | BasicAuth 密码 |
| `plugin.ngrok.fail-fast` | `false` | 启动期隧道失败是否中断应用 |
| `plugin.ngrok.extra-local-ports` | 无 | 额外本地端口，逗号分隔，如 `8081,8082` |
| `plugin.ngrok.auto-start` | `true` | 启动期是否用 `server.port` 自动建默认隧道 |
| `plugin.ngrok.auto-start-stored` | `true` | 启动时是否恢复 `autoStart=true` 的持久化映射 |
| `plugin.ngrok.store-dir` | `~/.chua/tunnel/ngrok` | 映射持久化目录（一条映射一个 JSON） |

必填项速查：

| 场景 | 必填 |
|:-----|:-----|
| HTTP 转发（默认） | `authtoken` |
| HTTP 固定域名 | `authtoken` + `domain` |
| HTTP BasicAuth | `authtoken` + `basic-auth`（或 username + password） |
| TCP 隧道 | `authtoken` + `remote-address` |
| TLS 隧道 | `authtoken` + `domain` |
| 私有部署 | `authtoken` + `server-addr` + `ca-cert` |

## 四、快速开始

**第一步：引入依赖**（见上）。

**第二步：配置 authtoken（推荐走环境变量）**

```bash
# Linux / macOS
export NGROK_AUTHTOKEN=2abc...xyz

# Windows PowerShell
$env:NGROK_AUTHTOKEN="2abc...xyz"
```

```yaml
plugin:
  ngrok:
    authtoken: ${NGROK_AUTHTOKEN}
```

**第三步：启动并确认公网地址**

```
=========================================================
  Ngrok 隧道已建立，本机服务已暴露到公网
=========================================================
  🌐 https://xxxx-xxx-xxx-xxx.ngrok-free.app
=========================================================
```

`auto-start: false` 时不建默认隧道，改由 `/ms/tunnel` 按需创建（适合管理台部署）。

生产环境彻底禁用：`plugin.ngrok.enable=false`，或环境变量 `PLUGIN_NGROK_ENABLE=false`。

## 五、运行示例

### 5.1 调用示例

本模块**无链式 API**：对外能力是自动配置 + `TunnelProvider` 实现，调用方式是配置或 REST，不是链式 Builder。

方式一：配置驱动（启动期一次性隧道）

```yaml
plugin:
  ngrok:
    authtoken: ${NGROK_AUTHTOKEN}
    type: http
    mode: forward
    domain: my-app.ngrok-free.app
    basic-auth: admin:secret123
    fail-fast: false
```

方式二：统一入口按需创建（需要 `spring-support-tunnel-starter`）

```java
// TunnelService 来自 spring-support-tunnel-starter，NgrokTunnelProvider 已由
// NgrokAutoConfiguration#ngrokTunnelProvider 注册为 Bean，无需手工 new。
private final TunnelService tunnelService;

public String expose() {
    TunnelRecord input = new TunnelRecord();
    input.setProvider("ngrok");
    input.setName("调试");
    input.setLocalPort(8080);
    input.setMode("http");              // http / tcp / tls
    input.setDomain("my-app.ngrok-free.app");
    TunnelView created = tunnelService.create(input);   // 只保存，不启动
    TunnelView running = tunnelService.start(created.getId());
    return running.getPublicUrls().get(0);
}

// 走 REST 更常见：
// POST /ms/tunnel {"provider":"ngrok","name":"调试","localPort":8080,"mode":"http"}
// POST /ms/tunnel/{id}/start
```

方式三：底层客户端（属于 `utils-support-ngrok-starter`，这里是链式 API）

```java
NgrokClient client = NgrokClient.create(authtoken)
        .metadata("my-spring-service")
        .heartbeatInterval(Duration.ofSeconds(30));
client.connect();
client.forwardHttp(client.http().domain("my-app.ngrok-free.app").builder(),
        new URL("http://127.0.0.1:8080"));
List<String> urls = client.getUrls();
client.close();
```

### 5.2 SPI 扩展示例

本模块**提供** `TunnelProvider` 实现，而不是定义扩展点；登记动作在自动配置里完成：

```java
@Bean
@ConditionalOnMissingBean
public NgrokTunnelProvider ngrokTunnelProvider(ObjectProvider<ResourceLoader> resourceLoader) {
    return new NgrokTunnelProvider(properties, resourceLoader.getIfAvailable());
}
```

生效链路：Bean 注册 → tunnel-starter 的 `TunnelProviderRegistry` 收集 → `plugin.tunnel.providers`
白名单裁剪 → `/ms/tunnel/providers` 可见 → 创建映射时 `provider=ngrok` 可选中。

若要新增别的提供方，实现 `TunnelProvider` 并注册 Bean 即可（详见 tunnel-starter README）；
名称必须唯一，`ngrok` 已被本模块占用。

## 六、与其它 starter 的关系

| 模块 | 层次 | 与本模块的关系 |
|:-----|:-----|:---------------|
| `utils-support-ngrok-starter` | 通用客户端层 | 本模块依赖它；纯 Java、零 Spring，可在非 Spring 环境单独使用 |
| `spring-support-tunnel-ngrok-starter` | Spring 集成层 | 自动配置 + 启动期隧道 + `NgrokTunnelProvider` |
| `spring-support-tunnel-starter` | 编排层 | 本模块依赖它；统一 REST、持久化、SSE、启动恢复都由它提供 |

与 cloudflared 的差异：

| 维度 | ngrok | cloudflared |
|:-----|:------|:------------|
| 运行形态 | 进程内 SDK（native 库） | 外部 `cloudflared` 子进程 |
| 是否要装二进制 | 否 | 是 |
| 免账号可用 | 否，必须 authtoken | 是，Quick Tunnel 免账号 |
| 支持模式 | `http` / `tcp` / `tls` | `quick` / `named` |
| 固定域名 | `domain`（需面板预留） | `hostname`（需账户 + CNAME） |

两者可同时引入，用 `plugin.tunnel.providers` 决定暴露哪个。

## 七、已知限制

- **`server.port=0` 不会自动探测实际端口**：`resolveLocalPort` 读到 `0` 时返回 -1，
  只打印「无法解析本地 server.port，跳过隧道建立」。随机端口场景请显式配置端口或用 `/ms/tunnel` 指定 `localPort`。
- **启动期路径与提供方路径能力不完全等价**：
  - `scheme`、`compression`、`extra-local-ports`、`mode=listen` 只在启动期默认隧道生效；
  - `NgrokTunnelProvider` 的 http 固定使用 HTTPS 并只做 forward，不读取上述字段；
  - `subdomain` 在任何路径都只打印「已在新版套餐下线」告警，不设置到 SDK。
- **authtoken 缺失不会无条件中断启动**：`startTunnels` 抛出的异常被 `handleStartupFailure` 捕获，
  默认 `fail-fast: false` 只打 ERROR 日志（`[Ngrok] 隧道启动失败: ...authtoken 未配置...`）后继续启动；
  要强制失败必须显式 `fail-fast: true`。
- **`auto-start` 默认 true**：同时使用管理台时建议设为 `false`，否则启动期默认隧道与页面映射会各建一条。
- **BasicAuth 明文**：`basic-auth` 会随映射写入 `<store-dir>/<id>.json`，注意目录权限。
- `NgrokTunnelProvider#isAvailable()` 恒为 `true`（客户端是编译期依赖），不代表凭据已配置；
  凭据状态看 `capabilities.authtokenConfigured`。
- tcp 模式只接受已 reserve 的 `remote-address`；tls 模式必须有 `domain`。
- 连接被拒绝时先确认本机服务已启动、`server.port` 与实际监听端口一致。
- `UnsatisfiedLinkError: no ngrok-java-native-<os>` 说明 native 分类器不匹配，
  确认父 pom 的 `os-maven-plugin` 生效，构建产物包含对应平台 jar。
