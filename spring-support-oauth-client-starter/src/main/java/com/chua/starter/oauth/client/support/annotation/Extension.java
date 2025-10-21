package com.chua.starter.oauth.client.support.annotation;

import java.lang.annotation.*;

/**
 * 扩展注解，用于标识扩展点类型
 *
 * @author CH
 * @since 2023-08-01
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Extension {
    /**
     * 扩展点类型值
     *
     * @return 扩展点类型，例如："userService"、"orderService" 等
     */
    String value();
}
