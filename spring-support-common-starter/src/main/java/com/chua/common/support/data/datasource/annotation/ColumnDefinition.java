package com.chua.common.support.data.datasource.annotation;

import java.lang.annotation.*;

/**
* 列定义注解，用于标记实体类字段对应的数据库列信息
*
* @author CH
* @since 4.0.0
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ColumnDefinition {

    /**
    * 数据库列名
    *
    * @return 列名
     */
    String value() default "";

    /**
    * 列类型
    *
    * @return 列类型
     */
    String type() default "";

    /**
    * 列长度
    *
    * @return 列长度
     */
    int length() default 255;

    /**
    * 默认值
    *
    * @return 默认值
     */
    String defaultValue() default "";

    /**
    * 列描述
    *
    * @return 列描述
     */
    String comment() default "";

    /**
    * 是否刷新路由
    *
    * @return 是否刷新路由
     */
    boolean refresh() default false;

    /**
    * 是否必填
    *
    * @return 是否必填
     */
    boolean required() default false;

    /**
    * 列描述
    *
    * @return 列描述
     */
    String description() default "";
}
