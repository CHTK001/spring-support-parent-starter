package com.chua.starter.sync.data.support.annotation;

import java.lang.annotation.*;

/**
 * SPI 参数注解
 * 用于标注 SPI 实现类的配置参数
 *
 * @author CH
 * @since 2024/12/19
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SpiParam {

    /**
     * 参数名称（如果不指定，使用字段名）
     */
    String name() default "";

    /**
     * 显示名称
     */
    String label() default "";

    /**
     * 参数描述
     */
    String description() default "";

    /**
     * 参数类型
     * 支持: string, number, boolean, select, password, textarea, json, date, datetime, color, slider, keyvalue, filepath, multiselect
     */
    String type() default "string";

    /**
     * 是否必填
     */
    boolean required() default false;

    /**
     * 是否为敏感信息（如密码）
     */
    boolean sensitive() default false;

    /**
     * 默认值（字符串形式）
     */
    String defaultValue() default "";

    /**
     * 占位符文本
     */
    String placeholder() default "";

    /**
     * 可选值（逗号分隔，格式: label:value,label:value）
     */
    String options() default "";

    /**
     * 验证正则表达式
     */
    String pattern() default "";

    /**
     * 验证失败提示信息
     */
    String patternMessage() default "";

    /**
     * 最小值（用于 number 类型）
     */
    double min() default Double.MIN_VALUE;

    /**
     * 最大值（用于 number 类型）
     */
    double max() default Double.MAX_VALUE;

    /**
     * 步长（用于 number/slider 类型）
     */
    double step() default 1;

    /**
     * 精度/小数位数（用于 number 类型）
     */
    int precision() default 0;

    /**
     * 参数分组
     */
    String group() default "";

    /**
     * 排序（数值越小越靠前）
     */
    int order() default 100;

    /**
     * 依赖条件（格式: fieldName=value）
     */
    String dependsOn() default "";
}
