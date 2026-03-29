package com.chua.starter.sync.data.support.sync.transformer.impl;

import com.chua.starter.sync.data.support.sync.transformer.DataTransformer;
import com.chua.starter.sync.data.support.sync.transformer.TransformConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

/**
 * 数据过滤转换器
 * 使用SpEL表达式过滤数据
 *
 * @author CH
 * @since 2024/12/31
 */
@Slf4j
public class DataFilterTransformer implements DataTransformer {

    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public Map<String, Object> transform(Map<String, Object> input, TransformConfig config) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        TransformConfig.FilterRule rule = config.getFilterRule();
        if (rule == null) {
            return input;
        }

        try {
            TransformConfig.FilterType ruleType = rule.getType();
            if (TransformConfig.FilterType.EXPRESSION == ruleType) {
                // 使用SpEL表达式过滤
                Expression exp = parser.parseExpression(rule.getExpression());
                StandardEvaluationContext context = new StandardEvaluationContext(input);
                context.addPropertyAccessor(new MapAccessor());
                
                // 设置变量，支持直接访问字段
                input.forEach(context::setVariable);
                
                Boolean result = exp.getValue(context, Boolean.class);
                return Boolean.TRUE.equals(result) ? input : null;
            } else if (TransformConfig.FilterType.SCRIPT == ruleType) {
                // 简单字段值过滤
                String field = rule.getField();
                Object expectedValue = rule.getValue();
                Object actualValue = input.get(field);
                
                return expectedValue != null && expectedValue.equals(actualValue) ? input : null;
            }
        } catch (Exception e) {
            log.error("过滤表达式执行失败: {}", rule.getExpression(), e);
            // 过滤失败时根据配置决定是否保留数据
            return Boolean.TRUE.equals(config.getKeepOnFilterError()) ? input : null;
        }

        return input;
    }

    @Override
    public boolean validateConfig(TransformConfig config) {
        if (config == null) {
            log.error("转换配置不能为空");
            return false;
        }

        TransformConfig.FilterRule rule = config.getFilterRule();
        if (rule == null) {
            log.error("过滤规则不能为空");
            return false;
        }

        TransformConfig.FilterType type = rule.getType();
        if (type == null) {
            log.error("过滤规则类型不能为空");
            return false;
        }

        if (TransformConfig.FilterType.EXPRESSION == type) {
            String expression = rule.getExpression();
            if (expression == null || expression.trim().isEmpty()) {
                log.error("过滤表达式不能为空");
                return false;
            }
            
            // 验证表达式语法
            try {
                parser.parseExpression(expression);
            } catch (Exception e) {
                log.error("过滤表达式语法错误: {}", expression, e);
                return false;
            }
        } else if (TransformConfig.FilterType.SCRIPT == type) {
            if (rule.getField() == null || rule.getField().trim().isEmpty()) {
                log.error("过滤字段名不能为空");
                return false;
            }
        } else {
            log.error("不支持的过滤规则类型: {}", type);
            return false;
        }

        return true;
    }

    public String getType() {
        return "DATA_FILTER";
    }
}
