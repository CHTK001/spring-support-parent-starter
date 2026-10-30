package com.chua.starter.common.support.expression;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.lang.ast.BTreeNode;
import com.chua.common.support.lang.ast.ExpressionParser;
import org.springframework.expression.Expression;

/**
 * Spring EL 表达式解析器
 * @author CH
 * @since 4.0.0
 */
@Spi("spring")
public class SpelExpressionParser implements ExpressionParser {

    /**
     * 表达式解析器
    */
    private final org.springframework.expression.ExpressionParser expressionParser = new org.springframework.expression.spel.standard.SpelExpressionParser();

    @Override
    /**
     * 类型
     * @return 返回值
    */
    public String type() {
        return "spring";
    }

    @Override
    /**
     * 解析
     * @param expression 字符串
     * @return 返回值
    */
    public BTreeNode parse(String expression) {
        try {
            Expression expr = expressionParser.parseExpression(expression);
            /**
             * b树节点。
             * @param BTreeNode.Type.RAW b树节点.类型.raw
             * @param expression expression
             * @param expression expression
             * @return b树节点的结果
             */
            return new BTreeNode(BTreeNode.Type.RAW, expression, expression);
        } catch (Exception e) {
            /**
             * b树节点。
             * @param BTreeNode.Type.RAW b树节点.类型.raw
             * @param expression expression
             * @param expression expression
             * @return b树节点的结果
             */
            return new BTreeNode(BTreeNode.Type.RAW, expression, expression);
        }
    }

    @Override
    /**
     * Generate
     * @param tree 参数
     * @return 返回值
    */
    public String generate(BTreeNode tree) {
        if (tree == null) {
            return "";
        }
        Object value = tree.getValue();
        return value != null ? value.toString() : "";
    }
}

