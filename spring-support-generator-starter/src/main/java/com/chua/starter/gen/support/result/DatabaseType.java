package com.chua.starter.gen.support.result;

import lombok.Data;

import java.util.List;

/**
 * @author CH
 */
@Data
public class DatabaseType {

    private String name;

    private String type;

    private List<String> driver;

    private String driverUrl;
    private DatabaseEnum database;

    private JdbcType jdbcType = JdbcType.JDBC;

    private boolean log;


    public static enum JdbcType {
        /**
         * jdbc
         */
        JDBC,

        /**
         * nosql
         */
        NOSQL,

    }

    public static enum DatabaseEnum {
        /**
         * 文件
         */
        FILE,

        /**
         *
         */
        NONE,

    }
}
