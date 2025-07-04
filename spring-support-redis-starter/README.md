# Spring Support Redis Starter

## 📖 模块简介

Spring Support Redis Starter 是一个功能完整的Redis集成模块，提供了企业级应用中Redis缓存、分布式锁、消息队列、会话管理等功能的完整解决方案。该模块支持多种Redis客户端，提供了丰富的Redis数据结构操作和高级功能。

## ✨ 主要功能

### 🔄 缓存管理
- 多级缓存管理器
- 自定义缓存策略
- 缓存注解支持
- 缓存序列化配置

### 🔒 分布式锁
- 基于Redisson的分布式锁
- 防重复提交
- 锁超时控制
- 公平锁和非公平锁

### 📨 消息队列
- Redis发布订阅
- 消息监听器
- 异步消息处理
- 消息序列化

### 🕐 时间序列
- Redis TimeSeries支持
- 时间序列数据存储
- 数据聚合和查询
- 指标监控

### 🔍 搜索功能
- Redis Search集成
- 全文搜索
- 索引管理
- 复杂查询

### 🖥️ 嵌入式Redis
- 测试环境支持
- 内存Redis服务器
- 自动启动和停止
- 配置简化

## 🚀 快速开始

### Maven依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-redis-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 基础配置

```yaml
# Redis基础配置
spring:
  redis:
    host: localhost
    port: 6379
    password: 
    database: 0
    timeout: 10s
    connect-timeout: 10s
    client-name: spring-app
    
    # 连接池配置
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
        max-wait: 2s

# 插件配置
plugin:
  redis:
    server:
      open-embedded: false  # 是否启用嵌入式Redis
      port: 6379
      max-memory: 128mb
```

## 📋 详细功能说明

### 1. 缓存管理

#### 使用预定义缓存管理器

```java
@Service
public class UserService {
    
    // 10分钟过期缓存
    @Cacheable(cacheManager = "systemCacheManager600", cacheNames = "users", key = "#id")
    public User findById(Long id) {
        return userRepository.findById(id);
    }
    
    // 1小时过期缓存
    @Cacheable(cacheManager = "systemCacheManager", cacheNames = "users", key = "#username")
    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    // 永不过期缓存
    @Cacheable(cacheManager = "systemCacheManagerAlways", cacheNames = "config", key = "#key")
    public String getConfig(String key) {
        return configRepository.getValue(key);
    }
    
    // 一天过期缓存
    @Cacheable(cacheManager = "redis86400CacheManager", cacheNames = "reports", key = "#date")
    public Report getDailyReport(String date) {
        return reportService.generateReport(date);
    }
}
```

#### 缓存操作

```java
@Service
public class CacheService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    public void setCache(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }
    
    public Object getCache(String key) {
        return redisTemplate.opsForValue().get(key);
    }
    
    public void deleteCache(String key) {
        redisTemplate.delete(key);
    }
    
    public void deleteCachePattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
```

### 2. 分布式锁

#### 使用Redisson分布式锁

```java
@Service
public class OrderService {
    
    @Autowired
    private RedissonClient redissonClient;
    
    public void processOrder(String orderId) {
        String lockKey = "order:lock:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待10秒，锁定30秒后自动释放
            if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                // 执行业务逻辑
                doProcessOrder(orderId);
            } else {
                throw new RuntimeException("获取锁失败，订单正在处理中");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    
    private void doProcessOrder(String orderId) {
        // 具体的订单处理逻辑
        log.info("处理订单: {}", orderId);
    }
}
```

#### 防重复提交

```java
@Component
public class SubmitLockAspect {
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Around("@annotation(submitLock)")
    public Object around(ProceedingJoinPoint joinPoint, SubmitLock submitLock) throws Throwable {
        String key = generateKey(joinPoint, submitLock);
        RLock lock = redissonClient.getLock(key);
        
        try {
            if (lock.tryLock()) {
                return joinPoint.proceed();
            } else {
                throw new RuntimeException("请勿重复提交");
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    
    private String generateKey(ProceedingJoinPoint joinPoint, SubmitLock submitLock) {
        // 生成锁的key
        return "submit:lock:" + joinPoint.getSignature().toShortString();
    }
}
```

