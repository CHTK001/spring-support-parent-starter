package com.chua.starter.sync.data.support.adapter;

import com.chua.starter.sync.data.support.adapter.file.FileDataSourceAdapter;
import com.chua.starter.sync.data.support.adapter.jdbc.*;
import com.chua.starter.sync.data.support.adapter.mq.KafkaDataSourceAdapter;
import com.chua.starter.sync.data.support.adapter.nosql.MongoDataSourceAdapter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源适配器工厂
 */
@Component
public class DataSourceAdapterFactory {
    
    private final Map<String, Class<? extends DataSourceAdapter>> adapterRegistry = new ConcurrentHashMap<>();
    private final Map<String, DataSourceAdapter> adapterPool = new ConcurrentHashMap<>();
    
    public DataSourceAdapterFactory() {
        register("MYSQL", MySQLDataSourceAdapter.class);
        register("POSTGRESQL", PostgreSQLDataSourceAdapter.class);
        register("ORACLE", OracleDataSourceAdapter.class);
        register("SQLSERVER", SQLServerDataSourceAdapter.class);
        register("MONGODB", MongoDataSourceAdapter.class);
        register("KAFKA", KafkaDataSourceAdapter.class);
        register("FILE", FileDataSourceAdapter.class);
    }
    
    public void register(String type, Class<? extends DataSourceAdapter> adapterClass) {
        adapterRegistry.put(type.toUpperCase(), adapterClass);
    }
    
    public DataSourceAdapter getAdapter(String type) {
        return adapterPool.computeIfAbsent(type.toUpperCase(), k -> {
            Class<? extends DataSourceAdapter> adapterClass = adapterRegistry.get(k);
            if (adapterClass == null) {
                throw new IllegalArgumentException("不支持的数据源类型: " + type);
            }
            try {
                var constructor = adapterClass.getDeclaredConstructor();
                if (!constructor.canAccess(null)) {
                    constructor.setAccessible(true);
                }
                return constructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("创建适配器失败: " + type, e);
            }
        });
    }
    
    public void releaseAdapter(String type) {
        DataSourceAdapter adapter = adapterPool.remove(type.toUpperCase());
        if (adapter != null) {
            adapter.close();
        }
    }
    
    public void releaseAll() {
        adapterPool.values().forEach(DataSourceAdapter::close);
        adapterPool.clear();
    }
}
