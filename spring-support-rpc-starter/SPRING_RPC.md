# Spring RPC 实现文档

## 概述

基于Spring的RPC实现，通过Spring的ApplicationContext来管理服务注册和调用，支持拦截器、重试、熔断等企业级特性。

## 核心特性

1. **基于Spring容器**: 使用Spring Bean作为服务提供者和消费者
2. **SPI集成**: 通过SPI机制与utils-common集成
3. **拦截器支持**: 支持RPC调用拦截器链
4. **重试机制**: 支持灵活的重试策略
5. **熔断保护**: 支持熔断器防止故障扩散
6. **性能监控**: 支持性能指标统计

## 配置参数说明

### RpcConsumerConfig 新增参数

```yaml
plugin:
  rpc:
    enable: true
    type: spring  # 使用Spring RPC实现
    application-name: ${spring.application.name}

    # 消费者配置
    consumer:
      # 基础配置
      timeout: 3000              # 调用超时时间（毫秒）
      retries: 2                 # 重试次数
      check: false               # 启动时检查服务是否可用
      async: false               # 是否异步调用
      load-balance: round-robin  # 负载均衡策略

      # 连接配置
      connect-timeout: 5000      # 连接超时时间（毫秒）
      connections: 10            # 最大连接数

      # 熔断器配置
      circuit-breaker-enabled: true              # 是否启用熔断器
      circuit-breaker-failure-threshold: 5       # 失败阈值
      circuit-breaker-timeout: 60                # 熔断超时时间（秒）

      # 重试配置
      retry-enabled: true        # 是否启用重试
      retry-delay: 100           # 重试延迟（毫秒）

      # 序列化配置
      serialization: hessian     # 序列化方式
      compression: gzip          # 压缩方式

      # 缓存配置
      cache-enabled: false       # 是否启用缓存
      cache-size: 1000           # 缓存大小
      cache-expire: 300          # 缓存过期时间（秒）

      # 监控配置
      monitor-enabled: true      # 是否启用监控
      monitor-interval: 60       # 监控间隔（秒）

      # 高级配置
      cluster: failover          # 集群容错策略
      sticky: false              # 是否启用粘滞连接
      access-log: false          # 是否发送访问日志
      version: 1.0.0             # 服务版本
      group: default             # 服务分组
```

## 使用示例

### 1. 服务提供者

```java
// 定义服务接口
public interface UserService {
    User getUser(Long userId);
    List<User> listUsers();
}

// 实现服务
@Service
@RpcService  // 标记为RPC服务
public class UserServiceImpl implements UserService {

    @Override
    public User getUser(Long userId) {
        return userRepository.findById(userId);
    }

    @Override
    public List<User> listUsers() {
        return userRepository.findAll();
    }
}
```

### 2. 服务消费者

```java
@Service
public class OrderService {

    @RpcReference  // 注入RPC服务
    private UserService userService;

    public Order createOrder(OrderDTO dto) {
        // 调用远程服务
        User user = userService.getUser(dto.getUserId());

        // 创建订单
        Order order = new Order();
        order.setUserId(user.getId());
        order.setUserName(user.getName());

        return orderRepository.save(order);
    }
}
```

### 3. 使用拦截器

```java
@Component
public class AuthRpcInterceptor implements RpcInterceptor {

    @Override
    public boolean preHandle(Method method, Object[] args) {
        // 权限校验
        String token = SecurityContextHolder.getToken();
        if (token == null) {
            throw new SecurityException("未登录");
        }
        return true;
    }

    @Override
    public Object postHandle(Method method, Object[] args, Object result) {
        // 结果处理
        log.info("RPC调用成功: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
        return result;
    }

    @Override
    public void handleException(Method method, Object[] args, Throwable error) throws Throwable {
        // 异常处理
        log.error("RPC调用失败", error);
        throw error;
    }

    @Override
    public int getOrder() {
        return -1000; // 高优先级
    }
}
```

### 4. 使用熔断器

```java
@Service
public class PaymentService {

    @Autowired
    private RpcCircuitBreakerManager circuitBreakerManager;

    @RpcReference
    private PaymentRpcService paymentRpcService;

    public PaymentResult pay(PaymentRequest request) {
        String serviceName = "PaymentRpcService.pay";
        RpcCircuitBreaker breaker = circuitBreakerManager.getOrCreate(serviceName);

        // 检查熔断器状态
        if (!breaker.tryAcquire()) {
            throw new CircuitBreakerOpenException("支付服务熔断中");
        }

        try {
            PaymentResult result = paymentRpcService.pay(request);
            breaker.recordSuccess();
            return result;
        } catch (Exception e) {
            breaker.recordFailure();
            throw e;
        }
    }
}
```

