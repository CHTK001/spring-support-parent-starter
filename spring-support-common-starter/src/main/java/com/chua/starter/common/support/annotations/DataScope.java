package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * 权限(只支持注入 xxxMapper)
 *
 * @author CH
 * @since 2022/7/29 8:23
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

}
