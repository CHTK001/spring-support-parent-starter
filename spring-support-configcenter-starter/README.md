# spring-support-configcenter-starter

## 📖 模块简介

**配置中心模块** - 提供分布式配置管理功能，支持动态配置刷新、配置版本管理、多环境配置等特性。

## ✨ 核心功能

### 🔧 配置管理

- ✅ 集中式配置管理
- ✅ 多环境配置支持
- ✅ 配置动态刷新
- ✅ 配置版本管理
- ✅ 配置加密存储

### 🔄 配置同步

- ✅ 配置变更实时推送
- ✅ 配置缓存机制
- ✅ 配置回滚支持

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-configcenter-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置开关

**配置文件**：`application.yml`

```yaml
plugin:
  config-center:
    # 是否启用配置中心
    # 默认: false
    # 说明: 设置为true时才会启用配置中心功能
    enable: true

    # 配置中心地址
    server-url: http://localhost:8888

    # 应用名称
    application: ${spring.application.name}

    # 环境
    profile: ${spring.profiles.active}

    # 命名空间
    namespace: default
```

### 3. 使用配置

```java
@Value("${custom.config.key}")
private String configValue;

// 或使用 @ConfigurationProperties
@ConfigurationProperties(prefix = "custom.config")
@Data
public class CustomConfig {
    private String key;
}
```

## ⚙️ 配置说明

### 完整配置示例

```yaml
plugin:
  config-center:
    # 功能开关
    enable: true

    # 配置中心服务地址
    server-url: http://config-server:8888

    # 应用名称（用于区分不同应用的配置）
    application: my-app

    # 环境（dev/test/prod）
    profile: dev

    # 命名空间（用于配置隔离）
    namespace: default

    # 配置刷新间隔（秒）
    refresh-interval: 60

    # 是否启用配置加密
    enable-encryption: true

    # 加密密钥
    encryption-key: your-secret-key
```

## 💡 使用示例

### 动态刷新配置

```java
@RefreshScope
@RestController
public class ConfigController {

    @Value("${custom.message}")
    private String message;

    @GetMapping("/message")
    public String getMessage() {
        return message;  // 配置变更后自动刷新
    }
}
```

### 监听配置变更

```java
@Component
public class ConfigChangeListener {

    @EventListener
    public void onConfigChange(ConfigChangeEvent event) {
        log.info("配置变更: {} -> {}",
            event.getOldValue(),
            event.getNewValue());
    }
}
```

## 🎯 设计原则

### 1. 配置隔离

- ✅ 按应用隔离配置
- ✅ 按环境隔离配置
- ✅ 按命名空间隔离配置

### 2. 高可用

- ✅ 本地配置缓存
- ✅ 配置中心故障降级
- ✅ 配置变更通知机制

### 3. 安全性

- ✅ 配置加密存储
- ✅ 访问权限控制
- ✅ 配置变更审计

## 🔗 相关模块

- [spring-support-common-starter](../spring-support-common-starter) - 公共基础模块
- [spring-support-redis-starter](../spring-support-redis-starter) - Redis 缓存模块

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
