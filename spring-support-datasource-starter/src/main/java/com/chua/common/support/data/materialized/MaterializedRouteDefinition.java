package com.chua.common.support.data.materialized;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 物理化路由定义。
 *
 * @author CH
 * @since 2026/4/2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterializedRouteDefinition {

    /**
     * 表行数阈值。
     */
    private long threshold;

    /**
     * 源数据源名称。
     */
    private String dataSource;
}
