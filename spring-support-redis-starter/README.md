# spring-support-redis-starter

Redis 集成：Redisson 客户端、预置分档 `CacheManager`、分布式锁模板、发布订阅监听、KV 与计数服务。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| Redisson 客户端 | `RedisConfiguration#redissonClient` | 自动装配，`@ConditionalOnMissingBean` |
| Redis 操作模板 | `redisTemplate`（`RedisTemplate<String,Object>`）、`stringRedisTemplate` | 直接注入 |
| 预置分档缓存 | `CacheConfiguration#createRedisCacheManager` | Bean 名 `redis-cache-10/30/60/600/3600/-1/86400` |
| 分布式锁模板 | `LockTemplate`、`RedissonLockTemplate`、`NoopLockTemplate` | Bean 名 `customLockTemplate` |
| 锁 SPI 实现 | `LockProvider`、`RedissonLockProvider`（`@Spi("redisson")`） | `ServiceProvider` |
| 发布订阅 | `RedisMessageListenerContainer`、`RedisListener` | 声明 `RedisListener` Bean 自动注册 |
| KV 引擎 | `KvEngine`、`RedisKvEngine` | 注入 `KvEngine` |
| 指标计数 | `SimpleRedisService`、`SimpleRedisServiceImpl` | 注入后 `increment`/`decrement`/`qps` |
| 时间序列 | `TimeSeriesService` | 占位接口，当前无方法 |
| Redis 搜索 | `RedisSearchService` | 占位接口，当前无方法 |
| 服务信息登记 | `ServiceInfoContributor`（`redis` 端点/标志） | 启动横幅 |

自动配置类（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）：
`RedisConfiguration`、`CacheConfiguration`、`LockTemplateConfiguration`。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-redis-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 说明 |
|---|---|---|
| `spring-support-cache-starter` | `CacheConstant` 常量与多级缓存 | 必需 |
| `spring-support-common-starter` | `ModuleEnvironmentRegistration`、`ServiceInfoContributor` | 必需 |
| `spring-boot-starter-data-redis` | `RedisTemplate`、监听容器 | 必需 |
| `redisson` | 分布式锁与客户端 | 必需 |
| `utils-support-redis-starter` | Redis 工具类与 SPI 基础设施 | 必需 |
| `lock4j-redis-template-spring-boot-starter` | `@Lock4j` 注解能力 | 依赖带入，本模块不做额外配置 |
| `utils-support-springboot-starter` | `LockTemplate` 相关扩展 | `provided` + `optional` |

## 三、配置项

| 前缀 | 配置类 | 关键项（默认值） |
|---|---|---|
| `spring.data.redis` | Spring Boot `DataRedisProperties` | `host`、`port`=`6379`、`password`、`database`=`0`、`username`、`url`、`timeout`、`connect-timeout`、`client-name`、`lettuce.pool.max-active/max-idle/min-idle/max-wait` |
| `plugin.redis.server` | `RedisServerProperties` | `enable`=`false`（注解为 `matchIfMissing=true`，不配置即生效）；`open-embedded`=`false`；`port`=`6379`；`database`=`0`；`host`=`0.0.0.0`；`max-memory`=`128M`；`max-heap`=`100M` |

注意：Spring Boot 4 的 Redis 连接配置前缀是 `spring.data.redis`，旧写法 `spring.redis.*` 不再生效。

`plugin.redis.server.enable=false` 会关闭整个 `RedisConfiguration`（Redisson 客户端、两个模板、监听容器、计数/KV 服务全部不注册）。

## 四、快速开始

**第一步**：引入依赖并配置连接：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password:
      database: 0
      timeout: 10s
      connect-timeout: 10s
      client-name: spring-app
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
          max-wait: 2s
```

**第二步**：按需使用预置 `CacheManager`（名称即 Bean 名，不是方法名）：

| Bean 名 | TTL |
|---|---|
| `redis-cache-10` | 10 秒 |
| `redis-cache-30` | 30 秒 |
| `redis-cache-60` | 60 秒 |
| `redis-cache-600` | 600 秒 |
| `redis-cache-3600` | 3600 秒 |
| `redis-cache-86400` | 86400 秒 |
| `redis-cache--1` | 永不过期 |

**第三步**：在 `@Cacheable` 上指定 `cacheManager`，或直接注入 `RedisTemplate` / `LockTemplate`。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API，以下为注入 + 步骤式用法。

**分档缓存**：

```java
@Service
public class UserService {

