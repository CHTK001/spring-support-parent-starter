package com.chua.starter.gen.support.vo;

import lombok.Data;

/**
 * 表格结果
 *
 * @author CH
 */
@Data
public class TableResult {

    /**
     * 表名称
     */
    private String tableName;
    private String database;
    private String remark;
}
