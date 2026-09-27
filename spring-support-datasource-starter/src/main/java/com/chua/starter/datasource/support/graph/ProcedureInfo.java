package com.chua.starter.datasource.support.graph;

/**
 * 数据库存储过程 / 函数元数据
 *
 * @param name 名称
 * @param type 类型：PROCEDURE / FUNCTION
 * @param schema 所属 模式/Catalog
 * @param remark 简短备注
 * @param tableName 关联的表名（如能从定义中识别，未
 *                  知时为 空）
 *
 * @since 2026/8/5
 * @author CH
 */
public record ProcedureInfo(
        String name,
        String type,
        String schema,
        String remark,
        String tableName
) {
}