### 3. 消息发布订阅

#### 消息发布

```java
@Service
public class MessagePublisher {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    public void publishMessage(String channel, Object message) {
        redisTemplate.convertAndSend(channel, message);
    }
    
    public void publishUserEvent(String userId, String event) {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId);
        message.put("event", event);
        message.put("timestamp", System.currentTimeMillis());
        
        redisTemplate.convertAndSend("user:events", message);
    }
}
```

#### 消息监听

```java
@Component
public class UserEventListener implements RedisListener {
    
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());
        
        log.info("收到消息 - 频道: {}, 内容: {}", channel, body);
        
        // 处理用户事件
        handleUserEvent(body);
    }
    
    @Override
    public Collection<Topic> getTopics() {
        return List.of(new ChannelTopic("user:events"));
    }
    
    private void handleUserEvent(String message) {
        // 处理用户事件逻辑
        try {
            Map<String, Object> event = JsonUtils.parseMap(message);
            String userId = (String) event.get("userId");
            String eventType = (String) event.get("event");
            
            switch (eventType) {
                case "login":
                    handleUserLogin(userId);
                    break;
                case "logout":
                    handleUserLogout(userId);
                    break;
                default:
                    log.warn("未知事件类型: {}", eventType);
            }
        } catch (Exception e) {
            log.error("处理用户事件失败: {}", message, e);
        }
    }
}
```

### 4. 时间序列数据

#### 时间序列服务

```java
@Service
public class MetricsService {
    
    @Autowired
    private TimeSeriesService timeSeriesService;
    
    public void recordMetric(String metricName, double value) {
        long timestamp = System.currentTimeMillis();
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put("app", "spring-app");
        labels.put("env", "prod");
        
        // 保存时间序列数据，保留7天
        timeSeriesService.save(metricName, timestamp, value, labels, 7 * 24 * 3600);
    }
    
    public void recordApiMetrics(String apiPath, long responseTime) {
        String metricName = "api:response_time:" + apiPath.replace("/", ":");
        recordMetric(metricName, responseTime);
    }
    
    public void recordUserActivity(String userId, String activity) {
        String metricName = "user:activity:" + activity;
        recordMetric(metricName, 1.0);
    }
}
```

### 5. Redis搜索

#### 搜索服务

```java
@Service
public class SearchService {
    
    @Autowired
    private RedisSearchService redisSearchService;
    
    public void indexDocument(String indexName, String docId, Map<String, Object> fields) {
        // 创建或更新文档索引
        redisSearchService.addDocument(indexName, docId, fields);
    }
    
    public List<Map<String, Object>> search(String indexName, String query) {
        // 执行搜索查询
        return redisSearchService.search(indexName, query);
    }
    
    public void createUserIndex() {
        // 创建用户索引
        Map<String, String> schema = new HashMap<>();
        schema.put("username", "TEXT");
        schema.put("email", "TEXT");
        schema.put("age", "NUMERIC");
        schema.put("status", "TAG");
        
        redisSearchService.createIndex("user_index", schema);
    }
}
```

### 6. 简单Redis操作

#### 计数器服务

```java
@Service
public class CounterService {
    
    @Autowired
    private SimpleRedisService simpleRedisService;
    
    public void incrementPageView(String pageId) {
        String indicator = "page:views";
        simpleRedisService.increment(indicator, pageId, 24 * 3600); // 24小时过期
    }
    
    public void incrementUserAction(String userId, String action) {
        String indicator = "user:actions:" + action;
        simpleRedisService.increment(indicator, userId);
    }
    
    public void decrementStock(String productId) {
        String indicator = "product:stock";
        simpleRedisService.decrement(indicator, productId, 3600); // 1小时过期
    }
}
```

