package com.chua.starter.mybatis.entity;

import com.baomidou.mybatisplus.annotation.DbType;

/**
 * SQL 相关常量类
 * 用于存储数据库类型等 SQL 相关常量
 *
 * @author 芋道源码
 */
public class SqlConstants {

    /**
     * 数据库类型
     */
    public static DbType DB_TYPE;

    /**
     * 初始化数据库类型
     *
     * @param dbType 数据库类型
     */
    public static void init(DbType dbType) {
        DB_TYPE = dbType;
    }

}
