# spring-support-oauth-client-starter

单点登录**客户端** Starter：把「令牌校验 / 白名单 / 权限判定 / 登录页 / 会话控制」下沉到框架层，
业务代码只依赖 `OAuthPrincipal` 与 `@Permission`，不再自己解析 token、比对角色。

- 包名：`com.chua.starter.oauth.client.support`
- 版本：`4.0.0.51`
- 定位：本模块是**消费方**，认证服务端由独立服务提供（通过 `address` + `protocol` 通信）；
  没有服务端时只有 `dev` / `test` + `enable=false` 的降级过滤器可用。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 自动装配 | `AuthClientConfiguration`（`plugin.oauth.enable`，默认 `matchIfMissing=true`） | `META-INF/spring/...AutoConfiguration.imports` |
| 配置 | `AuthClientProperties`（前缀 `plugin.oauth`） | `@EnableConfigurationProperties` |
| 认证过滤器 | `AuthFilter`（order `LOWEST_PRECEDENCE - 10`，`url-patterns = block-address`） | 所有被拦截的 HTTP 请求 |
| 令牌校验 | `WebRequest#authentication`、`Protocol` | 5 种协议实现 |
| 限流 | `RateLimitFilter`（`HIGHEST_PRECEDENCE`）+ `RateLimitService` | `plugin.oauth.rate-limit.*` |
| 权限判定 | `PermissionInterceptor`、`@Permission` | 方法 / 类级注解 |
| 当前用户 | `OAuthPrincipal`、`OauthAuthService`（`AuthService` 的 `@Primary` Bean） | 注入 `AuthService` |
| 请求增强 | `OAuthHttpServletRequestWrapper` | `request.isUserInRole`、`request.getUserPrincipal` |
| 参数注入 | `@UserValue`、`@TokenValue` + 两个 `HandlerMethodArgumentResolver` | Controller 方法参数 |
| 认证执行 | `AuthClientExecute`（单例 `INSTANCE`） | 登录 / 登出 / 在线 / 踢人 / 升级 / 临时令牌 |
| 协议扩展 | `Protocol`、`AbstractProtocol`、`TcpProtocol`、`HttpProtocol`、`Oauth2Protocol`、`RSocketProtocol`、`StaticProtocol` | `ServiceProvider.of(Protocol.class)` |
| 白名单 | `AuthClientProperties#getEffectiveWhitelist`、`WhitelistFileWatcher`、`OAuthWhitelistController` | `plugin.oauth.whitelist` / `whitelist-file` / `/oauth/whitelist` |
| 登录页 | `LocalLoginPageProvider`、`UserStatisticProvider` | 自渲染表单 `POST /login/form`、`POST /v1/users/login` |
| 异常处理 | `OauthExceptionAdvice`、`OauthException` | 权限拦截器抛出的 401/403 |
| 会话工具 | `AuthSessionUtils` | `getUsername()` / `getUserId()` / `getTenantId()` |
| 凭据扩展 | `CredentialProvider`、`PasswordCredentialProvider`、`VerifiedCodeCredentialProvider` | 静态账号 / 验证码登录 |
| AI AppKey | `AiAppKeySupport` | `plugin.oauth.ai-app-key.enabled=true` |
| 指纹校验 | `@VerifyFingerprint`、`@FingerprintCheck`、`AuthClientProperties.Fingerprint` | 浏览器指纹 |
| 降级过滤器 | `AuthFilterBypassConfiguration` | 仅 `dev` / `test` Profile 且 `enable=false` |

### 1.1 认证流程（简版）

1. `RateLimitFilter` 先做限流（总开关 `rate-limit.enable`，默认 `false`）。
2. `AuthFilter` 收请求：命中白名单直接放行；否则按 `protocol` 找 `Protocol` 实现做令牌校验。
3. 校验成功后把用户信息写入请求，并包一层 `OAuthHttpServletRequestWrapper`。
4. 控制器侧用 `@Permission` 触发 `PermissionInterceptor` 做角色 / 权限 / 只读判定。
5. 失败抛 `OauthException`，由 `OauthExceptionAdvice` 转成对应状态码（不是 500）。

### 1.2 权限注解（只有 `@Permission` 一个）

```java
@Permission("user:create")                       // 权限位，命中任一即可
@Permission(role = {"ADMIN", "OPS"})             // 角色，命中任一即可
@Permission(value = "user:delete", role = "ADMIN") // 权限或角色，命中任一即可
```

