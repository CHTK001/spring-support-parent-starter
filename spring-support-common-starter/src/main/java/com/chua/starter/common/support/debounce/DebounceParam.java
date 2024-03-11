package com.chua.starter.common.support.debounce;

import java.lang.annotation.*;

/**
 * 请求锁定（防抖）参数
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/10
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DebounceParam {

    /**
     * 别名
     * @return 别名
     */
    String value() ;

}
