package com.chua.starter.datasource.materialized;

import com.chua.common.support.data.materialized.MaterializedRouteDefinition;

/**
 * 物理化路由上下文。
 *
 * @since 2026/4/2
 */
public final class MaterializedRouteContext {

    /** Holder */
    private static final ThreadLocal<MaterializedRouteDefinition> HOLDER = new ThreadLocal<>();

    private MaterializedRouteContext() {
    }

    public static void set(MaterializedRouteDefinition definition) {
        HOLDER.set(definition);
    }

    public static MaterializedRouteDefinition get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
