package com.chua.starter.datasource.materialized;

import com.chua.common.support.data.materialized.MaterializedRouteDefinition;

/**
 * 物理化路由上下文。
 *
 * @since 2026/4/2
 * @author CH
 */
public final class MaterializedRouteContext {

    /** Holder */
    private static final ThreadLocal<MaterializedRouteDefinition> HOLDER = new ThreadLocal<>();

    /** 创建 物化route上下文 实例 */
    private MaterializedRouteContext() {
    }

    /**
     * 设置
     *
     * @param definition definition
     */
    public static void set(MaterializedRouteDefinition definition) {
        HOLDER.set(definition);
    }

    /**
     * 获取
     *
     * @return 获取的结果
     */
    public static MaterializedRouteDefinition get() {
        return HOLDER.get();
    }

    /** Clear */
    public static void clear() {
        HOLDER.remove();
    }
}
