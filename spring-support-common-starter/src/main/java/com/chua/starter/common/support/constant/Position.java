package com.chua.starter.common.support.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 位置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/01
 */
@Getter
@AllArgsConstructor
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

}
