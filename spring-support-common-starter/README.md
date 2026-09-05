# Spring Support Common Starter

## 📖 模块简介

Spring Support Common Starter 是 Spring Support 框架的核心通用模块，提供了企业级应用开发中常用的基础功能和工具类。该模块作为其他模块的基础依赖，提供统一的响应处理、参数验证、缓存支持、验证码生成等核心功能。

## ✨ 主要功能

### 🔄 统一响应处理
- 统一API响应格式
- 全局异常处理
- 响应数据加密/解密
- 异步响应支持

### ✅ 参数验证
- Bean Validation集成
- 自定义验证注解
- 参数校验异常处理
- 数据转换和格式化

### 💾 缓存支持
- 多级缓存管理
- Redis缓存集成
- 缓存注解支持
- 缓存过期策略

### 🔐 验证码功能
- 图形验证码生成
- 验证码验证
- Session管理
- Base64编码支持

### 📝 用户操作日志
- 自动记录用户操作日志
- 支持SpEL表达式自定义日志内容
- 自动识别操作类型（增删改查等）
- 格式化日志输出：[时间] 用户名 执行了 操作

### 🛠️ 工具类集成
- 防抖动处理
- 异步任务执行
- 线程池管理
- SPI选项提供

## 🚀 快速开始

### Maven依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-common-starter</artifactId>
    <version>4.0.0.42</version>
</dependency>
```

### 基础配置

```yaml
plugin:
  # 统一响应格式配置
  parameter:
    enable: true  # 启用统一响应格式
  
  # 缓存配置
  cache:
    type: ["default", "redis"]
    redis:
      ttl: 600  # 缓存时间（秒）
  
  # 验证码配置
  captcha:
    enable: true
    width: 130
    height: 48
    length: 4
    
  # SPI选项配置
  optional:
    enable: true
```

## 📋 详细功能说明

### 1. 统一响应处理

#### 启用统一响应格式

```yaml
plugin:
  parameter:
    enable: true
```

#### 响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1640995200000,
  "success": true
}
```

#### 使用示例

```java
@RestController
public class UserController {
    
    @GetMapping("/users")
    public List<User> getUsers() {
        // 直接返回数据，框架自动包装为统一格式
        return userService.findAll();
    }
    
    @GetMapping("/login/{id}")
    @ReturnOrigin  // 返回原始数据，不进行包装
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
}
```

#### 忽略响应包装

```java
@ApiReturnFormatIgnore  // 类级别忽略
@RestController
public class FileController {
    
    @GetMapping("/download")
    @IgnoreReturnType  // 方法级别忽略
    public byte[] downloadFile() {
        return fileService.getFileBytes();
    }
}
```

### 2. 全局异常处理

框架提供了完整的异常处理机制：

```java
@RestController
public class UserController {
    
    @PostMapping("/users")
    public User createUser(@Valid @RequestBody CreateUserRequest request) {
        // 参数验证失败会自动返回统一错误格式
        return userService.create(request);
    }
}
```

异常响应格式：

```json
{
  "code": 400,
  "message": "用户名不能为空；邮箱格式不正确",
  "data": null,
  "timestamp": 1640995200000,
  "success": false
}
```

### 3. 验证码功能

#### 获取验证码

```http
GET /v1/captcha/captcha
```

响应：

```json
{
  "verifyCodeBase64": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
  "verifyCodeKey": "ABCD"
}
```

#### 验证码校验

```java
@RestController
public class AuthController {
    
    @PostMapping("/login")
    public LoginResult login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        // 验证码会自动从Session中获取并验证
        return authService.login(request);
    }
}
```

### 4. 缓存支持

#### 使用Redis缓存

```java
@Service
public class UserService {
    
    @Cacheable(cacheManager = "systemCacheManager", cacheNames = "users", key = "#id")
    public User findById(Long id) {
        return userRepository.findById(id);
    }
    
    @CacheEvict(cacheManager = "systemCacheManager", cacheNames = "users", key = "#login.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }
}
```

