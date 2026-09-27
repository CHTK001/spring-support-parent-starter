package com.chua.starter.monitor.support.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis 流消息
 *
 * <p>字段用 {@link RedisHashFieldVO} 列表承载而非 Map，保证接口出入参是对象；
 * Redis 侧的 field-value 映射在实现内部完成。</p>
 *
 * @param id     消息 ID，追加时留空则由 Redis 生成
 * @param fields 字段列表
 * @author CH
 * @since 4.0.0.42
 */
public record RedisStreamEntryVO(
        String id,
        List<RedisHashFieldVO> fields
) {

    /**
     * 规范构造器：集合组件做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。</p>
     *
     * <p>本 record 同时用作请求体，未携带字段列表时就是 null，而
     * {@code RedisManageServiceImpl#xadd} 与 {@code SystemRedisKvServiceImpl#appendStream}
     * 都靠 null 抛出「流消息至少需要一个字段」的业务错误；元素也允许为 null
     * （由调用方显式跳过）。因此这里保留 null 语义，并采用可含 null 元素的不可变包装，
     * 而不是会拒绝 null 的 copyOf。</p>
     *
     * @param id     消息 ID，追加时留空则由 Redis 生成
     * @param fields 字段列表
     */
    public RedisStreamEntryVO {
        fields = fields == null
                ? null : Collections.unmodifiableList(new ArrayList<>(fields));
    }
}
