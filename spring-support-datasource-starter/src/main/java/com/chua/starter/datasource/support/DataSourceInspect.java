package com.chua.starter.datasource.support;

import com.chua.starter.datasource.properties.HikariDataSourceProperties;
import com.chua.starter.datasource.properties.MultiDataSourceProperties;
import com.chua.starter.datasource.properties.MultiHikariDataSourceProperties;
import com.chua.starter.datasource.provider.DataSourcePropertyProvider;
import com.chua.starter.datasource.provider.DataSourcePropertyProviderFactory;
import com.chua.starter.datasource.util.ClassUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Ordering;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

import javax.sql.DataSource;
import java.util.*;

/**
 * 数据库注入
 */
@Slf4j
public class DataSourceInspect implements BeanDefinitionRegistryPostProcessor,
        EnvironmentAware,
        ApplicationContextAware {

    protected Class<DataSource> dataSourceClass;
    private ApplicationContext applicationContext;

    @Value("${spring.datasource.druid.filters:}")
    private String filter;

    {
        // 使用Provider工厂获取首选数据源类型
        Class<? extends DataSource> preferredType = DataSourcePropertyProviderFactory.getPreferredDataSourceType();
        if (preferredType != null) {
            dataSourceClass = (Class<DataSource>) preferredType;
        } else {
            dataSourceClass = (Class<DataSource>) ClassUtils.forNames("com.alibaba.druid.pool.DruidDataSource", "com.zaxxer.hikari.HikariDataSource");
        }
    }

    private static final String TRANSACTION = "_transaction";
    private final String[] PREFIX = new String[]{
            "spring.datasource.hikari.*",
            "spring.datasource.druid.*",
            "spring.datasource.*",
    };

    private Environment environment;

    private EnvironmentSupport environmentSupport;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        registerMultiDataSourceProperties(beanDefinitionRegistry);
        registerEnvironment(beanDefinitionRegistry);
    }

    private void registerMultiDataSourceProperties(BeanDefinitionRegistry beanDefinitionRegistry) {
        MultiDataSourceProperties multiDataSourceProperties = Binder.get(environment).bindOrCreate(MultiDataSourceProperties.PRE, MultiDataSourceProperties.class);
        registerDataSource(multiDataSourceProperties.getDataSource(), beanDefinitionRegistry, null);

        MultiHikariDataSourceProperties multiHikariDataSourceProperties = Binder.get(environment).bindOrCreate(MultiHikariDataSourceProperties.PRE, MultiHikariDataSourceProperties.class);
        registerDataSource(multiHikariDataSourceProperties.getDataSource(), beanDefinitionRegistry, HikariDataSource.class);
    }

    private void registerDataSource(List<? extends DataSourceProperties> dataSource,
                                    BeanDefinitionRegistry beanDefinitionRegistry,
                                    Class<? extends DataSource> dataSourceType) {
        if (null == dataSource) {
            return;
        }

        for (DataSourceProperties dataSourceProperty : dataSource) {
            if (null == dataSourceProperty.getUrl() || null == dataSourceProperty.getName()) {
                continue;
            }

            if (null != dataSourceType) {
                dataSourceProperty.setType(dataSourceType);
            }

            try {
                dataSourceProperty.afterPropertiesSet();
            } catch (Exception e) {
                continue;
            }

            DataSource dataSource1 = dataSourceProperty.initializeDataSourceBuilder()
                    .build();

            if (dataSourceProperty instanceof HikariDataSourceProperties hikariDataSourceProperties) {
                HikariDataSource hikariDataSource = (HikariDataSource) dataSource1;
                hikariDataSource.setIdleTimeout(hikariDataSourceProperties.getIdleTimeout());
                hikariDataSource.setLeakDetectionThreshold(hikariDataSourceProperties.getLeakDetectionThreshold());
                hikariDataSource.setMaxLifetime(hikariDataSourceProperties.getMaxLifetime());
                hikariDataSource.setMaximumPoolSize(hikariDataSourceProperties.getMaxPoolSize());
                hikariDataSource.setMinimumIdle(hikariDataSourceProperties.getMinIdle());
            }
            //注册动态数据源
            String name = dataSourceProperty.getName();
            beanDefinitionRegistry.registerBeanDefinition("dataSource#" + name, BeanDefinitionBuilder
                    .rootBeanDefinition(DataSource.class, () -> dataSource1).getBeanDefinition()
            );
            DataSourceContextSupport.addDatasource(name, dataSource1);
            log.info("注册数据源:{}", name);
        }
    }

    private void registerEnvironment(BeanDefinitionRegistry beanDefinitionRegistry) {
        if (null == dataSourceClass) {
            return;
        }
        for (String prefix : PREFIX) {
            Map<String, Object> objectMap = environmentSupport.getProperties(prefix);
            analysisDataSource(objectMap, prefix, beanDefinitionRegistry);
        }


        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            MutablePropertySources propertySources = configurableEnvironment.getPropertySources();
            propertySources.stream().iterator().forEachRemaining(propertySource -> {
            });
        }

        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(DynamicDataSource.class);
        beanDefinitionBuilder.setPrimary(true);
        //注册动态数据源
        beanDefinitionRegistry.registerBeanDefinition("dynamicDataSource", beanDefinitionBuilder.getBeanDefinition());
        log.info("注册动态数据源");
    }

    /**
     * 分析数据源
     *
     * @param objectMap              配置集
     * @param prefix                 前缀
     * @param beanDefinitionRegistry 注册器
     */
    private void analysisDataSource(Map<String, Object> objectMap, String prefix, BeanDefinitionRegistry beanDefinitionRegistry) {
        String urlPrefix = prefix.replace("*", "url");
        String urlPrefix2 = prefix.replace("*", "jdbc-url");
        Map<String, DataSource> dataSources = new HashMap<>();

        String oldPrefix = prefix.replace(".*", "");
        //单数据源
        if (objectMap.containsKey(urlPrefix)) {
            dataSources.put("master", createDataSource(objectMap, oldPrefix));
        }
        if (objectMap.containsKey(urlPrefix2)) {
            dataSources.put("master", createDataSource(objectMap, urlPrefix2.replace(".jdbc-url", "")));
        }
        dataSources.putAll(createDataSources(objectMap, oldPrefix));

        List<String> strings = Ordering.natural().sortedCopy(dataSources.keySet());
        for (String string : strings) {

            DataSource dataSource = dataSources.get(string);
            if (null == dataSource) {
                continue;
            }

            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(dataSourceClass);
            
            // 使用Provider获取属性，替代反射
            DataSourcePropertyProvider<DataSource> provider = DataSourcePropertyProviderFactory.getProvider(dataSource);
            if (provider != null) {
                Map<String, Object> properties = provider.getProperties(dataSource);
                properties.forEach((name, value) -> {
                    if (value != null) {
                        beanDefinitionBuilder.addPropertyValue(name, value);
                    }
                });
            }

            if (beanDefinitionRegistry.containsBeanDefinition(string)) {
                return;
            }

            beanDefinitionRegistry.registerBeanDefinition(string, beanDefinitionBuilder.getBeanDefinition());
            DataSourceContextSupport.addDatasource(string, dataSource);
        }
    }

    /**
     * 数据源
     *
     * @param objectMap 配置
     * @param prefix    前缀
     * @return 数据源
     */
    private Map<String, DataSource> createDataSources(Map<String, Object> objectMap, String prefix) {
        Set<String> result = new HashSet<>();
        objectMap.forEach((k, v) -> {
            String replace = k.replace(prefix + ".", "");
            int index = replace.indexOf(".");
            if (index > -1) {
                result.add(replace.substring(0, index));
            }
        });
        Map<String, DataSource> dataSources = new HashMap<>();
        for (String item : result) {
            String newKey = prefix + "." + item;
            Class<? extends DataSource> dataSourceType = getDataSourceType(objectMap, newKey);
            if (environment.containsProperty(newKey + ".jdbcUrl")) {
                dataSources.put(item, Binder.get(environment).bind(newKey, dataSourceType).orElse(null));
            }
            if (environment.containsProperty(newKey + ".url")) {
                dataSources.put(item, Binder.get(environment).bind(newKey, dataSourceType).orElse(null));
            }

            DataSource dataSource = dataSources.get(item);
            if (null != dataSource) {
                //补充名称
                analysisMapping(objectMap, newKey, dataSource);
            }
        }
        return dataSources;
    }

    /**
     * 数据源
     *
     * @param objectMap 配置
     * @param prefix    前缀
     * @return 数据源
     */
    private DataSource createDataSource(Map<String, Object> objectMap, String prefix) {
        Class<? extends DataSource> dataSourceType = getDataSourceType(objectMap, prefix);
        BindResult<? extends DataSource> bindResult = Binder.get(environment).bind(prefix, dataSourceType);
        DataSource dataSource = bindResult.orElse(null);
        
        // 使用Provider设置过滤器，替代反射
        if (null != dataSource && null != filter && !filter.isEmpty()) {
            DataSourcePropertyProviderFactory.setFilters(dataSource, filter);
        }
        
        //补充名称
        analysisMapping(objectMap, prefix, dataSource);
        return dataSource;
    }

    /**
     * 数据类型
     *
     * @param objectMap 数据
     * @param prefix    前缀
     * @return 数据源
     */
    private Class<? extends DataSource> getDataSourceType(Map<String, Object> objectMap, String prefix) {
        String dataSourceType = objectMap.getOrDefault(prefix + ".type", "").toString();
        if (Strings.isNullOrEmpty(dataSourceType) || null == ClassUtils.forName(dataSourceType)) {
            dataSourceType = dataSourceClass.getName();
        }
        return (Class<? extends DataSource>) ClassUtils.forName(dataSourceType);
    }

    /**
     * 名称映射
     *
     * @param objectMap  数据源配置
     * @param prefix     前缀
     * @param dataSource 数据源
     */
    private void analysisMapping(Map<String, Object> objectMap, String prefix, DataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        
        // 使用Provider设置属性，替代反射
        DataSourcePropertyProvider<DataSource> provider = DataSourcePropertyProviderFactory.getProvider(dataSource);
        if (provider == null) {
            return;
        }
        
        Map<String, String> propertyMapping = provider.getPropertyNameMapping();
        Map<String, Object> properties = new HashMap<>();
        
        // 收集需要设置的属性
        for (Map.Entry<String, String> entry : propertyMapping.entrySet()) {
            String configKey = entry.getKey();
            String propertyName = entry.getValue();
            
            Object value = objectMap.get(prefix + "." + configKey);
            if (value != null) {
                properties.put(propertyName, value);
            }
        }
        
        // 通过Provider设置属性
        provider.setProperties(dataSource, properties);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.filter = applicationContext.getEnvironment().getProperty("spring.datasource.druid.filters");
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        this.environmentSupport = new EnvironmentSupport(environment);
    }
}
