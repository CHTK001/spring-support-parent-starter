package com.chua.starter.common.support.expression;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.lang.ast.BTreeNode;
import com.chua.common.support.lang.ast.ExpressionParser;
import org.springframework.expression.Expression;

/**
 * Spring EL 表达式解析器
 */
@Spi("spring")
public class SpelExpressionParser implements ExpressionParser {

    private final org.springframework.expression.ExpressionParser expressionParser = new org.springframework.expression.spel.standard.SpelExpressionParser();

    @Override
    public String type() {
        return "spring";
    }

    @Override
    public BTreeNode parse(String expression) {
        try {
            Expression expr = expressionParser.parseExpression(expression);
            return new BTreeNode(BTreeNode.Type.RAW, expression, expression);
        } catch (Exception e) {
            return new BTreeNode(BTreeNode.Type.RAW, expression, expression);
        }
    }

    @Override
    public String generate(BTreeNode tree) {
        if (tree == null) {
            return "";
        }
        Object value = tree.getValue();
        return value != null ? value.toString() : "";
    }
}
