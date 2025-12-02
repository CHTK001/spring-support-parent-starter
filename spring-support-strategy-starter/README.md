# Spring Support Strategy Starter

策略管理模块 - 提供限流、熔断、降级等策略的统一配置管理和 API 接口。

## 功能特性

- 🚦 **限流策略** - API 接口访问频率控制，支持多维度限流
- 🔥 **熔断策略** - 防止级联故障，快速失败机制
- 📉 **降级策略** - 服务降级配置，支持降级方法和固定返回值
- 🔄 **重试策略** - 失败重试配置（开发中）
- 📊 **策略记录** - 记录策略触发日志

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-strategy-starter</artifactId>
    <version>4.0.0.34</version>
</dependency>
```

### 2. 执行数据库脚本

执行 `src/main/resources/db/strategy-schema.sql` 初始化数据库表。

### 3. API 接口

#### 限流配置管理

| 接口                         | 方法   | 说明                        |
| ---------------------------- | ------ | --------------------------- |
| `/v2/strategy/limit/page`    | GET    | 分页查询限流配置            |
| `/v2/strategy/limit/list`    | GET    | 查询所有限流配置            |
| `/v2/strategy/limit/enabled` | GET    | 查询启用的限流配置          |
| `/v2/strategy/limit/{id}`    | GET    | 根据 ID 查询限流配置        |
| `/v2/strategy/limit/save`    | POST   | 保存限流配置                |
| `/v2/strategy/limit/update`  | PUT    | 更新限流配置                |
| `/v2/strategy/limit/delete`  | DELETE | 删除限流配置                |
| `/v2/strategy/limit/refresh` | POST   | 刷新限流配置到 Resilience4j |

#### 熔断配置管理

| 接口                                   | 方法   | 说明                        |
| -------------------------------------- | ------ | --------------------------- |
| `/v2/strategy/circuit-breaker/page`    | GET    | 分页查询熔断配置            |
| `/v2/strategy/circuit-breaker/list`    | GET    | 查询所有熔断配置            |
| `/v2/strategy/circuit-breaker/enabled` | GET    | 查询启用的熔断配置          |
| `/v2/strategy/circuit-breaker/{id}`    | GET    | 根据 ID 查询熔断配置        |
| `/v2/strategy/circuit-breaker/save`    | POST   | 保存熔断配置                |
| `/v2/strategy/circuit-breaker/update`  | PUT    | 更新熔断配置                |
| `/v2/strategy/circuit-breaker/delete`  | DELETE | 删除熔断配置                |
| `/v2/strategy/circuit-breaker/refresh` | POST   | 刷新熔断配置到 Resilience4j |

#### 熔断记录管理

| 接口                                         | 方法   | 说明                   |
| -------------------------------------------- | ------ | ---------------------- |
| `/v2/strategy/circuit-breaker-record/page`   | GET    | 分页查询熔断记录       |
| `/v2/strategy/circuit-breaker-record/{id}`   | GET    | 根据 ID 查询熔断记录   |
| `/v2/strategy/circuit-breaker-record/delete` | DELETE | 删除熔断记录           |
| `/v2/strategy/circuit-breaker-record/clean`  | DELETE | 清理指定天数之前的记录 |

## 限流维度说明

| 维度   | 说明                       |
| ------ | -------------------------- |
| GLOBAL | 全局限流，所有请求共享配额 |
| IP     | 按客户端 IP 限流           |
| USER   | 按用户 ID 限流             |
| API    | 按接口路径限流             |

## 配置示例

```json
{
  "sysLimitPath": "/api/user/**",
  "sysLimitName": "用户接口限流",
  "sysLimitForPeriod": 100,
  "sysLimitRefreshPeriodSeconds": 1,
  "sysLimitTimeoutDurationMillis": 500,
  "sysLimitDimension": "IP",
  "sysLimitMessage": "请求过于频繁，请稍后再试",
  "sysLimitStatus": 1,
  "sysLimitSort": 10
}
```

## 模块结构

```
spring-support-strategy-starter/
├── src/main/java/com/chua/starter/strategy/
│   ├── config/           # 自动配置
│   ├── controller/       # API 控制器
│   ├── entity/           # 实体类
│   ├── mapper/           # MyBatis Mapper
│   └── service/          # 业务服务
├── src/main/resources/
│   ├── db/               # 数据库脚本
│   └── META-INF/         # Spring Boot 自动配置
└── pom.xml
```

## 熔断配置示例

```json
{
  "sysCircuitBreakerName": "用户服务熔断器",
  "sysCircuitBreakerPath": "/api/user/**",
  "failureRateThreshold": 50,
  "slowCallRateThreshold": 100,
  "slowCallDurationThresholdMs": 60000,
  "minimumNumberOfCalls": 10,
  "slidingWindowSize": 10,
  "slidingWindowType": "COUNT_BASED",
  "waitDurationInOpenStateMs": 60000,
  "permittedCallsInHalfOpenState": 3,
  "fallbackMethod": "userFallback",
  "sysCircuitBreakerStatus": 1,
  "sysCircuitBreakerSort": 10
}
```

## 版本历史

### v1.0.0 (2025-12-02)

- 初始版本
- 支持限流配置管理
- 支持限流记录日志
- 支持熔断配置管理
- 支持熔断记录日志

## 作者

- CH

## 许可证

ISC
