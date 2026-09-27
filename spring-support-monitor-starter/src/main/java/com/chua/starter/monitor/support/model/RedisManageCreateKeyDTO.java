package com.chua.starter.monitor.support.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 新增 Redis 键请求体（按类型装载不同的值）
 *
 * <p>type=STRING 时用 value；HASH 用 hashFields；LIST/SET 用 listItems/setItems；
 * ZSET 用 zsetMembers。STREAM 不在此创建，首条消息经 XADD 自动建流。</p>
 *
 * @param key         键名
 * @param type        键类型（STRING/HASH/LIST/SET/ZSET）
 * @param value       STRING 类型的值
 * @param ttl         过期秒数，-1 表示永不过期
 * @param hashFields  HASH 字段列表
 * @param listItems   LIST 元素列表（RPUSH 入队）
 * @param setItems    SET 成员列表
 * @param zsetMembers ZSET 成员列表
 * @author CH
 * @since 4.0.0.42
 */
public record RedisManageCreateKeyDTO(
        String key,
        String type,
        String value,
        long ttl,
        List<RedisHashFieldVO> hashFields,
        List<String> listItems,
        List<String> setItems,
        List<RedisMemberScoreVO> zsetMembers
) {

    /**
     * 规范构造器：集合组件做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。</p>
     *
     * <p>本 DTO 由请求体反序列化而来，未携带的集合就是 null，而
     * {@code RedisManageServiceImpl#createKey} 正是靠 null 区分「该类型不传值」；
     * 元素也允许为 null（同样由调用方显式跳过）。因此这里保留 null 语义，
     * 并采用可含 null 元素的不可变包装，而不是会拒绝 null 的 copyOf。</p>
     *
     * @param key         键名
     * @param type        键类型（STRING/HASH/LIST/SET/ZSET）
     * @param value       STRING 类型的值
     * @param ttl         过期秒数，-1 表示永不过期
     * @param hashFields  HASH 字段列表
     * @param listItems   LIST 元素列表（RPUSH 入队）
     * @param setItems    SET 成员列表
     * @param zsetMembers ZSET 成员列表
     */
    public RedisManageCreateKeyDTO {
        hashFields = hashFields == null
                ? null : Collections.unmodifiableList(new ArrayList<>(hashFields));
        listItems = listItems == null
                ? null : Collections.unmodifiableList(new ArrayList<>(listItems));
        setItems = setItems == null
                ? null : Collections.unmodifiableList(new ArrayList<>(setItems));
        zsetMembers = zsetMembers == null
                ? null : Collections.unmodifiableList(new ArrayList<>(zsetMembers));
    }
}
