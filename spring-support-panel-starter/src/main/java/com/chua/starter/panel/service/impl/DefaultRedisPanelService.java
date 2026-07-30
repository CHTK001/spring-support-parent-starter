package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.model.*;
import com.chua.starter.panel.service.RedisPanelService;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.resps.Tuple;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 Redis 面板服务（基于 Jedis）。
 *
 * @author CH
 */
public class DefaultRedisPanelService implements RedisPanelService {

    private final Map<String, JedisPool> poolMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> dbMap = new ConcurrentHashMap<>();

    @Override
    public String open(String host, int port, String password, int db) {
        String connectionId = UUID.randomUUID().toString();
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(8);
        config.setMaxIdle(4);
        config.setMinIdle(1);
        config.setTestOnBorrow(true);

        JedisPool pool;
        if (password != null && !password.isBlank()) {
            pool = new JedisPool(config, host, port, 5000, password, db);
        } else {
            pool = new JedisPool(config, host, port, 5000, null, db);
        }
        // 测试连接
        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
        }
        poolMap.put(connectionId, pool);
        dbMap.put(connectionId, db);
        return connectionId;
    }

    @Override
    public void close(String connectionId) {
        JedisPool pool = poolMap.remove(connectionId);
        dbMap.remove(connectionId);
        if (pool != null) {
            pool.close();
        }
    }

    @Override
    public PanelRedisInfoView info(String connectionId) {
        try (Jedis jedis = getJedis(connectionId)) {
            String rawInfo = jedis.info();
            Map<String, String> infoMap = parseInfo(rawInfo);

            Map<String, Long> dbKeyCounts = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : infoMap.entrySet()) {
                if (entry.getKey().startsWith("db")) {
                    String dbSection = entry.getValue();
                    long keys = parseDbKeys(dbSection);
                    dbKeyCounts.put(entry.getKey(), keys);
                }
            }

            return PanelRedisInfoView.builder()
                    .version(infoMap.getOrDefault("redis_version", ""))
                    .mode(infoMap.getOrDefault("redis_mode", "standalone"))
                    .usedMemory(parseLong(infoMap.get("used_memory")))
                    .connectedClients(parseLong(infoMap.get("connected_clients")))
                    .totalCommandsProcessed(parseLong(infoMap.get("total_commands_processed")))
                    .uptimeInSeconds(parseLong(infoMap.get("uptime_in_seconds")))
                    .totalDatabases(parseInt(infoMap.getOrDefault("databases", "16")))
                    .dbKeyCounts(dbKeyCounts)
                    .rawInfo(infoMap)
                    .build();
        }
    }

    @Override
    public void selectDb(String connectionId, int db) {
        dbMap.put(connectionId, db);
    }

    @Override
    public List<PanelRedisKeyView> scan(String connectionId, String pattern, int count) {
        try (Jedis jedis = getJedis(connectionId)) {
            List<PanelRedisKeyView> result = new ArrayList<>();
            String cursor = "0";
            ScanParams params = new ScanParams()
                    .match(pattern == null || pattern.isBlank() ? "*" : pattern)
                    .count(count <= 0 ? 100 : count);
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, params);
                cursor = scanResult.getCursor();
                for (String key : scanResult.getResult()) {
                    String type = jedis.type(key);
                    long ttl = jedis.ttl(key);
                    result.add(PanelRedisKeyView.builder()
                            .key(key)
                            .type(type)
                            .ttl(ttl)
                            .build());
                }
                if (result.size() >= (count <= 0 ? 200 : count)) break;
            } while (!"0".equals(cursor));
            return result;
        }
    }

    @Override
    public PanelRedisValueView get(String connectionId, String key) {
        try (Jedis jedis = getJedis(connectionId)) {
            String type = jedis.type(key);
            long ttl = jedis.ttl(key);
            PanelRedisValueView.PanelRedisValueViewBuilder builder = PanelRedisValueView.builder()
                    .key(key).type(type).ttl(ttl);
            switch (type) {
                case "string" -> builder.stringValue(jedis.get(key));
                case "hash" -> builder.hashValue(jedis.hgetAll(key));
                case "list" -> builder.listValue(jedis.lrange(key, 0, 499));
                case "set" -> builder.setValue(new ArrayList<>(jedis.smembers(key)));
                case "zset" -> {
                    List<Tuple> tuples = jedis.zrangeWithScores(key, 0, 499);
                    Map<String, Double> zset = new LinkedHashMap<>();
                    for (Tuple t : tuples) zset.put(t.getElement(), t.getScore());
                    builder.zsetValue(zset);
                }
            }
            return builder.build();
        }
    }

    @Override
    public void set(String connectionId, PanelRedisSetRequest request) {
        try (Jedis jedis = getJedis(connectionId)) {
            jedis.set(request.getKey(), request.getValue());
            if (request.getTtl() > 0) {
                jedis.expire(request.getKey(), request.getTtl());
            }
        }
    }

    @Override
    public long del(String connectionId, String... keys) {
        try (Jedis jedis = getJedis(connectionId)) {
            return jedis.del(keys);
        }
    }

    @Override
    public boolean expire(String connectionId, String key, long seconds) {
        try (Jedis jedis = getJedis(connectionId)) {
            return jedis.expire(key, seconds) == 1L;
        }
    }

    @Override
    /**
     * 执行 Redis 命令文本。
     * <p>
     * 该方法用于面板命令行快速调试，优先覆盖常见读写命令，并对包含空格的 value/member 做分段解析。
     *
     * @param connectionId Redis 连接标识
     * @param command Redis 命令文本
     * @return 命令执行结果；参数不足时返回错误提示
     */
    public Object execute(String connectionId, String command) {
        if (command == null || command.isBlank()) return null;
        String normalized = command.trim();
        String[] head = normalized.split("\\s+", 2);
        String cmd = head[0].toUpperCase();
        try (Jedis jedis = getJedis(connectionId)) {
            String[] parts = head;
            return switch (cmd) {
                case "PING" -> jedis.ping();
                case "DBSIZE" -> jedis.dbSize();
                case "INFO" -> jedis.info(parts.length > 1 ? parts[1] : "all");
                case "GET" -> parts.length > 1 ? jedis.get(parts[1]) : "ERR wrong number of arguments";
                case "HGET" -> {
                    String[] hgetParts = normalized.split("\\s+", 3);
                    if (hgetParts.length < 3) yield "ERR wrong number of arguments";
                    yield jedis.hget(hgetParts[1], hgetParts[2]);
                }
                case "HGETALL" -> parts.length > 1 ? jedis.hgetAll(parts[1]) : "ERR wrong number of arguments";
                case "HLEN" -> parts.length > 1 ? jedis.hlen(parts[1]) : "ERR wrong number of arguments";
                case "HEXISTS" -> {
                    String[] hexistsParts = normalized.split("\\s+", 3);
                    if (hexistsParts.length < 3) yield "ERR wrong number of arguments";
                    yield jedis.hexists(hexistsParts[1], hexistsParts[2]);
                }
                case "LLEN" -> parts.length > 1 ? jedis.llen(parts[1]) : "ERR wrong number of arguments";
                case "LRANGE" -> {
                    String[] lrangeParts = normalized.split("\\s+");
                    if (lrangeParts.length < 4) yield "ERR wrong number of arguments";
                    yield jedis.lrange(lrangeParts[1], Long.parseLong(lrangeParts[2]), Long.parseLong(lrangeParts[3]));
                }
                case "SMEMBERS" -> parts.length > 1 ? jedis.smembers(parts[1]) : "ERR wrong number of arguments";
                case "SCARD" -> parts.length > 1 ? jedis.scard(parts[1]) : "ERR wrong number of arguments";
                case "SISMEMBER" -> {
                    String[] sismemberParts = normalized.split("\\s+", 3);
                    if (sismemberParts.length < 3) yield "ERR wrong number of arguments";
                    yield jedis.sismember(sismemberParts[1], sismemberParts[2]);
                }
                case "ZRANGE" -> {
                    String[] zrangeParts = normalized.split("\\s+");
                    if (zrangeParts.length < 4) yield "ERR wrong number of arguments";
                    yield jedis.zrange(zrangeParts[1], Long.parseLong(zrangeParts[2]), Long.parseLong(zrangeParts[3]));
                }
                case "ZRANGEBYSCORE" -> {
                    String[] zrangeByScoreParts = normalized.split("\\s+");
                    if (zrangeByScoreParts.length < 4) yield "ERR wrong number of arguments";
                    yield jedis.zrangeByScore(zrangeByScoreParts[1], Double.parseDouble(zrangeByScoreParts[2]), Double.parseDouble(zrangeByScoreParts[3]));
                }
                case "ZCARD" -> parts.length > 1 ? jedis.zcard(parts[1]) : "ERR wrong number of arguments";
                case "TTL" -> parts.length > 1 ? jedis.ttl(parts[1]) : "ERR wrong number of arguments";
                case "TYPE" -> parts.length > 1 ? jedis.type(parts[1]) : "ERR wrong number of arguments";
                case "EXISTS" -> parts.length > 1 ? jedis.exists(parts[1]) : "ERR wrong number of arguments";
                case "DEL" -> parts.length > 1 ? jedis.del(parts[1]) : "ERR wrong number of arguments";
                case "KEYS" -> jedis.keys(parts.length > 1 ? parts[1] : "*");
                case "FLUSHDB" -> jedis.flushDB();
                case "EXPIRE" -> {
                    String[] expireParts = normalized.split("\\s+", 3);
                    if (expireParts.length < 3) yield "ERR wrong number of arguments";
                    yield jedis.expire(expireParts[1], Long.parseLong(expireParts[2]));
                }
                case "SET" -> {
                    // SET key value（value 可能包含空格）
                    String[] setParts = normalized.split("\\s+", 3);
                    if (setParts.length < 3) yield "ERR wrong number of arguments";
                    yield jedis.set(setParts[1], setParts[2]);
                }
                case "HSET" -> {
                    // HSET key field value（value 可能包含空格）
                    String[] hsetParts = normalized.split("\\s+", 4);
                    if (hsetParts.length < 4) yield "ERR wrong number of arguments";
                    yield jedis.hset(hsetParts[1], hsetParts[2], hsetParts[3]);
                }
                case "HDEL" -> {
                    String[] hdelParts = normalized.split("\\s+", 3);
                    if (hdelParts.length < 3) yield "ERR wrong number of arguments";
                    yield jedis.hdel(hdelParts[1], hdelParts[2]);
                }
                case "RPUSH" -> {
                    // RPUSH key value（value 可能包含空格）
                    String[] rpushParts = normalized.split("\\s+", 3);
                    if (rpushParts.length < 3) yield "ERR wrong number of arguments";
                    yield jedis.rpush(rpushParts[1], rpushParts[2]);
                }
                case "LPUSH" -> {
                    String[] lpushParts = normalized.split("\\s+", 3);
                    if (lpushParts.length < 3) yield "ERR wrong number of arguments";
                    yield jedis.lpush(lpushParts[1], lpushParts[2]);
                }
                case "LPOP" -> parts.length > 1 ? jedis.lpop(parts[1]) : "ERR wrong number of arguments";
                case "RPOP" -> parts.length > 1 ? jedis.rpop(parts[1]) : "ERR wrong number of arguments";
                case "SADD" -> {
                    // SADD key member（member 可能包含空格）
                    String[] saddParts = normalized.split("\\s+", 3);
                    if (saddParts.length < 3) yield "ERR wrong number of arguments";
                    yield jedis.sadd(saddParts[1], saddParts[2]);
                }
                case "SREM" -> {
                    String[] sremParts = normalized.split("\\s+", 3);
                    if (sremParts.length < 3) yield "ERR wrong number of arguments";
                    yield jedis.srem(sremParts[1], sremParts[2]);
                }
                case "ZADD" -> {
                    // ZADD key score member（member 可能包含空格）
                    String[] zaddParts = normalized.split("\\s+", 4);
                    if (zaddParts.length < 4) yield "ERR wrong number of arguments";
                    yield jedis.zadd(zaddParts[1], Double.parseDouble(zaddParts[2]), zaddParts[3]);
                }
                case "ZREM" -> {
                    String[] zremParts = normalized.split("\\s+", 3);
                    if (zremParts.length < 3) yield "ERR wrong number of arguments";
                    yield jedis.zrem(zremParts[1], zremParts[2]);
                }
                default -> "ERR unsupported command: " + cmd;
            };
        }
    }

    private Jedis getJedis(String connectionId) {
        JedisPool pool = poolMap.get(connectionId);
        if (pool == null) throw new IllegalStateException("Redis 连接不存在: " + connectionId);
        Jedis jedis = pool.getResource();
        Integer db = dbMap.get(connectionId);
        if (db != null && db > 0) jedis.select(db);
        return jedis;
    }

    private Map<String, String> parseInfo(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null) return map;
        for (String line : raw.split("\r?\n")) {
            if (line.startsWith("#") || line.isBlank()) continue;
            int idx = line.indexOf(':');
            if (idx > 0) map.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
        }
        return map;
    }

    private long parseDbKeys(String dbSection) {
        if (dbSection == null) return 0;
        for (String part : dbSection.split(",")) {
            if (part.startsWith("keys=")) {
                try { return Long.parseLong(part.substring(5)); } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    private long parseLong(String value) {
        if (value == null) return 0;
        try { return Long.parseLong(value.replaceAll("[^0-9]", "")); } catch (NumberFormatException e) { return 0; }
    }

    private int parseInt(String value) {
        if (value == null) return 16;
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return 16; }
    }
}
