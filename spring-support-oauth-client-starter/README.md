# Spring Support OAuth Client Starter

## 📖 模块简介

Spring Support OAuth Client Starter 是一个功能强大的 OAuth 客户端模块，提供完整的 OAuth 认证集成方案。该模块不仅支持标准的 OAuth 认证流程，还实现了 Java EE/Jakarta EE 的安全 API 标准，包括`java.security.Principal`接口和`HttpServletRequest`的认证相关方法。

## ✨ 主要功能

### 🔐 OAuth 认证

- 多协议支持（HTTP、WebSocket、Static 等）
- 自动令牌管理和刷新
- 用户信息缓存
- 会话管理

### 👤 Principal 支持

- 完整实现`java.security.Principal`接口
- 集成 OAuth 用户信息
- 角色和权限管理
- 用户详细信息访问

### 🌐 HttpServletRequest 增强

- `isUserInRole()` - 角色检查
- `getAuthType()` - 认证类型获取
- `logout()` - 用户登出
- `login()` - 用户登录（OAuth 流程）
- `getUserPrincipal()` - 获取用户 Principal

### 🛡️ 权限控制

- 基于注解的权限验证
- 方法级别的访问控制
- 角色和权限检查
- 自定义权限拦截器

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-oauth-client-starter</artifactId>
    <version>4.0.0.42</version>
</dependency>
```

### 基础配置

```yaml
plugin:
  oauth:
    # OAuth服务器地址
    address: "http://oauth-server:8080"
    # 认证协议类型
    protocol: "http"
    # 拦截地址模式
    block-address:
      - "/*"
    # 排除地址模式
    exclude-address:
      - "/login"
      - "/public/*"
      - "/actuator/**"
    # 缓存配置
    cache-timeout: 3600
    cache-hot-cold-backup: true
    # 加密配置
    encryption: "SM4"