`value` 与 `permission` 互为别名（`@AliasFor`），二者不可同时使用。
**本模块没有 `@RequiredRole` / `@RequiredPermission` / `@RequiredAnyRole`** —— 早期文档里的这三个注解
不存在，用它们会直接编译失败。类与常量：`AuthConstant.SUPER_ADMIN` / `ADMIN` / `OPS` / `STUDENT` / `TEACHER`。

只读账号：`PermissionInterceptor` 在**所有权限判定之前**检查「账号级 `readonly` 字段」或
角色含 `READ_ONLY`（大小写不敏感）。命中后：`GET` / `HEAD` / `OPTIONS` 放行，其余方法一律 403；
该判定**优先于超管**，即超管账号被标记只读时写操作同样被拒。

### 1.3 注解清单

| 注解 | 目标 | 作用 |
|---|---|---|
| `@Permission` | 类 / 方法 | 权限 / 角色判定（见上） |
| `@TokenForIgnore` | 类 / 方法 | 免令牌校验（webhook 入站等） |
| `@TokenForType` | 类 / 方法 | 限定适用的 `AuthType` 与分组 |
| `@UserValue` | 参数 | 注入当前用户字段（`value`/`name`/`required`/`defaultValue`） |
| `@TokenValue` | 参数 | 注入当前令牌（`value`/`defaultValue`） |
| `@LoginType` | 类 | 标识登录方式（`value` + `AuthType group`） |
| `@Extension` | 类 | 标识扩展点类型 |
| `@VerifyFingerprint` | 类 / 方法 | 该接口需要浏览器指纹校验 |
| `@FingerprintCheck` | 类 / 方法 | 指纹校验声明 |

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-oauth-client-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 说明 |
|---|---|---|
| `com.chua:spring-support-common-starter` | `AuthService`、`@Permission`、`ServiceInfoContributor` | 必需 |
| `com.chua:spring-support-cache-starter` | `CacheProvider`，认证信息缓存 | 必需 |
| `com.chua:spring-support-swagger-starter` | OpenAPI 注解 | 必需 |
| `com.chua:spring-support-mybatis-starter` | 分页 / `PageResult` | 必需 |
| `me.zhyd.oauth:JustAuth` | 第三方登录模型（`AuthUser`） | 必需，`UserInfoService` 扩展点用 |
| `com.konghq:unirest-java` | `HttpProtocol` 的 HTTP 客户端 | 必需 |
| `com.chua:utils-support-rsocket-starter` | `RSocketProtocol` | 必需 |
| `com.chua:utils-support-httpclient-starter`、`utils-support-spring-starter` | HTTP / Spring 工具 | 必需 |
| `com.chua:utils-support-datasource-starter` | `JdbcEngine`（AI AppKey 动态表查询） | `optional`，仅在启用 AI AppKey 时需要 |
| `jakarta.servlet:jakarta.servlet-api` | Servlet API | `provided` |

## 三、配置项

前缀 `plugin.oauth`。核心项：

