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

    /** 创建 MaterializedRouteContext 实例 */
    private MaterializedRouteContext() {
    }

    /** 设置 */
    public static void set(MaterializedRouteDefinition definition) {
        HOLDER.set(definition);
    }

    /** 获取 */
    public static MaterializedRouteDefinition get() {
        return HOLDER.get();
    }

    /** Clear */
    public static void clear() {
        HOLDER.remove();
    }
}
