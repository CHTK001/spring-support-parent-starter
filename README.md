# Spring Support Parent Starter

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 📖 项目简介

Spring Support Parent Starter 是一个基于 Spring Boot 3.x 的企业级应用支持框架，提供了丰富的功能模块和组件，帮助开发者快速构建企业级应用。框架采用模块化设计，按需引入，涵盖数据库集成、缓存、消息队列、文件存储、认证授权、监控、第三方服务集成等多个方面。

### ✨ 主要特性

- 🚀 **基于最新技术栈** - Spring Boot 3.x + Java 21 LTS
- 🧩 **模块化设计** - 按需引入，避免依赖冗余
- 🏢 **企业级功能** - 提供生产环境所需的各种组件
- ⚙️ **统一配置管理** - 一致的配置风格和管理方式
- 📦 **开箱即用** - 预配置的集成方案，快速上手
- 🔧 **高度可定制** - 灵活的配置选项，满足不同需求
- 📚 **完善文档** - 详细的使用说明和示例代码

## 🏗️ 架构设计

```
Spring Support Parent Starter
├── 基础功能模块
│   ├── spring-support-common-starter          # 通用功能和工具类
│   ├── spring-support-datasource-starter      # 数据源配置和管理
│   └── spring-support-mybatis-starter         # MyBatis Plus集成
├── 认证和安全模块
│   └── spring-support-oauth-client-starter    # OAuth客户端认证
├── 缓存和存储模块
│   ├── spring-support-redis-starter           # Redis缓存集成
│   └── spring-support-minio-starter           # MinIO对象存储
├── 容错和稳定性模块
│   └── spring-support-circuit-breaker-starter # 熔断降级和限流
├── 消息和通信模块
│   ├── spring-support-email-starter           # 邮件发送服务
│   ├── spring-support-mqtt-starter            # MQTT消息队列
│   ├── spring-support-socketio-starter        # Socket.IO实时通信
│   ├── spring-support-sse-starter             # Server-Sent Events
│   ├── spring-support-rpc-starter             # RPC远程调用
│   ├── spring-support-subscribe-starter       # 订阅发布模式
│   └── spring-support-websockify-starter      # WebSocket代理
├── 搜索和数据处理模块
│   └── spring-support-elasticsearch-starter   # Elasticsearch搜索引擎
├── 服务发现和配置模块
│   ├── spring-support-discovery-starter       # 服务发现
│   └── spring-support-configcenter-starter    # 配置中心
├── 监控和日志模块
│   ├── spring-support-prometheus-starter      # Prometheus监控
│   ├── spring-support-loki-starter            # Loki日志收集
│   └── spring-support-report-client-starter   # 设备数据上报客户端
├── API文档和接口模块
│   └── spring-support-swagger-starter         # Swagger API文档
├── 数据库扩展模块
│   └── spring-support-mybatis-tenant-starter  # MyBatis多租户支持
└── 第三方服务集成模块
    ├── spring-support-tencent-starter         # 腾讯云服务集成
    ├── spring-support-pay-client-starter      # 支付客户端
    └── spring-support-guacamole-starter       # Apache Guacamole远程桌面
```

## 🚀 快速开始

### 环境要求

- **Java**: 21 或更高版本
- **Maven**: 3.6+ 或 **Gradle**: 7.0+
- **Spring Boot**: 3.4.5

### 添加依赖

在你的项目中添加需要的模块依赖：

#### Maven

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-common-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>

<!-- 根据需要添加其他模块 -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-circuit-breaker-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

#### Gradle

```
implementation 'com.chua:spring-support-common-starter:4.0.0.32'
implementation 'com.chua:spring-support-circuit-breaker-starter:4.0.0.32'
```

### 基本配置

#### 🔧 配置开关说明

**重要**：从 v4.0.0.32 版本开始，所有功能模块都添加了 `enable` 配置开关，默认值为 `false`。

**使用原则**：

- ✅ 所有功能默认不启用，避免不必要的资源占用
- ✅ 需要使用某个功能时，显式设置 `enable: true`
- ✅ 统一的配置前缀：`plugin.{模块名}.enable`

**配置示例**：

在 `application.yml` 中添加基本配置：

```yaml
# 通用功能配置
plugin:
  # API 统一响应格式
  api:
    enable: true # 默认: false，需要显式启用

  # Redis 缓存
  redis:
    server:
      enable: true # 默认: false

  # MyBatis 数据库
  mybatis:
    enable: true # 默认: false

  # OAuth 客户端
  oauth:
    client:
      enable: true # 默认: false

  # 熔断降级和限流配置
  circuit-breaker:
    enable: true
    circuit-breaker:
      failure-rate-threshold: 50.0
      minimum-number-of-calls: 10
    rate-limiter:
      limit-for-period: 100
      limit-refresh-period: 1s
      enable-management: true
```