```

## 📋 详细功能说明

### 1. Principal 支持

#### 获取用户 Principal

```java
@RestController
public class UserController {

    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(Principal principal) {
        if (principal instanceof OAuthPrincipal oauthPrincipal) {
            return ResponseEntity.ok(Map.of(
                "username", oauthPrincipal.getName(),
                "userId", oauthPrincipal.getUserId(),
                "realName", oauthPrincipal.getRealName(),
                "roles", oauthPrincipal.getRoles(),
                "permissions", oauthPrincipal.getPermissions(),
                "tenantId", oauthPrincipal.getTenantId(),
                "deptId", oauthPrincipal.getDeptId(),
                "isAdmin", oauthPrincipal.isAdmin()
            ));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
```

#### 权限检查

```java
@Service
public class UserService {

    public void updateUser(User user, Principal principal) {
        if (principal instanceof OAuthPrincipal oauthPrincipal) {
            // 检查单个角色
            if (!oauthPrincipal.hasRole("ADMIN")) {
                throw new AccessDeniedException("需要管理员权限");
            }

            // 检查多个角色（任意一个）
            if (!oauthPrincipal.hasAnyRole("ADMIN", "USER_MANAGER")) {
                throw new AccessDeniedException("权限不足");
            }

            // 检查权限
            if (!oauthPrincipal.hasPermission("USER_UPDATE")) {
                throw new AccessDeniedException("缺少用户更新权限");
            }
        }

        userRepository.save(user);
    }
}
```

### 2. HttpServletRequest 增强

#### 使用标准 Java EE API

```java
@RestController
public class AuthController {

    @GetMapping("/login-info")
    public ResponseEntity<?> getUserInfo(HttpServletRequest request) {
        // 获取认证类型
        String authType = request.getAuthType(); // 返回: OAUTH_HTTP, OAUTH_STATIC等

        // 获取远程用户
        String remoteUser = request.getRemoteUser();

        // 获取用户Principal
        Principal principal = request.getUserPrincipal();

        // 角色检查
        boolean isAdmin = request.isUserInRole("ADMIN");
        boolean isUser = request.isUserInRole("USER");

        return ResponseEntity.ok(Map.of(
            "authType", authType,
            "remoteUser", remoteUser,
            "isAdmin", isAdmin,
            "isUser", isUser
        ));
    }

    @PostMapping("/admin-only")
    public ResponseEntity<?> adminOnlyAction(HttpServletRequest request) {
        if (!request.isUserInRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("需要管理员权限");
        }

        // 执行管理员操作
        return ResponseEntity.ok("操作成功");
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            request.logout(); // 清除Session中的用户信息
            return ResponseEntity.ok("登出成功");
        } catch (ServletException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("登出失败: " + e.getMessage());
        }
    }
}
```

#### 使用增强功能

```java
@RestController
public class EnhancedController {

    @GetMapping("/enhanced-info")
    public ResponseEntity<?> getEnhancedInfo(HttpServletRequest request) {
        if (request instanceof OAuthHttpServletRequestWrapper oauthRequest) {
            // 多角色检查
            boolean hasAnyRole = oauthRequest.isUserInAnyRole("ADMIN", "USER", "GUEST");

            // 权限检查
            boolean hasPermission = oauthRequest.hasPermission("USER_READ");
            boolean hasAnyPermission = oauthRequest.hasAnyPermission("USER_READ", "USER_WRITE");

            // 用户详细信息
            String userId = oauthRequest.getUserId();
            String tenantId = oauthRequest.getTenantId();
            String deptId = oauthRequest.getDeptId();
            boolean isAdmin = oauthRequest.isAdmin();
            boolean isAuthenticated = oauthRequest.isAuthenticated();

            // 获取OAuth Principal
            OAuthPrincipal oauthPrincipal = oauthRequest.getOAuthPrincipal();

            return ResponseEntity.ok(Map.of(
                "hasAnyRole", hasAnyRole,
                "hasPermission", hasPermission,
                "hasAnyPermission", hasAnyPermission,
                "userId", userId,
                "tenantId", tenantId,
                "deptId", deptId,
                "isAdmin", isAdmin,
                "isAuthenticated", isAuthenticated,
                "principal", oauthPrincipal
            ));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
```

### 3. 权限注解

```java
@RestController
public class SecureController {

    @GetMapping("/admin/users")
    @RequiredRole("ADMIN")  // 需要管理员角色
    public List<User> getUsers() {
        return userService.findAll();
    }

    @PostMapping("/users")
    @RequiredPermission("USER_CREATE")  // 需要用户创建权限
    public User createUser(@RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @DeleteMapping("/users/{id}")
    @RequiredAnyRole({"ADMIN", "USER_MANAGER"})  // 需要任意一个角色
    public void deleteUser(@PathVariable Long id) {
        userService.delete(id);
    }
}
```

### 3.1 @Permission 注解（接口权限 / 按钮权限 / 只读角色）——推荐

#### 🎯 场景与用途

- **接口权限**：标注在 Controller/Service 方法或类上，要求用户持有指定权限码或角色才能访问（方法级、类级均可）。
- **按钮权限**：按钮与接口共用同一套权限码——前端按权限码控制按钮显隐，后端在接口上用 `@Permission` 拦截兜底，防止绕过前端直接调用。
- **只读角色**：账号授予 `READ_ONLY` 角色后，写操作（POST/PUT/DELETE/PATCH）一律拒绝，**优先于所有权限（含超级管理员）**。

#### 📦 注解属性

| 属性 | 类型 | 说明 | 示例 |
|---|---|---|---|
| `value` / `permission`（互为别名） | `String[]` | 权限标识列表，用户拥有**任一**即满足 | `@Permission("user:delete")` |
| `role` | `String[]` | 角色标识列表，用户拥有**任一**即满足 | `@Permission(role = "ADMIN")` |

匹配语义：权限码或角色**任一命中即放行**；两者都不满足则拒绝（抛 `OauthException`，由应用层 `OauthExceptionAdvice` 映射为 403）。

#### 🧩 使用示例

```java
// 接口权限：需要 user:delete 权限码
@PostMapping("/user/delete")
@Permission("user:delete")
public Result<Void> deleteUser(@RequestBody DeleteRequest request) {
    return userService.delete(request);
}

// 按钮权限：按钮绑定同一权限码（前端控制显隐，后端拦截兜底）
// 前端：hasPermission('user:add') 为 true 才渲染"新增用户"按钮
@PostMapping("/user/add")
@Permission("user:add")
public Result<Void> addUser(@RequestBody User user) {
    return userService.create(user);
}

// 角色权限：ADMIN 或 USER_MANAGER 任一角色放行
@GetMapping("/user/export")
@Permission(role = {"ADMIN", "USER_MANAGER"})
public Result<Void> exportUsers() { ... }

// 类级注解：整个 Controller 统一权限（@Target 支持 TYPE）
@RestController
@RequestMapping("/api/order")
@Permission("order:manage")
public class OrderController { ... }
```

#### 🔒 只读账号（readonly 字段 + READ_ONLY 角色）

账号只读由两种来源任一触发（**均来自 OAuth 服务端数据库查询，非配置文件**）：

| 来源 | 说明 |
|---|---|
| 账号级 `readonly` 字段（推荐） | OAuth 服务端用户表增加只读字段（如 `user_readonly`），认证/用户信息接口在 `UserResume` 返回 `readonly: true/false`；客户端经 `userInfo.isReadonly()` 判断 |
| `READ_ONLY` 角色（兼容兜底） | 账号角色含 `READ_ONLY` 时同样生效（角色数据同样来自服务端数据库） |

命中只读后：

| 请求方法 | 行为 |
|---|---|
| GET / HEAD / OPTIONS | 正常放行（只读操作） |
| POST / PUT / DELETE / PATCH | 一律拒绝（403），即使持有对应权限码甚至为超级管理员 |

适用于：只读报表账号、审计账号、只读第三方对接账号等场景。校验发生在 `PermissionInterceptor` 权限判断**之前**，因此优先级最高；无 HTTP 请求上下文（非 Web 调用）时不阻断。

> 🗄️ **服务端返回契约**：用户表需含只读字段（建议 `user_readonly` TINYINT/boolean），登录/用户信息接口在返回的 `UserResume` 中输出 `readonly`；`UserResume` 其余用户数据（角色、权限码、部门、数据权限等）同样全部来自服务端数据库查询，客户端不通过配置文件承载任何业务数据。
>
> 🖥️ **前端同步契约**（`readonly` 字段前后端联调约定）：
> - **字段**：`readonly`，`boolean`，`true`=只读账号；由用户信息接口从 `UserResume` 返回（JSON 示例：`"userInfo": { "userId": "...", "username": "...", "readonly": true }`）。
> - **账号管理页**：创建/编辑账号表单增加"是否只读"开关（对应服务端 `user_readonly` 字段）；用户列表/详情展示只读状态标识。
> - **操作按钮**：当前用户 `readonly=true` 时前端**同步禁用写操作按钮**（新增/编辑/删除/导入等），仅保留查询/导出——与后端 `PermissionInterceptor` 拦截一致（前端提示"只读账号不允许写操作"）。
> - **登录态响应**：若应用自建用户信息接口（如 ai-starter 的 `UserAuthController` 手工组装 map），需在返回体中补 `readonly` 字段（取 `userResume.isReadonly()`）。

#### 📖 怎么用（接入步骤）

1. 引入 `spring-support-oauth-client-starter` + `spring-support-common-starter`；
2. 用户信息（`UserResume`）经认证后存入 Session，其中 `permission` 为权限码集合、`roles` 为角色集合、`readonly` 为服务端返回的只读标记；
3. 在方法/类上标注 `@Permission`（权限码或角色，任一命中放行）；
4. 本地联调模式（无远程 OAuth 服务）下无用户上下文时自动注入本地 admin 放行；生产环境须接入真实 OAuth 认证。

> ⚠️ **注意事项**：权限码/角色为"任一命中"（OR）语义；按钮权限码与接口权限码必须一致；`READ_ONLY` 为兼容兜底的角色名（`PermissionInterceptor.READ_ONLY_ROLE`），新接入建议使用账号级 `readonly` 字段。

### 4. 过滤器和拦截器

#### 自定义安全过滤器

```java
@Component
public class CustomSecurityFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 检查用户是否已认证
        Principal principal = httpRequest.getUserPrincipal();
        if (principal == null) {
            ((HttpServletResponse) response).sendRedirect("/login");
            return;
        }

        // 检查特定路径的权限
        String requestURI = httpRequest.getRequestURI();
        if (requestURI.startsWith("/admin/") && !httpRequest.isUserInRole("ADMIN")) {
            ((HttpServletResponse) response).sendError(HttpStatus.FORBIDDEN.value());
            return;
        }

        chain.doFilter(request, response);
    }
}
```

#### 权限拦截器

```java
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler) throws Exception {

        Principal principal = request.getUserPrincipal();
        if (principal instanceof OAuthPrincipal oauthPrincipal) {

            // 管理员可以访问所有资源
            if (oauthPrincipal.isAdmin()) {
                return true;
            }

            // 检查特定权限
            if (request.getRequestURI().startsWith("/api/users/") &&
                !oauthPrincipal.hasPermission("USER_MANAGE")) {
                response.sendError(HttpStatus.FORBIDDEN.value(), "权限不足");
                return false;
            }
        }

        return true;
    }
}
```

### 5. 登录单端/多端控制（在线数量限制）

查询用户在线状态，判断当前为单端（SINGLE）还是多端（MULTIPLE）模式：

```java
AuthClientExecute client = AuthClientExecute.getInstance();

// 获取指定用户在线状态（单端/多端、当前在线数、最大在线数）
Protocol.OnlineStatus status = client.getOnlineStatus("userId");
boolean multiple = status.isMultipleMode();   // 是否多端模式
boolean canLogin = status.canLogin();         // 是否还可继续登录

// 分页查询在线用户
Protocol.OnlineUserResult onlineUsers = client.getOnlineUsers(null, null, 1, 100);
```

> **在线数量限制由认证服务器（sso）强制**：登录时校验 `maxOnlineCount`（默认 3），
> 达到上限返回「已达到最大在线设备数量限制」；Http/Armeria 协议返回真实在线状态，
> static/OAuth2 协议无状态部署时返回默认多端模式（不强制限制）。

### 6. Refresh 多种升级模式

`UpgradeType` 支持三种模式：

```java
AuthClientExecute client = AuthClientExecute.getInstance();

// VERSION：升级账号信息（刷新用户信息）
client.upgrade("access-token", UpgradeType.VERSION);

// TIMESTAMP：升级账号信息并重置时长（续期）
client.upgrade("access-token", UpgradeType.TIMESTAMP);

// REFRESH：使用 refresh token 换发新 token
client.upgrade("refresh-token", UpgradeType.REFRESH);
```

对应 HTTP 接口：`POST /v2/user/upgrade`（VERSION/TIMESTAMP，通过 `x-oauth-upgrade-type` 头指定）、
`POST /v2/user/refresh-token`（REFRESH）。

### 7. AppKey 认证模式

基于 `appId` / `appSecret` 的客户端认证，无需用户密码：

```java
AppKeySecret appKeySecret = new AppKeySecret()
        .setAppId("APP-1001")
        .setAppSecret("secret")
        .setUserCode("U-100");               // 缓存键优先使用 userCode
// 可选自定义请求体（兼容服务端两种 body 形式）
appKeySecret.setBody("{\"appKey\":\"xxx\",\"appSecret\":\"yyy\"}");

AuthenticationInformation info = protocol.authentication(appKeySecret);
```

> Http/Armeria/RSocket 协议支持 AppKey 认证；static 协议不支持（降级返回 403）。

### 8. 临时令牌（含 Refresh 换临时令牌）

创建短期有效的临时令牌，可限定过期时间与允许访问的 URL：

```java
AuthClientExecute client = AuthClientExecute.getInstance();

// 基于当前登录用户创建临时令牌（默认 10 秒过期）
client.createTemporaryToken();

// 基于任意有效源令牌创建
client.createTemporaryTokenByToken("source-token", 60, List.of("/api/upload", "/api/download"));

// 用 refresh token 换取临时令牌（access token 过期时的临时授权场景）
client.createTemporaryTokenByRefreshToken("refresh-token", 60, List.of("/api/file/*"));
```

> 过期时间范围 1~3600 秒；Http/Armeria/RSocket 协议实现，OAuth2/static 协议返回 501。

## ⚙️ 高级配置

### 完整配置示例
```yaml
plugin:
  oauth:
    # OAuth服务器配置
    address: "http://oauth-server:8080"
    protocol: "http" # http, websocket, static

    # 路径配置
    block-address:
      - "/*"
    exclude-address:
      - "/login"
      - "/register"
      - "/public/**"
      - "/actuator/**"
      - "/swagger-ui/**"
      - "/v3/api-docs/**"

    # 缓存配置
    cache-timeout: 3600 # 缓存超时时间（秒）
    cache-hot-cold-backup: true # 启用热冷备份

    # 安全配置
    encryption: "SM4" # 加密算法

    # 临时提供者配置
    temp:
      open: true

    # 会话配置
    session:
      timeout: 1800 # 会话超时时间（秒）

    # 权限配置
    permission:
      enable: true
      default-role: "USER"
```

### 多环境配置

```yaml
# 开发环境
spring:
  profiles:
    active: dev

---
spring:
  profiles: dev
plugin:
  oauth:
    address: "http://localhost:8080"
    cache-timeout: 300

---
spring:
  profiles: prod
plugin:
  oauth:
    address: "https://oauth.example.com"
    cache-timeout: 3600
    encryption: "AES"
```

## 🔧 自定义扩展

### 自定义认证处理

```java
@Component
public class CustomAuthService implements AuthService {

    @Override
    public String getCurrentUserId() {
        // 自定义用户ID获取逻辑
        return "custom-login-id";
    }

    @Override
    public String getCurrentUsername() {
        // 自定义用户名获取逻辑
        return "custom-username";
    }

    // 实现其他方法...
}
```

### 自定义权限验证

```java
@Component
public class CustomPermissionEvaluator {

    public boolean hasPermission(Principal principal, String permission) {
        if (principal instanceof OAuthPrincipal oauthPrincipal) {
            // 自定义权限验证逻辑
            return customPermissionCheck(oauthPrincipal, permission);
        }
        return false;
    }
}
```

## 🔄 OAuth 认证流程图

```mermaid
sequenceDiagram
    participant User as 用户/浏览器
    participant Web as Web层<br/>(Controller/Filter)
    participant Filter as AuthFilter<br/>(认证过滤器)
    participant Cache as Cache<br/>(本地缓存)
    participant Session as Session<br/>(会话存储)
    participant Protocol as ProtocolExecutor<br/>(协议执行器)
    participant Server as OAuth服务端
    participant Principal as OAuthPrincipal<br/>(Principal对象)

    User->>Web: 1. 发起HTTP请求
    Web->>Filter: 2. 请求被AuthFilter拦截
    
    alt 请求在排除列表中
        Filter->>Web: 直接放行
        Web->>User: 返回响应
    else 需要认证
        Filter->>Session: 3. 检查Session中是否有Token
        alt Session中有Token
            Session-->>Filter: 返回Token和UserResume
            Filter->>Cache: 4. 检查缓存中是否有用户信息
            alt 缓存命中
                Cache-->>Filter: 返回缓存的UserResume
                Filter->>Principal: 5. 创建OAuthPrincipal
                Principal-->>Filter: 返回Principal对象
                Filter->>Web: 6. 设置Principal到Request
                Web->>User: 7. 返回响应
            else 缓存未命中
                Filter->>Protocol: 8. 调用ProtocolExecutor验证Token
                Protocol->>Server: 9. 发送认证请求<br/>(Header: x-oauth-type, x-oauth-token)
                Server-->>Protocol: 10. 返回UserResume
                Protocol-->>Filter: 返回用户信息
                Filter->>Cache: 11. 缓存用户信息
                Filter->>Session: 12. 更新Session
                Filter->>Principal: 13. 创建OAuthPrincipal
                Principal-->>Filter: 返回Principal对象
                Filter->>Web: 14. 设置Principal到Request
                Web->>User: 15. 返回响应
            end
        else Session中没有Token
            Filter->>Protocol: 16. 检查请求头中的Token
            alt 请求头中有Token
                Protocol->>Server: 17. 发送认证请求验证Token
                alt Token有效
                    Server-->>Protocol: 返回UserResume
                    Protocol-->>Filter: 返回用户信息
                    Filter->>Session: 18. 保存Token和UserResume到Session
                    Filter->>Cache: 19. 缓存用户信息
                    Filter->>Principal: 20. 创建OAuthPrincipal
                    Principal-->>Filter: 返回Principal对象
                    Filter->>Web: 21. 设置Principal到Request
                    Web->>User: 22. 返回响应
                else Token无效
                    Server-->>Protocol: 返回认证失败
                    Protocol-->>Filter: 认证失败
                    Filter->>Web: 23. 返回401未授权
                    Web->>User: 返回401错误
                end
            else 请求头中没有Token
                Filter->>Web: 24. 返回401未授权
                Web->>User: 返回401错误
            end
        end
    end

    Note over User,Principal: 认证成功后，用户可以通过<br/>request.getUserPrincipal()<br/>获取OAuthPrincipal对象
```

## 📝 注意事项

1. **登录方法限制**：OAuth 客户端不支持直接的用户名密码登录，`request.login()`方法会抛出`ServletException`
2. **登出行为**：`request.logout()`方法会清除 Session 中的用户信息，但不会完全销毁 Session
3. **线程安全**：Principal 和用户信息存储在 Session 中，在多线程环境下是安全的
4. **性能考虑**：用户信息会缓存在 Session 中，避免频繁的远程调用
5. **兼容性**：完全兼容 Java EE/Jakarta EE 的安全 API 标准

## 🐛 故障排除

### 常见问题

1. **Principal 为 null**

   - 检查用户是否已通过 OAuth 认证
   - 确认请求路径不在排除列表中
   - 验证 OAuth 服务器配置是否正确

2. **isUserInRole 返回 false**

   - 确认用户确实具有该角色
   - 检查角色名称是否正确（区分大小写）
   - 验证 OAuth 服务器返回的角色信息

3. **logout 不生效**
   - 检查 Session 是否存在
   - 确认没有其他组件重新设置用户信息

### 调试建议

启用调试日志：

```yaml
logging:
  level:
    com.chua.starter.oauth.client: DEBUG
```

这将输出详细的认证和 Principal 创建过程日志。
