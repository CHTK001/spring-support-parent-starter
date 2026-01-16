package com.chua.starter.common.support.constant;

/**
 * 位置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/01
 */
public enum Position {


    /**
     * 绝对值
     */
    ABSOLUTE(0),

    /**
     * 相对值
     */
    RELATIVE(1),
    ;

    /**
     * 私有的整数值
     */
    private final int value;

    /**
     * 构造函数
     *
     * @param value 整数值
     */
    Position(int value) {
        this.value = value;
    }

    /**
     * 获取 value
     *
     * @return value
     */
    public int getValue() {
        return value;
    }