| key | 默认值 | 说明 |
|---|---|---|
| `enable` | `true` | 自动装配总开关（`matchIfMissing=true`，不写也生效） |
| `protocol` | `tcp` | 可选 `tcp` / `http` / `oauth2` / `rsocket` / `static`（**无 websocket**） |
| `address` | 空 | 认证服务端地址 |
| `login-address` | `${plugin.oauth.auth-address:}` | 登录端点地址 |
| `oauth-url` | `/verify` | 令牌校验路径 |
| `token-name` | `x-oauth-token` | 令牌请求头名 |
| `cookie-name` | `x-oauth-cookie` | 令牌 Cookie 名 |
| `alias` | `Boren-Token` | 令牌别名头 |
| `cache-timeout` | `21600`（6 小时） | 客户端鉴权信息缓存秒数 |
| `cache-hot-cold-backup` | `true` | 缓存冷热备份 |
| `encryption` | `aes` | 传输加密算法 |
| `enable-encryption` | `true` | 是否启用数据加密 |
| `block-address` | `{/*}` | **拦截**地址集合，注册为过滤器的 `url-patterns` |
| `whitelist` | `[]` | 白名单（Ant 风格），只能增加不能减少登录链路 |
| `whitelist-file` | `config/oauth.whitelist` | 外部白名单文件，支持热更新与 `#` 注释 |
| `use-default-whitelist` | `true` | 是否启用内置默认白名单（不影响登录链路必备项） |
| `local-admin-fallback` | `false` | 取不到用户上下文时是否兜底为本地 admin（等价绕过鉴权，仅联调用） |
| `static-token-expire-seconds` | `86400` | 静态账号令牌有效期，取值被夹到 `[60, 604800]` |
| `login-page` | `/login` | 登录页 |
| `login-form-action` | `/login/form` | 自渲染登录表单提交端点（表单编码 POST） |
| `logout-page` | `/logout` | 登出页 |
| `temporary-token-page` | `/temporary-token` | 临时令牌页 |
| `upgrade-page` | `/upgrade` | 升级页 |
| `no-permission-page` | `/oauth-page` | 无权限页 |
| `balance` | `polling` | 多服务端时的均衡模式 |
| `connect-timeout` / `read-timeout` / `request-timeout` | `5000` / `10000` / `15000` | 毫秒 |
| `retry.enabled` / `max-attempts` / `delay` / `multiplier` / `max-delay` | `true` / `3` / `1000` / `1.5` / `5000` | 重试（指数退避） |
| `circuit-breaker.enabled` / `failure-rate-threshold` / `wait-duration-in-open-state` | `true` / `50` / `30` | 熔断 |
| `rate-limit.enable` | `false` | 限流总开关；`rate-limit.login.*` 与 `rate-limit.api.*` 只有它为 `true` 才生效 |
| `rate-limit.api.qps` / `window-seconds` | `200` / `1` | 接口限流（默认 200 QPS） |
| `rate-limit.login.ip-limit` / `ip-window-seconds` / `account-fail-limit` / `account-lock-minutes` | `30` / `60` / `5` / `5` | 登录防暴破 |
| `temp.open` | 无默认 | 只有显式 `true` 才装配 `UserStatisticProvider`（临时账号 / 代入） |
| `temp.user` | `""` | 静态账号 `用户名:口令;...`，默认**刻意留空**；支持加盐摘要 `用户名:盐$MD5(盐+口令)` |
| `temp.menu-path` | 空 | 临时账号菜单文件 |
| `key.enabled` / `access-key` / `secret-key` | `false` | AK/SK 二次加密 |
| `ai-app-key.enabled` / `table-name` / `app-key-column` / `status-column` / `expire-time-column` | `false` / `sys_ai_app_key` / `sys_ai_app_key_app_key` / `sys_ai_app_key_status` / `sys_ai_app_key_expire_datetime` | AI AppKey 本地校验 |
| `fingerprint.global-verification` / `enable-client-verification` / `config-cache-timeout` | `false` / `false` / `3600` | 指纹校验 |

**没有 `exclude-address` 这个配置项**：排除靠 `whitelist`（yaml 清单 / 外部文件 / 动态接口）实现，
`block-address` 只决定过滤器拦哪些 URL。

### 3.1 白名单优先级

`getEffectiveWhitelist()` 的拼接顺序（后者不能删除前者）：

1. `ALWAYS_WHITELIST`（**不可关闭**）：`/login`、`/**/login`、`/**/login/**`、`/**/doLogin`、
   `/**/captcha`、`/**/users/loginCode`。关掉会让登录接口 403 自锁 —— 拿 token 本身需要 token。
2. `DEFAULT_WHITELIST`（`use-default-whitelist: false` 可关）：`/doc.html`、`/actuator/**`、
   `/v3/api-docs/**`、`/webjars/**`、`/**/logout`、`/v1/sys/setting` 等。
3. yaml `plugin.oauth.whitelist`。
4. 动态白名单：`POST /oauth/whitelist?pattern=...`、`DELETE /oauth/whitelist?pattern=...`
   （两个接口都要求 `@Permission("admin")`），`GET /oauth/whitelist` 查当前生效清单。

外部文件 `whitelist-file` 由 `WhitelistFileWatcher` 监听，改动后自动重载。

## 四、快速开始

**第一步**：引入依赖（见第二节）。

**第二步**：指向认证服务端：

```yaml
plugin:
  oauth:
    enable: true
    protocol: http          # tcp 为默认；http 基于 Unirest，同步实现
    address: http://127.0.0.1:9090
    oauth-url: /verify
    whitelist:
      - /api/public/**
```

**第三步**：在业务侧用注解与注入的 `AuthService`：

```java
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthService authService;   // OauthAuthService，@Primary

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @Permission(role = "ADMIN")
    @GetMapping("/me")
    public CurrentUser me() {
        return authService.getCurrentUser();
    }
}
```

## 五、运行示例

### 5.1 调用示例

