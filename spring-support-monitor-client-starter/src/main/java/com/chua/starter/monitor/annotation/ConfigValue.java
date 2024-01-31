package com.chua.starter.monitor.annotation;

import java.lang.annotation.*;

/**
 * 取值
 * @author CH
 * @since 2022/7/30 11:29
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigValue {
    /**
     * 值
     * @return 值
     */
    String value();

    /**
     * 是否自动刷新
     * @return 是否自动刷新
     */
    boolean autoRefreshed() default false;

}
