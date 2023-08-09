package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * 忽略一些方法转化
 *
 * @author ch
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Ignore {
}
