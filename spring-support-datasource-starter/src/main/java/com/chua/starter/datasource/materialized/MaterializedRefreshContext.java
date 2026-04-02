package com.chua.starter.datasource.materialized;

import com.chua.common.support.data.materialized.MaterializedRouteDefinition;

/**
 * 手动刷新上下文。
 *
 * @author CH
 * @since 2026/4/2
 */
public final class MaterializedRefreshContext {

    private static final ThreadLocal<MaterializedRouteDefinition> HOLDER = new ThreadLocal<>();

    private MaterializedRefreshContext() {
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
