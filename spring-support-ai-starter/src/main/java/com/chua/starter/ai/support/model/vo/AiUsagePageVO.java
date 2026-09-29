package com.chua.starter.ai.support.model.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Objects;

/**
 * 分页数据信封
 *
 * <p>字段名沿用前端既有口径（{@code records/total/page/pageSize}），只把 {@code Map} 承载换成类型化对象。</p>
 *
 * <p>取值来源为 MyBatis-Plus 分页对象：{@link #of(IPage)} 把
 * {@code getRecords() / getTotal() / getCurrent() / getSize()} 一一对应过来，
 * 四个分页字段的单位与口径完全沿用 {@code IPage}，本 record 不做二次换算。</p>
 *
 * @param records 当前页的记录列表，取 {@code IPage#getRecords()}，元素类型即泛型 {@code T}。
 *                <b>不允许为 {@code null}</b>，也不允许含 {@code null} 元素——紧凑构造器用
 *                {@code List.copyOf} + {@code requireNonNull} 做防御性拷贝并冻结为不可变列表，
 *                违反会抛 {@code NullPointerException}。无匹配数据时传空列表而非 {@code null}。
 *                元素自身不保证深不可变，调用方不应通过访问器改写元素内部状态
 * @param total 符合查询条件的总记录数，取 {@code IPage#getTotal()}，单位「条记录」，
 *              不受分页偏移影响，用于前端计算总页数；空结果集为 0，不允许为负
 * @param page  当前页码，取 {@code IPage#getCurrent()}，<b>从 1 开始</b>（不是 0 基），
 *             单位「页」；超出总页数时列表为空而本值仍为请求页码。
 *             对应入参 {@code UsageController#list} 的 {@code page}，缺省为 1
 * @param pageSize 每页条数，取 {@code IPage#getSize()}，单位「条记录/页」，必须为正数。
 *                 对应入参 {@code UsageController#list} 的 {@code size}，缺省为 20
 * @param <T> 记录类型，即 {@link #records} 的元素类型，调用方通常传入实体
 *            （如 {@code SysAiUsage}、{@code SysAiUsageSyncLog}），由 {@link #of(IPage)} 推断
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
