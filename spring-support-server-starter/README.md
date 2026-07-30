# spring-support-server-starter

基础服务器管理模块，提供服务器主数据列表、详情与维护能力

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-server-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 功能概览

| 类/接口 | 说明 |
|---------|------|
| `ServerManagementConfiguration` | ServerManagementConfiguration |
| `ServerManagementProperties` | ServerManagementProperties |
| `ServerSocketEvents` | ServerSocketEvents |
| `ServerAlertController` | ServerAlertController |
| `ServerCapabilityController` | ServerCapabilityController |
| `ServerFirewallController` | ServerFirewallController |
| `ServerHostController` | ServerHostController |
| `ServerHostFileController` | ServerHostFileController |
| `ServerLogViewController` | ServerLogViewController |
| `ServerMetricsController` | 为前端与历史验收路径提供独立的指标访问入口，避免必须经过 /server/hosts 前缀。 |
| ... | 共 169 个类 |

---

## 配置说明

本模块为零配置模块，引入依赖后即可使用。

---

## 依赖关系

```
spring-support-server-starter
├── spring-support-common-starter
├── spring-support-socket-starter
├── spring-support-job-starter
├── spring-support-ai-starter
├── spring-support-datasource-starter
├── spring-support-redis-starter
├── spring-support-mybatis-starter
├── spring-api-support-common-service-starter
├── utils-support-ssh-starter
```