package com.chua.starter.common.support.constant;

/**
 * 运算符类型枚举
 * <p>
 * 定义数据查询时支持的各种运算符类型，用于构建动态查询条件。
 * </p>
 *
 * @since 2024/01/22
 * @author CH
 */
public enum OperatorType {

    /**
     * 等于运算符
     * @param "=" "="
     * @return EQ的结果
     */
    EQ("="),

    /**
     * 全模糊匹配（前后都加%）
     * @param "%%" "%%"
     * @return LIKE的结果
     */
    LIKE("%%"),

    /**
     * 左模糊匹配（前面加%）
     * @param "%" "%"
     * @return LEFT_LIKE的结果
     */
    LEFT_LIKE("%"),

    /**
     * 右模糊匹配（后面加%）
     * @param "%" "%"
     * @return RIGHT_LIKE的结果
     */
    RIGHT_LIKE("%"),

    /**
     * 正则表达式匹配
     * @param "" ""
     * @return REGEX的结果
     */
    REGEX(""),

    /**
     * 通配符匹配
     * @param "*" "*"
     * @return WILL的结果
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
     * 获取 操作符
     *
     * @return operator
     */
    public String getOperator() {
        return operator;
    }
}