    @Cacheable(cacheManager = "redis-cache-600", cacheNames = "users", key = "#id")
    public User findById(Long id) { return userRepository.findById(id); }

    @Cacheable(cacheManager = "redis-cache-3600", cacheNames = "users", key = "#username")
    public User findByUsername(String username) { return userRepository.findByUsername(username); }

    @Cacheable(cacheManager = "redis-cache--1", cacheNames = "config", key = "#key")
    public String getConfig(String key) { return configRepository.getValue(key); }
}
```

**模板操作**：

```java
@Autowired
private RedisTemplate<String, Object> redisTemplate;

redisTemplate.opsForValue().set(key, value, 600, TimeUnit.SECONDS);
Object value = redisTemplate.opsForValue().get(key);
redisTemplate.delete(key);

Set<String> keys = redisTemplate.keys("user:*");
if (keys != null && !keys.isEmpty()) {
    redisTemplate.delete(keys);
}
```

**分布式锁（推荐用模板，自动释放）**：

```java
@Autowired
private LockTemplate lockTemplate;   // Bean 名 customLockTemplate

// 阻塞获取，租约默认 30 秒
String result = lockTemplate.lock("order:create", () -> createOrder(req));

// 最多等 5 秒，拿不到返回 null
String tried = lockTemplate.tryLock("order:create", Duration.ofSeconds(5), () -> createOrder(req));

// 拿不到锁走降级
String fallback = lockTemplate.tryLock("order:create",
        Duration.ofSeconds(5), () -> createOrder(req), () -> "busy");
```

锁 key 会统一加 `lock:` 前缀（`RedissonLockTemplate#getLockKey`）。需要手工控制时用 `acquire(key, waitTime, leaseTime)` / `release(key)`，注意成对调用。

**发布订阅**：声明 `RedisListener` Bean 即被 `RedisMessageListenerContainer` 收集。

```java
@Component
public class UserEventListener implements RedisListener {

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("收到消息 channel={}, body={}", channel, body);
    }

    @Override
    public Collection<Topic> getTopics() {
        return List.of(new ChannelTopic("login:events"), new PatternTopic("system:*"));
    }
}
```

发布端直接用模板：`redisTemplate.convertAndSend("login:events", payload)`。

**指标计数**：

```java
@Autowired
private SimpleRedisService simpleRedisService;

simpleRedisService.increment("page:views", pageId, 24 * 3600); // 带过期
simpleRedisService.incrementDay("login:actions", userId);      // 到当天 24 点过期
simpleRedisService.decrement("product:stock", productId, 3600);
ReturnResult<BigDecimal> qps = simpleRedisService.qps("api:/orders");
```

**KV 引擎**：

```java
@Autowired
private KvEngine kvEngine;   // RedisKvEngine，基于 StringRedisTemplate

kvEngine.put("user:1", "tom");
kvEngine.put("user:2", "jerry", Duration.ofMinutes(10));
String v = kvEngine.get("user:1");
Map<String, String> all = kvEngine.findAllByPrefix("user:");
```

**自定义 TTL 的缓存管理器**（`createRedisCacheManager` 是 public static）：

```java
@Bean("redisCacheManager1800")
public CacheManager redisCacheManager1800(RedisConnectionFactory factory) {
    return CacheConfiguration.createRedisCacheManager(null, factory, 1800);
}
```

预置实现固定使用 `StringRedisSerializer` 作 key、`GenericJackson2JsonRedisSerializer` 作 value，前缀 `CACHE:` + 缓存名 + `:`，并 `disableCachingNullValues()`。

### 5.2 SPI 扩展示例

模块自身没有 `META-INF/extensions` 登记表，但提供锁实现的 SPI 扩展点：`LockProvider`（`com.chua.starter.redis.support.task.lock`），内置实现 `RedissonLockProvider` 标注 `@Spi(value = "redisson", order = 100)`。

三步新增一个自定义锁提供者：

