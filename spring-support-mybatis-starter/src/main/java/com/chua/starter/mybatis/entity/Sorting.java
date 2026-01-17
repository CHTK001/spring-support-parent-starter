package com.chua.starter.mybatis.entity;

import lombok.Data;

/**
 * 排序对象
 * 用于指定排序字段和排序方向
 *
 * @author CH
 */
@Data
public class Sorting {

    /**
     * 排序方向 - 升序
     */
    public static final String ORDER_ASC = "asc";

    /**
     * 排序方向 - 降序
     */
    public static final String ORDER_DESC = "desc";

    /**
     * 排序字段名
     */
    private String field;

    /**
     * 排序方向（asc 或 desc）
     */
    private String order;

    /**
     * 获取排序方向
     *
     * @return 排序方向
     */
    public String getOrder() {
        return order;
    }

    /**
     * 获取排序字段名
     *
     * @return 排序字段名
     */
    public String getField() {
        return field;
    }
}
