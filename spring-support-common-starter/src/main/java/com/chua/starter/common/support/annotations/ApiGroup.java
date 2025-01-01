package com.chua.starter.common.support.annotations;

import java.lang.annotation.*;

/**
 * API 分组
 * @author CH
 * @since 2025/1/1
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiGroup {

    /**
     * 忽略分组
     * @return 忽略分组
     */
    Class<?>[] value();
}