```java
// 1. 实现接口 + @Spi 声明名称
@Spi(value = "zk", order = 50)
public class ZkLockProvider implements LockProvider {

    private final String key;
    private ZkLock lock;

    public ZkLockProvider(String key) { this.key = key; }

    @Override
    public boolean tryLock(int timeout, TimeUnit timeUnit) {
        this.lock = zkClient.acquire(key);
        return true;
    }

    @Override
    public void unlock() { if (lock != null) { lock.release(); } }

    @Override
    public String getName() { return "zk"; }

    @Override
    public String getType() { return "zk"; }
}
```

```text
# 2. 登记到 src/main/resources/META-INF/extensions/com.chua.starter.redis.support.task.lock.LockProvider
com.example.ZkLockProvider
```

```java
// 3. 生效：按名称取用
LockProvider provider = ServiceProvider.of(LockProvider.class).getExtension("zk");
provider.tryLock(5, TimeUnit.SECONDS);
```

注意 `LockProvider` 实现需提供 `String key` 构造参数，SPI 框架按构造参数实例化。

## 六、与其它 starter 的关系

- **`spring-support-cache-starter`**：本模块依赖它（拿 `CacheConstant` 与多级缓存）。它的 `CacheConfiguration` 会 `@AutoConfigureAfter` 本模块的 `CacheConfiguration`，因此 `redis-cache-*` 系列先于 `redisCacheManager` 就绪。两者可共存：`redis-cache-*` 是单一 Redis 分档缓存，`multiLevelCacheManager` 是 L1+L2 组合。
- **`spring-support-common-starter`**：提供 `ModuleEnvironmentRegistration`（启动横幅按 `plugin.redis.server` 显示开启状态）与 `ServiceInfoContributor`。
- **`spring-support-datasource-starter`**：无直接依赖，健康检查各自注册 `HealthIndicator`，在 `/actuator/health` 并列展示。
- **`spring-support-oauth-client-starter`**：使用 `CacheProvider`（来自 cache-starter）做令牌撤销缓存，不依赖本模块。
- **Lock4j**：`lock4j-redis-template-spring-boot-starter` 由本模块传递引入，其原生 `@Lock4j` 注解可直接使用；本模块的 `LockTemplate` 是独立封装，两者互不覆盖。

## 七、已知限制

1. `TimeSeriesService`、`RedisSearchService` 是空接口（无任何方法），Bean 虽注册但无法调用；时间序列/搜索能力需自行实现或换模块。
2. `plugin.redis.server` 下的 `open-embedded`、`max-memory`、`max-heap` 目前只有配置字段，没有代码消费——**嵌入式 Redis 未实现**，不要指望 `open-embedded=true` 能启动内存实例。
3. Redisson 只按单机模式构建（`config.useSingleServer()`），`spring.data.redis.cluster` / `sentinel` 配置不会传给 Redisson；集群/哨兵需自行声明 `RedissonClient` Bean（`@ConditionalOnMissingBean` 会退让）。
4. `LockTemplateConfiguration` 在缺少 `RedissonClient` 时回退 `NoopLockTemplate`：`tryLock` 恒成功且不真正加锁，只适合无 Redis 的本地/测试环境，生产需确认 Bean 类型。
5. `RedisConfiguration` 整体受 `plugin.redis.server.enable` 控制，且 `matchIfMissing=true`；显式配置 `enable: false` 会连带关闭模板、监听容器与计数服务。
6. 预置缓存 Bean 名是常量（`redis-cache-600` 等），与创建方法名（`systemCacheManager600`）不一致，`cacheManager` 属性必须写 Bean 名。
7. 监听容器的 topic 序列化使用 `Jackson2JsonRedisSerializer` + `enableDefaultTyping`（已废弃 API），且序列化器必须在 `addMessageListener` 之后设置，改动顺序会收不到消息。
8. 预置缓存一律 `disableCachingNullValues()`，无法缓存空值；需要防穿透须自定义 `CacheManager`。
9. `SimpleRedisService#qps` 以「哈希汇总值 ÷ 86400」估算，不是滑动窗口 QPS，仅作粗略指标。
10. 运行环境：Java 25、Spring Boot 4.1.x，Redis 配置前缀为 `spring.data.redis`。
