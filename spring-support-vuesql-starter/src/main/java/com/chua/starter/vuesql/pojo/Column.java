package com.chua.starter.vuesql.pojo;

import lombok.Data;

/**
 * 数据库
 *
 * @author CH
 */
@Data
public class Column {

    private String columnName;
    private String columnType;
    private String dataType;
    private String columnDefault;
    private String characterMaximumLength;
    private String numericPrecision;
    private String numericScale;
    private String isNullable;
    private String columnKey;
    private String extra;
    private String columnComment;
}
