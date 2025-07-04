# Spring Support Circuit Breaker Starter

基于Resilience4j的熔断降级模块，提供完整的容错解决方案。

## 功能特性

- 🔥 **熔断器（Circuit Breaker）** - 防止级联故障，提供快速失败机制
- 🔄 **重试机制（Retry）** - 自动重试失败的操作，提高系统可靠性
- 🚦 **增强限流器（Enhanced Rate Limiter）** - 控制请求速率，支持多维度限流和动态管理
- 🏠 **舱壁隔离（Bulkhead）** - 资源隔离，防止资源耗尽
- ⏰ **超时控制（Time Limiter）** - 防止长时间等待，及时释放资源
- 💾 **缓存机制（Cache）** - 提供结果缓存，减少重复计算
- 📊 **监控指标** - 提供详细的监控指标和健康检查
- 🎯 **注解支持** - 支持注解和编程式两种使用方式
- ⚙️ **动态配置** - 支持运行时动态调整参数

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-circuit-breaker-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置属性

```yaml
plugin:
  circuit-breaker:
    enable: true
    circuit-breaker:
      failure-rate-threshold: 50.0
      slow-call-rate-threshold: 100.0
      slow-call-duration-threshold: 60s
      minimum-number-of-calls: 10
      sliding-window-size: 10
      wait-duration-in-open-state: 60s
      permitted-number-of-calls-in-half-open-state: 3
      instances:
        userService:
          failure-rate-threshold: 30.0
          minimum-number-of-calls: 5
    retry:
      max-attempts: 3
      wait-duration: 500ms
      interval-multiplier: 1.5
      max-wait-duration: 10s
      instances:
        userService:
          max-attempts: 5
    rate-limiter:
      limit-refresh-period: 1s
      limit-for-period: 10
      timeout-duration: 500ms
      enable-management: true
      management-path: /actuator/rate-limiter
      default-dimension: GLOBAL
      instances:
        userService:
          limit-for-period: 20
      rules:
        api-limit:
          name: "API限流"
          pattern: "/api/**"
          limit-for-period: 50
          limit-refresh-period: 1s
          dimension: API
          enabled: true
        ip-limit:
          name: "IP限流"
          pattern: "/**"
          limit-for-period: 100
          limit-refresh-period: 1s
          dimension: IP
          enabled: true
    bulkhead:
      max-concurrent-calls: 25
      max-wait-duration: 0ms
      instances:
        userService:
          max-concurrent-calls: 10
    time-limiter:
      timeout-duration: 1s
      cancel-running-future: true
      instances:
        userService:
          timeout-duration: 2s
```

### 3. 使用注解

```java
@RestController
public class UserController {

    @GetMapping("/users/{id}")
    @CircuitBreakerProtection(
        circuitBreaker = "userService",
        retry = "userService", 
        rateLimiter = "userService",
        fallbackMethod = "getUserFallback"
    )
    public User getUser(@PathVariable Long id) {
        // 业务逻辑
        return userService.findById(id);
    }

    // 降级方法
    public User getUserFallback(Long id, Exception ex) {
        return User.builder()
                .id(id)
                .name("默认用户")
                .build();
    }

    @PostMapping("/users")
    @CircuitBreakerProtection(
        circuitBreaker = "userService",
        bulkhead = "userService",
        timeLimiter = "userService",
        async = true,
        fallbackMethod = "createUserFallback"
    )
    public CompletableFuture<User> createUser(@RequestBody User user) {
        return CompletableFuture.supplyAsync(() -> userService.save(user));
    }

    // 使用专门的限流注解
    @GetMapping("/users/search")
    @RateLimiter(
        name = "userSearch",
        limitForPeriod = 20,
        limitRefreshPeriodSeconds = 1,
        dimension = RateLimiter.Dimension.IP,
        fallbackMethod = "searchUsersFallback",
        message = "搜索请求过于频繁，请稍后再试"
    )
    public List<User> searchUsers(@RequestParam String keyword) {
        return userService.search(keyword);
    }

    // 使用SpEL表达式的限流
    @PostMapping("/users/{userId}/orders")
    @RateLimiter(
        name = "createOrder",
        key = "#userId",
        limitForPeriod = 5,
        limitRefreshPeriodSeconds = 60,
        dimension = RateLimiter.Dimension.USER,
        fallbackMethod = "createOrderFallback"
    )
    public Order createOrder(@PathVariable Long userId, @RequestBody Order order) {
        return orderService.create(userId, order);
    }

    public User createUserFallback(User user, Exception ex) {
        return User.builder()
                .name(user.getName())
                .status("创建失败")
                .build();
    }
}
```

