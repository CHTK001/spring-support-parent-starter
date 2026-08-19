package com.chua.starter.common.support.expression;

import com.chua.common.support.lang.ast.BTreeNode;
import com.chua.common.support.spi.annotations.Spi;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * SpEL 表达式解析器适配。
 *
 * @since 2024/01/01
 * @author CH
 */
@Spi("spring")
public class SpringSpelExpressionParser implements com.chua.common.support.lang.ast.ExpressionParser {

    /** Evaluation上下文 */
    private final EvaluationContext evaluationContext = new StandardEvaluationContext();
    /** 表达式解析器 */
    private final org.springframework.expression.ExpressionParser expressionParser = new org.springframework.expression.spel.standard.SpelExpressionParser();

    @Override
    public String type() {
        return "spring";
    }

    @Override
    public BTreeNode parse(String express) {
        Object value = expressionParser.parseExpression(express, new TemplateParserContext()).getValue(evaluationContext);
        return BTreeNode.value(value);
    }

    @Override
    public String generate(BTreeNode node) {
        if (node == null) {
            return "";
        }
        return node.asString();
    }
}
