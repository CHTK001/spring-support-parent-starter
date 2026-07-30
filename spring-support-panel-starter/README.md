# spring-support-panel-starter

统一客户端面板模块 - 提供连接管理、连接缓存与 JDBC 面板能力骨架

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-support-panel-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 功能概览

| 类/接口 | 说明 |
|---------|------|
| `InMemoryPanelConnectionCache` | 内存连接缓存。 |
| `PanelConnectionCache` | 连接缓存。 |
| `PanelSshSessionRegistry` | SSH Session 注册表，供 Nginx / Project 等多个 Service 共享。 |
| `PanelAutoConfiguration` | 面板模块自动配置。 |
| `PanelProperties` | 面板模块配置。 |
| `PanelDatasourceController` | Panel 数据源控制器。 |
| `PanelDockerController` | Docker 面板控制器。 |
| `PanelInfluxController` | InfluxDB 2.x 面板控制器。 |
| `PanelInfluxV3Controller` | InfluxDB 3.x 面板控制器。 路由：/v1/panel/influxv3/{connectionId}/ |
| `PanelJdbcController` | JDBC 面板控制器。 |
| ... | 共 142 个类 |

---

## 配置说明

本模块为零配置模块，引入依赖后即可使用。

---

## 依赖关系

```
spring-support-panel-starter
├── spring-support-common-starter
├── spring-support-datasource-starter
├── utils-support-common-starter
├── spring-support-ai-starter
├── spring-support-mybatis-starter
├── spring-support-oauth-client-starter
├── spring-support-socket-io-starter
├── utils-support-zookeeper-starter
├── spring-support-job-starter
├── spring-support-sync-data-starter
├── utils-support-ssh-starter
├── utils-support-docker-starter
├── utils-support-mysql-starter
```