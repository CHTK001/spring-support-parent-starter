package com.chua.starter.datasource.annotation;

import java.lang.annotation.*;

/**
 * 开启 SQL 物理化路由。
 * <p>
 * 标注在类或方法上后，查询会优先尝试命中内存副本；
 * 未命中或内存执行失败时自动回落到原始数据源。
 * </p>
 *
 * @author CH
 * @since 2026/4/2
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MaterializedRoute {

    /**
     * 物理化阈值，单位为表行数。
     * 小于等于 0 时使用全局默认值。
     *
     * @return 阈值
     */
    long threshold() default -1L;

    /**
     * 指定源数据源名称。
     * 留空时优先使用当前上下文数据源，否则回退到 master。
     *
     * @return 数据源名称
     */
    String dataSource() default "";

}
