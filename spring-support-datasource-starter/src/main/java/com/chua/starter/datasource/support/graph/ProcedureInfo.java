package com.chua.starter.datasource.support.graph;

import lombok.Data;

/**
 * 数据库存储过程 / 函数元数据
 *
 * @since 2026/8/5
 * @author CH
 */
@Data
public class ProcedureInfo {

    /**
     * 名称
     */
    private String name;

    /**
     * 类型：PROCEDURE / FUNCTION
     */
    private String type;

    /**
     * 所属 Schema/Catalog
     */
    private String schema;

    /**
     * 简短备注
     */
    private String remark;

    /**
     * 关联的表名（如能从定义中识别，未知时为 null）
     */
    private String tableName;
}
