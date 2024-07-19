package com.chua.starter.monitor.server.pojo;

import io.swagger.annotations.ApiModel;
import lombok.Data;
/**
 * JdbcDriverItem 类代表数据库驱动的配置项。
 * 它包含了驱动的类名和类型，用于配置和管理不同的数据库驱动。
 * @author CH
 * @since 2024/7/18
 */
@Data
@ApiModel("数据库驱动")
public class JdbcDriverItem {

    /**
     * JDBC 驱动类名。
     * 该字段用于指定数据库连接所需的 JDBC 驱动类名。
     */
    private String jdbcDriverClassName;

    /**
     * JDBC 类型。
     * 该枚举类型定义了驱动的类型，目前包括 JDBC 和 FILE 两种。
     * JDBC 代表标准的数据库连接驱动，FILE 代表用于文件存储的驱动。
     */
    private Type jdbcType;

    /**
     * JDBC 驱动类型的枚举定义。
     * 包括 JDBC 和 FILE 两种类型。
     */
    public enum Type {

        /**
         * JDBC 类型。
         * 表示标准的数据库连接驱动。
         */
        JDBC,

        /**
         * FILE 类型。
         * 表示用于文件存储的驱动。
         */
        FILE

    }
}
