package com.chua.starter.common.support.constant;

/**
* 位置
*
* @版本 1.0.0
* @since 2024/03/01
* @author CH
 */
public enum Position {


    /**
    * 绝对值
    * @param 0 0
    * @return ABSOLUTE的结果
    */
    ABSOLUTE(0),

    /**
    * 相对值
    * @param 1 1
    * @return RELATIVE的结果
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
    * 获取 价值观
    *
    * @return value
    */
    public int getValue() {
        return value;
    }
}
