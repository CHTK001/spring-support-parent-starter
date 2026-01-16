package com.chua.starter.datasource.provider;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 数据源属性提供者接口
 * 用于替代反射操作，直接通过类型安全的方式获取和设置数据源属性
 *
 * @author CH
 * @since 2024/12/21
 */
public interface DataSourcePropertyProvider<T extends DataSource> {

    /**
     * 获取支持的数据源类型
     *
     * @return 数据源类型
     */
    Class<T> getDataSourceType();

    /**
     * 判断是否支持该数据源
     *
     * @param dataSource 数据源
     * @return 是否支持
     */
    default boolean supports(DataSource dataSource) {
        return dataSource != null && getDataSourceType().isAssignableFrom(dataSource.getClass());
    }

    /**
     * 判断是否支持该数据源类型
     *
     * @param dataSourceClass 数据源类型
     * @return 是否支持
     */
    default boolean supports(Class<?> dataSourceClass) {
        return dataSourceClass != null && getDataSourceType().isAssignableFrom(dataSourceClass);
    }

    /**
     * 获取数据源的所有属性
     *
     * @param dataSource 数据源
     * @return 属性Map
     */
    Map<String, Object> getProperties(T dataSource);

    /**
     * 设置数据源属性
     *
     * @param dataSource 数据源
     * @param properties 属性Map
     */
    void setProperties(T dataSource, Map<String, Object> properties);

    /**
     * 设置单个属性
     *
     * @param dataSource 数据源
     * @param name       属性名
     * @param value      属性值
     */
    void setProperty(T dataSource, String name, Object value);

    /**
     * 设置过滤器(如果支持)
     *
     * @param dataSource 数据源
     * @param filters    过滤器配置
     */
    default void setFilters(T dataSource, String filters) {
        // 默认不支持
    }

    /**
     * 获取属性名称映射
     * 用于处理不同数据源的属性名差异
     *
     * @return 属性名映射 key=通用名称, value=实际属性名
     */
    default Map<String, String> getPropertyNameMapping() {
        return Map.of();
    }

    /**
     * 获取优先级，数值越小优先级越高
     *
     * @return 优先级
     */
    default int getOrder() {
        return 0;
    }
}
