package com.chua.common.support.data.materialized;

/**
 * 物化视图 SQL 路由类
 *
 * @author CH
 * @since 4.0.0
 */
public class MaterializedSqlRoute {

    /**
     * 数据来源名称
    */
    private String dataSourceName;
    /**
     * 表名称
    */
    private String tableName;

    /**
     * 获取数据源名称
     *
     * @return 数据源名称
     */
    public String getDataSource() {
        return dataSourceName;
    }

    /**
     * 设置数据源名称
     *
     * @param dataSourceName 数据源名称
     */
    public void setDataSource(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    /**
     * 判断是否已路由
     *
     * @return 已路由时返回 true
     */
    public boolean isRouted() {
        return dataSourceName != null && !dataSourceName.isEmpty();
    }

    /**
     * 获取数据源名称（与 获取数据源 相同）
     *
     * @return 数据源名称
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * 设置数据源名称
     *
     * @param dataSourceName 数据源名称
     */
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    /**
     * 获取表名
     *
     * @return 表名
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 设置表名
     *
     * @param tableName 表名
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
}
