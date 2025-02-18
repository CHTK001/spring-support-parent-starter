package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * 忽略返回格式
 *
 * @author CH
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiReturnFormatIgnore {
}
