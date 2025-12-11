# spring-support-discovery-starter

## 📖 模块简介

**服务发现模块** - 提供服务注册与发现功能，支持多种服务发现协议，实现微服务架构中的服务治理。

## ✨ 核心功能

### 🔍 服务发现

- ✅ 服务自动注册
- ✅ 服务健康检查
- ✅ 服务实例管理
- ✅ 服务负载均衡

### 🌐 多协议支持

- ✅ Consul
- ✅ Eureka
- ✅ Nacos
- ✅ Zookeeper

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-discovery-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置开关

**配置文件**：`application.yml`

```yaml
plugin:
  discovery:
    # 是否启用服务发现
    # 默认: false
    # 说明: 设置为true时才会启用服务发现功能
    enable: true

    # 服务发现类型（consul/eureka/nacos/zookeeper）
    type: consul

    # 服务发现服务器地址
    server-url: http://localhost:8500

    # 服务名称
    service-name: ${spring.application.name}

    # 服务端口
    service-port: ${server.port}
```

### 3. 服务注册

服务启动后会自动注册到服务发现中心，无需额外代码。

### 4. 服务调用

```java
@Autowired
private DiscoveryClient discoveryClient;

public List<ServiceInstance> getInstances(String serviceName) {
    return discoveryClient.getInstances(serviceName);
}
```

## ⚙️ 配置说明

### Consul 配置

```yaml
plugin:
  discovery:
    enable: true
    type: consul
    server-url: http://localhost:8500

    # Consul 特定配置
    consul:
      # 健康检查间隔（秒）
      health-check-interval: 10
      # 健康检查超时（秒）
      health-check-timeout: 5
      # 服务标签
      tags:
        - version=1.0.0
        - env=dev
```

### Nacos 配置

```yaml
plugin:
  discovery:
    enable: true
    type: nacos
    server-url: http://localhost:8848

    # Nacos 特定配置
    nacos:
      # 命名空间
      namespace: public
      # 分组
      group: DEFAULT_GROUP
      # 集群名称
      cluster-name: DEFAULT
```

## 💡 使用示例

### 服务调用

```java
@Service
public class UserService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DiscoveryClient discoveryClient;

    public String callUserService() {
        // 获取服务实例
        List<ServiceInstance> instances =
            discoveryClient.getInstances("user-service");

        if (instances.isEmpty()) {
            throw new ServiceException("服务不可用");
        }

        // 选择第一个实例
        ServiceInstance instance = instances.get(0);
        String url = instance.getUri() + "/api/user/list";

        // 调用服务
        return restTemplate.getForObject(url, String.class);
    }
}
```

### 负载均衡

```java
@Configuration
public class LoadBalancerConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

// 使用服务名调用
@Service
public class OrderService {

    @Autowired
    private RestTemplate restTemplate;

    public String callUserService() {
        // 直接使用服务名，自动负载均衡
        return restTemplate.getForObject(
            "http://user-service/api/user/list",
            String.class
        );
    }
}
```

## 🎯 设计原则

### 1. 高可用

- ✅ 服务健康检查
- ✅ 故障自动剔除
- ✅ 服务自动恢复

### 2. 负载均衡

- ✅ 轮询策略
- ✅ 随机策略
- ✅ 权重策略

### 3. 易于集成

- ✅ 自动配置
- ✅ 最小化配置
- ✅ 多协议支持

## 🔗 相关模块

- [spring-support-common-starter](../spring-support-common-starter) - 公共基础模块
- [spring-support-rpc-starter](../spring-support-rpc-starter) - RPC 远程调用模块

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
