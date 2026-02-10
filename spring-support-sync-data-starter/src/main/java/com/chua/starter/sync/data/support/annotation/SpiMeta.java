package com.chua.starter.sync.data.support.annotation;

import java.lang.annotation.*;

/**
 * SPI 元数据注解
 * 用于标注 SPI 实现类的元信息
 *
 * @author CH
 * @since 2024/12/19
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SpiMeta {

    /**
     * 显示名称
     */
    String displayName() default "";

    /**
     * 描述
     */
    String description() default "";

    /**
     * 图标名称
     */
    String icon() default "";

    /**
     * 颜色（十六进制）
     */
    String color() default "";

    /**
     * 排序（数值越小越靠前）
     */
    int order() default 100;
}