#### 可用的缓存管理器

- `systemCacheManager600` - 10分钟过期
- `systemCacheManager` - 1小时过期  
- `systemCacheManagerAlways` - 永不过期

### 5. 用户操作日志

#### 基本使用

使用 `@UserLogger` 注解标记需要记录日志的方法：

```java
@RestController
@RequestMapping("/users")
public class UserController {
    
    @PostMapping
    @UserLogger(name = "新增用户", module = "用户管理")
    public User createUser(@RequestBody User user) {
        return userService.save(user);
    }
    
    @PutMapping("/{id}")
    @UserLogger(name = "更新用户信息", module = "用户管理")
    public User updateUser(@PathVariable Long id, @RequestBody User user) {
        return userService.update(id, user);
    }
    
    @DeleteMapping("/{id}")
    @UserLogger(name = "删除用户", module = "用户管理")
    public void deleteUser(@PathVariable Long id) {
        userService.delete(id);
    }
}
```

#### 日志输出格式

```
[2024-01-01 12:00:00] 张三 执行了 用户管理-新增用户 (耗时: 50ms, 状态: 成功)
[2024-01-01 12:01:00] 李四 执行了 用户管理-删除用户 (耗时: 30ms, 状态: 失败) 异常: 用户不存在
```

#### 自动识别操作类型

如果不指定 `module`，框架会根据方法名自动识别操作类型：

| 方法名关键词 | 操作类型 |
|------------|---------|
| save, insert, add, create | 添加 |
| update, modify, edit, change | 修改 |
| delete, drop, remove, del | 删除 |
| reset | 重置 |
| import | 导入 |
| export | 导出 |
| upload | 上传 |
| download | 下载 |
| login, signin | 登录 |
| logout, signout | 登出 |
| approve, audit | 审批 |
| 其他 | 查询 |

#### 使用SpEL表达式自定义日志内容

```java
@UserLogger(
    name = "更新用户状态", 
    module = "用户管理",
    content = "将用户 #{#arg0.username} 的状态修改为 #{#arg0.status}"
)
public User updateUserStatus(User user) {
    return userService.updateStatus(user);
}
```

**SpEL可用变量：**
- `#arg0...n` - 方法参数（按顺序）
- `#args` - 所有参数数组
- `#method` - 方法对象
- `#result` - 返回值
- `#now` - 当前时间

#### @UserLogger注解参数说明

| 参数 | 类型 | 默认值 | 说明 |
|-----|------|-------|------|
| name | String | "" | 操作名称，为空时尝试读取Swagger注解 |
| module | String | "" | 操作模块，为空时根据方法名自动识别 |
| content | String | "" | 日志内容，支持SpEL表达式 |
| action | Action | NONE | 操作类型枚举 |
| loginType | String | "" | 登录类型 |
| enable | boolean | true | 是否启用日志记录 |
| logArgs | boolean | true | 是否记录方法参数 |
| logResultData | boolean | true | 是否记录返回结果 |

#### 忽略日志记录

```java
@UserLogger(name = "批量查询")
@LoggerIgnore  // 使用此注解忽略日志记录
public List<User> batchQuery(List<Long> ids) {
    return userService.findByIds(ids);
}
```

#### 监听日志事件

可以通过监听 `UserLoggerInfo` 事件来实现自定义日志处理（如存储到数据库）：

```java
@Component
public class UserLoggerEventListener {
    
    @EventListener
    public void onUserLogger(UserLoggerInfo event) {
        // 将日志保存到数据库
        logService.save(LogRecord.builder()
            .username(event.getCreateName())
            .module(event.getLogModule())
            .operation(event.getLogName())
            .content(event.getLogContent())
            .params(event.getLogParam())
            .ip(event.getClientIp())
            .browser(event.getBrowser())
            .os(event.getSystem())
            .status(event.getLogStatus())
            .costTime(event.getLogCost())
            .createTime(event.getCreateTime())
            .build());
    }
}
```

