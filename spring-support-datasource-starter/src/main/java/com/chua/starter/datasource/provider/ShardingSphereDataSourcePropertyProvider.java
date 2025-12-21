package com.chua.starter.datasource.provider;

import org.apache.shardingsphere.driver.jdbc.core.datasource.ShardingSphereDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * ShardingSphere数据源属性提供者
 * 支持ShardingSphere 5.x分库分表
 *
 * @author CH
 * @since 2024/12/21
 */
public class ShardingSphereDataSourcePropertyProvider implements DataSourcePropertyProvider<ShardingSphereDataSource> {

    @Override
    public Class<ShardingSphereDataSource> getDataSourceType() {
        return ShardingSphereDataSource.class;
    }

    @Override
    public Map<String, Object> getProperties(ShardingSphereDataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        
        // ShardingSphere属性
        properties.put("databaseName", dataSource.getDatabaseName());
        
        return properties;
    }

    @Override
    public void setProperties(ShardingSphereDataSource dataSource, Map<String, Object> properties) {
        // ShardingSphere数据源通常通过配置文件或Builder创建，不支持动态设置属性
    }

    @Override
    public void setProperty(ShardingSphereDataSource dataSource, String name, Object value) {
        // ShardingSphere数据源通常通过配置文件或Builder创建，不支持动态设置属性
    }

    @Override
    public int getOrder() {
        return 50;
    }
}
