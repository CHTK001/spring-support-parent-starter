package com.chua.starter.ai.support.model.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Objects;

/**
 * 分页数据信封
 *
 * <p>字段名沿用前端既有口径（{@code records/total/page/pageSize}），只把 {@code Map} 承载换成类型化对象。</p>
 *
 * @param <T> 记录类型
 * @author CH
 * @since 4.0.0.42
 */
public record AiUsagePageVO<T>(
        List<T> records,
        long total,
        long page,
        long pageSize
) {

    /**
     * 规范构造器：对记录列表做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。</p>
     *
     * @param records 记录列表
     */
    public AiUsagePageVO {
        records = List.copyOf(Objects.requireNonNull(records, "records 不能为 null"));
    }

    /**
     * 由 MyBatis-Plus 分页对象构造。
     *
     * @param source 分页查询结果
     * @param <T>    记录类型
     * @return 分页信封
     */
    public static <T> AiUsagePageVO<T> of(IPage<T> source) {
        return new AiUsagePageVO<>(source.getRecords(), source.getTotal(), source.getCurrent(), source.getSize());
    }
}
