package com.chua.starter.common.support.debounce;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 防抖生成器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/10
 */
public class StandardDebounceKeyGenerator implements DebounceKeyGenerator{

    private final String key;

    public StandardDebounceKeyGenerator(String key) {
        this.key = key;
    }

    @Override
    public String getKey(String prefix, Method method, Object[] args, Object[] argsValue) {
        ExpressionParser expressionParser = new SpelExpressionParser();
        ParserContext parserContext = new TemplateParserContext();
        EvaluationContext evaluationContext = new StandardEvaluationContext();

        for (int i = 0; i < args.length; i++) {
            Object argument = args[i];
            Object value = argsValue[i];
            evaluationContext.setVariable("$arg" + i, value);
            evaluationContext.setVariable(argument.toString(), value);
        }

        evaluationContext.setVariable("$method", method);
        Expression expression = expressionParser.parseExpression(key, parserContext);
        return prefix + Optional.ofNullable( expression.getValue(evaluationContext)).orElse(key).toString();
    }
}
