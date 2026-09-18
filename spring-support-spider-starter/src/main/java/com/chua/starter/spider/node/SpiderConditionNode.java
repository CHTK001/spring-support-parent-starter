package com.chua.starter.spider.node;

import com.chua.common.support.task.flow.ConditionNode;
import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowNodeField;
import com.chua.common.support.task.flow.FlowNodeOption;
import com.chua.common.support.task.flow.FlowProps;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
* 条件分支节点。
*
* <p>对指定字段值与期望值按操作符比较，结果决定分支走向：
* 条件成立走 {@code true} 边，不成立走 {@code false} 边。</p>
*
* <p>节点配置说明：</p>
* <ul>
*   <li>{@code field} 取值来源，当前数据 {@code current} 或上下文属性名，默认 current</li>
*   <li>{@code op} 比较操作符，eq/ne/gt/lt/contains/regex，默认 eq</li>
*   <li>{@code value} 期望值（必填）</li>
* </ul>
*
* @since 4.0.0.42
* @author CH
 */
@Component
public class SpiderConditionNode implements ConditionNode {

    /**
    * 取值来源：当前数据
    */
    private static final String SOURCE_CURRENT = "current";

    /**
    * 比较操作符：相等
    */
    private static final String OP_EQ = "eq";

    /**
    * 比较操作符：不相等
    */
    private static final String OP_NE = "ne";

    /**
    * 比较操作符：大于
    */
    private static final String OP_GT = "gt";

    /**
    * 比较操作符：小于
    */
    private static final String OP_LT = "lt";

    /**
    * 比较操作符：包含
    */
    private static final String OP_CONTAINS = "contains";

    /**
    * 比较操作符：正则匹配
    */
    private static final String OP_REGEX = "regex";

    /**
    * 默认比较操作符
    */
    private static final String DEFAULT_OP = OP_EQ;

    /**
    * 节点类型标识。
    *
    * @return 节点类型
    */
    @Override
    public String type() {
        return "spider.condition";
    }

    /**
    * 节点配置表单元信息。
    *
    * @return 表单元信息列表
    */
    @Override
    public List<FlowNodeField> configSchema() {
        List<FlowNodeField> schema = new ArrayList<>();
        schema.add(FlowNodeField.input("field", "取值来源"));
        schema.add(FlowNodeField.select("op", "比较操作符", List.of(
                FlowNodeOption.of("等于", OP_EQ),
                FlowNodeOption.of("不等于", OP_NE),
                FlowNodeOption.of("大于", OP_GT),
                FlowNodeOption.of("小于", OP_LT),
                FlowNodeOption.of("包含", OP_CONTAINS),
                FlowNodeOption.of("正则匹配", OP_REGEX))));
        schema.add(FlowNodeField.input("value", "期望值", true));
        return schema;
    }

    /**
    * 条件判断。
    *
    * <p>按操作符对字段值与期望值比较，返回判断结果。</p>
    *
    * @param context 当前流程上下文
    * @return 条件是否成立
    */
    @Override
    public boolean test(FlowContext context) {
        FlowProps props = context.currentNodeProps();
        String field = props.getString("field", SOURCE_CURRENT);
        String op = props.getString("op", DEFAULT_OP);
        String value = props.getString("value");
        if (value == null) {
            /**
            * illegal参数异常。
            * @param 参数" 参数"
            * @return illegal参数异常的结果
            */
            throw new IllegalArgumentException("spider.condition 节点缺少 value 参数");
        }
        Object actual = resolveValue(field, context);
        String actualText = actual != null ? String.valueOf(actual) : null;
        switch (op) {
            case OP_NE -> {
                return !value.equals(actualText);
            }
            case OP_GT -> {
                return compareNumeric(actualText, value) > 0;
            }
            case OP_LT -> {
                return compareNumeric(actualText, value) < 0;
            }
            case OP_CONTAINS -> {
                return actualText != null && actualText.contains(value);
            }
            case OP_REGEX -> {
                return actualText != null && Pattern.compile(value).matcher(actualText).find();
            }
            default -> {
                return value.equals(actualText);
            }
        }
    }

    /**
    * 解析取值来源。
    *
    * @param field   取值来源标识
    * @param context 当前流程上下文
    * @return 来源值
    */
    private Object resolveValue(String field, FlowContext context) {
        if (SOURCE_CURRENT.equals(field)) {
            return context.getData();
        }
        return context.getAttribute(field);
    }

    /**
    * 数值比较。
    *
    * <p>两个值都解析为数值时按数值比较，否则按字符串比较。</p>
    *
    * @param actual   实际值文本
    * @param expected 期望值文本
    * @return 比较结果：小于 0 / 等于 0 / 大于 0
    */
    private int compareNumeric(String actual, String expected) {
        if (actual == null) {
            return -1;
        }
        try {
            double actualNumber = Double.parseDouble(actual);
            double expectedNumber = Double.parseDouble(expected);
            return Double.compare(actualNumber, expectedNumber);
        } catch (NumberFormatException e) {
            return actual.compareTo(expected);
        }
    }
}
