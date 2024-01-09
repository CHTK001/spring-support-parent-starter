package com.chua.starter.oauth.client.support.annotation;

import java.lang.annotation.*;

/**
 * 权限
 *
 * @author CH
 * @since 2022/7/29 8:23
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginType {
    /**
     * 类型
     *
     * @return 类型
     */
    String value();
}
