package com.chua.starter.mybatis.entity;

import lombok.Data;

/**
 * 排序
 *
 * @author CH
 */
@Data
public class Sorting {

    /**
     * 顺序 - 升序
     */
    public static final String ORDER_ASC = "asc";
    /**
     * 顺序 - 降序
     */
    public static final String ORDER_DESC = "desc";

    /**
     * 字段
     */
    private String field;
    /**
     * 顺序
     */
    private String order;
}
