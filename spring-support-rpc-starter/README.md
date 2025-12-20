# spring-support-rpc-starter

## 📖 模块简介

**RPC 远程调用模块** - 提供高性能的 RPC 远程调用功能，支持多种序列化协议和传输协议。

## ✨ 核心功能

### 🚀 RPC 调用

- ✅ 同步调用
- ✅ 异步调用

### 🔧 协议支持

- ✅ Dubbo 协议
- ✅ SOFA 协议
- ✅ HTTP 协议
- ✅ JSON-RPC 协议
- ✅ Armeria 协议

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-rpc-starter</artifactId>
    <version>4.0.0.34</version>
</dependency>
```

### 2. 配置开关

```yaml
plugin:
  rpc:
    # 是否启用RPC功能
    # 默认: false
    enable: true

    # RPC 类型 (DUBBO, SOFA, HTTP, JSON, ARMERIA)
    type: DUBBO

    # 注册中心配置
    registry:
      - address: zookeeper://localhost:2181

    # 协议配置
    protocols:
      - name: dubbo
        host: 0.0.0.0
        port: 20880
```

### 3. 提供服务

```java
@RpcService
public class UserServiceImpl implements UserService {

    @Override
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }
}
```

### 4. 调用服务

```java
@Service
public class OrderService {

    @RpcResource
    private UserService userService;

    public void createOrder(Long userId) {
        User user = userService.getUserById(userId);
        // 创建订单逻辑
    }
}
```

## ⚙️ 配置说明

```yaml
plugin:
  rpc:
    # 是否启用
    enable: true
    
    # RPC类型: DUBBO, SOFA, HTTP, JSON, ARMERIA
    type: DUBBO
    
    # 应用名称
    application-name: ${spring.application.name:app}
    
    # 注册中心配置(支持多个)
    registry:
      - address: zookeeper://localhost:2181
        timeout: 3000
        check: false
    
    # 协议配置(支持多个)
    protocols:
      - name: dubbo
        host: 0.0.0.0
        port: 20880
    
    # 消费者配置
    consumer:
      timeout: 3000
      retries: 2
      load-balance: random
      check: false
```

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.34  
**更新时间**: 2024/12/18