运行期 API 为**步骤式**：`AuthClientExecute` 是单例、方法即调用，所有参数按签名传入；
**本模块无链式 API**（没有 builder / fluent 构造器）。

**取当前用户 + 权限判定**：

```java
// 控制器里直接注入 OAuthPrincipal（AuthFilter 包装请求后可用）
@GetMapping("/who")
public Map<String, Object> who(OAuthPrincipal principal) {
    return Map.of(
            "userId", principal.getUserId(),
            "tenantId", principal.getTenantId(),
            "admin", principal.isAdmin(),
            "roles", principal.getRoles());
}

// 非 Controller 场景：从会话工具取
String username = AuthSessionUtils.getUsername();
String tenantId = AuthSessionUtils.getTenantId();
```

`OAuthPrincipal` 方法：`getName` / `getUserId` / `getRealName` / `getTenantId` / `getDeptId` /
`getRoles` / `getPermissions` / `hasRole` / `hasAnyRole` / `hasPermission` / `hasAnyPermission` /
`isAdmin`。

**在线统计 / 踢人 / 升级 / 临时令牌**（`AuthClientExecute.INSTANCE`）：

```java
AuthClientExecute exec = AuthClientExecute.getInstance();

Protocol.OnlineStatus status = exec.getOnlineStatus(uid);                 // 单用户在线状态
Protocol.OnlineUserResult page = exec.getOnlineUsers(null, null, 1, 20);  // 分页在线列表
boolean canLogin = exec.canLogin(uid);                                    // 单端/多端登录限制

exec.kickUserSessions(userId);            // 踢掉该用户全部会话
exec.kickOtherSessions(currentToken);     // 保留当前令牌，踢掉其它设备
exec.logoutByToken(token);                // 指定令牌登出

LoginResult refreshed = exec.upgrade(refreshToken, UpgradeType.REFRESH);  // REFRESH 返回新令牌
LoginAuthResult temp = exec.createTemporaryToken(600, List.of("/api/report/**"));
```

`UpgradeType`：`VERSION`（只升级账号信息）/ `TIMESTAMP`（升级并重置时长）/ `REFRESH`（换新令牌）。
`LogoutType` 决定 `uid` 语义（`LOGOUT` = 令牌、`KICK` = 用户 id、`KICK_OTHERS` = 当前令牌、
`DISABLE`/`ENABLE` = 用户 id），传错不会报错但会命中错误的会话范围。

**登录**（`LoginAuthResult`）：`getAccessToken(username, password, AuthType.WEB, ext)`、
`thirdPartyLogin(account, credential, authType, ext)`、`smsLogin`、`emailLogin`；
三方登录的用户映射由 `UserInfoService` 的 `@Spi` 实现提供（见 5.2）。

**Servlet 增强**：`AuthFilter` 把请求包成 `OAuthHttpServletRequestWrapper`，
可用标准 API `request.getUserPrincipal()` / `request.isUserInRole("ADMIN")` / `request.isAuthenticated()`。
注意 `request.login(...)` **恒抛** `ServletException("OAuth客户端不支持直接用户名密码登录，请使用OAuth认证流程")`。

### 5.2 SPI 扩展示例

**新增一种认证协议**（例如内部 RPC 通道），按「实现 → 登记 → 生效」三步：

**① 实现**：继承 `AbstractProtocol`（复用令牌编解码、错误处理、均衡逻辑），只覆写通信部分：

```java
@Spi("acme")
public class AcmeProtocol extends AbstractProtocol {

    public AcmeProtocol(AuthClientProperties properties) {
        super(properties);
    }

    // 覆写 AbstractProtocol 定义的握手 / 校验 / 升级等抽象方法，
    // 不需要的方法显式抛 UnsupportedOperationException，不返回空值冒充成功
}
```

**② 登记**：`com.chua.common.support.spi` 有三个解析器，任选其一：

- **同包扫描**（本模块自带协议就是这样发现的）：实现类与 `Protocol` 接口在
  `...support.protocol` 包（或子包）内，`@Spi` 即完成登记，**不需要**任何文件。
- **`META-INF/extensions/<接口全限定名>`**：写入实现类全名，一行一个
  （`spring-support-aliyun-starter` / `-tencent-starter` 用这种方式登记 `UserInfoService`）。
- **`META-INF/services/<接口全限定名>`**：标准 `ServiceLoader` 格式。

