package com.chua.starter.monitor.support.model;

import java.util.List;
import java.util.Objects;

/**
 * 数据同步字段映射预览结果。
 *
 * <p>供界面在保存任务前看清楚「会读哪些列、写哪些列、按什么规则配对」，
 * 并把自动配对的缺口提前告知，避免保存后才发现目标列没对上。</p>
 *
 * @param sourceColumns 源侧列（按读取 SQL 的结果顺序）
 * @param targetColumns 目标侧列（按目标表定义顺序）
 * @param mapping       最终采用的字段映射；界面已显式配置时原样回显，未配置时给出按同名自动配对的结果
 * @param warnings      需要人工确认的提示（如目标列未匹配、源列重复、映射引用了不存在的列）
 * @author CH
 * @since 4.0.0.43
 */
public record SyncPreviewVO(
        List<SyncColumnVO> sourceColumns,
        List<SyncColumnVO> targetColumns,
        List<SyncFieldMappingVO> mapping,
        List<String> warnings) {

    /**
     * 规范构造器：四个集合做防御性拷贝，避免调用方在拿回结果后继续改内部列表。
     *
     * <p>元素由本模块自行构造，保证非 空，因此可以直接用拒绝 空 元素的
     * {@link List#copyOf}；整体为 空 属于编程错误，用 判空 显式暴露。</p>
     *
     * @param sourceColumns 源侧列
     * @param targetColumns 目标侧列
     * @param mapping       字段映射
     * @param warnings      提示
     */
    public SyncPreviewVO {
        sourceColumns = List.copyOf(Objects.requireNonNull(sourceColumns, "sourceColumns 不能为 null"));
        targetColumns = List.copyOf(Objects.requireNonNull(targetColumns, "targetColumns 不能为 null"));
        mapping = List.copyOf(Objects.requireNonNull(mapping, "mapping 不能为 null"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings 不能为 null"));
    }
}