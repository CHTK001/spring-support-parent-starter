# spring-support-protocol-starter

Spring Support协议管理聚合模块，统一协议管理能力并对外提供稳定依赖入口

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-protocol-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 功能概览

| 类/接口 | 说明 |
|---------|------|
| `SystemServerController` | 协议管理-系统服务控制器。 |
| `SystemServerLogController` | 协议管理-过滤器日志控制器。 |
| `SystemServerRuntimeLogController` | 协议管理-系统服务器运行时日志（生命周期日志）控制器。 |
| `SystemServerSettingController` | 协议管理-服务配置控制器。 |
| `SystemServerSettingFileStorageController` | 协议管理-文件存储配置控制器。 |
| `SystemServerSettingHttpsController` | 协议管理-HTTPS配置控制器。 |
| `SystemServerSettingItemController` | 协议管理-过滤器配置项控制器。 |
| `SystemServerSettingPreviewExtensionController` | 协议管理-预览扩展名控制器。 |
| `SystemServerSettingRateLimitController` | 协议管理-限流配置控制器。 |
| `SystemServerSettingServiceDiscoveryController` | 协议管理-服务发现配置控制器。 |
| ... | 共 70 个类 |

---

## 配置说明

本模块为零配置模块，引入依赖后即可使用。

---

## 依赖关系

```
spring-support-protocol-starter
├── spring-support-ai-starter
├── spring-support-common-starter
├── spring-support-mybatis-starter
├── utils-support-common-starter
├── utils-support-protocol-starter
├── utils-support-netty-starter
├── utils-support-ftp-starter
├── spring-support-swagger-starter
```