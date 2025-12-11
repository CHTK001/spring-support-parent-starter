# spring-support-rpc-starter

## 📖 模块简介

**RPC 远程调用模块** - 提供高性能的 RPC 远程调用功能，支持多种序列化协议和传输协议。

## ✨ 核心功能

### 🚀 RPC 调用

- ✅ 同步调用
- ✅ 异步调用
- ✅ 泛化调用
- ✅ 服务降级

### 🔧 协议支持

- ✅ Dubbo 协议
- ✅ gRPC 协议
- ✅ HTTP 协议
- ✅ 自定义协议

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-rpc-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置开关

```yaml
plugin:
  rpc:
    # 是否启用RPC功能
    # 默认: false
    enable: true

    # RPC 协议
    protocol: dubbo

    # 注册中心地址
    registry-address: zookeeper://localhost:2181

    # 服务端口
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

    @RpcReference
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
    enable: true
    protocol: dubbo
    registry-address: zookeeper://localhost:2181
    port: 20880

    # 超时配置
    timeout: 3000

    # 重试次数
    retries: 2

    # 负载均衡策略
    loadbalance: random

    # 序列化方式
    serialization: hessian2
```

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
