package com.chua.starter.monitor.support.provider.redis;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.redis.support.client.RedisClient;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.service.MonitorConfigService;
import com.chua.starter.monitor.support.spi.CollectContext;
import com.chua.starter.monitor.support.spi.OperationProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Redis 操作 提供者
 * <p>
 * 支持 Redis 键 的扫描、读取、写入、删除、过期设置等操作。
 * 连接参数从任务配置 {@code collect_config} 中获取。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@Slf4j
@Spi(value = "redis", order = 100)
public class RedisOperationProvider implements OperationProvider {

    /**
     * 允许删除的配置键
     */
    private static final String CONFIG_KEY_ALLOW_DELETE = "monitor.redis.allowDelete";

    /**
     * 全局配置 服务
     */
    @Autowired
    private MonitorConfigService configService;

    @Override
    /**
     * 服务类型
    */
    public ServiceType serviceType() {
        return ServiceType.REDIS;
    }

    @Override
    /**
     * 扫描键
    */
    public Mono<ScanResult> scanKeys(CollectContext ctx, String pattern, String cursor, int count) {
        return Mono.fromCallable(() -> doScanKeys(ctx, pattern, cursor, count))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    /**
     * 获取值
    */
    public Mono<String> getValue(CollectContext ctx, String key) {
        return Mono.fromCallable(() -> {
            /**
             * 尝试。
             * @param buildClient(ctx 构建客户端(ctx
             */
            try (RedisClient client = buildClient(ctx)) {
                return client.get(key);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    /**
     * 设置值
    */
    public Mono<Boolean> setValue(CollectContext ctx, String key, String value, Duration ttl) {
        return Mono.fromCallable(() -> {
            /**
             * 尝试。
             * @param buildClient(ctx 构建客户端(ctx
             */
            try (RedisClient client = buildClient(ctx)) {
                if (ttl != null) {
                    client.put(key, value, Duration.ofSeconds(ttl.getSeconds()));
                } else {
                    client.put(key, value);
                }
                return true;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    /**
     * 删除键
    */
    public Mono<Long> deleteKeys(CollectContext ctx, List<String> keys) {
        return Mono.fromCallable(() -> {
            if (!configService.getBoolean(CONFIG_KEY_ALLOW_DELETE, false)) {
                /**
                 * illegal状态异常。
                 * @param monitor.redis.allowDelete=true" 监控.Redis.allow删除=true"
                 * @return illegal状态异常的结果
                 */
                throw new IllegalStateException("Redis 删除操作已禁用，请设置 monitor.redis.allowDelete=true");
            }
            /**
             * 尝试。
             * @param buildClient(ctx 构建客户端(ctx
             */
            try (RedisClient client = buildClient(ctx)) {
                long count = 0;
                for (String key : keys) {
                    if (client.delete(key)) {
                        count++;
                    }
                }
                return count;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    /**
     * Expire
    */
    public Mono<Boolean> expire(CollectContext ctx, String key, Duration ttl) {
        return Mono.fromCallable(() -> {
            /**
             * 尝试。
             * @param buildClient(ctx 构建客户端(ctx
             */
            try (RedisClient client = buildClient(ctx)) {
                client.expire(key, ttl.getSeconds());
                return true;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    /**
     * Ttl
    */
    public Mono<Long> ttl(CollectContext ctx, String key) {
        return Mono.fromCallable(() -> {
            /**
             * 尝试。
             * @param buildClient(ctx 构建客户端(ctx
             */
            try (RedisClient client = buildClient(ctx)) {
                return client.ttl(key);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    /**
     * 类型
    */
    public Mono<String> type(CollectContext ctx, String key) {
        return Mono.fromCallable(() -> {
            /**
             * 尝试。
             * @param buildClient(ctx 构建客户端(ctx
             */
            try (RedisClient client = buildClient(ctx)) {
                Object result = client.reactiveExecCommand("TYPE " + key).block();
                return result != null ? result.toString() : "none";
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 同步执行 键 扫描 (使用 Jedis 扫描 游标迭代, 避免 键 阻塞)
     *
     * @param ctx     操作上下文
     * @param pattern 匹配模式
     * @param cursor  游标
     * @param count   期望返回条数
     * @return 扫描结果
     */
    private OperationProvider.ScanResult doScanKeys(CollectContext ctx, String pattern, String cursor, int count) {
        /**
         * 尝试。
         * @param buildClient(ctx 构建客户端(ctx
         */
        try (RedisClient client = buildClient(ctx)) {
            String match = pattern != null ? pattern : "*";
            List<String> keys = new ArrayList<>();
            java.util.Map<String, String> result = client.findAllByPrefix(
                    match.endsWith("*") ? match.substring(0, match.length() - 1) : match);
            if (result != null) {
                for (java.util.Map.Entry<String, String> entry : result.entrySet()) {
                    keys.add(entry.getKey());
                    if (keys.size() >= count * 10) {
                        break;
                    }
                }
            }
            return new OperationProvider.ScanResult(keys, null);
        }
    }

    /**
     * 构建 Redis 客户端
     *
     * @param ctx 操作上下文
     * @return Redis 客户端
     */
    private RedisClient buildClient(CollectContext ctx) {
        JsonObject config = Json.getJsonObject(ctx.getTask().getCollectConfig());
        String host = str(config.getObject("redis_host"));
        int port = NumberUtils.toInt(str(config.getObject("redis_port")), 6379);
        String password = str(config.getObject("redis_password"));
        int db = NumberUtils.toInt(str(config.getObject("redis_db")), 0);
        int timeout = NumberUtils.toInt(str(config.getObject("redis_timeout")), 5000);
        return RedisClient.builder()
                .host(host != null ? host : "127.0.0.1")
                .port(port)
                .password(password != null ? password : "")
                .database(db)
                .timeout(timeout)
                .build();
    }

    /**
     * 对象转字符串
     *
     * @param o 对象
     * @return 字符串
     */
    private static String str(Object o) {
        return StringUtils.toString(o);
    }
}
