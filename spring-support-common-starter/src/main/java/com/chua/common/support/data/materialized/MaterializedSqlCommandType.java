package com.chua.common.support.data.materialized;

/**
* 物化视图 SQL 命令类型枚举
*
* @author CH
* @since 4.0.0
 */
public enum MaterializedSqlCommandType {

    /**
    * 查询操作
    */
    SELECT,

    /**
    * 插入操作
    */
    INSERT,

    /**
    * 更新操作
    */
    UPDATE,

    /**
    * 删除操作
    */
    DELETE,

    /**
    * 其他操作
    */
    OTHER
}
