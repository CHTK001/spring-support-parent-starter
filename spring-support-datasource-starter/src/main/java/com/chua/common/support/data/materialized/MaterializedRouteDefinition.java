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

    private long threshold;
    private String dataSource;
}
