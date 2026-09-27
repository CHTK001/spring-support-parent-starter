package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.monitor.support.model.RedisHashFieldVO;
import com.chua.starter.monitor.support.model.RedisKeyDetailVO;
import com.chua.starter.monitor.support.model.RedisManageCreateKeyDTO;
import com.chua.starter.monitor.support.model.RedisManageDatabaseVO;
import com.chua.starter.monitor.support.model.RedisManageKeyVO;
import com.chua.starter.monitor.support.model.RedisManageScanResultVO;
import com.chua.starter.monitor.support.model.RedisMemberScoreVO;
import com.chua.starter.monitor.support.model.RedisStreamEntryVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.monitor.support.service.RedisManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.resps.StreamEntry;
import redis.clients.jedis.resps.Tuple;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据源级 Redis 管理实现：每次操作临时建立 Jedis 连接，用完即关。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisManageServiceImpl implements RedisManageService {

    /**
     * 大键保护：集合类型元素超过此数量只取前 N 条
     */
    private static final int MAX_ELEMS = 500;

    /**
     * 连接超时（毫秒）
     */
    private static final int TIMEOUT_MS = 5000;

    /**
     * 数据源服务
     */
    private final DatasourceService datasourceService;

    @Override
    public RedisManageScanResultVO scanKeys(Long datasourceId, int db, String pattern, String cursor, int count) {
        String match = (pattern == null || pattern.isBlank()) ? "*" : pattern;
        int per = count > 0 ? count : 100;
        try (Jedis jedis = open(datasourceId, db)) {
            ScanParams params = new ScanParams().match(match).count(per);
            String cur = (cursor == null || cursor.isBlank()) ? "0" : cursor;
            ScanResult<String> scanned = jedis.scan(cur, params);
            List<RedisManageKeyVO> list = new ArrayList<>();
            for (String key : scanned.getResult()) {
                String type = jedis.type(key).toUpperCase();
                long ttl = jedis.ttl(key);
                list.add(new RedisManageKeyVO(key, type, ttl));
            }
            return new RedisManageScanResultVO(list, scanned.getCursor(), list.size());
        }
    }

    @Override
    public RedisKeyDetailVO keyDetail(Long datasourceId, int db, String key) {
        try (Jedis jedis = open(datasourceId, db)) {
            if (!jedis.exists(key)) {
                throw new IllegalStateException("键不存在: " + key);
            }
            String type = jedis.type(key).toUpperCase();
            long ttl = jedis.ttl(key);
            long size;
            Object value;
            switch (type) {
                case "STRING" -> {
                    String v = jedis.get(key);
                    size = v == null ? 0 : v.length();
                    value = v == null ? "" : v;
                }
                case "HASH" -> {
                    Map<String, String> all = jedis.hgetAll(key);
                    size = all.size();
                    value = truncateMap(all, MAX_ELEMS);
                }
                case "LIST" -> {
                    long len = jedis.llen(key);
                    size = len;
                    value = jedis.lrange(key, 0, Math.min(len, MAX_ELEMS) - 1);
                }
                case "SET" -> {
                    Set<String> all = jedis.smembers(key);
                    size = all.size();
                    value = truncateList(new ArrayList<>(all), MAX_ELEMS);
                }
                case "ZSET" -> {
                    long zlen = jedis.zcard(key);
                    size = zlen;
                    List<Tuple> tuples = jedis.zrangeWithScores(key, 0, Math.min(zlen, MAX_ELEMS) - 1);
                    List<RedisMemberScoreVO> members = new ArrayList<>(tuples.size());
                    for (Tuple t : tuples) {
                        members.add(new RedisMemberScoreVO(t.getElement(), t.getScore()));
                    }
                    value = members;
                }
                case "STREAM" -> {
                    long slen = jedis.xlen(key);
                    size = slen;
                    List<StreamEntry> entries = jedis.xrange(key, StreamEntryID.MINIMUM_ID, StreamEntryID.MAXIMUM_ID, MAX_ELEMS);
                    List<RedisStreamEntryVO> msgs = new ArrayList<>(entries.size());
                    for (StreamEntry e : entries) {
                        List<RedisHashFieldVO> fields = new ArrayList<>();
                        Map<String, String> f = e.getFields();
                        if (f != null) {
                            for (Map.Entry<String, String> en : f.entrySet()) {
                                fields.add(new RedisHashFieldVO(en.getKey(), en.getValue() == null ? "" : en.getValue()));
                            }
                        }
                        msgs.add(new RedisStreamEntryVO(e.getID() == null ? "" : e.getID().toString(), fields));
                    }
                    value = msgs;
                }
                default -> {
                    size = 0;
                    value = "";
                }
            }
            return new RedisKeyDetailVO(key, type, ttl, value, size);
        }
    }

    @Override
    public boolean createKey(Long datasourceId, int db, RedisManageCreateKeyDTO dto) {
        if (dto == null || isBlank(dto.key())) {
            throw new IllegalArgumentException("键名不能为空");
        }
        String type = dto.type() == null ? "STRING" : dto.type().toUpperCase();
        try (Jedis jedis = open(datasourceId, db)) {
            if (jedis.exists(dto.key())) {
                throw new IllegalStateException("键已存在: " + dto.key());
            }
            switch (type) {
                case "STRING" -> jedis.set(dto.key(), dto.value() == null ? "" : dto.value());
                case "HASH" -> {
                    if (dto.hashFields() != null && !dto.hashFields().isEmpty()) {
                        Map<String, String> map = new LinkedHashMap<>();
                        for (RedisHashFieldVO f : dto.hashFields()) {
                            if (f != null && !isBlank(f.field())) {
                                map.put(f.field(), f.value() == null ? "" : f.value());
                            }
                        }
                        if (!map.isEmpty()) {
                            jedis.hset(dto.key(), map);
                        }
                    }
                }
                case "LIST" -> {
                    if (dto.listItems() != null) {
                        for (String item : dto.listItems()) {
                            if (item != null) {
                                jedis.rpush(dto.key(), item);
                            }
                        }
                    }
                }
                case "SET" -> {
                    if (dto.setItems() != null) {
                        for (String item : dto.setItems()) {
                            if (item != null) {
                                jedis.sadd(dto.key(), item);
                            }
                        }
                    }
                }
                case "ZSET" -> {
                    if (dto.zsetMembers() != null) {
                        for (RedisMemberScoreVO m : dto.zsetMembers()) {
                            if (m != null && !isBlank(m.member())) {
                                jedis.zadd(dto.key(), m.score(), m.member());
                            }
                        }
                    }
                }
                default -> throw new IllegalArgumentException("不支持的键类型: " + type);
            }
            applyTtl(jedis, dto.key(), dto.ttl());
            return true;
        }
    }

    @Override
    public boolean setString(Long datasourceId, int db, String key, String value) {
        try (Jedis jedis = open(datasourceId, db)) {
            requireType(jedis, key, "STRING");
            jedis.set(key, value == null ? "" : value);
            return true;
        }
    }

    @Override
    public long deleteKeys(Long datasourceId, int db, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        try (Jedis jedis = open(datasourceId, db)) {
            return jedis.del(keys.toArray(new String[0]));
        }
    }

    @Override
    public boolean expireKey(Long datasourceId, int db, String key, long ttl) {
        try (Jedis jedis = open(datasourceId, db)) {
            if (!jedis.exists(key)) {
                throw new IllegalStateException("键不存在: " + key);
            }
            applyTtl(jedis, key, ttl);
            return true;
        }
    }

    @Override
    public boolean renameKey(Long datasourceId, int db, String key, String newKey) {
        if (isBlank(newKey)) {
            throw new IllegalArgumentException("新键名不能为空");
        }
        try (Jedis jedis = open(datasourceId, db)) {
            if (!jedis.exists(key)) {
                throw new IllegalStateException("键不存在: " + key);
            }
            jedis.rename(key, newKey);
            return true;
        }
    }

    @Override
    public Map<String, String> info(Long datasourceId) {
        try (Jedis jedis = open(datasourceId, 0)) {
            String raw = jedis.info();
            Map<String, String> all = parseInfo(raw);
            Map<String, String> picked = new LinkedHashMap<>();
            for (String k : new String[]{
                    "redis_version", "os", "uptime_in_seconds", "connected_clients",
                    "used_memory_human", "used_memory_peak_human", "instantaneous_ops_per_sec",
                    "keyspace_hits", "keyspace_misses", "role", "maxmemory_human",
                    "redis_mode", "tcp_port", "process_id"}) {
                if (all.containsKey(k)) {
                    picked.put(k, all.get(k));
                }
            }
            return picked;
        }
    }

    @Override
    public List<RedisManageDatabaseVO> databases(Long datasourceId) {
        try (Jedis jedis = open(datasourceId, 0)) {
            String keyspace = jedis.info("keyspace");
            Map<Integer, long[]> result = new LinkedHashMap<>();
            for (int i = 0; i <= 15; i++) {
                result.put(i, new long[]{0, 0});
            }
            for (String line : keyspace.split("\\r?\\n")) {
                line = line.trim();
                if (!line.startsWith("db")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String dbName = line.substring(0, colon);
                int dbNum;
                try {
                    dbNum = Integer.parseInt(dbName.substring(2));
                } catch (NumberFormatException e) {
                    continue;
                }
                String body = line.substring(colon + 1);
                long keys = 0;
                long expires = 0;
                for (String part : body.split(",")) {
                    String[] kv = part.split("=");
                    if (kv.length == 2) {
                        if ("keys".equals(kv[0])) {
                            keys = parseLong(kv[1]);
                        } else if ("expires".equals(kv[0])) {
                            expires = parseLong(kv[1]);
                        }
                    }
                }
                result.put(dbNum, new long[]{keys, expires});
            }
            List<RedisManageDatabaseVO> list = new ArrayList<>(16);
            for (Map.Entry<Integer, long[]> e : result.entrySet()) {
                list.add(new RedisManageDatabaseVO(e.getKey(), e.getValue()[0], e.getValue()[1]));
            }
            return list;
        }
    }

    @Override
    public boolean hset(Long datasourceId, int db, String key, String field, String value) {
        if (isBlank(field)) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        try (Jedis jedis = open(datasourceId, db)) {
            requireType(jedis, key, "HASH");
            boolean isNew = !jedis.hexists(key, field);
            jedis.hset(key, field, value == null ? "" : value);
            return isNew;
        }
    }

    @Override
    public long hdel(Long datasourceId, int db, String key, String field) {
        if (isBlank(field)) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        try (Jedis jedis = open(datasourceId, db)) {
            requireType(jedis, key, "HASH");
            return jedis.hdel(key, field);
        }
    }

    @Override
    public long rpush(Long datasourceId, int db, String key, String value) {
        try (Jedis jedis = open(datasourceId, db)) {
            requireType(jedis, key, "LIST");
            return jedis.rpush(key, value == null ? "" : value);
        }
    }

    @Override
    public boolean lset(Long datasourceId, int db, String key, long index, String value) {
        try (Jedis jedis = open(datasourceId, db)) {
            requireType(jedis, key, "LIST");
            long len = jedis.llen(key);
            if (index < 0 || index >= len) {
                throw new IllegalArgumentException("列表下标越界: " + index + "，当前长度 " + len);
            }
            jedis.lset(key, index, value == null ? "" : value);
            return true;
        }
    }

    @Override
    public long lremIndex(Long datasourceId, int db, String key, long index) {
        try (Jedis jedis = open(datasourceId, db)) {
            requireType(jedis, key, "LIST");
            long len = jedis.llen(key);
            if (index < 0 || index >= len) {
                throw new IllegalArgumentException("列表下标越界: " + index + "，当前长度 " + len);
            }
            String current = jedis.lindex(key, index);
            if (current == null) {
                return 0L;
            }
            return jedis.lrem(key, 1, current);
        }
    }

    @Override
    public boolean sadd(Long datasourceId, int db, String key, String member) {
        if (isBlank(member)) {
            throw new IllegalArgumentException("成员不能为空");
        }
        try (Jedis jedis = open(datasourceId, db)) {
            requireType(jedis, key, "SET");
            return jedis.sadd(key, member) > 0;
        }
    }

    @Override
    public long srem(Long datasourceId, int db, String key, String member) {
        if (isBlank(member)) {
            throw new IllegalArgumentException("成员不能为空");
        }
        try (Jedis jedis = open(datasourceId, db)) {
            requireType(jedis, key, "SET");
            return jedis.srem(key, member);
        }
    }

    @Override
    public boolean zadd(Long datasourceId, int db, String key, String member, double score) {
        if (isBlank(member)) {
            throw new IllegalArgumentException("成员不能为空");
        }
        try (Jedis jedis = open(datasourceId, db)) {
            requireType(jedis, key, "ZSET");
            return jedis.zadd(key, score, member) > 0;
        }
    }

    @Override
    public long zrem(Long datasourceId, int db, String key, String member) {
        if (isBlank(member)) {
            throw new IllegalArgumentException("成员不能为空");
        }
        try (Jedis jedis = open(datasourceId, db)) {
            requireType(jedis, key, "ZSET");
            return jedis.zrem(key, member);
        }
    }

    @Override
    public String xadd(Long datasourceId, int db, String key, List<RedisHashFieldVO> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("流消息至少需要一个字段");
        }
        try (Jedis jedis = open(datasourceId, db)) {
            Map<String, String> body = new LinkedHashMap<>();
            for (RedisHashFieldVO f : fields) {
                if (f == null || isBlank(f.field())) {
                    throw new IllegalArgumentException("流消息字段名不能为空");
                }
                body.put(f.field(), f.value() == null ? "" : f.value());
            }
            StreamEntryID id = jedis.xadd(key, StreamEntryID.NEW_ENTRY, body);
            return id == null ? "" : id.toString();
        }
    }

    // ==================== 内部辅助 ====================

    /**
     * 按数据源配置建立 Jedis 连接并 SELECT 目标库。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @return 已连接的 Jedis
     */
    private Jedis open(Long datasourceId, int db) {
        if (datasourceId == null) {
            throw new IllegalArgumentException("未选择 Redis 数据源");
        }
        ReturnResult<DataSourceVO> rr = datasourceService.get(datasourceId);
        if (!rr.isOk() || rr.getData() == null) {
            throw new IllegalStateException("Redis 数据源不可用: " + rr.getMsg());
        }
        DataSourceVO ds = rr.getData();
        if (ds.type() == null || !"redis".equalsIgnoreCase(ds.type())) {
            throw new IllegalStateException("该数据源不是 Redis 类型: " + ds.type());
        }
        int port = ds.port() == null ? 6379 : ds.port();
        Jedis jedis = new Jedis(ds.host(), port, TIMEOUT_MS);
        try {
            if (ds.password() != null && !ds.password().isBlank()) {
                jedis.auth(ds.password());
            }
            int targetDb = db;
            if (targetDb < 0) {
                targetDb = parseIntSafe(ds.database(), 0);
            }
            jedis.select(targetDb);
        } catch (RuntimeException e) {
            jedis.close();
            throw e;
        }
        return jedis;
    }

    /**
     * 设置/清除过期时间。
     *
     * @param jedis 连接
     * @param key   键名
     * @param ttl   过期秒数，-1 表示永久
     */
    private static void applyTtl(Jedis jedis, String key, long ttl) {
        if (ttl > 0) {
            jedis.expire(key, ttl);
        } else if (ttl == -1) {
            jedis.persist(key);
        }
    }

    /**
     * 校验键类型（键不存在时放行，便于首次元素操作自动建结构）。
     *
     * @param jedis    连接
     * @param key      键名
     * @param expected 期望类型
     */
    private static void requireType(Jedis jedis, String key, String expected) {
        String actual = jedis.type(key).toUpperCase();
        if ("NONE".equals(actual)) {
            return;
        }
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("键 " + key + " 当前类型是 " + actual + "，不是 " + expected);
        }
    }

    /**
     * 截断 Map 到最多 N 项。
     *
     * @param map 原 Map
     * @param max 最大项数
     * @return 截断后的 LinkedHashMap
     */
    private static Map<String, String> truncateMap(Map<String, String> map, int max) {
        if (map.size() <= max) {
            return map;
        }
        Map<String, String> out = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (i++ >= max) {
                break;
            }
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * 截断 List 到最多 N 项。
     *
     * @param list 原 List
     * @param max  最大项数
     * @return 截断后的 List
     */
    private static List<String> truncateList(List<String> list, int max) {
        if (list.size() <= max) {
            return list;
        }
        return new ArrayList<>(list.subList(0, max));
    }

    /**
     * 解析 INFO 全文为 key=value Map。
     *
     * @param infoRaw INFO 原始文本
     * @return 字段 Map
     */
    private static Map<String, String> parseInfo(String infoRaw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (infoRaw == null) {
            return map;
        }
        for (String line : infoRaw.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int idx = line.indexOf(':');
            if (idx > 0) {
                map.put(line.substring(0, idx), line.substring(idx + 1));
            }
        }
        return map;
    }

    /**
     * 安全解析长整数。
     *
     * @param s 文本
     * @return 数值，失败返回 0
     */
    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 安全解析整数。
     *
     * @param s            文本
     * @param defaultValue 默认值
     * @return 数值
     */
    private static int parseIntSafe(String s, int defaultValue) {
        if (s == null || s.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 是否空白。
     *
     * @param s 文本
     * @return 是否空白
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