### 6. 防抖动处理

```java
@Service
public class OrderService {
    
    @Debounce(timeout = 5000)  // 5秒内重复调用会被忽略
    public Order createOrder(CreateOrderRequest request) {
        return orderRepository.save(new Order(request));
    }
}
```

### 7. SPI选项提供

#### 获取可用选项

```http
GET /v1/option/get?type=base64编码的类型
```

#### 获取已加载对象

```http
GET /v1/option/objects/get?type=类型&name=名称
```

### 8. 服务信息注册表（ServiceInfo）

各业务 starter 可通过 `ServiceInfoRegistry` 注册自己的端点 URL 与开关状态（三态：true / false / null），
启动后由框架统一渲染为启动横幅，并暴露聚合查询端点。

#### 注册方式（推荐使用 Bean 贡献器）

```java
@Configuration
public class MyConfiguration {

    @Bean
    public ServiceInfoContributor myServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("文件系统", "分片上传", "/chunk/create,upload,complete", "分片上传");
            registry.registerFlag("oss", "存储", true, "阿里云 OSS 已启用");
        };
    }
}
```

#### 查询聚合信息

```http
GET /service-info
```

返回结构：

```json
{
  "endpoints": { "group/name": { "group": "...", "name": "...", "url": "...", "description": "..." } },
  "flags": { "group/name": { "name": "...", "group": "...", "value": true, "description": "..." } }
}
```

### 9. 链路追踪与方法监控

- 链路追踪上下文：`TrackContext` 提供 TraceId 贯穿请求链路，配合日志模板使用
- 方法监控注解：`@Watch` 标记需监控的方法，由切面自动记录调用轨迹与耗时

```java
@Watch(description = "处理订单")
public Order processOrder(OrderRequest request) {
    return orderService.process(request);
}
```

### 10. 媒体类型处理

`media` 提供统一媒体信息处理器抽象 `MediaTypeHandler`，内置 JSON / XML / CSV 三种实现，
用于不同媒体类型消息的解析与转换。

### 11. Actuator 安全保护

`actuator` 提供 Actuator 认证过滤器，可对 `/actuator` 端点做访问控制保护，避免生产环境敏感信息泄露。

### 12. 常用工具类

- `network.UserAgent`：User-Agent 解析（浏览器、操作系统、设备识别）
- `project.Project`：项目/应用元信息模型
- `watch`：方法调用链 Span 记录与追踪

## ⚙️ 高级配置

### API响应配置

```yaml
plugin:
  api:
    # 忽略响应格式化的包
    ignore-format-packages:
      - "com.example.external"
      - "org.springframework"
    # 响应加密
    encryption:
      enable: true
      algorithm: "AES"
```

### 缓存详细配置

```yaml
plugin:
  cache:
    # 缓存类型
    type: ["redis", "caffeine"]
    # Redis配置
    redis:
      ttl: 3600
      prefix: "APP:"
    # 本地缓存配置
    local:
      maximum-size: 1000
      expire-after-write: 600
```

### 验证码详细配置

```yaml
plugin:
  captcha:
    enable: true
    width: 130        # 验证码宽度
    height: 48        # 验证码高度
    length: 4         # 验证码长度
    font-size: 32     # 字体大小
    line-count: 5     # 干扰线数量
    char-type: "number"  # 字符类型: number, letter, mixed
```

## 🔧 自定义扩展

### 自定义异常处理

```java
@RestControllerAdvice
public class CustomExceptionHandler {
    
    @ExceptionHandler(CustomBusinessException.class)
    public Result<Void> handleBusinessException(CustomBusinessException e) {
        return Result.failed(e.getCode(), e.getMessage());
    }
}
```

### 自定义响应处理

