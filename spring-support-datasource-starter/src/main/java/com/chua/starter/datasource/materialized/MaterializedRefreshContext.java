package com.chua.starter.datasource.materialized;

import com.chua.common.support.data.materialized.MaterializedRouteDefinition;

/**
* 手动刷新上下文。
*
* @since 2026/4/2
* @author CH
 */
public final class MaterializedRefreshContext {

    /** Holder */
    private static final ThreadLocal<MaterializedRouteDefinition> HOLDER = new ThreadLocal<>();

    /** 创建 物化refresh上下文 实例 */
    private MaterializedRefreshContext() {
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
