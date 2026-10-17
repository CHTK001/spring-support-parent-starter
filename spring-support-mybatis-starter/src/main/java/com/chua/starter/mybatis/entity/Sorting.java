package com.chua.starter.mybatis.entity;

/**
 * 排序对象
 * 用于指定排序字段和排序方向
 *
 * @param field 排序字段名
 * @param order 排序方向（asc 或 desc）
 * @author CH
 * @since 4.0.0
 */
public record Sorting(
        String field,
        String order) {

    /**
     * 排序方向 - 升序
     */
    public static final String ORDER_ASC = "asc";

    /**
     * 排序方向 - 降序
     */
    public static final String ORDER_DESC = "desc";
}
