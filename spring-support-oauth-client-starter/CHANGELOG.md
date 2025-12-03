# 更新日志

## [4.0.0.34] - 2024-12-02

### ✨ 新增功能

#### Principal 支持

- **完整实现 java.security.Principal 接口** - 符合 Java EE/Jakarta EE 标准
- **OAuthPrincipal 类** - 扩展 Principal 接口，提供 OAuth 用户信息
- **用户信息集成** - 包含用户 ID、用户名、真实姓名、角色、权限等
- **角色权限管理** - 支持角色和权限的检查和验证

#### HttpServletRequest 增强

- **isUserInRole() 实现** - 标准的角色检查方法
- **getAuthType() 实现** - 返回 OAuth 认证类型
- **getUserPrincipal() 实现** - 获取 OAuthPrincipal 对象
- **logout() 实现** - 清除 Session 中的用户信息
- **login() 实现** - OAuth 登录流程（抛出异常，不支持用户名密码登录）

#### 扩展方法

- **isUserInAnyRole()** - 检查用户是否拥有任意一个角色
- **hasPermission()** - 检查用户是否拥有特定权限
- **hasAnyPermission()** - 检查用户是否拥有任意一个权限
- **getUserId()** - 获取用户 ID
- **getTenantId()** - 获取租户 ID
- **getDeptId()** - 获取部门 ID
- **isAdmin()** - 检查是否为管理员
- **isAuthenticated()** - 检查是否已认证

### 🔧 核心组件

#### 配置类

- **AuthClientConfiguration** - OAuth 客户端自动配置
- **AuthClientEnvironmentPostProcessor** - 环境后处理器
- **TokenRequestHandlerMethodArgumentResolver** - Token 参数解析器
- **UserRequestHandlerMethodArgumentResolver** - User 参数解析器

#### 服务类

- **OauthAuthService** - OAuth 认证服务实现
- **UserStatisticProvider** - 用户统计提供者

#### 拦截器

- **PermissionInterceptor** - 权限拦截器
- **TokenForTypeInterceptor** - Token 类型拦截器

#### 过滤器

- **AuthFilter** - OAuth 认证过滤器
- **OAuthHttpServletRequestWrapper** - HttpServletRequest 包装器

### 📦 依赖管理

#### 核心依赖

- `spring-support-common-starter` - 通用支持模块
- `spring-support-swagger-starter` - Swagger 文档支持
- `spring-support-mybatis-starter` - MyBatis 支持
- `unirest-java:3.14.1` - HTTP 客户端
- `JustAuth:1.16.6` - 第三方登录支持

#### 协议支持

- `utils-support-armeria-starter:4.0.0.33` - Armeria 协议支持
- `utils-support-rsocket-starter:4.0.0.33` - RSocket 协议支持

### ⚙️ 配置支持

#### 基础配置

```yaml
plugin:
  oauth:
    address: "http://oauth-server:8080"
    protocol: "http"
    block-address: ["/*"]
    exclude-address: ["/login", "/public/**"]
    cache-timeout: 3600
    cache-hot-cold-backup: true
    encryption: "SM4"
```

#### 高级配置

- **路径拦截配置** - 支持 Ant 风格路径匹配
- **缓存配置** - 用户信息缓存和热冷备份
- **加密配置** - 支持多种加密算法
- **会话配置** - 会话超时和管理
- **权限配置** - 默认角色和权限设置

### 🔒 安全特性

#### 认证机制

- **OAuth 令牌验证** - 自动验证和刷新令牌
- **会话管理** - 基于 Session 的用户信息存储
- **防重放攻击** - 令牌时间戳验证
- **签名验证** - 请求签名校验

#### 权限控制

- **注解式权限** - @RequiredRole、@RequiredPermission
- **方法级权限** - 支持方法级别的权限控制
- **角色继承** - 支持角色层级关系
- **动态权限** - 运行时权限检查

### 📝 注解支持

#### 认证注解

- **@TokenForIgnore** - 忽略 Token 验证
- **@TokenForType** - 指定 Token 类型
- **@LoginType** - 指定登录类型
- **@Extension** - 扩展配置

#### 参数注解

- **@TokenValue** - 注入 Token 值
- **@UserValue** - 注入用户信息

### 🔄 兼容性

#### Java EE 标准

- **完全兼容 java.security.Principal** - 标准接口实现
- **完全兼容 HttpServletRequest** - 标准方法实现
- **完全兼容 Jakarta EE** - 支持新版本规范

#### Spring 集成

- **Spring Boot 自动配置** - 零配置启动
- **Spring MVC 集成** - 无缝集成 Spring MVC
- **Spring Security 兼容** - 可与 Spring Security 共存

### 🚀 性能优化

#### 缓存机制

- **用户信息缓存** - 减少远程调用
- **令牌缓存** - 避免重复验证
- **热冷备份** - 提高缓存命中率

#### 连接管理

- **连接池** - HTTP 连接池管理
- **长连接** - 支持 Keep-Alive
- **超时控制** - 可配置的超时时间

### 📚 文档完善

#### 使用文档

- **完整的 README.md** - 详细的使用说明
- **配置示例** - 多种场景的配置示例
- **代码示例** - 丰富的代码示例
- **最佳实践** - 推荐的使用方式

#### API 文档

- **JavaDoc 注释** - 完整的 API 文档
- **中文注释** - 符合项目规范
- **示例代码** - 每个 API 都有示例

### 🐛 已知问题

#### 当前限制

- **不支持用户名密码登录** - login() 方法会抛出异常
- **Session 依赖** - 必须在 Web 环境中使用
- **单服务器部署** - Session 不支持分布式

#### 后续计划

- **分布式 Session** - 支持 Redis Session
- **JWT 支持** - 支持无状态认证
- **多租户增强** - 更完善的多租户支持

---

## [4.0.0.32] - 2024-11-XX

### 🔧 优化改进

- 优化认证性能
- 完善令牌管理机制
- 增强缓存策略

### 🐛 问题修复

- 修复并发认证问题
- 修复令牌过期处理问题
- 修复权限检查逻辑

---

## [4.0.0.30] - 2024-10-XX

### 🚀 新增功能

- 初始版本发布
- 基础 OAuth 认证功能
- 多协议支持

---

## 未来计划

### 计划中的功能

- [ ] 分布式 Session 支持
- [ ] JWT 无状态认证
- [ ] OAuth 2.1 协议支持
- [ ] OIDC（OpenID Connect）支持
- [ ] 多因素认证（MFA）
- [ ] 生物识别认证集成

### 性能优化

- [ ] 异步认证处理
- [ ] 批量权限检查
- [ ] 缓存预热机制
- [ ] 连接池优化

### 文档完善

- [ ] 视频教程
- [ ] 迁移指南
- [ ] 故障排除手册
- [ ] 性能调优指南

---

**维护者**: CH  
**项目地址**: spring-support-oauth-client-starter  
**反馈渠道**: 请通过 Issue 提交问题和建议