### 5. 性能监控

```java
@Service
public class RpcMonitorService {

    @Autowired
    private MetricsRpcInterceptor metricsInterceptor;

    @Scheduled(fixedRate = 60000) // 每分钟
    public void reportMetrics() {
        String[] methods = {
            "com.example.UserService.getUser",
            "com.example.OrderService.createOrder",
            "com.example.PaymentService.pay"
        };

        for (String method : methods) {
            long callCount = metricsInterceptor.getCallCount(method);
            long successCount = metricsInterceptor.getSuccessCount(method);
            long failureCount = metricsInterceptor.getFailureCount(method);
            long avgDuration = metricsInterceptor.getAverageDuration(method);

            log.info("[RPC Metrics] {}: 调用={}, 成功={}, 失败={}, 平均耗时={}ms",
                method, callCount, successCount, failureCount, avgDuration);
        }
    }
}
```

## Spring RPC vs Dubbo RPC

| 特性 | Spring RPC | Dubbo RPC |
|------|-----------|-----------|
| 部署方式 | 单体应用内调用 | 分布式服务调用 |
| 注册中心 | 不需要 | 需要（Zookeeper等） |
| 网络通信 | 本地方法调用 | 网络通信 |
| 性能 | 高（无网络开销） | 较低（有网络开销） |
| 适用场景 | 单体应用、微服务内部 | 微服务之间 |
| 配置复杂度 | 低 | 高 |

## 使用场景

### 场景1: 单体应用模块解耦

```java
// 订单模块
@Service
@RpcService
public class OrderServiceImpl implements OrderService {
    // 订单业务逻辑
}

// 用户模块
@Service
public class UserController {

    @RpcReference
    private OrderService orderService;  // 通过RPC调用订单模块

    @GetMapping("/orders")
    public List<Order> getUserOrders() {
        return orderService.listOrders();
    }
}
```

### 场景2: 微服务内部调用

```java
// 同一个微服务内的不同层之间通过RPC调用
@Service
@RpcService
public class DataAccessService {
    // 数据访问层
}

@Service
public class BusinessService {

    @RpcReference
    private DataAccessService dataAccessService;

    // 业务逻辑层通过RPC调用数据访问层
}
```

### 场景3: 测试环境

```java
// 在测试环境中，可以使用Spring RPC替代真实的远程调用
@Configuration
@Profile("test")
public class TestRpcConfig {

    @Bean
    public RpcProperties rpcProperties() {
        RpcProperties properties = new RpcProperties();
        properties.setType(RpcType.SPRING);  // 使用Spring RPC
        return properties;
    }
}
```

## 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     Application Layer                        │
│  @RpcService  @RpcReference  Business Logic                 │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Spring RPC Layer                          │
│  SpringRpcServer  SpringRpcClient  SpringRpcInvocationHandler│
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Interceptor Chain                         │
│  AuthInterceptor → MetricsInterceptor → LoggingInterceptor  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Circuit Breaker                           │
│  Check State → Try Acquire → Record Result                  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                      Retry Policy                            │
│  Check Exception → Calculate Delay → Retry                  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  Spring ApplicationContext                   │
│  Bean Registry → Bean Lookup → Method Invocation            │
└─────────────────────────────────────────────────────────────┘
```

## 优势

1. **零配置**: 不需要配置注册中心，开箱即用
2. **高性能**: 本地方法调用，无网络开销
3. **易测试**: 可以直接使用Spring的测试框架
4. **易调试**: 可以直接断点调试
5. **低成本**: 不需要额外的基础设施

## 限制

1. **仅限单应用**: 只能在同一个Spring应用内使用
2. **无服务发现**: 不支持动态服务发现
3. **无负载均衡**: 不支持跨实例负载均衡
4. **无容错**: 不支持跨实例容错

## 总结

Spring RPC是一个轻量级的RPC实现，适用于：
- 单体应用模块解耦
- 微服务内部调用
- 测试环境
- 开发环境

对于分布式服务调用，建议使用Dubbo、gRPC等成熟的RPC框架。
