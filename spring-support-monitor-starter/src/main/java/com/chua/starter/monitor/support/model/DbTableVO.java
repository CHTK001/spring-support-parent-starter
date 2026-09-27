package com.chua.starter.monitor.support.model;

/**
 * 数据库表元数据条目
 *
 * @param name   表名或视图名
 * @param type   类型（TABLE/VIEW）
 * @param remark 表注释
 * @author CH
 * @since 4.0.0.42
 */
public record DbTableVO(
        String name,
        String type,
        String remark
) {
}
