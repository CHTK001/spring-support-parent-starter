package com.chua.starter.datasourcehub.support.model;

/**
 * 数据源表元数据。
 *
 * <p>{@code /tables} 只给表名串，前端想显示中文备注只能再逐表查列；
 * 本 VO 一次把「表名 + 表备注 + 表类型」给全，供数据源/表/字段三联选的
 * 表下拉直接渲染「中文名 + 表名 subtitle」。</p>
 *
 * @param name    表名
 * @param comment 表注释（REMARKS），可为空
 * @param type    表类型（TABLE / VIEW），可为空
 * @author CH
 * @since 4.0.0.46
 */
public record SchemaTableVO(
        String name,
        String comment,
        String type) {
}
