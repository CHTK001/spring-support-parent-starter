package com.chua.starter.gen.support.vo;

import lombok.Data;

/**
 * 表格结果
 *
 * @author CH
 */
@Data
public class ColumnResult {

    /**
     * 表名称
     */
    private String tableName;

    /**
     * 字段
     */
    private String column;
    /**
     * 字段类型
     */
    private String columnType;
    /**
     * 字段类型code
     */
    private int columnTypeCode;
}
