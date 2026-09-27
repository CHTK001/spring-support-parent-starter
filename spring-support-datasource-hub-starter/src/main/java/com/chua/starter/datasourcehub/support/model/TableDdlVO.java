package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 建表 / DDL 结果。
 *
 * @param table            目标表名
 * @param exists           执行前目标表是否已存在
 * @param created          本次是否建了新表
 * @param appendedColumns  本次补上的列名（表已存在且策略为 APPEND 时）
 * @param columns          本次解析出的列定义（含推断后的类型）
 * @param statements       实际执行（或预览时将要执行）的 SQL 列表
 * @param warnings         可读提示：类型推断、列已存在被跳过、策略生效等
 * @author CH
 * @since 4.0.0.42
 */
public record TableDdlVO(
        String table,
        boolean exists,
        boolean created,
        List<String> appendedColumns,
        List<SchemaColumnVO> columns,
        List<String> statements,
        List<String> warnings) {

    /**
     * 规范化集合组件，阻断外部别名写入。
     *
     * <p>本 record 是 DDL 结果的 JSON 序列化目标，各集合允许为 {@code null}
     * （未产出该类信息时不返回该键），因此这里保留 null 语义，
     * 只在非 null 时做防御性拷贝，不使用 {@code requireNonNull}。</p>
     */
    public TableDdlVO {
        appendedColumns = appendedColumns == null ? null : List.copyOf(appendedColumns);
        columns = columns == null ? null : List.copyOf(columns);
        statements = statements == null ? null : List.copyOf(statements);
        warnings = warnings == null ? null : List.copyOf(warnings);
    }
}
