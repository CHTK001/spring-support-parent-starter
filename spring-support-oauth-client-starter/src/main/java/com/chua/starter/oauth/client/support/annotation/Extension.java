package com.chua.starter.oauth.client.support.annotation;

import java.lang.annotation.*;

/**
 * 扩展
 *
 * @author CH
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Extension {
    /**
     * 类型
     *
     * @return 类型
     */
    String value();
}
