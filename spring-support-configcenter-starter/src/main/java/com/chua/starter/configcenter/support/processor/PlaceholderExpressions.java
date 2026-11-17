package com.chua.starter.configcenter.support.processor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 占位符表达式解析工具（模块内共享）。
 *
 * <p>把 {@code ${key:defaultValue}} 形式的表达式解析为「键 + 默认值」；
 * 供 {@link ValueAnnotationBeanPostProcessor} 与 {@link ConfigValueBeanPostProcessor}
 * 共用，避免同一套正则解析逻辑在模块内双份实现（R4：重复实现 = S0）。
 * 完整的占位符求值能力由底层 {@code PlaceholderSupport} 提供，此处只做表达式结构解析。</p>
 *
 * @author CH
 * @since 4.0.0
 */
final class PlaceholderExpressions {

    /**
     * {@code ${key:defaultValue}} 占位符表达式模式。
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    /**
     * 私有构造，禁止实例化。
     */
    private PlaceholderExpressions() {
    }

    /**
     * 解析占位符表达式。
     *
     * @param expression 表达式
     * @return 解析结果；非占位符表达式时 key 为原表达式、defaultValue 为 null
     */
    static ParsedExpression parse(String expression) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(expression);
        if (matcher.find()) {
            return new ParsedExpression(matcher.group(1), matcher.group(2));
        }
        return new ParsedExpression(expression, null);
    }

    /**
     * 解析后的表达式。
     *
     * @param key          配置键
     * @param defaultValue 默认值（表达式未写默认值时为 {@code null}）
     */
    record ParsedExpression(String key, String defaultValue) {
    }
}
