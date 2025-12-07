package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * 数据源切�?
 *
 * @author ch
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DS {
    /**
     * 数据源名�?
     *
     * @return 数据源名�?
     */
    String value();
}

