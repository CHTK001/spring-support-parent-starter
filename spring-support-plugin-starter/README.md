# Spring Support Plugin Starter

这是一个 Spring Boot 插件模块，提供 SQLite 数据库支持和全面的限流功能。

## 功能特性

### 1. SQLite 数据库支持

- 自动配置 SQLite 数据源
- JPA 实体管理
- 事务支持
- 连接池配置

### 2. 多维度限流功能

- **IP 限流**: 基于客户端 IP 地址的访问限制
- **API 限流**: 基于 API 路径的访问限制
- **QPS 限流**: 全局每秒请求数限制
- **黑名单**: 阻止特定 IP 或 API 的访问
- **白名单**: 允许特定 IP 或 API 绕过限流
- 基于注解的限流配置
- 多种限流算法（令牌桶、漏桶、固定窗口、滑动窗口）
- 内存缓存管理
- SQLite 持久化存储
- 实时配置更新

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-plugin-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置文件

```yaml
plugin:
  enabled: true
  sqlite:
    enabled: true
    database-path: plugin.db
  rate-limit:
    enabled: true
    storage-type: MEMORY
    default-rule:
      permits-per-second: 100
      burst-capacity: 200
      algorithm: TOKEN_BUCKET
```

### 3. 使用限流注解

```java
@RestController
@RequestMapping("/api")
@RateLimit(qps = 1000, description = "API整体限制")
public class MyController {

    @GetMapping("/data")
    @RateLimit(qps = 10, description = "获取数据接口限制")
    public String getData() {
        return "data";
    }

    @PostMapping("/create")
    @RateLimit(
        qps = 20,
        limitIp = true,
        ipQps = 5,
        description = "创建接口限制，同时限制IP"
    )
    public String createData(@RequestBody String data) {
        return "created";
    }
}
```

## 注解参数说明

### @RateLimit 注解参数

| 参数              | 类型             | 默认值                     | 说明                       |
| ----------------- | ---------------- | -------------------------- | -------------------------- |
| limitType         | LimitType        | API                        | 限流类型：IP 或 API        |
| key               | String           | ""                         | 限流键，为空时使用方法路径 |
| qps               | int              | 100                        | 每秒允许的请求数           |
| burstCapacity     | int              | 200                        | 突发容量                   |
| algorithm         | AlgorithmType    | TOKEN_BUCKET               | 限流算法类型               |
| overflowStrategy  | OverflowStrategy | REJECT                     | 超出限制时的处理策略       |
| windowSizeSeconds | int              | 1                          | 时间窗口大小（秒）         |
| enabled           | boolean          | true                       | 是否启用                   |
| description       | String           | ""                         | 配置描述                   |
| limitIp           | boolean          | false                      | 是否同时限制 IP            |
| ipQps             | int              | 1000                       | IP 限流的 QPS              |
| message           | String           | "请求过于频繁，请稍后再试" | 错误消息                   |
| errorCode         | int              | 429                        | 错误代码                   |

## API 接口

模块提供了管理限流配置的 REST API：

### 获取所有配置

```
GET /api/rate-limit/configs
```

### 获取启用的配置

```
GET /api/rate-limit/configs/enabled
```

### 更新 QPS

```
PUT /api/rate-limit/configs/{limitType}/{limitKey}/qps?qps=100
```

### 启用/禁用配置

```
PUT /api/rate-limit/configs/{limitType}/{limitKey}/enabled?enabled=true
```

### 重新加载缓存

```
POST /api/rate-limit/reload-cache
```

### 获取缓存统计

```
GET /api/rate-limit/cache-stats
```

## 工作原理

### 1. 启动流程

1. 应用启动时，`RateLimitAnnotationProcessor`扫描所有带有`@RateLimit`注解的类和方法
2. 将注解配置解析并保存到 SQLite 数据库
3. `RateLimitConfigService`加载所有启用的配置到内存缓存
4. `RateLimitCacheManager`创建对应的限流器实例

### 2. 请求处理流程

1. 请求进入`RateLimitInterceptor`拦截器
2. 根据请求路径和客户端 IP 查找对应的限流配置
3. 使用`RateLimitCacheManager`检查是否超出限流
4. 如果超出限制，返回 429 错误；否则继续处理请求

### 3. 配置更新流程

1. 通过 API 接口更新配置
2. 配置同时保存到 SQLite 数据库和内存缓存
3. 内存中的限流器实例实时更新

## 数据库表结构

### rate_limit_config 表

存储限流配置信息：

- id: 主键
- limit_type: 限流类型（IP/API）
- limit_key: 限流键
- qps: 每秒请求数
- burst_capacity: 突发容量
- algorithm_type: 算法类型
- enabled: 是否启用
- created_time/updated_time: 创建/更新时间

### rate_limit_record 表

存储限流记录（可选）：

- id: 主键
- limit_key: 限流键
- request_count: 请求计数
- window_start/window_end: 时间窗口
- is_limited: 是否被限流

## 注意事项

1. SQLite 数据库文件默认创建在应用根目录下
2. 内存缓存在应用重启后会重新加载
3. 限流配置支持热更新，无需重启应用
4. 建议在生产环境中定期清理过期的限流记录

## 扩展开发

如需扩展功能，可以：

1. 实现自定义限流算法
2. 添加更多存储后端（Redis 等）
3. 扩展限流维度（用户、角色等）
4. 添加监控和告警功能
