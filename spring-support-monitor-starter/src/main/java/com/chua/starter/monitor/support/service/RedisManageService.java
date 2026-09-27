package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.model.RedisKeyDetailVO;
import com.chua.starter.monitor.support.model.RedisManageCreateKeyDTO;
import com.chua.starter.monitor.support.model.RedisManageDatabaseVO;
import com.chua.starter.monitor.support.model.RedisManageScanResultVO;

import java.util.List;
import java.util.Map;

/**
 * 数据源级 Redis 管理服务：连接任意已登记的 Redis 数据源，做键浏览与增删改查。
 *
 * <p>与 {@link SystemRedisKvService}（操作应用自身 Redis）互补：本服务按 datasourceId
 * 临时建立 Jedis 连接，每次操作用完即关，不持有连接池。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface RedisManageService {

    /**
     * SCAN 键列表（游标分页）。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param pattern      键匹配模式
     * @param cursor       起始游标，"0" 表示从头开始
     * @param count        每批期望数量
     * @return 本批键摘要 + 下一个游标
     */
    RedisManageScanResultVO scanKeys(Long datasourceId, int db, String pattern, String cursor, int count);

    /**
     * 读取键详情（按类型返回结构化值）。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @return 键详情
     */
    RedisKeyDetailVO keyDetail(Long datasourceId, int db, String key);

    /**
     * 新增键（按类型写入对应结构）。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param dto          键定义
     * @return 是否成功
     */
    boolean createKey(Long datasourceId, int db, RedisManageCreateKeyDTO dto);

    /**
     * 覆盖 STRING 键的值。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param value        新值
     * @return 是否成功
     */
    boolean setString(Long datasourceId, int db, String key, String value);

    /**
     * 删除一个或多个键。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param keys         键名列表
     * @return 删除的键数量
     */
    long deleteKeys(Long datasourceId, int db, List<String> keys);

    /**
     * 设置键过期时间（ttl=-1 时转为永久）。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param ttl          过期秒数，-1 表示永久
     * @return 是否成功
     */
    boolean expireKey(Long datasourceId, int db, String key, long ttl);

    /**
     * 重命名键。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          原键名
     * @param newKey       新键名
     * @return 是否成功
     */
    boolean renameKey(Long datasourceId, int db, String key, String newKey);

    /**
     * 读取 Redis INFO 关键段字段。
     *
     * @param datasourceId 数据源 ID
     * @return 关键字段 Map
     */
    Map<String, String> info(Long datasourceId);

    /**
     * 列出 db0~db15 的键统计（解析 INFO keyspace）。
     *
     * @param datasourceId 数据源 ID
     * @return 各库键数
     */
    List<RedisManageDatabaseVO> databases(Long datasourceId);

    /**
     * 写入 HASH 字段。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param field        字段名
     * @param value        字段值
     * @return 是否为新增字段
     */
    boolean hset(Long datasourceId, int db, String key, String field, String value);

    /**
     * 删除 HASH 字段。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param field        字段名
     * @return 删除数量
     */
    long hdel(Long datasourceId, int db, String key, String field);

    /**
     * RPUSH 追加 LIST 元素。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param value        元素值
     * @return 追加后的列表长度
     */
    long rpush(Long datasourceId, int db, String key, String value);

    /**
     * 按下标覆盖 LIST 元素。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param index        下标
     * @param value        新值
     * @return 是否成功
     */
    boolean lset(Long datasourceId, int db, String key, long index, String value);

    /**
     * 按下标删除 LIST 元素。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param index        下标
     * @return 删除数量
     */
    long lremIndex(Long datasourceId, int db, String key, long index);

    /**
     * SADD 添加 SET 成员。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param member       成员值
     * @return 是否为新增成员
     */
    boolean sadd(Long datasourceId, int db, String key, String member);

    /**
     * SREM 删除 SET 成员。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param member       成员值
     * @return 删除数量
     */
    long srem(Long datasourceId, int db, String key, String member);

    /**
     * ZADD 写入 ZSET 成员。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param member       成员值
     * @param score        分值
     * @return 是否为新增成员
     */
    boolean zadd(Long datasourceId, int db, String key, String member, double score);

    /**
     * ZREM 删除 ZSET 成员。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param member       成员值
     * @return 删除数量
     */
    long zrem(Long datasourceId, int db, String key, String member);

    /**
     * XADD 追加 STREAM 消息。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param key          键名
     * @param fields       字段列表
     * @return 新消息 ID
     */
    String xadd(Long datasourceId, int db, String key, List<com.chua.starter.monitor.support.model.RedisHashFieldVO> fields);
}
