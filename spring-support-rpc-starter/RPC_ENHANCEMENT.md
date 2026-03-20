# Spring Support RPC Starter 增强文档

## 概述

spring-support-rpc-starter 是一个统一的RPC框架抽象层，支持多种RPC实现（Dubbo、SOFA、JSON-RPC等），并提供了丰富的企业级特性。

## 核心增强功能

### 1. RPC拦截器（Interceptor）

支持在RPC调用前后进行拦截处理。

**核心类:**
- `RpcInterceptor`: 拦截器接口
- `LoggingRpcInterceptor`: 日志记录拦截器
- `MetricsRpcInterceptor`: 性能监控拦截器

**使用场景:**
```java
@Component
public class AuthRpcInterceptor implements RpcInterceptor {

    @Override
    public boolean preHandle(Method method, Object[] args) {
        // 权限校验
        if (!hasPermission(method)) {
            throw new SecurityException("无权限调用");
        }
        return true;
    }

    @Override
    public Object postHandle(Method method, Object[] args, Object result) {
        // 结果处理
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

### 2. RPC重试策略（Retry Policy）

支持灵活的重试策略配置。

**核心类:**
- `RpcRetryPolicy`: 重试策略配置

**使用场景:**
```java
// 默认重试策略
RpcRetryPolicy defaultPolicy = RpcRetryPolicy.defaultPolicy();
// 最大重试3次，初始延迟100ms，最大延迟10秒

// 快速重试
RpcRetryPolicy fastRetry = RpcRetryPolicy.fastRetry();
// 最大重试5次，初始延迟50ms，最大延迟2秒

// 慢速重试
RpcRetryPolicy slowRetry = RpcRetryPolicy.slowRetry();
// 最大重试10次，初始延迟1秒，最大延迟1分钟

// 自定义重试策略
RpcRetryPolicy customPolicy = new RpcRetryPolicy();
customPolicy.setMaxRetries(5);
customPolicy.setInitialDelay(Duration.ofMillis(200));
customPolicy.setMaxDelay(Duration.ofSeconds(30));
customPolicy.setMultiplier(2.0);
customPolicy.setRetryableExceptions(new Class[]{
    SocketTimeoutException.class,
    ConnectException.class
});

// 使用重试策略
int retryCount = 0;
while (retryCount <= policy.getMaxRetries()) {
    try {
        return rpcCall();
    } catch (Exception e) {
        if (policy.isRetryableException(e) && policy.shouldRetry(retryCount)) {
            Thread.sleep(policy.calculateDelay(retryCount).toMillis());
            retryCount++;
        } else {
            throw e;
        }
    }
}
```

### 3. RPC熔断器（Circuit Breaker）

实现熔断保护机制，防止故障扩散。

**核心类:**
- `RpcCircuitBreaker`: 熔断器
- `RpcCircuitBreakerManager`: 熔断器管理器

**熔断器状态:**
- `CLOSED`: 关闭状态（正常）
- `OPEN`: 打开状态（熔断）
- `HALF_OPEN`: 半开状态（尝试恢复）

**使用场景:**
```java
@Service
public class OrderService {

    @Autowired
    private RpcCircuitBreakerManager circuitBreakerManager;

    public Order getOrder(Long orderId) {
        RpcCircuitBreaker breaker = circuitBreakerManager.getOrCreate("order-service");

        // 检查熔断器状态
        if (!breaker.tryAcquire()) {
            throw new CircuitBreakerOpenException("服务熔断中");
        }

        try {
            // RPC调用
            Order order = orderRpcService.getOrder(orderId);
            breaker.recordSuccess();
            return order;
        } catch (Exception e) {
            breaker.recordFailure();
            throw e;
        }
    }
}

