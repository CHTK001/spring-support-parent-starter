package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * 数据源切换
 *
 * @author ch
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableAutoTable {
    /**
     * 包
     *
     * @return 包
     */
    String[] value() default {};


    Class<?>[] packageType() default {};
}
