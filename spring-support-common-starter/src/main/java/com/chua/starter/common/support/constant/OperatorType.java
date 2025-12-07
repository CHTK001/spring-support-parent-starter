package com.chua.starter.common.support.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 运算符类�?
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/22
 */
@AllArgsConstructor
@Getter
public enum OperatorType {

    EQ("="),

    LIKE("%%"),

    LEFT_LIKE("%"),
    RIGHT_LIKE("%"),

    REGEX(""),

    WILL("*"),
    ;

    private String operator;


}