#### 📋 所有模块配置开关列表

| 模块              | 配置前缀                          | 默认值 | 说明               |
| ----------------- | --------------------------------- | ------ | ------------------ |
| **基础功能**      |
| Actuator          | `plugin.actuator.enable`          | false  | 监控端点           |
| API               | `plugin.api.enable`               | false  | API 统一配置       |
| API CORS          | `plugin.api.cors.enable`          | false  | 跨域配置           |
| Cache             | `plugin.cache.enable`             | false  | 缓存管理           |
| Jackson           | `plugin.jackson.enable`           | false  | JSON 序列化        |
| Log               | `plugin.log.enable`               | false  | 日志配置           |
| Async             | `plugin.async.enable`             | false  | 异步线程池         |
| Captcha           | `plugin.captcha.enable`           | false  | 验证码             |
| Create Table      | `plugin.create-table.enable`      | false  | 自动建表           |
| IP                | `plugin.ip.enable`                | false  | IP 解析            |
| Message Converter | `plugin.message-converter.enable` | false  | 消息转换           |
| Notice            | `plugin.notice.enable`            | false  | 通知               |
| Optional          | `plugin.optional.enable`          | false  | 可选配置           |
| **数据库**        |
| MyBatis           | `plugin.mybatis.enable`           | false  | MyBatis Plus       |
| MyBatis Tenant    | `plugin.tenant.enable`            | false  | 多租户             |
| DataSource Script | `plugin.datasource.script.enable` | false  | 数据源脚本         |
| Multi DataSource  | `plugin.datasource.multi.enable`  | false  | 多数据源           |
| Transaction       | `plugin.transaction.enable`       | false  | 事务管理           |
| **缓存存储**      |
| Redis             | `plugin.redis.server.enable`      | false  | Redis 服务         |
| Minio             | `plugin.minio.enable`             | false  | 对象存储           |
| Elasticsearch     | `plugin.elasticsearch.enable`     | false  | 搜索引擎           |
| **认证授权**      |
| OAuth Client      | `plugin.oauth.client.enable`      | false  | OAuth 客户端       |
| **消息通信**      |
| Email             | `plugin.email.enable`             | false  | 邮件发送           |
| MQTT              | `plugin.mqtt.enable`              | false  | MQTT 消息          |
| Socket.IO         | `plugin.socketio.enable`          | false  | Socket.IO          |
| SSE               | `plugin.sse.enable`               | false  | Server-Sent Events |
| Socket            | `plugin.socket.enable`            | false  | TCP/UDP Socket     |
| RPC               | `plugin.rpc.enable`               | false  | RPC 远程调用       |
| **服务治理**      |
| Discovery         | `plugin.discovery.enable`         | false  | 服务发现           |
| Config Center     | `plugin.config-center.enable`     | false  | 配置中心           |
| Sync              | `plugin.sync.enable`              | false  | 同步服务           |
| **监控运维**      |
| Prometheus        | `plugin.prometheus.enable`        | false  | Prometheus 监控    |
| Report Client     | `plugin.report.enable`            | false  | 上报客户端         |
| Arthas Client     | `plugin.arthas.enable`            | false  | Arthas 诊断        |
| **其他服务**      |
| Pay Client        | `plugin.pay.enable`               | false  | 支付客户端         |
| Swagger           | `plugin.knife4j.enable`           | false  | API 文档           |
| Shell             | `plugin.shell.enable`             | false  | Shell 服务         |
| SSH               | `plugin.ssh.enable`               | false  | SSH 服务           |
| Tencent           | `plugin.tencent.mini-app.enable`  | false  | 腾讯云服务         |

### 简单示例

```
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    @CircuitBreakerProtection(
        circuitBreaker = "userService",
        rateLimiter = "userService",
        fallbackMethod = "getUserFallback"
    )
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    @RateLimiter(
        name = "createUser",
        limitForPeriod = 10,
        dimension = RateLimiter.Dimension.IP,
        message = "创建用户请求过于频繁"
    )
    public User createUser(@RequestBody User user) {
        return userService.save(user);
    }

    // 降级方法
    public User getUserFallback(Long id, Exception ex) {
        return User.builder()
                .id(id)
                .name("默认用户")
                .build();
    }
}
```

## 📋 模块详细说明

### 🔧 基础功能模块

#### spring-support-common-starter

**通用功能和工具类模块**

提供企业级应用开发中常用的基础功能：

