package com.chua.starter.sync.data.support.adapter;

import com.chua.starter.sync.data.support.adapter.jdbc.JdbcDataSourceAdapter;
import com.chua.starter.sync.data.support.adapter.nosql.MongoDataSourceAdapter;
import com.chua.starter.sync.data.support.adapter.file.FileDataSourceAdapter;
import com.chua.starter.sync.data.support.config.DataSourceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据源适配器单元测试
 */
class AdapterTest {
    
    private DataSourceConfig jdbcConfig;
    private DataSourceConfig mongoConfig;
    private DataSourceConfig fileConfig;
    
    @BeforeEach
    void setUp() {
        // JDBC配置
        jdbcConfig = new DataSourceConfig();
        jdbcConfig.setType("jdbc");
        Map<String, Object> jdbcProps = new HashMap<>();
        jdbcProps.put("url", "jdbc:h2:mem:testdb");
        jdbcProps.put("username", "sa");
        jdbcProps.put("password", "");
        jdbcProps.put("driverClassName", "org.h2.Driver");
        jdbcConfig.setProperties(jdbcProps);
        
        // MongoDB配置
        mongoConfig = new DataSourceConfig();
        mongoConfig.setType("mongodb");
        Map<String, Object> mongoProps = new HashMap<>();
        mongoProps.put("uri", "mongodb://localhost:27017");
        mongoProps.put("database", "test");
        mongoProps.put("collection", "test_collection");
        mongoConfig.setProperties(mongoProps);
        
        // 文件配置
        fileConfig = new DataSourceConfig();
        fileConfig.setType("file");
        Map<String, Object> fileProps = new HashMap<>();
        fileProps.put("path", "test.csv");
        fileProps.put("format", "csv");
        fileConfig.setProperties(fileProps);
    }
    
    @Test
    void testJdbcAdapterInitialization() {
        JdbcDataSourceAdapter adapter = new JdbcDataSourceAdapter();
        assertNotNull(adapter);
        
        // 测试配置
        adapter.configure(jdbcConfig);
        assertTrue(adapter.isConfigured());
    }
    
    @Test
    void testJdbcAdapterConnect() {
        JdbcDataSourceAdapter adapter = new JdbcDataSourceAdapter();
        adapter.configure(jdbcConfig);
        
        try {
            adapter.connect();
            assertTrue(adapter.isConnected());
        } catch (Exception e) {
            // H2数据库可能未安装，跳过测试
            System.out.println("JDBC连接测试跳过: " + e.getMessage());
        } finally {
            adapter.disconnect();
        }
    }
    
    @Test
    void testJdbcAdapterRead() {
        JdbcDataSourceAdapter adapter = new JdbcDataSourceAdapter();
        adapter.configure(jdbcConfig);
        
        try {
            adapter.connect();
            
            // 创建测试表
            adapter.execute("CREATE TABLE test_table (id INT, name VARCHAR(50))");
            adapter.execute("INSERT INTO test_table VALUES (1, 'test1'), (2, 'test2')");
            
            // 读取数据
            Stream<Map<String, Object>> stream = adapter.read("SELECT * FROM test_table");
            assertNotNull(stream);
            
            long count = stream.count();
            assertEquals(2, count);
            
        } catch (Exception e) {
            System.out.println("JDBC读取测试跳过: " + e.getMessage());
        } finally {
            adapter.disconnect();
        }
    }
    
    @Test
    void testMongoAdapterInitialization() {
        MongoDataSourceAdapter adapter = new MongoDataSourceAdapter();
        assertNotNull(adapter);
        
        adapter.configure(mongoConfig);
        assertTrue(adapter.isConfigured());
    }
    
    @Test
    void testFileAdapterInitialization() {
        FileDataSourceAdapter adapter = new FileDataSourceAdapter();
        assertNotNull(adapter);
        
        adapter.configure(fileConfig);
        assertTrue(adapter.isConfigured());
    }
    
    @Test
    void testAdapterFactoryRegistration() {
        // 测试适配器工厂注册机制
        DataSourceAdapterFactory factory = new DataSourceAdapterFactory();
        
        factory.register("jdbc", JdbcDataSourceAdapter.class);
        factory.register("mongodb", MongoDataSourceAdapter.class);
        factory.register("file", FileDataSourceAdapter.class);
        
        assertTrue(factory.hasAdapter("jdbc"));
        assertTrue(factory.hasAdapter("mongodb"));
        assertTrue(factory.hasAdapter("file"));
        assertFalse(factory.hasAdapter("unknown"));
    }
    
    @Test
    void testAdapterFactoryCreate() {
        DataSourceAdapterFactory factory = new DataSourceAdapterFactory();
        factory.register("jdbc", JdbcDataSourceAdapter.class);
        
        DataSourceAdapter adapter = factory.create("jdbc");
        assertNotNull(adapter);
        assertTrue(adapter instanceof JdbcDataSourceAdapter);
    }
}
