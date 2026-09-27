package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.model.RedisHashFieldVO;
import com.chua.starter.monitor.support.model.RedisMemberScoreVO;
import com.chua.starter.monitor.support.model.RedisStreamEntryVO;

import java.util.List;

/**
 * 当前系统 Redis 集合类型键的元素级读写服务。
 *
 * <p>STRING 键可整体覆盖，集合类型只能按元素增删改；本服务只操作应用自身连接的 Redis
 * （{@code spring.data.redis}），与监控资产的 RedisClient 采集通道无关。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface SystemRedisKvService {

    /**
     * 写入哈希字段。
     *
     * @param key   键名
     * @param field 字段
     * @return 是否新增字段（false 表示覆盖已有字段）
     */
    boolean putHash(String key, RedisHashFieldVO field);

    /**
     * 删除哈希字段。
     *
     * @param key   键名
     * @param field 字段名
     * @return 删除数量
     */
    long removeHash(String key, String field);

    /**
     * 追加列表元素。
     *
     * @param key   键名
     * @param value 元素值
     * @return 追加后的列表长度
     */
    long pushList(String key, String value);

    /**
     * 按下标覆盖列表元素。
     *
     * @param key   键名
     * @param index 下标
     * @param value 元素值
     * @return 是否成功
     */
    boolean setList(String key, int index, String value);

    /**
     * 按下标删除列表元素。
     *
     * @param key   键名
     * @param index 下标
     * @return 删除数量
     */
    long removeList(String key, int index);

    /**
     * 添加集合成员。
     *
     * @param key   键名
     * @param value 成员值
     * @return 是否新增成员
     */
    boolean addSet(String key, String value);

    /**
     * 删除集合成员。
     *
     * @param key   键名
     * @param value 成员值
     * @return 删除数量
     */
    long removeSet(String key, String value);

    /**
     * 写入有序集合成员。
     *
     * @param key    键名
     * @param member 成员与分值
     * @return 是否新增成员
     */
    boolean addZset(String key, RedisMemberScoreVO member);

    /**
     * 删除有序集合成员。
     *
     * @param key    键名
     * @param member 成员值
     * @return 删除数量
     */
    long removeZset(String key, String member);

    /**
     * 追加流消息。
     *
     * @param key   键名
     * @param entry 消息（id 为空时由 Redis 生成）
     * @return 新消息 ID
     */
    String appendStream(String key, RedisStreamEntryVO entry);

    /**
     * 读取流消息。
     *
     * @param key   键名
     * @param limit 最多返回条数
     * @return 消息列表，按 ID 逆序
     */
    List<RedisStreamEntryVO> readStream(String key, int limit);
}
