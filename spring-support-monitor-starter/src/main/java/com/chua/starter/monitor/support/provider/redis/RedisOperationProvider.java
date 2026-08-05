package com.chua.starter.monitor.support.provider.redis;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.redis.support.client.RedisClient;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.spi.CollectContext;
import com.chua.starter.monitor.support.spi.OperationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Redis 操作 Provider
 * <p>
 * 支持 Redis key 的扫描、读取、写入、删除、过期设置等操作。
 * 连接参数从任务配置 {@code collect_config} 中获取。
 * </p>
 *
 * @author CH
 * @since 2026/8/4
 */
@Slf4j
@Component
public class RedisOperationProvider implements OperationProvider {

    @Override
    public ServiceType serviceType() {
        return ServiceType.REDIS;
    }

    @Override
    public Mono<ScanResult> scanKeys(CollectContext ctx, String pattern, String cursor, int count) {
        return Mono.fromCallable(() -> doScanKeys(ctx, pattern, cursor, count))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<String> getValue(CollectContext ctx, String key) {
        return Mono.fromCallable(() -> {
            try (RedisClient client = buildClient(ctx)) {
                return client.get(key);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> setValue(CollectContext ctx, String key, String value, Duration ttl) {
        return Mono.fromCallable(() -> {
            try (RedisClient client = buildClient(ctx)) {
                if (ttl != null) {
                    client.set(key, value, (int) ttl.getSeconds());
                } else {
                    client.set(key, value);
                }
                return true;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> deleteKeys(CollectContext ctx, List<String> keys) {
        return Mono.fromCallable(() -> {
            try (RedisClient client = buildClient(ctx)) {
                return client.del(keys.toArray(new String[0]));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> expire(CollectContext ctx, String key, Duration ttl) {
        return Mono.fromCallable(() -> {
            try (RedisClient client = buildClient(ctx)) {
                client.expire(key, ttl.getSeconds());
                return true;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ScanResult doScanKeys(CollectContext ctx, String pattern, String cursor, int count) {
        try (RedisClient client = buildClient(ctx)) {
            Set<String> keys = client.keys(pattern != null ? pattern : "*");
            return new ScanResult(new ArrayList<>(keys), null);
        }
    }

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

    private static String str(Object o) {
        return StringUtils.toString(o);
    }
}