package com.chua.common.support.data.datasource.annotation;

import java.lang.annotation.*;

/**
 * 列忽略注解，用于标记实体类字段不需要持久化到数据库
 *
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ColumnIgnore {
}