// 配置熔断器
RpcCircuitBreaker breaker = new RpcCircuitBreaker("order-service");
breaker.setFailureThreshold(5);        // 失败5次后熔断
breaker.setSuccessThreshold(2);        // 成功2次后恢复
breaker.setTimeout(Duration.ofSeconds(60)); // 熔断60秒后尝试恢复
```

### 4. RPC负载均衡（Load Balancer）

支持多种负载均衡策略。

**核心类:**
- `RpcLoadBalancer`: 负载均衡接口
- `RandomLoadBalancer`: 随机策略
- `RoundRobinLoadBalancer`: 轮询策略

**使用场景:**
```java
@Service
public class UserService {

    @Autowired
    private List<UserRpcService> userRpcServices;

    @Autowired
    private RoundRobinLoadBalancer loadBalancer;

    public User getUser(Long userId) {
        // 负载均衡选择服务提供者
        UserRpcService service = loadBalancer.select(userRpcServices);
        return service.getUser(userId);
    }
}

// 自定义负载均衡策略
@Component
public class WeightedLoadBalancer implements RpcLoadBalancer {

    @Override
    public <T> T select(List<T> providers) {
        // 实现加权负载均衡
        return selectByWeight(providers);
    }

    @Override
    public String getName() {
        return "weighted";
    }
}
```

## 完整使用示例

### 场景1: RPC调用 + 重试 + 熔断

```java
@Service
public class PaymentService {

    @Autowired
    private RpcCircuitBreakerManager circuitBreakerManager;

    private final RpcRetryPolicy retryPolicy = RpcRetryPolicy.defaultPolicy();

    public PaymentResult pay(PaymentRequest request) {
        RpcCircuitBreaker breaker = circuitBreakerManager.getOrCreate("payment-service");

        int retryCount = 0;
        while (retryCount <= retryPolicy.getMaxRetries()) {
            // 检查熔断器
            if (!breaker.tryAcquire()) {
                throw new CircuitBreakerOpenException("支付服务熔断中");
            }

            try {
                // RPC调用
                PaymentResult result = paymentRpcService.pay(request);
                breaker.recordSuccess();
                return result;
            } catch (Exception e) {
                breaker.recordFailure();

                // 判断是否可重试
                if (retryPolicy.isRetryableException(e) && retryPolicy.shouldRetry(retryCount)) {
                    log.warn("支付调用失败，准备重试: retryCount={}", retryCount);
                    Thread.sleep(retryPolicy.calculateDelay(retryCount).toMillis());
                    retryCount++;
                } else {
                    throw e;
                }
            }
        }

        throw new RuntimeException("支付调用失败，已达最大重试次数");
    }
}
```

### 场景2: 拦截器链 + 性能监控

```java
@Component
public class ValidationRpcInterceptor implements RpcInterceptor {

    @Override
    public boolean preHandle(Method method, Object[] args) {
        // 参数验证
        for (Object arg : args) {
            if (arg == null) {
                throw new IllegalArgumentException("参数不能为空");
            }
        }
        return true;
    }

    @Override
    public int getOrder() {
        return -500; // 高优先级
    }
}

@Service
public class RpcMetricsService {

    @Autowired
    private MetricsRpcInterceptor metricsInterceptor;

    @Scheduled(fixedRate = 60000) // 每分钟
    public void reportMetrics() {
        String[] methods = {
            "com.example.UserService.getUser",
            "com.example.OrderService.getOrder",
            "com.example.PaymentService.pay"
        };

        for (String method : methods) {
            long callCount = metricsInterceptor.getCallCount(method);
            long successCount = metricsInterceptor.getSuccessCount(method);
            long failureCount = metricsInterceptor.getFailureCount(method);
            long avgDuration = metricsInterceptor.getAverageDuration(method);

            log.info("[Metrics] {}: 调用={}, 成功={}, 失败={}, 平均耗时={}ms",
                method, callCount, successCount, failureCount, avgDuration);
        }
    }
}
```

### 场景3: 负载均衡 + 熔断

```java
@Service
public class OrderService {

    @Autowired
    private List<OrderRpcService> orderRpcServices;

    @Autowired
    private RoundRobinLoadBalancer loadBalancer;

    @Autowired
    private RpcCircuitBreakerManager circuitBreakerManager;

