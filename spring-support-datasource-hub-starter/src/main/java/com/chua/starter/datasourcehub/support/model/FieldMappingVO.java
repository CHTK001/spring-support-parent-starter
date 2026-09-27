package com.chua.starter.datasourcehub.support.model;

/**
 * 字段映射结论（单个来源字段）。
 *
 * @param sourceField  来源字段名
 * @param sourceType   来源字段类型
 * @param targetColumn 目标列名（{@code ADD} 时为建议新增的列名）
 * @param targetType   目标列类型（{@code ADD} 时为按来源类型推断出的 SQL 类型）
 * @param matched      目标表里是否已有同名列
 * @param action       建议动作：{@code USE}=直接写、{@code ADD}=需新增列、{@code NEW_TABLE}=表还不存在
 * @param note         可读说明（类型不一致、需要转换等）
 * @author CH
 * @since 4.0.0.42
 */
public record FieldMappingVO(
        String sourceField,
        String sourceType,
        String targetColumn,
        String targetType,
        boolean matched,
        String action,
        String note) {
}
