# spring-support-cache-starter

Caffeine + Redis 多级缓存：`@Cacheable` 注解增强、租户级缓存键、按层级组装的 `MultiLevelCache`。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 多级缓存管理器 | `MultiLevelCacheManager`（`@Primary`，Bean 名 `multiLevelCacheManager`） | `plugin.cache.enabled=true` |
| 多级缓存实现 | `MultiLevelCache`（L1 命中直返 / L2 命中回填 L1 / 写删同步全部层级） | 由管理器按缓存名创建 |
| 本地缓存 | `caffeineCacheManager`（`CaffeineCacheManager`） | `plugin.cache.caffeine.*` |
| Redis 缓存 | `redisCacheManager`（`RedisCacheManager`） | `plugin.cache.redis.*` |
| 租户缓存键 | `TenantedKeyGenerator`（Bean 名 `tenantedKeyGenerator`） | `plugin.cache.key.*` |
| 租户上下文 | `TenantIdProvider` | 声明 Bean 后自动装配，缺省租户 `0` |
| 注解增强 | `CacheCustomAnnotationParser`、`customCacheOperationSource` | 允许 `key` + `keyGenerator` 同时使用 |
| 手动缓存模板 | `CacheTemplate` | 与注解同口径的 key |
| 编程式缓存抽象 | `CacheProvider`（`MemoryCacheProvider` / `RedisCacheProvider`） | 代码直接调用，非 Spring Bean |
| 统计与运维 | `MultiLevelCacheManager#getStats/warmUp/evictByPattern/clearAll/resetAllStats` | 代码调用 |
| 服务信息登记 | `ServiceInfoContributor`（`cache` 标志） | 启动横幅 |

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-cache-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 说明 |
|---|---|---|
| `spring-support-common-starter` | `CacheConstant`、`ServiceInfoContributor`、Jackson 配置 | 必需 |
| `spring-boot-starter-cache` | Spring Cache 抽象 | 必需 |
| `caffeine` | 本地缓存实现 | 必需 |
| `spring-boot-starter-data-redis` | Redis 缓存管理器 | `optional`，需要 L2 时由宿主引入 |
| `utils-support-spring-starter` | Spring 工具类 | 必需 |

## 三、配置项

前缀 `plugin.cache`，配置类 `CacheProperties`：

| key | 默认值 | 说明 |
|---|---|---|
| `enabled` | `false` | 总开关；关闭时除 `tenantedKeyGenerator` / 注解解析器外均不注册 |
| `levels` | `[caffeine, redis]` | 层级顺序（越靠前越接近调用方）；`caffeine`/`redis` 解析为 `caffeine-bean`/`redis-bean`，其它值按 Bean 名大写匹配 |
| `caffeine-bean` | `caffeineCacheManager` | 本地层 Bean 名 |
| `redis-bean` | `redisCacheManager` | 分布式层 Bean 名 |
| `caffeine.initial-capacity` | `256` | 初始容量 |
| `caffeine.maximum-size` | `10000` | 最大条目数 |
| `caffeine.expire-after-write` | `300` | 写入后过期（秒），`≤0` 不设置 |
| `caffeine.expire-after-access` | `0` | 访问后过期（秒），`≤0` 不设置 |
| `caffeine.record-stats` | `true` | 命中率统计 |
| `caffeine.weak-keys` / `weak-values` | `false` | 弱引用 |
| `redis.ttl` | `600` | 默认过期（秒）；`-1` 永不过期 |
| `redis.key-prefix` | `cache:` | Redis key 前缀（`prefixCacheNameWith`） |
| `redis.separator` | `:` | 缓存名后的分隔符 |
| `redis.use-key-prefix` | `true` | 是否启用前缀 |
| `redis.serializer` | `jackson` | 当前实现固定使用 `GenericJackson2JsonRedisSerializer`，该值不参与构建 |
| `redis.cache-null-values` | `false` | 声明项；Redis 构建实际读顶层 `cache-null-values` |
| `cache-null-values` | `false` | 顶层开关，作用于 Caffeine 的 `allowNullValues` 与 Redis 的 `disableCachingNullValues` |
| `null-value-ttl` | `60` | 空值 TTL（声明项，当前无消费方） |
| `expires` | `{}` | 按缓存名细化 TTL（声明项，当前无消费方） |
| `record-stats` | `true` | 多级缓存命中率统计开关 |
| `key.prefix` | `cache:` | 缓存键前缀 |
| `key.separator` | `:` | 键分隔符（`TenantedKeyGenerator` 实际固定用 `:`） |
| `key.with-application` | `true` | 追加 `spring.application.name` |
| `key.with-platform` | `true` | 追加平台名（来自 `ApiProperties.platform`） |
| `key.with-tenant` | `true` | 追加租户 ID |