```java
@Component
public class CustomResponseBodyAdvice implements ResponseBodyAdvice<Object> {
    
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }
    
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, 
                                 MediaType selectedContentType, 
                                 Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                 ServerHttpRequest request, 
                                 ServerHttpResponse response) {
        // 自定义响应处理逻辑
        return body;
    }
}
```

## 📝 注意事项

1. **统一响应格式**：启用后会影响所有Controller的返回值，请确保前端适配
2. **缓存配置**：Redis缓存需要先配置Redis连接
3. **验证码Session**：验证码依赖Session，在分布式环境下需要配置Session共享
4. **异常处理**：全局异常处理会拦截所有异常，自定义异常处理器需要注意优先级
5. **性能考虑**：统一响应处理会对所有接口生效，对性能敏感的接口可以使用忽略注解

## 🐛 故障排除

### 常见问题

1. **响应格式不生效**
   - 检查 `plugin.parameter.enable` 配置
   - 确认Controller使用了 `@RestController` 注解
   - 检查是否使用了忽略注解

2. **验证码不显示**
   - 检查 `plugin.captcha.enable` 配置
   - 确认Session配置正确
   - 检查图片Base64编码是否完整

3. **缓存不生效**
   - 检查缓存管理器配置
   - 确认Redis连接正常
   - 检查缓存注解使用是否正确

### 调试建议

启用调试日志：

```yaml
logging:
  level:
    com.chua.starter.common: DEBUG
```

这将输出详细的处理过程日志，帮助定位问题。

### 13. api 版本 / 灰度 / 功能开关（@ApiVersion / @ApiGray / @ApiFeature）

> ⚠️ **前提**：`ApiConfiguration` / `ApiFeatureConfiguration` 未注册为自动配置，使用以下功能前须在应用上显式导入：
>
> ```java
> @SpringBootApplication
> @Import({ApiConfiguration.class, ApiFeatureConfiguration.class})
> public class Application { ... }
> ```

#### 13.1 @ApiVersion —— 接口版本路由

**🎯 场景与用途**：同一路径的接口渐进升级，新老版本并存——老客户端不感知新版本，新客户端显式指定版本号；适用于接口不兼容升级、按渠道/租户分发不同版本等场景。

**📦 注解属性**

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `value` | `String` | `"1"` | 版本号（语义化版本，如 `"1"`、`"1.2"`、`"v2"`） |

**📖 路由方式**（由 `ApiVersionRequestMappingHandlerMapping` 接管映射后生效）：

| 方式 | 示例 |
|---|---|
| 查询参数 | `GET /api/greet?apiVersion=1` |
| 请求头 | `GET /api/greet` + `X-Api-Version: 1` |
| latest 关键字 | `?apiVersion=latest` → 命中最大可用版本 |

匹配语义：请求版本 ≥ 接口版本才匹配；未指定版本时匹配所有接口、按版本降序取最大（即默认命中最新版）；`latest` 恒匹配并取最大版本。

**🧩 使用示例**（同一路径两个版本并存）：

```java
@RestController
@RequestMapping("/api/greet")
public class GreetController {

    @GetMapping
    @ApiVersion("1")
    public Result<String> greetV1() { return Result.ok("hello-v1"); }

    @GetMapping
    @ApiVersion("2")
    public Result<String> greetV2() { return Result.ok("hello-v2"); }
}
```

**🔧 实现机制（已修复）**：Boot 4.x 下默认映射为 `DelegatingWebMvcConfiguration` 的继承 Bean，竞争式 `@Bean` 无法令其退避（同路径多版本会 Ambiguous mapping）——`ApiConfiguration` 现经 **`WebMvcRegistrations`**（Boot 官方扩展点）装配 `ApiVersionRequestMappingHandlerMapping`，并将版本参数读取修正为 `getParameter`（`getQueryString` 在 MockMvc 等环境为 null）。开关 `plugin.api.version.enable` 默认开启。

#### 13.2 @ApiGray —— 接口灰度发布

