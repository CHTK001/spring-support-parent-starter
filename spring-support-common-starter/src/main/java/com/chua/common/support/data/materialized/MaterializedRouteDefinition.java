package com.chua.common.support.data.materialized;

/**
* 物化视图路由定义
*
* @author CH
* @since 4.0.0
 */
public class MaterializedRouteDefinition {

    /** 阈值 */
    private long threshold;
    /** 数据来源 */
    private String dataSource;

    /**
    * 构造路由定义
    *
    * @param threshold 阈值
    * @param dataSource 数据源
    */
    public MaterializedRouteDefinition(long threshold, String dataSource) {
        this.threshold = threshold;
        this.dataSource = dataSource;
    }

    /**
    * 获取阈值
    *
    * @return 阈值
    */
    public long getThreshold() {
        return threshold;
    }

    /**
    * 获取数据源
    *
    * @return 数据源
    */
    public String getDataSource() {
        return dataSource;
    }
}