## 四、快速开始

**第一步**：引入依赖。需要 L2 时额外引入 `spring-boot-starter-data-redis` 并配好 `spring.data.redis.*`。

**第二步**：打开开关并选择层级：

```yaml
plugin:
  cache:
    enabled: true
    levels:
      - caffeine
      - redis
    caffeine:
      maximum-size: 50000
      expire-after-write: 300
    redis:
      ttl: 1800
      key-prefix: "cache:"
```

**第三步**：正常使用 `@Cacheable` / `@CachePut` / `@CacheEvict`，不写 `cacheManager` 时使用 `@Primary` 的 `multiLevelCacheManager`。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API，以下为注解与步骤式用法。

**注解（默认走多级缓存）**：

```java
@Service
public class UserService {

    @Cacheable(cacheNames = "user", key = "#id")
    public User getUser(Long id) { return userMapper.selectById(id); }

    @CachePut(cacheNames = "user", key = "#result.id")
    public User updateUser(User user) { userMapper.updateById(user); return user; }

    @CacheEvict(cacheNames = "user", key = "#id")
    public void deleteUser(Long id) { userMapper.deleteById(id); }
}
```

**注解增强**：`key` 与 `keyGenerator` 可同时写，未指定 `keyGenerator` 时默认 `tenantedKeyGenerator`。

```java
@Cacheable(cacheNames = "order", key = "#id", keyGenerator = "tenantedKeyGenerator")
public Order getOrder(Long id) { ... }
```

**缓存键结构**（`TenantedKeyGenerator`）：

```text
{key.prefix}{applicationName}:{platformName}:{tenantId}:{SimpleKey(params)}
# 例：cache:myapp:system:tenant-001:123
```

租户 ID 由 `TenantIdProvider` 提供，未声明该 Bean 时取 `"0"`：

```java
@Component
public class HeaderTenantIdProvider implements TenantIdProvider {
    @Override
    public String getCurrentTenantId() {
        return RequestUtils.getCurrentTenantId();
    }
}
```

**手动读写（`CacheTemplate`）**：与注解使用同一套 key 规则，避免手写 key 命中不到注解缓存。

```java
@Autowired
private CacheTemplate cacheTemplate;

cacheTemplate.put("user", "user:" + id, user);            // 写入
User u = cacheTemplate.get("user", "user:" + id, User.class); // 读取，未命中返回 null
cacheTemplate.evict("user", "user:" + id);                // 删除单键
cacheTemplate.evictByPattern("user", "user:*");           // 按模式清理
cacheTemplate.clearAll();                                  // 清空所有多级缓存

Map<String, MultiLevelCacheManager.CacheStats> stats = cacheTemplate.getStats();
// CacheStats(hitCount, missCount, hitRate, backfillCount, levelCount)
```

**管理器级操作**：

```java
@Autowired
private MultiLevelCacheManager cacheManager;

cacheManager.warmUp("user", Map.of("user:1", user1, "user:2", user2));
cacheManager.evictByPattern("user", "tenant001");
cacheManager.resetAllStats();
```

**编程式抽象（非 Bean）**：`CacheProvider.auto()` 返回内存实现；Redis 实现为抽象类 `CacheProvider.RedisCacheProvider`，需自行继承。

```java
CacheProvider provider = CacheProvider.auto();
provider.put("k", "v", 60);
Object v = provider.get("k", String.class);
provider.remove("k");
```

**多 Redis 实例**：`levels` 直接引用容器中的 Bean 名，可自行注册多个 `CacheManager` 后加入：

