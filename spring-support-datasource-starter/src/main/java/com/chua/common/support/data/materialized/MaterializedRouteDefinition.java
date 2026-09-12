package com.chua.common.support.data.materialized;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
* 物理化路由定义。
*
* @since 2026/4/2
* @author CH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterializedRouteDefinition {

    /** 阈值 */
    private long threshold;
    /** 数据来源 */
    private String dataSource;
}