- 统一响应处理和异常处理
- 参数验证和数据转换
- 缓存支持和管理
- 验证码生成和验证
- 文件存储统一接口
- 数据加密和隐私保护
- 异步任务和线程池管理
- 通用拦截器和过滤器

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-common-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

**配置示例：**

```yaml
plugin:
  parameter:
    enable: true # 启用统一响应格式
  cache:
    type: ["default", "redis"]
    redis:
      ttl: 600 # 缓存时间（秒）
```

#### spring-support-datasource-starter

**数据源配置和管理模块**

提供多数据源配置和管理功能：

- 多数据源动态切换
- 数据源连接池管理
- 数据库连接监控
- 事务管理增强

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-datasource-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

#### spring-support-mybatis-starter

**MyBatis Plus 集成模块**

基于 MyBatis Plus 的数据库操作增强：

- 自动代码生成
- 分页插件集成
- 乐观锁支持
- 逻辑删除
- 自动填充功能

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-mybatis-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 🛡️ 容错和稳定性模块

#### spring-support-circuit-breaker-starter

**熔断降级和增强限流模块**

基于 Resilience4j 的完整容错解决方案：

- 🔥 **熔断器** - 防止级联故障，快速失败
- 🔄 **重试机制** - 自动重试失败操作
- 🚦 **增强限流** - 多维度限流（IP、用户、API、全局）
- 🏠 **舱壁隔离** - 资源隔离，防止资源耗尽
- ⏰ **超时控制** - 防止长时间等待
- 📊 **动态管理** - Web 管理界面，实时监控和配置

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-circuit-breaker-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

**配置示例：**

```yaml
plugin:
  circuit-breaker:
    enable: true
    # 熔断器配置
    circuit-breaker:
      failure-rate-threshold: 50.0
      minimum-number-of-calls: 10
      instances:
        userService:
          failure-rate-threshold: 30.0

    # 增强限流配置
    rate-limiter:
      limit-for-period: 100
      limit-refresh-period: 1s
      enable-management: true # 启用管理页面
      rules:
        api-limit:
          name: "API限流"
          pattern: "/api/**"
          limit-for-period: 50
          dimension: API
        ip-limit:
          name: "IP限流"
          pattern: "/**"
          limit-for-period: 1000
          dimension: IP
```

**使用示例：**

```
// 使用组合注解
@CircuitBreakerProtection(
    circuitBreaker = "userService",
    rateLimiter = "userService",
    fallbackMethod = "fallback"
)
public User getUser(Long id) { ... }

// 使用专门的限流注解
@RateLimiter(
    name = "searchUsers",
    limitForPeriod = 20,
    dimension = RateLimiter.Dimension.IP,
    message = "搜索请求过于频繁"
)
public List<User> searchUsers(String keyword) { ... }
```

**管理页面：**
访问 `http://localhost:8080/actuator/rate-limiter` 查看限流管理界面

### 🗄️ 缓存和存储模块

#### spring-support-redis-starter

**Redis 缓存集成模块**

提供 Redis 缓存的完整集成方案：

- Redis 连接池配置
- 分布式锁实现
- 缓存注解增强
- 序列化配置
- 集群支持

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-redis-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

#### spring-support-minio-starter

**MinIO 对象存储模块**

MinIO 对象存储服务集成：

- 文件上传下载
- 存储桶管理
- 文件预览和分享
- 权限控制

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-minio-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 📡 消息和通信模块

#### spring-support-email-starter

**邮件发送服务模块**

企业级邮件发送解决方案：

- 多邮件服务商支持
- 模板邮件
- 附件支持
- 发送状态跟踪

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-email-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

#### spring-support-mqtt-starter

**MQTT 消息队列模块**

MQTT 协议消息队列集成：

- 发布订阅模式
- QoS 质量保证
- 连接管理
- 消息持久化

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-mqtt-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 🔍 搜索和数据处理模块

#### spring-support-elasticsearch-starter

**Elasticsearch 搜索引擎模块**

Elasticsearch 集成和搜索功能：

- 全文搜索
- 聚合分析
- 索引管理
- 查询构建器

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-elasticsearch-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 📊 监控和日志模块

#### spring-support-prometheus-starter

**Prometheus 监控模块**

应用性能监控和指标收集：

- 自定义指标
- JVM 监控
- HTTP 请求监控
- 数据库连接池监控

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-prometheus-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

#### spring-support-loki-starter

**Loki 日志收集模块**

集成 Grafana Loki 进行日志收集和分析：

- 结构化日志
- 日志聚合
- 实时日志流
- 日志查询和过滤

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-loki-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 🔐 认证和安全模块

#### spring-support-oauth-client-starter

**OAuth 客户端认证模块**

