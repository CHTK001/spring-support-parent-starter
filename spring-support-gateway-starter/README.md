# Spring Support Gateway Starter

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 📖 模块简介

Spring Support Gateway Starter 是基于 Spring Cloud Gateway 的API网关模块，提供路由、负载均衡、限流、熔断等功能，是微服务架构的统一入口。

### ✨ 主要特性

- 🌐 **动态路由** - 支持动态路由配置和刷新
- ⚖️ **负载均衡** - 支持多种负载均衡策略
- 🚦 **限流熔断** - 集成限流和熔断功能
- 🔐 **统一认证** - 集中式认证和授权
- 📊 **监控日志** - 请求日志和性能监控
- 🔧 **过滤器链** - 灵活的过滤器扩展机制

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-gateway-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>
```

## ⚙️ 配置说明

### 基础配置

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `plugin.gateway.enable` | Boolean | false | 是否启用网关 |
| `plugin.gateway.rate-limit.enabled` | Boolean | false | 是否启用限流 |
| `plugin.gateway.rate-limit.default-replenish-rate` | Integer | 10 | 默认令牌生成速率 |
| `plugin.gateway.rate-limit.default-burst-capacity` | Integer | 20 | 默认突发容量 |

### 配置示例

```yaml
plugin:
  gateway:
    enable: true
    
    # 路由配置
    routes:
      - id: user-service
        uri: lb://user-service
        predicates:
          - Path=/api/users/**
        filters:
          - StripPrefix=2
      
      - id: order-service
        uri: lb://order-service
        predicates:
          - Path=/api/orders/**
        filters:
          - StripPrefix=2
    
    # 限流配置
    rate-limit:
      enabled: true
      default-replenish-rate: 10
      default-burst-capacity: 20
```

## 📝 使用示例

### 自定义路由配置

```java
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("user_route", r -> r
                .path("/api/users/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .addRequestHeader("X-Gateway", "true")
                    .retry(config -> config
                        .setRetries(3)
                        .setStatuses(HttpStatus.BAD_GATEWAY)))
                .uri("lb://user-service"))
            
            .route("order_route", r -> r
                .path("/api/orders/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .circuitBreaker(config -> config
                        .setName("orderCircuitBreaker")
                        .setFallbackUri("forward:/fallback")))
                .uri("lb://order-service"))
            
            .build();
    }
}
```

### 自定义过滤器

```java
@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 请求前处理
        log.info("请求路径: {}", exchange.getRequest().getPath());
        
        // 添加自定义响应头
        exchange.getResponse().getHeaders().add("X-Custom-Header", "CustomValue");
        
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return -1; // 优先级
    }
}
```

## 🔗 相关链接

- [返回主文档](../README.md)
- [Spring Cloud Gateway文档](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
- [配置示例文件](../application-example.yml)

## 📄 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。
