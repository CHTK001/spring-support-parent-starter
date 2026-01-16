package com.chua.starter.common.support.constant;

/**
 * 运算符类型枚举
 * <p>
 * 定义数据查询时支持的各种运算符类型，用于构建动态查询条件。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/22
 */
public enum OperatorType {

    /**
     * 等于运算符
     */
    EQ("="),

    /**
     * 全模糊匹配（前后都加%）
     */
    LIKE("%%"),

    /**
     * 左模糊匹配（前面加%）
     */
    LEFT_LIKE("%"),

    /**
     * 右模糊匹配（后面加%）
     */
    RIGHT_LIKE("%"),

    /**
     * 正则表达式匹配
     */
    REGEX(""),

    /**
     * 通配符匹配
     */
    WILL("*"),
    ;

    /**
     * 运算符字符串表示
     */
    private final String operator;

    /**
     * 构造函数
     *
     * @param operator 运算符字符串
     */
    OperatorType(String operator) {
        this.operator = operator;
    }

    /**
     * 获取 operator
     *
     * @return operator
     */
    public String getOperator() {
        return operator;
    }


