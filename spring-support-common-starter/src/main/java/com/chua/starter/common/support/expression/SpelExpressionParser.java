package com.chua.starter.common.support.expression;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.lang.ast.BTreeNode;
import com.chua.common.support.lang.ast.ExpressionParser;
import org.springframework.expression.Expression;

/**
 * Spring EL 表达式解析器
 * @author CH
 */
@Spi("spring")
public class SpelExpressionParser implements ExpressionParser {

    /** 表达式解析器 */
    private final org.springframework.expression.ExpressionParser expressionParser = new org.springframework.expression.spel.standard.SpelExpressionParser();

    @Override
    /** Type */
    public String type() {
        return "spring";
    }

    @Override
    /** 解析 */
    public BTreeNode parse(String expression) {
        try {
            Expression expr = expressionParser.parseExpression(expression);
            return new BTreeNode(BTreeNode.Type.RAW, expression, expression);
        } catch (Exception e) {
            return new BTreeNode(BTreeNode.Type.RAW, expression, expression);
        }
    }

    @Override
    /** Generate */
    public String generate(BTreeNode tree) {
        if (tree == null) {
            return "";
        }
        Object value = tree.getValue();
        return value != null ? value.toString() : "";
    }
}

