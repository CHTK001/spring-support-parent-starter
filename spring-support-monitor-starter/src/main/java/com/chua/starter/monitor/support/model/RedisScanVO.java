package com.chua.starter.monitor.support.model;

import java.util.List;
import java.util.Objects;

/**
 * Redis 键扫描结果
 *
 * @param keys       本次扫描到的键名
 * @param nextCursor 下一次扫描游标，"0" 表示已遍历结束
 * @param total      本次返回的键数量
 * @author CH
 * @since 4.0.0.42
 */
public record RedisScanVO(
        List<String> keys,
        String nextCursor,
        int total
) {

    /**
     * 规范构造器：集合组件做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。</p>
     *
     * @param keys       本次扫描到的键名
     * @param nextCursor 下一次扫描游标，"0" 表示已遍历结束
     * @param total      本次返回的键数量
     */
    public RedisScanVO {
        keys = List.copyOf(Objects.requireNonNull(keys, "keys 不能为 null"));
    }
}
