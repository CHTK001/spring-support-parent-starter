package com.chua.starter.common.support.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 枚举描述
 *
 * @author CH
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumDescribe {

    /**
     * 继承EnumName接口的枚举类
     *
     * @return {@link Class}<{@link ?} {@link extends} {@link Enum}>
     */
    Class<? extends Enum<?>> value();

    /**
     * 前缀
     *
     * @return {@link String}
     */
    String prefix() default "";

    /**
     * 后缀
     *
     * @return {@link String}
     */
    String suffix() default "Name";
}
