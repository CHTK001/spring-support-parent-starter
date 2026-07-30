# spring-support-lock-starter

基于 utils-support 的锁与幂等能力适配 Spring Boot AOP 自动配置模块

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-lock-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 功能概览

| 类/接口 | 说明 |
|---------|------|
| `Idempotent` | 幂等注解。 |
| `IdempotentAspect` | 幂等切面。 |
| `LockedAspect` | 锁切面，复用 utils-support 的锁能力并补齐 Spring AOP 适配。 |
| `StrategyDistributedLockAspect` | 兼容 strategy 模块旧版 注解。 |
| `StrategyIdempotentAspect` | 兼容 strategy 模块旧版 注解。 |
| `LockAutoConfiguration` | 锁模块自动配置。 |
| `LockRedisAutoConfiguration` | Redis 幂等适配自动配置。 |
| `IdempotentException` | 幂等异常。 |
| `LockAcquireException` | 锁获取异常。 |
| `LockProperties` | 锁模块配置。 |
| ... | 共 19 个类 |

---

## 配置说明

本模块为零配置模块，引入依赖后即可使用。

---

## 依赖关系

```
spring-support-lock-starter
├── utils-support-common-starter
├── spring-support-strategy-starter
```