```java
@Bean("redisCacheManager2")
public CacheManager redisCacheManager2(RedisConnectionFactory factory) {
    return CacheConfiguration.createRedisCacheManager(null, factory, 1800);
}
```

```yaml
plugin:
  cache:
    levels: [caffeineCacheManager, redisCacheManager, redisCacheManager2]
```

### 5.2 SPI 扩展示例

本模块不提供 SPI 扩展点（`src/main/resources/META-INF` 下只有 `spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，无 `extensions` / `services` 目录）。

可扩展的是 Spring Bean 级别：

| 扩展点 | 方式 |
|---|---|
| `TenantIdProvider` | 声明一个 Bean，`TenantedKeyGenerator` 自动装配 |
| `CacheManager` | 声明任意 Bean，在 `levels` 中按名称引用 |
| `CacheProperties` 之外的层级名 | 自定义 Bean 名，`levels` 写大写名即可匹配 |

示例（自定义层级）：

```java
@Configuration
public class CustomCacheConfig {
    @Bean("ehcacheManager")
    public CacheManager ehcacheManager() { return new EhCacheCacheManager(...); }
}
```

```yaml
plugin:
  cache:
    levels: [caffeineCacheManager, ehcacheManager]
```

生效条件：`plugin.cache.enabled=true`，且该 Bean 在 `MultiLevelCacheManager#afterPropertiesSet` 之前已注册。

## 六、与其它 starter 的关系

- **上游**：`spring-support-common-starter`（`CacheConstant`、`ServiceInfoContributor`、`JacksonConfiguration.createObjectMapper`）。
- **`spring-support-redis-starter`**：本模块的 pom 不被 redis-starter 反向依赖；实际是 redis-starter 依赖本模块。redis-starter 额外注册 `redis-cache-10/30/60/600/3600/-1/86400` 系列 `CacheManager`，与本模块的 `redisCacheManager` 并列存在。
- **加载顺序**：`CacheConfiguration` 标注 `@AutoConfigureAfter(name = "com.chua.starter.redis.support.CacheConfiguration")`，确保 `levels` 解析时 redis-starter 的 Bean 已就绪。
- **`spring-support-common-starter` 的缓存注解**：`OptionalProvider` 等使用 `systemCacheManager` / `redis-cache--1` 常量名，这些 Bean 由 redis-starter 提供，不在本模块。

## 七、已知限制

1. `plugin.cache.enabled` 默认 `false`，未开启时不注册 `caffeineCacheManager`、`redisCacheManager`、`multiLevelCacheManager`、`CacheTemplate`。
2. `redisCacheManager` 需要类路径存在 `spring-data-redis` 且容器中有 `RedisConnectionFactory`，否则该 Bean 不创建，`redis` 层级会被跳过并输出 WARN。
3. `expires`（按缓存名细化 TTL）、`null-value-ttl`、`redis.cache-null-values` 目前只有配置字段，没有代码消费，配了不生效（空值缓存以顶层 `cache-null-values` 为准）。
4. `redis.serializer` 不参与构建，L2 始终使用 `GenericJackson2JsonRedisSerializer`；需要 JDK 序列化时须自行定义 `CacheManager`。
5. `key.separator` 对 `TenantedKeyGenerator` 无效，键分隔符固定为 `:`。
6. L2 写操作强制同步（`RedisCacheWriter.immediateWrites(true)`），避免 Spring Data Redis 4.x 默认异步导致 `@CacheEvict` 丢指令；代价是写路径多一次同步等待。
7. 多层一致性仅为「写入/删除同步全部层级」，没有跨实例的 L1 失效广播；多实例下实例 A 的写入不会清理实例 B 的 Caffeine 副本，需自行用 Redis Pub/Sub 等机制补齐。
8. `levels` 中的自定义名称按 `toUpperCase()` 匹配 Bean 名，Bean 名若含大写或特殊字符需按同样规则书写。
9. `CacheProvider` 不是 Spring Bean，`auto()` 只返回内存实现；Redis 实现需继承 `CacheProvider.RedisCacheProvider` 并自行管理模板。
10. 空值缓存仅在 `cache-null-values=true` 时开启，关闭时 Redis 配置 `disableCachingNullValues`，`@Cacheable` 返回 `null` 不会落缓存（无法防穿透）。
