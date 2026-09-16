package com.chua.starter.datasource.support;

import com.chua.common.support.lang.bean.NamingStyle;
import com.chua.starter.datasource.properties.HikariDataSourceProperties;
import com.chua.starter.datasource.properties.MultiDataSourceProperties;
import com.chua.starter.datasource.properties.MultiDataSourceSettingProperties;
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
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
* 数据库注入
* @author CH
* @since 4.0.0
 */
@Slf4j
public class DataSourceInspect implements BeanDefinitionRegistryPostProcessor,
        EnvironmentAware,
        ApplicationContextAware {

    /** 数据来源类 */
    protected Class<DataSource> dataSourceClass;
    /** Application上下文 */
    private ApplicationContext applicationContext;

    @Value("${spring.datasource.druid.filters:}")
    /** 过滤器 */
    private String filter;

    {
 // 使用提供者工厂获取首选数据源类型
        Class<? extends DataSource> preferredType = DataSourcePropertyProviderFactory.getPreferredDataSourceType();
        if (preferredType != null) {
            dataSourceClass = (Class<DataSource>) preferredType;
        } else {
            dataSourceClass = (Class<DataSource>) ClassUtils.forNames("com.alibaba.druid.pool.DruidDataSource", "com.zaxxer.hikari.HikariDataSource");
        }
    }

    /** Transaction */
    private static final String TRANSACTION = "_transaction";
    /** 前缀 */
    private final String[] PREFIX = new String[]{
            "spring.datasource.hikari.*",
            "spring.datasource.druid.*",
            "spring.datasource.*",
    };

    /** 环境 */
    private Environment environment;

    /** 环境支持 */
    private EnvironmentSupport environmentSupport;

    @Override
    /** Post处理Beandefinitionregistry */
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        /**
        * 注册multi数据源属性。
        * @param beanDefinitionRegistry Beandefinitionregistry
         */
        registerMultiDataSourceProperties(beanDefinitionRegistry);
        /**
        * 注册环境。
        * @param beanDefinitionRegistry Beandefinitionregistry
         */
        registerEnvironment(beanDefinitionRegistry);
    }

    /**
    * 注册multi数据源属性
    *
    * @param beanDefinitionRegistry Beandefinitionregistry
     */
    private void registerMultiDataSourceProperties(BeanDefinitionRegistry beanDefinitionRegistry) {
        MultiDataSourceProperties multiDataSourceProperties = bindMultiDataSourceProperties(environment);
        /**
        * 注册数据源。
        * @param multiDataSourceProperties.getDataSource( multi数据源属性.获取数据源(
         */
        registerDataSource(multiDataSourceProperties.getDataSource(), beanDefinitionRegistry, null);

        MultiHikariDataSourceProperties multiHikariDataSourceProperties = bindMultiHikariDataSourceProperties(environment);
        /**
        * 注册数据源。
        * @param multiHikariDataSourceProperties.getDataSource( multihikari数据源属性.获取数据源(
         */
        registerDataSource(multiHikariDataSourceProperties.getDataSource(), beanDefinitionRegistry, HikariDataSource.class);
    }

    /**
    * 绑定multi数据源属性
    *
    * @param environment 环境
    * @return bindmulti数据源属性的结果
     */
    static MultiDataSourceProperties bindMultiDataSourceProperties(Environment environment) {
        MultiDataSourceProperties current = Binder.get(environment)
                .bindOrCreate(MultiDataSourceProperties.PRE, MultiDataSourceProperties.class);
        MultiDataSourceProperties legacy = Binder.get(environment)
                .bindOrCreate(MultiDataSourceProperties.LEGACY_PRE, MultiDataSourceProperties.class);

        MultiDataSourceProperties result = new MultiDataSourceProperties();
        result.setEnable(current.isEnable() || legacy.isEnable());
        result.setDataSource(mergeDataSourceProperties(current.getDataSource(), legacy.getDataSource()));
        return result;
    }

    /**
    * 绑定multihikari数据源属性
    *
    * @param environment 环境
    * @return bindmultihikari数据源属性的结果
     */
    static MultiHikariDataSourceProperties bindMultiHikariDataSourceProperties(Environment environment) {
        MultiHikariDataSourceProperties current = Binder.get(environment)
                .bindOrCreate(MultiHikariDataSourceProperties.PRE, MultiHikariDataSourceProperties.class);
        MultiHikariDataSourceProperties legacy = Binder.get(environment)
                .bindOrCreate(MultiHikariDataSourceProperties.LEGACY_PRE, MultiHikariDataSourceProperties.class);

        MultiHikariDataSourceProperties result = new MultiHikariDataSourceProperties();
        result.setEnable(current.isEnable() || legacy.isEnable());
        result.setDataSource(mergeDataSourceProperties(current.getDataSource(), legacy.getDataSource()));
        return result;
    }

    /**
    * 合并数据源属性
    *
    * @param current 当前
    * @param legacy legacy
    * @return 合并数据源属性的结果
     */
    private static <T extends DataSourceProperties> List<T> mergeDataSourceProperties(List<T> current, List<T> legacy) {
        Map<String, T> merged = new LinkedHashMap<>();
        /**
        * 放入数据源属性。
        * @param merged 合并
        * @param legacy legacy
         */
        putDataSourceProperties(merged, legacy);
        /**
        * 放入数据源属性。
        * @param merged 合并
        * @param current 当前
         */
        putDataSourceProperties(merged, current);
        return merged.isEmpty() ? null : new ArrayList<>(merged.values());
    }

    /**
    * 放入数据源属性
    *
    * @param target Target
    * @param source 源
    * @return 放入数据源属性的结果
     */
    private static <T extends DataSourceProperties> void putDataSourceProperties(Map<String, T> target, List<T> source) {
        if (source == null) {
            return;
        }
        for (T item : source) {
            if (item == null) {
                continue;
            }
            String key = Strings.isNullOrEmpty(item.getName()) ? UUID.randomUUID().toString() : item.getName();
            target.put(key, item);
        }
    }

    /**
    * 注册数据源
    * @param dataSource 数据源
    * @param beanDefinitionRegistry Beandefinitionregistry
    * @param dataSourceType 数据源类型
     */
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
            log.info("[Datasource] 注册数据源: {}", highlight(name));
        }
    }

    /**
    * 注册环境
    *
    * @param beanDefinitionRegistry Beandefinitionregistry
     */
    private void registerEnvironment(BeanDefinitionRegistry beanDefinitionRegistry) {
        if (null == dataSourceClass) {
            return;
        }
        for (String prefix : PREFIX) {
            Map<String, Object> objectMap = environmentSupport.getProperties(prefix);
            /**
            * analysis数据源。
            * @param objectMap 对象映射
            * @param prefix 前缀
            * @param beanDefinitionRegistry Beandefinitionregistry
             */
            analysisDataSource(objectMap, prefix, beanDefinitionRegistry);
        }


        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            MutablePropertySources propertySources = configurableEnvironment.getPropertySources();
            propertySources.stream().iterator().forEachRemaining(propertySource -> {
            });
        }

        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(DynamicDataSource.class);
        beanDefinitionBuilder.setPrimary(true);

 // 桥接配置：将 strict/加载balance 设置到 dynamic数据源
        MultiDataSourceSettingProperties settingProperties = Binder.get(environment)
                .bindOrCreate(MultiDataSourceSettingProperties.PRE, MultiDataSourceSettingProperties.class);
        beanDefinitionBuilder.addPropertyValue("strict", settingProperties.isStrict());

        //注册动态数据源
        beanDefinitionRegistry.registerBeanDefinition("dynamicDataSource", beanDefinitionBuilder.getBeanDefinition());

        // 在所有数据源注册完成后，应用全局负载均衡策略
        String loadBalance = settingProperties.getLoadBalance();
        if (loadBalance != null && !loadBalance.isEmpty()) {
            DataSourceGroupRegistry.setGlobalLoadBalanceStrategy(loadBalance);
        }

        log.info("[Datasource] 注册动态数据源 (strict={}, loadBalance={})",
                settingProperties.isStrict(), settingProperties.getLoadBalance());
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
            
 // 使用提供者获取属性，替代反射
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
                /**
                * analysismapping。
                * @param objectMap 对象映射
                * @param newKey 新钥匙
                * @param dataSource 数据源
                 */
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

 // 将 spring.datasource.hikari.* 属性应用到已创建的 hikari数据源
        if (dataSource instanceof HikariDataSource hikari) {
            /**
            * applyhikari属性。
            * @param hikari hikari
            * @param prefix 前缀
             */
            applyHikariProperties(hikari, prefix);
        }

 // 使用提供者设置过滤器，替代反射
        if (null != dataSource && null != filter && !filter.isEmpty()) {
            DataSourcePropertyProviderFactory.setFilters(dataSource, filter);
        }

        //补充名称
        /**
        * analysismapping。
        * @param objectMap 对象映射
        * @param prefix 前缀
        * @param dataSource 数据源
         */
        analysisMapping(objectMap, prefix, dataSource);
        return dataSource;
    }

    /**
    * 将 前缀.hikari.* 配置属性应用到 hikari数据源。
    * Spring Boot 的 Binder.bind() 对已有实例的绑定可能不生效，
    * 因此通过 提供者 直接设置属性。
    * @param hikari hikari
    * @param prefix 前缀
     */
    private void applyHikariProperties(HikariDataSource hikari, String prefix) {
        String hikariPrefix = prefix + ".hikari";
        Map<String, Object> hikariProps = environmentSupport.getProperties(hikariPrefix + ".*");
        if (hikariProps == null || hikariProps.isEmpty()) {
            return;
        }
        DataSourcePropertyProvider<HikariDataSource> provider = DataSourcePropertyProviderFactory.getProvider(hikari);
        if (provider == null) {
            return;
        }
        Map<String, Object> toSet = new HashMap<>();
        String stripPrefix = hikariPrefix + ".";
        for (Map.Entry<String, Object> entry : hikariProps.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(stripPrefix)) {
                String prop = key.substring(stripPrefix.length());
                // kebab-大小写 → camel大小写
                prop = NamingStyle.ROMAN.toCamel(prop);
                toSet.put(prop, entry.getValue());
            }
        }
        if (!toSet.isEmpty()) {
            provider.setProperties(hikari, toSet);
            log.info("[Datasource] 应用 HikariCP 属性: {}", toSet);
        }
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
        
 // 使用提供者设置属性，替代反射
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
        
 // 通过提供者设置属性
        provider.setProperties(dataSource, properties);
    }

    @Override
    /** Post处理Bean工厂 */
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
    }

    @Override
    /** 设置application上下文 */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.filter = applicationContext.getEnvironment().getProperty("spring.datasource.druid.filters");
    }

    @Override
    /** 设置环境 */
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        this.environmentSupport = new EnvironmentSupport(environment);
    }
}

