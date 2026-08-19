package com.chua.common.support.data.materialized;

/**
 * 物理化 SQL 命令类型。
 *
 * @since 2026/4/2
 * @author CH
 */
public enum MaterializedSqlCommandType {
    SELECT,
    INSERT,
    UPDATE,
    DELETE,
    OTHER
}
