package com.chua.starter.vuesql.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据库类型
 *
 * @author CH
 */
@Getter
@AllArgsConstructor
public enum DatabaseType {
    /**
     * mysql
     */
    MYSQL5("com.mysql.jdbc.Driver"),
    /**
     * mysql
     */
    MYSQL8("com.mysql.jdbc.Driver"),
    /**
     * sqlite
     */
    SQLITE("org.sqlite.JDBC"),
    /**
     * csv
     */
    CSV(""),
    /**
     * tsv
     */
    TSV(""),
    /**
     * XLS
     */
    XLS(""),
    /**
     * XLSX
     */
    XLSX(""),
    /**
     * DBF
     */
    DBF(""),
    /**
     * ZOOKEEPER
     */
    ZOOKEEPER(""),
    /**
     * redis
     */
    REDIS("");
    private final String driver;

}