OAuth 2.0 客户端集成：

- 多 OAuth 提供商支持
- 令牌管理
- 用户信息获取
- 权限控制

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-oauth-client-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

#### spring-support-token-starter

**令牌认证模块**

基于数据库的令牌认证功能：

- 令牌生成和验证
- IP 白名单控制
- 令牌有效期管理
- 用户关联认证

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-token-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 🌐 服务发现和配置模块

#### spring-support-discovery-starter

**服务发现模块**

微服务架构中的服务发现功能：

- 服务注册与发现
- 健康检查
- 负载均衡
- 服务路由

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-discovery-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

#### spring-support-configcenter-starter

**配置中心模块**

分布式配置管理：

- 配置热更新
- 环境隔离
- 配置版本管理
- 配置加密

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-configcenter-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 📚 API 文档模块

#### spring-support-swagger-starter

**Swagger API 文档模块**

自动生成 API 文档：

- OpenAPI 3.0 支持
- 交互式文档界面
- API 测试功能
- 文档自定义

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-swagger-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

**配置示例：**

```yaml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
```

### 🏢 数据库扩展模块

#### spring-support-mybatis-tenant-starter

**MyBatis 多租户支持模块**

SaaS 应用的多租户数据隔离：

- 租户数据隔离
- 动态数据源切换
- 租户上下文管理
- 数据权限控制

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-mybatis-tenant-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 🔌 第三方服务集成模块

#### spring-support-tencent-starter

**腾讯云服务集成模块**

腾讯云服务 SDK 集成：

- 对象存储 COS
- 短信服务 SMS
- 人脸识别
- 语音识别

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-tencent-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

#### spring-support-pay-client-starter

**支付客户端模块**

多支付平台集成：

- 支付宝支付
- 微信支付
- 银联支付
- 支付回调处理

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-pay-client-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

#### spring-support-guacamole-starter

**Apache Guacamole 远程桌面模块**

远程桌面访问功能：

- VNC 协议支持
- RDP 协议支持
- SSH 协议支持
- Web 端远程访问

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-guacamole-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 📡 实时通信模块

#### spring-support-socketio-starter

**Socket.IO 实时通信模块**

WebSocket 实时通信：

- 实时消息推送
- 房间管理
- 事件处理
- 连接管理

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socketio-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

#### spring-support-sse-starter

**Server-Sent Events 模块**

服务器推送事件：

- 单向数据流
- 自动重连
- 事件类型支持
- 浏览器兼容

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-sse-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

#### spring-support-rpc-starter

**RPC 远程调用模块**

远程过程调用支持：

- 多协议支持
- 服务发现集成
- 负载均衡
- 容错处理

**Maven 依赖：**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-rpc-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

## 🔧 版本兼容性

| Spring Support | Spring Boot | Java | 说明               |
| -------------- | ----------- | ---- | ------------------ |
| 4.0.0.32       | 3.4.5       | 21+  | 当前版本，推荐使用 |
| 4.0.0.x        | 3.2.x       | 21+  | 稳定版本           |

## 📝 更新日志

### v4.0.0.32 (2024-12-20)

#### 🚀 新功能

- **增强限流功能**：从 common 模块迁移到 circuit-breaker 模块，使用 resilience4j 实现
- **多维度限流**：支持全局、IP、用户、API 四种限流维度
- **动态管理页面**：提供 Web 界面进行限流器的实时监控和管理
- **SpEL 表达式支持**：限流键支持 Spring 表达式语言

#### 🔧 改进

- **模块重构**：优化模块间依赖关系，提高可维护性
- **配置统一**：统一各模块的配置命名规范
- **文档完善**：更新所有模块的使用文档和示例

#### 🐛 修复

- 修复限流功能在高并发场景下的性能问题
- 优化熔断器状态转换逻辑

## 🤝 贡献指南

我们欢迎所有形式的贡献，包括但不限于：

1. **提交 Issue** - 报告 bug 或提出新功能建议
2. **提交 PR** - 修复 bug 或实现新功能
3. **完善文档** - 改进文档或添加示例
4. **分享经验** - 分享使用心得和最佳实践

### 开发流程

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

## 📞 联系我们

- **作者**: CH
- **邮箱**: [your-email@example.com]
- **项目地址**: [https://github.com/your-username/spring-support-parent-starter]

## 🙏 致谢

感谢以下开源项目的支持：

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Resilience4j](https://resilience4j.readme.io/)
- [MyBatis Plus](https://baomidou.com/)
- [Redis](https://redis.io/)
- [Elasticsearch](https://www.elastic.co/)

---

⭐ 如果这个项目对你有帮助，请给我们一个星标！
