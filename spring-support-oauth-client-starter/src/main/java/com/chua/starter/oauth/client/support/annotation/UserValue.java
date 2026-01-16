package com.chua.starter.oauth.client.support.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 解析参数 (Header: application/json)
 *
 * @author CH
 * @since 2023-08-01
 * @see org.springframework.web.bind.annotation.RequestParam
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UserValue {

    /**
     * 参数名称别名
     * <p>等同于 {@link #name()} 方法</p>
     * <p>例如: \@UserValue("userId") String userId</p>
     *
     * @return 参数名称
     */
    @AliasFor("name")
    String value() default "";

    /**
     * 请求参数的名称
     * <p>用于绑定HTTP请求中的指定参数</p>
     * <p>例如: \@UserValue(name = "userName") String name</p>
     *
     * @since 4.2
     * @return 参数名称
     */
    @AliasFor("value")
    String name() default "";

    /**
     * 是否为必需参数
     * <p>默认值为true，如果请求中缺少该参数将抛出异常</p>
     * <p>设置为false时，若参数不存在则使用null值</p>
     * <p>注意：如果设置了{@link #defaultValue()}，此属性将隐式设置为false</p>
     * <p>例如: \@UserValue(name = "age", required = false) Integer age</p>
     *
     * @return 是否必需
     */
    boolean required() default true;

    /**
     * 参数默认值
     * <p>当请求参数未提供或值为空时使用的回退值</p>
     * <p>例如: \@UserValue(name = "status", defaultValue = "ACTIVE") String status</p>
     * <p>注意：提供默认值会隐式将{@link #required()}设置为false</p>
     *
     * @return 默认值
     */
    String defaultValue() default "";

}
