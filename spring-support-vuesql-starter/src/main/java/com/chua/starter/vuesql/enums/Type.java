package com.chua.starter.vuesql.enums;

import lombok.Getter;

/**
 * 类型
 * @author CH
 */
@Getter
public enum Type {
    /**
     * 表
     */
    TABLE,
    /**
     * 视图
     */
    VIEW,
    /**
     * 数据库
     */
    DATABASE,
    /**
     * 函数
     */
    FUNCTION
}