## ⚙️ 高级配置

### 完整配置示例

```yaml
spring:
  redis:
    # 基础连接配置
    host: localhost
    port: 6379
    password: your_password
    database: 0
    timeout: 10s
    connect-timeout: 10s
    client-name: spring-app
    
    # 集群配置
    cluster:
      nodes:
        - 192.168.1.100:7000
        - 192.168.1.100:7001
        - 192.168.1.100:7002
      max-redirects: 3
    
    # 哨兵配置
    sentinel:
      master: mymaster
      nodes:
        - 192.168.1.100:26379
        - 192.168.1.101:26379
        - 192.168.1.102:26379
    
    # Lettuce连接池配置
    lettuce:
      pool:
        max-active: 20    # 最大连接数
        max-idle: 10      # 最大空闲连接数
        min-idle: 5       # 最小空闲连接数
        max-wait: 2s      # 最大等待时间

# 插件配置
plugin:
  redis:
    server:
      open-embedded: false    # 是否启用嵌入式Redis
      port: 6379             # 嵌入式Redis端口
      max-memory: 128mb      # 最大内存
      config-file: redis.conf # 配置文件路径
```

### 自定义缓存管理器

```java
@Configuration
public class CustomCacheConfig {
    
    @Bean("customCacheManager")
    public CacheManager customCacheManager(RedisConnectionFactory factory) {
        // 创建自定义过期时间的缓存管理器
        return CacheConfiguration.createRedisCacheManager(null, factory, 1800); // 30分钟
    }
    
    @Bean("shortTermCacheManager")
    public CacheManager shortTermCacheManager(RedisConnectionFactory factory) {
        // 短期缓存管理器
        return CacheConfiguration.createRedisCacheManager(null, factory, 300); // 5分钟
    }
}
```

## 🔧 自定义扩展

### 自定义Redis监听器

```java
@Component
public class CustomRedisListener implements RedisListener {
    
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String content = new String(message.getBody());
        
        // 自定义消息处理逻辑
        processMessage(channel, content);
    }
    
    @Override
    public Collection<Topic> getTopics() {
        return Arrays.asList(
            new ChannelTopic("custom:channel"),
            new PatternTopic("system:*")
        );
    }
    
    private void processMessage(String channel, String content) {
        log.info("处理消息 - 频道: {}, 内容: {}", channel, content);
        // 实现具体的消息处理逻辑
    }
}
```

### 自定义分布式锁

```java
@Component
public class CustomLockService {
    
    @Autowired
    private RedissonClient redissonClient;
    
    public <T> T executeWithLock(String lockKey, int waitTime, int leaseTime, 
                                TimeUnit unit, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            if (lock.tryLock(waitTime, leaseTime, unit)) {
                return supplier.get();
            } else {
                throw new RuntimeException("获取锁失败: " + lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

## 📝 注意事项

1. **连接池配置**：根据应用负载合理配置连接池参数
2. **序列化选择**：选择合适的序列化方式，平衡性能和兼容性
3. **缓存过期**：合理设置缓存过期时间，避免内存泄漏
4. **分布式锁**：注意锁的超时时间设置，避免死锁
5. **消息队列**：处理消息时要考虑幂等性和异常处理
6. **嵌入式Redis**：仅用于测试环境，生产环境使用独立Redis服务

## 🐛 故障排除

### 常见问题

1. **连接超时**
   - 检查Redis服务器状态
   - 验证网络连接
   - 调整超时配置

2. **序列化错误**
   - 检查对象是否可序列化
   - 验证序列化配置
   - 确认类路径一致性

3. **缓存不生效**
   - 检查缓存管理器配置
   - 验证缓存注解使用
   - 确认方法是否被代理

### 调试建议

启用调试日志：

```yaml
logging:
  level:
    com.chua.starter.redis: DEBUG
    org.springframework.data.redis: DEBUG
    org.redisson: DEBUG
```
