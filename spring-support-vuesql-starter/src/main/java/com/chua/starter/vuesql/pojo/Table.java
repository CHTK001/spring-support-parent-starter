package com.chua.starter.vuesql.pojo;

import lombok.Data;

/**
 * 数据库
 *
 * @author CH
 */
@Data
public class Table {
    /**
     * 表
     */
    private String tableName;
    /**
     * 版本
     */
    private String version;
    /**
     * rowFormat
     */
    private String rowFormat;
    /**
     * engine
     */
    private String engine;
    /**
     * tableRows
     */
    private String tableRows;
    /**
     * autoIncrement
     */
    private String autoIncrement;
    /**
     * tableCollation
     */
    private String tableCollation;
    /**
     * tableComment
     */
    private String tableComment;
}
