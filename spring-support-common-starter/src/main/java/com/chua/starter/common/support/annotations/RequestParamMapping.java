package com.chua.starter.common.support.annotations;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.ValueConstants;

import java.lang.annotation.*;

/**
 * 请求参数映射注解
 *
 * @author CH
 * @since 2023-07-01
 * @see org.springframework.web.bind.annotation.RequestParam
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestParamMapping {

    /**
     * 参数名称别名
     * <p>例如: @RequestParamMapping("id") �?@RequestParamMapping(value = "id")</p>
     *
     * @return 参数名称数组
     */
    @AliasFor("name")
    String[] value() default "";

    /**
     * 请求参数绑定的名�?
     * <p>例如: @RequestParamMapping(name = "userId")</p>
     *
     * @return 参数名称数组
     * @since 4.2
     */
    @AliasFor("value")
    String[] name() default "";

    /**
     * 参数是否必需
     * <p>默认�?{@code true}，如果请求中缺少该参数将抛出异常�?
     * 如果希望参数不存在时使用 {@code null} 值，则设置为 {@code false}�?/p>
     * <p>或者提供一�?{@link #defaultValue}，这会隐式地将此标志设置�?{@code false}�?/p>
     * <p>例如: @RequestParamMapping(required = false)</p>
     *
     * @return 是否必需
     */
    boolean required() default true;

    /**
     * 当请求参数未提供或值为空时使用的默认�?
     * <p>提供默认值会隐式地将 {@link #required} 设置�?{@code false}�?/p>
     * <p>例如: @RequestParamMapping(defaultValue = "0")</p>
     *
     * @return 默认�?
     */
    String defaultValue() default ValueConstants.DEFAULT_NONE;

}

