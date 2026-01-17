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
public @interface TokenValue {

    /**
     * 参数名称别名
     * <p>例如: \@TokenValue("userId") String userId</p>
     *
     * @return 参数名称
     */
    String value() default "";

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