### 4. 编程式使用

```java
@Service
public class UserService {

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    public User findById(Long id) {
        return circuitBreakerService.executeWithCircuitBreaker(
            "userService",
            () -> {
                // 实际业务逻辑
                return userRepository.findById(id);
            },
            () -> {
                // 降级逻辑
                return getDefaultUser(id);
            }
        );
    }

    public List<User> findAll() {
        return circuitBreakerService.executeWithCombined(
            "userService",  // 熔断器
            "userService",  // 重试
            "userService",  // 限流
            () -> userRepository.findAll(),
            () -> Collections.emptyList()
        );
    }

    public CompletableFuture<User> createUserAsync(User user) {
        return circuitBreakerService.executeWithFullProtection(
            "userService",  // 熔断器
            "userService",  // 重试
            "userService",  // 限流
            "userService",  // 舱壁隔离
            "userService",  // 超时控制
            () -> CompletableFuture.supplyAsync(() -> userRepository.save(user)),
            () -> getDefaultUser(user.getId())
        );
    }
}
```

## 监控和管理

### 1. 健康检查

访问 `/actuator/health` 查看熔断降级组件的健康状态。

### 2. 管理接口

- `GET /actuator/circuit-breaker/overview` - 获取整体状态概览
- `GET /actuator/circuit-breaker/circuit-breakers` - 获取所有熔断器状态
- `GET /actuator/circuit-breaker/circuit-breakers/{name}` - 获取指定熔断器状态
- `POST /actuator/circuit-breaker/circuit-breakers/{name}/reset` - 重置熔断器
- `POST /actuator/circuit-breaker/circuit-breakers/{name}/force-open` - 强制打开熔断器
- `POST /actuator/circuit-breaker/circuit-breakers/{name}/force-close` - 强制关闭熔断器
- `GET /actuator/circuit-breaker/rate-limiters` - 获取所有限流器状态
- `GET /actuator/circuit-breaker/bulkheads` - 获取所有舱壁隔离状态

### 3. 增强限流管理

访问 `/actuator/rate-limiter` 打开限流管理界面，支持：

- 📊 **实时监控** - 查看所有限流器的实时状态和指标
- ➕ **动态创建** - 运行时创建新的限流器
- 🗑️ **删除管理** - 删除不需要的限流器
- 🔄 **状态刷新** - 实时刷新限流器状态

#### 限流管理API

- `GET /actuator/rate-limiter` - 获取管理页面
- `GET /actuator/rate-limiter/status` - 获取所有限流器状态
- `GET /actuator/rate-limiter/{name}/status` - 获取指定限流器状态
- `POST /actuator/rate-limiter/{name}` - 创建新的限流器
- `DELETE /actuator/rate-limiter/{name}` - 删除限流器

### 3. 监控指标

集成Micrometer，提供以下监控指标：

- `resilience4j.circuitbreaker.calls` - 熔断器调用次数
- `resilience4j.circuitbreaker.state` - 熔断器状态
- `resilience4j.retry.calls` - 重试调用次数
- `resilience4j.ratelimiter.available.permissions` - 限流器可用许可数
- `resilience4j.bulkhead.available.concurrent.calls` - 舱壁隔离可用并发调用数

