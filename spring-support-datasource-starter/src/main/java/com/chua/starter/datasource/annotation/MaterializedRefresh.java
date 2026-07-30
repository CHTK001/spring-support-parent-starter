package com.chua.starter.datasource.annotation;

import java.lang.annotation.*;

/**
 * 手动触发物理化副本刷新。
 *
 * @author CH
 * @since 2026/4/2
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MaterializedRefresh {

    /**
     * 刷新阈值。
     * 小于等于 0 时使用全局默认值。
     *
     * @return 阈值
     */
    long threshold() default -1L;

    /**
     * 指定源数据源名称。
     *
     * @return 数据源名称
     */
    String dataSource() default "";
}
