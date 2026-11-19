package com.chua.starter.configcenter.support.processor;

import com.chua.common.support.lang.placeholder.PlaceholderSupport;

/**
 * 占位符表达式解析工具（模块内薄委托）。
 *
 * <p>把 {@code ${key:defaultValue}} 形式的表达式解析为「键 + 默认值」。
 * 结构解析的唯一实现下沉在底层 {@link PlaceholderSupport#parseExpression}
 * （R4：同一能力两份实现 = S0），本类只做包内命名委托，供
 * {@link ValueAnnotationBeanPostProcessor} 与 {@link ConfigValueBeanPostProcessor}
 * 共用；完整的占位符求值能力同样由底层 {@code PlaceholderSupport} 提供。</p>
 *
 * @author CH
 * @since 4.0.0
 */
final class PlaceholderExpressions {

    /**
     * 私有构造，禁止实例化。
     */
    private PlaceholderExpressions() {
    }

    /**
     * 解析占位符表达式（委托底层唯一原语）。
     *
     * @param expression 表达式
     * @return 解析结果；非占位符表达式时 key 为原表达式、defaultValue 为 null
     */
    static PlaceholderSupport.ParsedExpression parse(String expression) {
        return PlaceholderSupport.parseExpression(expression);
    }
}