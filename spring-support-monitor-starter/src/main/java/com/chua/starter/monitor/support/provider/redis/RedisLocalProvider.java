package com.chua.starter.monitor.support.provider.redis;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.redis.support.client.RedisClient;
import com.chua.starter.monitor.support.enums.ProviderType;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.spi.CollectContext;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import com.chua.starter.monitor.support.spi.MetricProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
* Redis 本地采集 提供者
* <p>
* 通过 Jedis 直连 Redis, 执行 信息 命令解析关键指标(内存/连接/命中率/键数量等),
* 无需部署 exporter。
* </p>
*
* @since 2026/8/4
* @author CH
 */
@Slf4j
@Component
public class RedisLocalProvider implements MetricProvider {

    @Override
    /** 提供者类型 */
    public ProviderType providerType() {
        return ProviderType.LOCAL;
    }

    @Override
    /** 支持服务类型 */
    public Set<ServiceType> supportServiceTypes() {
        return Set.of(ServiceType.REDIS);
    }

    @Override
    /** Collect */
    public Mono<CollectResult> collect(CollectContext context) {
        return Mono.fromCallable(() -> doCollect(context))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
    * 同步执行 信息 采集
    *
    * @param context 采集上下文
    * @return 采集结果
     */

    private CollectResult doCollect(CollectContext context) {
        CollectResult result = new CollectResult();
        result.setTask(context.getTask());
        result.setMonitor(context.getMonitor());

        JsonObject config = Json.getJsonObject(context.getTask().getCollectConfig());
        String host = str(config.getObject("redis_host"));
        if (host == null || host.isEmpty()) {
            host = context.getMonitor() == null ? null : context.getMonitor().getMonitorHost();
        }
        if (host == null) {
            return result.fail("collect_config 缺少 redis_host");
        }
        int port = intOrZero(config.getObject("redis_port"));
        if (port == 0) {
            port = context.getMonitor() == null || context.getMonitor().getMonitorPort() == null
                    ? 6379 : context.getMonitor().getMonitorPort();
        }
        String password = str(config.getObject("redis_password"));
        int database = intOrZero(config.getObject("redis_db"));

        try (RedisClient client = RedisClient.builder()
                .host(host)
                .port(port)
                .password(password == null ? "" : password)
                .database(database)
                .timeout(context.getTask().getMonitorTaskTimeout() == null
                        ? 5000 : context.getTask().getMonitorTaskTimeout())
                .build()) {

            String info = fetchInfo(client);
            result.setRawOutput(info);
            /**
            * 解析信息。
            * @param info 信息
            * @param result 结果
             */
            parseInfo(info, result);
            result.setSuccess(true);
        } catch (Exception e) {
            log.error("[RedisLocalProvider] 采集失败 task={} host={}", context.getTask().getId(), host, e);
            result.fail(e.getMessage());
        }
        return result;
    }

    /**
    * 获取 信息 全量输出
    *
    * @param client Redis 客户端
    * @return INFO 文本
     */
    private String fetchInfo(RedisClient client) {
        Object result = client.reactiveExecCommand("INFO").block();
        return result != null ? result.toString() : null;
    }

    /**
    * 解析 信息 输出为指标点
    *
    * @param info   信息 文本
    * @param result 结果
     */
    private void parseInfo(String info, CollectResult result) {
        if (info == null) {
            return;
        }
        Map<String, Double> metrics = new HashMap<>();
        for (String line : info.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int idx = trimmed.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = trimmed.substring(0, idx).trim();
            String value = trimmed.substring(idx + 1).trim();
 // keyspace 段：db0:钥匙=123,expires=2,avg_ttl=0 —— 累加各 db 的 钥匙 总数
            if (key.startsWith("db") && value.contains("keys=")) {
                int ks = value.indexOf("keys=");
                int comma = value.indexOf(',', ks);
                String keysRaw = comma > ks ? value.substring(ks + 5, comma) : value.substring(ks + 5);
                Double kc = parseDouble(keysRaw);
                if (kc != null) {
                    Double acc = metrics.get("_db_keys_total");
                    metrics.put("_db_keys_total", acc == null ? kc : acc + kc);
                }
                continue;
            }
            Double num = parseDouble(value);
            if (num != null) {
                metrics.put(key, num);
            }
        }

        // 关键指标
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_connected_clients" "Redis连接客户端"
        * @param metrics.get("connected_clients" 指标.获取("连接_客户端"
         */
        addMetric(result, "redis_connected_clients", metrics.get("connected_clients"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_used_memory_bytes" "redisused内存bytes"
        * @param metrics.get("used_memory" 指标.获取("used_内存"
         */
        addMetric(result, "redis_used_memory_bytes", metrics.get("used_memory"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_used_memory_peak_bytes" "redisused内存peakbytes"
        * @param metrics.get("used_memory_peak" 指标.获取("used内存peak"
         */
        addMetric(result, "redis_used_memory_peak_bytes", metrics.get("used_memory_peak"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_maxmemory_bytes" "Redis_maxmemory_bytes"
        * @param metrics.get("maxmemory" 指标.获取("maxmemory"
         */
        addMetric(result, "redis_maxmemory_bytes", metrics.get("maxmemory"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_total_keys" "Redis_total_键"
        * @param metrics.get("total_keys" 指标.获取("total_键"
         */
        addMetric(result, "redis_total_keys", metrics.get("total_keys"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_expired_keys" "Redis_expired_键"
        * @param metrics.get("expired_keys" 指标.获取("expired_键"
         */
        addMetric(result, "redis_expired_keys", metrics.get("expired_keys"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_hits" "Redis_hits"
        * @param metrics.get("keyspace_hits" 指标.获取("keyspace_hits"
         */
        addMetric(result, "redis_hits", metrics.get("keyspace_hits"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_misses" "Redis_misses"
        * @param metrics.get("keyspace_misses" 指标.获取("keyspace_misses"
         */
        addMetric(result, "redis_misses", metrics.get("keyspace_misses"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_evicted_keys" "Redis_evicted_键"
        * @param metrics.get("evicted_keys" 指标.获取("evicted_键"
         */
        addMetric(result, "redis_evicted_keys", metrics.get("evicted_keys"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_total_commands" "Redis_total_命令"
        * @param metrics.get("total_commands_processed" 指标.获取("total命令处理"
         */
        addMetric(result, "redis_total_commands", metrics.get("total_commands_processed"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_total_connections" "Redis_total_connections"
        * @param metrics.get("total_connections_received" 指标.获取("total_connections_接收"
         */
        addMetric(result, "redis_total_connections", metrics.get("total_connections_received"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_uptime_seconds" "Redis_uptime_seconds"
        * @param metrics.get("uptime_in_seconds" 指标.获取("uptime入seconds"
         */
        addMetric(result, "redis_uptime_seconds", metrics.get("uptime_in_seconds"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_blocked_clients" "Redis_blocked_客户端"
        * @param metrics.get("blocked_clients" 指标.获取("blocked_客户端"
         */
        addMetric(result, "redis_blocked_clients", metrics.get("blocked_clients"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_rejected_connections" "Redis_rejected_connections"
        * @param metrics.get("rejected_connections" 指标.获取("rejected_connections"
         */
        addMetric(result, "redis_rejected_connections", metrics.get("rejected_connections"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_instaneous_ops" "Redis_instaneous_ops"
        * @param metrics.get("instantaneous_ops_per_sec" 指标.获取("instantaneous_ops_per_sec"
         */
        addMetric(result, "redis_instaneous_ops", metrics.get("instantaneous_ops_per_sec"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_connected_slaves" "Redis连接slaves"
        * @param metrics.get("connected_slaves" 指标.获取("连接_slaves"
         */
        addMetric(result, "redis_connected_slaves", metrics.get("connected_slaves"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_aof_enabled" "Redis_aof_已启用"
        * @param metrics.get("aof_enabled" 指标.获取("aof_已启用"
         */
        addMetric(result, "redis_aof_enabled", metrics.get("aof_enabled"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_cpu_user" "Redis_cpu_用户"
        * @param metrics.get("used_cpu_user" 指标.获取("used_cpu_用户"
         */
        addMetric(result, "redis_cpu_user", metrics.get("used_cpu_user"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_cpu_sys" "Redis_cpu_sys"
        * @param metrics.get("used_cpu_sys" 指标.获取("used_cpu_sys"
         */
        addMetric(result, "redis_cpu_sys", metrics.get("used_cpu_sys"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_rdb_last_save" "redisrdb最后一个保存"
        * @param metrics.get("rdb_last_save" 指标.获取("rdb最后一个保存"
         */
        addMetric(result, "redis_rdb_last_save", metrics.get("rdb_last_save"));
        /**
        * 添加指标。
        * @param result 结果
        * @param "redis_db_keys_total" "redisdb钥匙total"
        * @param metrics.get("_db_keys_total" 指标.获取("db钥匙total"
         */
        addMetric(result, "redis_db_keys_total", metrics.get("_db_keys_total"));

        // 命中率
        Double hits = metrics.get("keyspace_hits");
        Double misses = metrics.get("keyspace_misses");
        if (hits != null && misses != null && (hits + misses) > 0) {
            /**
            * 添加指标。
            * @param result 结果
            * @param "redis_hit_rate" "Redis_hit_rate"
            * @param misses misses
             */
            addMetric(result, "redis_hit_rate", hits * 100.0 / (hits + misses));
        }
    }

    /**
    * 添加指标点
    *
    * @param result 采集结果
    * @param name   指标名
    * @param value  指标值
     */
    private void addMetric(CollectResult result, String name, Double value) {
        if (value == null) {
            return;
        }
        result.addPoint(MetricPoint.builder()
                .metricName(name)
                .value(value)
                .unit("")
                .tags(new HashMap<>())
                .targetTable("ms_monitor_redis_metric")
                .build());
    }

    /**
    * 解析数值(兼容带单位的字节数, 如 12345 或 1.5M)
    * @param raw raw
    * @return 解析double的结果
     */
    private static Double parseDouble(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            String s = raw.trim().toUpperCase();
            double factor = 1.0;
            if (s.endsWith("K")) {
                factor = 1024.0;
                s = s.substring(0, s.length() - 1);
            } else if (s.endsWith("M")) {
                factor = 1024.0 * 1024.0;
                s = s.substring(0, s.length() - 1);
            } else if (s.endsWith("G")) {
                factor = 1024.0 * 1024.0 * 1024.0;
                s = s.substring(0, s.length() - 1);
            } else if (s.endsWith("B")) {
                s = s.substring(0, s.length() - 1);
            }
            return Double.parseDouble(s.trim()) * factor;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
    * 对象转字符串
    *
    * @param o 对象
    * @return 字符串
     */
    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
    * 对象转 int
    *
    * @param o 对象
    * @return int, 转换失败返回 0
     */
    private static int intOrZero(Object o) {
        if (o == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}