**🎯 场景与用途**：让**部分用户先体验新功能**（名单/百分比/规则），命中用户获得灰度版本并返回 `X-Gray-Hit: true` 响应头；未命中用户走默认逻辑。**软灰度**（默认）不阻断未命中用户，**强制灰度**（`forceGray=true`）未命中直接拒绝——适用于新功能内测、AB 分流、渐进放量。

**📦 注解属性**

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `value` | `String` | `""` | 灰度版本标识（命中时写入 `X-Gray-Hit-Version` 头） |
| `description` | `String` | `""` | 功能描述 |
| `percentage` | `int` | `0` | 灰度百分比（0-100） |
| `rule` | `String` | `""` | SpEL 规则（按请求上下文动态命中） |
| `users` | `String[]` | `{}` | 灰度用户名单（按会话 username/userId 匹配） |
| `ips` | `String[]` | `{}` | 灰度 IP 名单 |
| `roles` | `String[]` | `{}` | 灰度角色名单 |
| `headers` | `String[]` | `{}` | 灰度请求头规则 |
| `fallback` | `String` | `""` | 强制灰度未命中时的降级转发路径 |
| `notInGrayMessage` | `String` | `"您暂未被邀请体验此功能"` | 未命中提示文案 |
| `notInGrayStatus` | `int` | `200` | 未命中拒绝状态码（强制灰度时生效，建议 403） |
| `forceGray` | `boolean` | `false` | 强制灰度：未命中直接拒绝（默认软灰度放行） |
| `group` | `String` | `"default"` | 灰度分组 |

**🧩 使用示例**：

```java
// 软灰度：gray-user 命中灰度体验，其余用户照常访问默认逻辑
@GetMapping("/preview")
@ApiGray(users = {"gray-user"})
public Result<String> preview() { return Result.ok("gray-preview-ok"); }

// 强制灰度：非名单用户 403（未获邀体验）
@GetMapping("/preview-force")
@ApiGray(users = {"gray-user"}, forceGray = true, notInGrayStatus = 403)
public Result<String> previewForce() { return Result.ok("gray-force-ok"); }
```

**📖 怎么用**：会话需包含用户上下文（`username`/`userId`；角色经请求/会话 `roles` 属性读取），灰度名单匹配即命中（返回 `X-Gray-Hit` 响应头）；全局开关 `plugin.api.gray.enable` 默认开启（非实验性功能）；拦截器由 `ApiFeatureConfiguration` 注册（`/**`，排除 `/error` 与 `/actuator/**`）。

#### 13.3 @ApiFeature —— 功能开关

**🎯 场景与用途**：按功能标记控制开放状态——未开放的功能返回统一提示（默认 503），适用于功能内测白名单、运营开关、渐进放量；与灰度（按用户分流）不同，功能开关是"整个功能开放与否"的全局/分组控制。

**📦 注解属性**

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `value` | `String` | （必填） | 功能标识 |
| `description` | `String` | `""` | 功能描述 |
| `group` | `String` | `"default"` | 功能分组 |
| `defaultEnabled` | `boolean` | `true` | 默认是否开放（实验性功能建议 `false`） |
| `disabledMessage` | `String` | `"此功能暂未开放"` | 未开放提示文案 |
| `disabledStatus` | `int` | `503` | 未开放响应状态码 |

**🧩 使用示例**：

```java
// 实验性功能：默认关闭，开关开启后放行
@GetMapping("/beta")
@ApiFeature(value = "beta-feature", defaultEnabled = false)
public Result<String> beta() { return Result.ok("beta-ok"); }
```

**📖 怎么用**：`ApiFeatureConfiguration` 在单例创建完成后扫描全部 `@ApiFeature` 注册到 `ApiFeatureManager`（方法级优先、类级兜底，同标识后注册覆盖前者）；拦截器按开关状态放行/拒绝。开关状态经运行时 API 或属性动态调整。
