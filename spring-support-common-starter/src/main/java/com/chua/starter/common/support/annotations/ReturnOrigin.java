package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * 忽略返回格式
 *
 * @author CH
 * @since 2023-08-01
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReturnOrigin {
}