    public Order getOrder(Long orderId) {
        // 过滤掉熔断的服务
        List<OrderRpcService> availableServices = orderRpcServices.stream()
            .filter(service -> {
                String serviceName = getServiceName(service);
                RpcCircuitBreaker breaker = circuitBreakerManager.get(serviceName);
                return breaker == null || !breaker.isOpen();
            })
            .collect(Collectors.toList());

        if (availableServices.isEmpty()) {
            throw new NoAvailableServiceException("所有服务都已熔断");
        }

        // 负载均衡选择服务
        OrderRpcService service = loadBalancer.select(availableServices);
        String serviceName = getServiceName(service);
        RpcCircuitBreaker breaker = circuitBreakerManager.getOrCreate(serviceName);

        try {
            Order order = service.getOrder(orderId);
            breaker.recordSuccess();
            return order;
        } catch (Exception e) {
            breaker.recordFailure();
            throw e;
        }
    }
}
```

## 配置示例

```yaml
plugin:
  rpc:
    enable: true
    type: dubbo
    application-name: ${spring.application.name}

    # 协议配置
    protocols:
      - name: dubbo
        port: 20880
        threads: 200

    # 注册中心配置
    registry:
      - address: zookeeper://localhost:2181
        timeout: 5000

    # 消费者配置
    consumer:
      timeout: 3000
      retries: 2
      check: false

    # 重试策略
    retry:
      enabled: true
      max-retries: 3
      initial-delay: 100ms
      max-delay: 10s
      multiplier: 2.0

    # 熔断器配置
    circuit-breaker:
      enabled: true
      failure-threshold: 5
      success-threshold: 2
      timeout: 60s

    # 负载均衡配置
    load-balancer:
      strategy: round-robin  # random, round-robin, weighted
```

## 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     Application Layer                        │
│  @RpcService  @RpcReference  Business Logic                 │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Interceptor Chain                         │
│  ValidationInterceptor → AuthInterceptor → MetricsInterceptor│
│  → LoggingInterceptor → CustomInterceptor                   │
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
│                    Load Balancer                             │
│  Random / RoundRobin / Weighted / Custom                    │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                      RPC Proxy                               │
│  JDK Proxy / Javassist / CGLIB                              │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  RPC Implementation                          │
│  Dubbo / SOFA / JSON-RPC / gRPC                             │
└─────────────────────────────────────────────────────────────┘
```

## 新增文件清单

### 拦截器
- RpcInterceptor.java
- LoggingRpcInterceptor.java
- MetricsRpcInterceptor.java

### 重试策略
- RpcRetryPolicy.java

### 熔断器
- RpcCircuitBreaker.java
- RpcCircuitBreakerManager.java

### 负载均衡
- RpcLoadBalancer.java
- RandomLoadBalancer.java
- RoundRobinLoadBalancer.java

## 最佳实践

1. **使用拦截器**: 统一处理日志、监控、权限校验
2. **配置重试策略**: 根据业务特点选择合适的重试策略
3. **启用熔断器**: 防止故障扩散，保护系统稳定性
4. **选择负载均衡**: 根据场景选择合适的负载均衡策略
5. **监控性能指标**: 及时发现和解决性能问题
6. **合理设置超时**: 避免长时间等待影响用户体验
7. **实现幂等性**: RPC调用应该是幂等的
8. **异常处理**: 区分可重试异常和不可重试异常

## 性能优化建议

1. **连接池**: 使用连接池复用连接
2. **异步调用**: 对于非关键路径使用异步调用
3. **批量调用**: 合并多个小请求为一个大请求
4. **缓存**: 对于不常变化的数据使用缓存
5. **压缩**: 对于大数据传输启用压缩
6. **超时控制**: 设置合理的超时时间
7. **限流**: 保护服务不被过载

## 总结

spring-support-rpc-starter增强功能包括：

1. **RPC拦截器**: 日志、监控、权限校验
2. **重试策略**: 灵活的重试配置
3. **熔断器**: 防止故障扩散
4. **负载均衡**: 多种负载均衡策略

这些功能使RPC框架具备了企业级的完整能力！