## 配置说明

### 熔断器配置

| 参数 | 说明 | 默认值 |
|------|------|--------|
| failure-rate-threshold | 失败率阈值（百分比） | 50.0 |
| slow-call-rate-threshold | 慢调用率阈值（百分比） | 100.0 |
| slow-call-duration-threshold | 慢调用持续时间阈值 | 60s |
| minimum-number-of-calls | 最小调用数量 | 10 |
| sliding-window-size | 滑动窗口大小 | 10 |
| wait-duration-in-open-state | 等待持续时间（半开状态） | 60s |
| permitted-number-of-calls-in-half-open-state | 半开状态下允许的调用数量 | 3 |

### 重试配置

| 参数 | 说明 | 默认值 |
|------|------|--------|
| max-attempts | 最大重试次数 | 3 |
| wait-duration | 重试间隔 | 500ms |
| interval-multiplier | 重试间隔倍数 | 1.0 |
| max-wait-duration | 最大重试间隔 | 10s |

### 增强限流器配置

| 参数 | 说明 | 默认值 |
|------|------|--------|
| limit-refresh-period | 限制刷新周期 | 1s |
| limit-for-period | 每个周期的许可数量 | 10 |
| timeout-duration | 超时持续时间 | 500ms |
| enable-management | 是否启用管理页面 | true |
| management-path | 管理页面路径 | /actuator/rate-limiter |
| default-dimension | 默认限流维度 | GLOBAL |

### 限流维度说明

| 维度 | 说明 | 使用场景 |
|------|------|----------|
| GLOBAL | 全局限流 | 对所有请求进行统一限流 |
| IP | 按IP限流 | 防止单个IP过度访问 |
| USER | 按用户限流 | 对不同用户设置不同的访问限制 |
| API | 按接口限流 | 对不同API接口设置不同的限流策略 |

### 限流规则配置

支持通过配置文件定义复杂的限流规则：

```yaml
plugin:
  circuit-breaker:
    rate-limiter:
      rules:
        # API接口限流
        api-users:
          name: "用户API限流"
          pattern: "/api/users/**"
          limit-for-period: 100
          limit-refresh-period: 1s
          dimension: API
          enabled: true
          message: "用户API访问过于频繁"

        # IP限流
        global-ip:
          name: "全局IP限流"
          pattern: "/**"
          limit-for-period: 1000
          limit-refresh-period: 1s
          dimension: IP
          enabled: true

        # 用户限流（使用SpEL表达式）
        user-orders:
          name: "用户订单限流"
          pattern: "/api/orders/**"
          key-expression: "#request.getHeader('X-User-Id')"
          limit-for-period: 10
          limit-refresh-period: 60s
          dimension: USER
          fallback-method: "orderLimitFallback"
```

### 舱壁隔离配置

| 参数 | 说明 | 默认值 |
|------|------|--------|
| max-concurrent-calls | 最大并发调用数 | 25 |
| max-wait-duration | 最大等待持续时间 | 0ms |

### 超时控制配置

| 参数 | 说明 | 默认值 |
|------|------|--------|
| timeout-duration | 超时持续时间 | 1s |
| cancel-running-future | 是否取消运行中的Future | true |

## 最佳实践

1. **合理设置阈值** - 根据业务特点设置合适的失败率和慢调用阈值
2. **组合使用** - 结合多种容错机制，提供全面保护
3. **监控告警** - 配置监控指标和告警，及时发现问题
4. **降级策略** - 设计合理的降级策略，保证用户体验
5. **测试验证** - 通过故障注入等方式验证容错机制的有效性

## 注意事项

- 异步方法需要返回 `CompletableFuture` 或 `CompletionStage`
- 降级方法的参数和返回值类型必须与原方法兼容
- 建议为不同的服务配置不同的实例参数
- 监控指标需要配置 Micrometer 相关依赖
