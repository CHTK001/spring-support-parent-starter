package com.chua.starter.datasource.provider;

import com.chua.starter.datasource.util.ClassUtils;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源属性提供者工厂
 * 使用Java SPI机制加载和管理数据源属性提供者
 *
 * @author CH
 * @since 2024/12/21
 */
@Slf4j
public class DataSourcePropertyProviderFactory {

    private static final Map<Class<?>, DataSourcePropertyProvider<?>> PROVIDER_CACHE = new ConcurrentHashMap<>();
    private static final List<DataSourcePropertyProvider<?>> PROVIDERS = new ArrayList<>();

    static {
        // 使用SPI机制加载Provider
        loadProvidersBySpi();
    }

    /**
     * 使用Java SPI机制加载Provider
     */
    private static void loadProvidersBySpi() {
        ServiceLoader<DataSourcePropertyProvider> serviceLoader = ServiceLoader.load(DataSourcePropertyProvider.class);
        for (DataSourcePropertyProvider<?> provider : serviceLoader) {
            try {
                // 检查数据源类是否存在
                if (isDataSourceClassAvailable(provider)) {
                    PROVIDERS.add(provider);
                    log.debug("加载数据源Provider: {}", provider.getClass().getName());
                }
            } catch (NoClassDefFoundError | Exception e) {
                log.debug("跳过不可用的Provider: {}", provider.getClass().getName());
            }
        }
        
        // 按优先级排序
        PROVIDERS.sort(Comparator.comparingInt(DataSourcePropertyProvider::getOrder));
        
        log.info("已加载{}个数据源Provider", PROVIDERS.size());
    }
    
    /**
     * 检查Provider对应的数据源类是否可用
     */
    private static boolean isDataSourceClassAvailable(DataSourcePropertyProvider<?> provider) {
        try {
            Class<?> dataSourceType = provider.getDataSourceType();
            return dataSourceType != null && ClassUtils.forName(dataSourceType.getName()) != null;
        } catch (NoClassDefFoundError | Exception e) {
            return false;
        }
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
