# spring-support-gateway-starter

网关客户端自动注册模块 - Spring Boot 应用引入后自动注册到网关

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-support-gateway-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 功能概览

| 类/接口 | 说明 |
|---------|------|
| `GatewayClientAgent` | 网关客户端 Agent 连接到网关并注册为 HTTP 服务，使网关可以转发 HTTP 请求到本应用 |
| `GatewayClientAutoConfiguration` | GatewayClientAutoConfiguration |
| `GatewayClientProperties` | 是否启用网关客户端自动注册 |

---

## 配置说明

本模块为零配置模块，引入依赖后即可使用。

---

## 依赖关系

```
spring-support-gateway-starter
├── utils-support-remote-starter
```