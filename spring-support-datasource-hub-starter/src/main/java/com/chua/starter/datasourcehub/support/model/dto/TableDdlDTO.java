package com.chua.starter.datasourcehub.support.model.dto;

import java.util.List;

/**
 * 建表 / DDL 请求。
 *
 * <p>"建新表"是数据源模块的能力：采集的入库节点只需要给出目标表与字段映射，
 * 由数据源模块负责类型推断、方言拼装与"表已存在怎么办"的策略。</p>
 *
 * @param datasource    数据源引用：留空或 {@code app} 表示应用主库，否则为台账 ID 或名称
 * @param table         目标表名，必须合法标识符
 * @param columns       列定义（含来源字段）
 * @param primaryKey    主键列名集合，可为空
 * @param comment       表注释，可为空
 * @param ifExists      目标表已存在时的策略：{@code APPEND}（默认，补缺失列）、{@code SKIP}（什么都不做）、
 *                      {@code ERROR}（直接报错）
 * @param autoIdPrimaryKey 是否自动加自增主键列并进行主键声明（默认 false；既没有 {@code primaryKey}
 *                      也没有本项时先建无主键表）
 * @author CH
 * @since 4.0.0.42
 */
public record TableDdlDTO(
        String datasource,
        String table,
        List<DdlColumn> columns,
        List<String> primaryKey,
        String comment,
        String ifExists,
        Boolean autoIdPrimaryKey) {

    /**
     * 规范化集合组件，阻断外部别名写入。
     *
     * <p>本 record 是 JSON 反序列化的请求体，各字段允许缺省为 {@code null}
     * （调用方已按 null 兜底为空集合，见 DatasourceSchemaServiceImpl），
     * 因此保留 null 语义，不使用 {@code requireNonNull}。</p>
     */
    public TableDdlDTO {
        columns = columns == null ? null : List.copyOf(columns);
        primaryKey = primaryKey == null ? null : List.copyOf(primaryKey);
    }
}
