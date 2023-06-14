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
     * redis
     */
    REDIS("");
    private final String driver;

}
