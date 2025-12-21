package com.chua.starter.datasource.provider;

import com.chua.starter.datasource.util.ClassUtils;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源属性提供者工厂
 * 使用SPI机制加载和管理数据源属性提供者
 *
 * @author CH
 * @since 2024/12/21
 */
public class DataSourcePropertyProviderFactory {

    private static final Map<Class<?>, DataSourcePropertyProvider<?>> PROVIDER_CACHE = new ConcurrentHashMap<>();
    private static final List<DataSourcePropertyProvider<?>> PROVIDERS = new ArrayList<>();

    static {
        // 静态初始化，加载默认的provider
        loadDefaultProviders();
    }

    private static void loadDefaultProviders() {
        // 尝试加载Druid Provider
        if (ClassUtils.forName("com.alibaba.druid.pool.DruidDataSource") != null) {
            try {
                PROVIDERS.add(new DruidDataSourcePropertyProvider());
            } catch (NoClassDefFoundError ignored) {
            }
        }

        // 尝试加载Hikari Provider
        if (ClassUtils.forName("com.zaxxer.hikari.HikariDataSource") != null) {
            try {
                PROVIDERS.add(new HikariDataSourcePropertyProvider());
            } catch (NoClassDefFoundError ignored) {
            }
        }

        // 按优先级排序
        PROVIDERS.sort(Comparator.comparingInt(DataSourcePropertyProvider::getOrder));
    }

    /**
     * 注册自定义Provider
     *
     * @param provider provider实例
     */
    public static void register(DataSourcePropertyProvider<?> provider) {
        PROVIDERS.add(provider);
        PROVIDERS.sort(Comparator.comparingInt(DataSourcePropertyProvider::getOrder));
        // 清除缓存
        PROVIDER_CACHE.clear();
    }

    /**
     * 获取数据源对应的Provider
     *
     * @param dataSource 数据源
     * @return Provider，如果没有找到返回null
     */
    @SuppressWarnings("unchecked")
    public static <T extends DataSource> DataSourcePropertyProvider<T> getProvider(T dataSource) {
        if (dataSource == null) {
            return null;
        }
        return (DataSourcePropertyProvider<T>) getProvider(dataSource.getClass());
    }

    /**
     * 获取数据源类型对应的Provider
     *
     * @param dataSourceClass 数据源类型
     * @return Provider，如果没有找到返回null
     */
    @SuppressWarnings("unchecked")
    public static <T extends DataSource> DataSourcePropertyProvider<T> getProvider(Class<T> dataSourceClass) {
        if (dataSourceClass == null) {
            return null;
        }

        return (DataSourcePropertyProvider<T>) PROVIDER_CACHE.computeIfAbsent(dataSourceClass, clazz -> {
            for (DataSourcePropertyProvider<?> provider : PROVIDERS) {
                if (provider.supports(clazz)) {
                    return provider;
                }
            }
            return null;
        });
    }

    /**
     * 获取所有已注册的Provider
     *
     * @return Provider列表
     */
    public static List<DataSourcePropertyProvider<?>> getAllProviders() {
        return Collections.unmodifiableList(PROVIDERS);
    }

    /**
     * 获取数据源的所有属性
     *
     * @param dataSource 数据源
     * @return 属性Map，如果没有对应的Provider返回空Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getProperties(DataSource dataSource) {
        DataSourcePropertyProvider<DataSource> provider = (DataSourcePropertyProvider<DataSource>) getProvider(dataSource);
        if (provider != null) {
            return provider.getProperties(dataSource);
        }
        return Collections.emptyMap();
    }

    /**
     * 设置数据源属性
     *
     * @param dataSource 数据源
     * @param properties 属性Map
     */
    @SuppressWarnings("unchecked")
    public static void setProperties(DataSource dataSource, Map<String, Object> properties) {
        DataSourcePropertyProvider<DataSource> provider = (DataSourcePropertyProvider<DataSource>) getProvider(dataSource);
        if (provider != null) {
            provider.setProperties(dataSource, properties);
        }
    }

    /**
     * 设置数据源过滤器
     *
     * @param dataSource 数据源
     * @param filters    过滤器配置
     */
    @SuppressWarnings("unchecked")
    public static void setFilters(DataSource dataSource, String filters) {
        DataSourcePropertyProvider<DataSource> provider = (DataSourcePropertyProvider<DataSource>) getProvider(dataSource);
        if (provider != null) {
            provider.setFilters(dataSource, filters);
        }
    }

    /**
     * 判断是否支持该数据源类型
     *
     * @param dataSourceClass 数据源类型
     * @return 是否支持
     */
    public static boolean isSupported(Class<?> dataSourceClass) {
        return getProvider((Class<? extends DataSource>) dataSourceClass) != null;
    }

    /**
     * 获取首选的数据源类型
     * 按优先级返回第一个可用的数据源类型
     *
     * @return 数据源类型，如果没有可用的返回null
     */
    public static Class<? extends DataSource> getPreferredDataSourceType() {
        if (!PROVIDERS.isEmpty()) {
            return PROVIDERS.get(0).getDataSourceType();
        }
        return null;
    }
}