**③ 生效**：`plugin.oauth.protocol=acme`，`AuthFilter` 通过
`ServiceProvider.of(Protocol.class).getNewExtension(protocol, properties)` 取实例；
扩展名匹配**不区分大小写**，可用 `ServiceProvider.of(Protocol.class).names()` 打印已注册协议。
协议实例按「协议名 + 配置摘要」在进程内缓存（`WebRequest.getProtocolExtension`）。

**新增一种第三方登录**：实现 `com.chua.starter.oauth.client.support.user.UserInfoService`
（`checkLogin(principal, credential, address, ext)` / `upgrade(UserResult)`），
用 `@Spi("<登录类型>")` 标注并登记到 `META-INF/extensions/...UserInfoService`；
`thirdPartyLogin(..., AuthType, ext)` 会按登录类型找到它。

## 六、与其它 starter 的关系

- **`spring-support-common-starter`（必需）**：`AuthService` 接口、`@Permission` 注解、
  `ServiceInfoContributor` 都在这里；本模块的 `OauthAuthService` 只是 `@Primary` 实现。
- **`spring-support-cache-starter`（必需）**：认证信息缓存（`cache-timeout`、冷热备份）。
- **`spring-support-swagger-starter` / `-mybatis-starter`（必需）**：接口文档与分页模型。
- **`utils-support-common-starter`（间接必需）**：`@Spi` 与 `ServiceProvider` 注册表。
- **被谁用**：`spring-support-aliyun-starter` / `-tencent-starter` 通过
  `UserInfoService` + `META-INF/extensions` 把支付宝 / 微信小程序登录接进来；
  `spring-support-device-starter` 只用 `@TokenForIgnore` 声明 webhook 免登录态；
  `spring-support-message-starter` 用它做接口权限注解。
- **服务端**：不在本仓库，`AuthClientConfiguration` 在检测到
  `com.chua.starter.oauth.server.support.SsoServer` 时会**跳过**过滤器装配
  （`@ConditionalOnMissingClass`），服务端自己处理鉴权。

## 七、已知限制

1. **只有 `@Permission` 一个权限注解**：`@RequiredRole` / `@RequiredPermission` / `@RequiredAnyRole`
   从未存在，历史文档里的示例不可编译。
2. **没有 `protocol: websocket`**：只有 `tcp` / `http` / `oauth2` / `rsocket` / `static`；
   `static` 的实现类标注是 `@Spi("Static")`（匹配不区分大小写，但日志里显示为 `Static`）。
3. **`local-admin-fallback` 等于关掉鉴权**：`true` 时取不到用户上下文按本地 admin 放行，
   只应出现在没有认证服务端的本地联调；默认 `false`，取不到就拒绝。
4. **`temp.user` 默认空**：不配置就没有可用的静态账号，登录会被明确拒绝；
   早期版本内置的 `admin:admin@123!456` 已删除。`temp.open` 不显式为 `true` 时连
   `UserStatisticProvider` 都不会装配。
5. **`block-address` 与 `whitelist` 是两套东西**：前者是过滤器拦截范围（默认 `/*`），
   后者是放行清单；写成 `exclude-address` 不生效，因为不存在该字段。
6. **登录链路白名单不可关闭**：`ALWAYS_WHITELIST` 的 6 条规则无视任何配置，这是防自锁设计，
   不是可选项。
7. **`request.login()` 不可用**：恒抛 `ServletException`，用户名密码登录必须走
   `AuthClientExecute#getAccessToken` 或 `/v1/users/login`。
8. **`/v1/users/**` 与 `/login/form` 由本模块提供**：`UserStatisticProvider` 挂在
   `/v1/users`（`POST /login`、`POST /grid`、`DELETE /logout`、`GET /menus/my/**`），
   与宿主已有的同名控制器会冲突（`ConflictingBeanDefinitionException`），需自行改名或排除。
9. **限流默认关闭**：`rate-limit.enable=false`，且 `login` / `api` 子开关默认 `true` ——
   只打开总开关就会立刻生效（200 QPS + 登录 30 次/60 秒 + 失败 5 次锁 5 分钟）。
10. **认证信息有缓存延迟**：`cache-timeout` 默认 6 小时，权限变更 / 踢人在缓存窗口内可能不立即生效。
11. **`AuthSessionUtils` 依赖请求上下文**：非 Web 线程（异步任务、定时任务）取不到
    `RequestContextHolder`，会返回空值而不是抛异常，调用方必须自己判空。
12. **AI AppKey 需要数据源**：`ai-app-key.enabled=true` 且容器中有 `DataSource` 才装配
    `AiAppKeySupport`，否则该开关静默无效。
