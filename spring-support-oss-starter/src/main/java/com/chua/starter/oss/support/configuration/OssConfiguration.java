package com.chua.starter.oss.support.configuration;

import com.chua.common.support.constant.Action;
import com.chua.common.support.database.AutoMetadata;
import com.chua.common.support.database.executor.MetadataExecutor;
import com.chua.common.support.log.Log;
import com.chua.common.support.utils.ClassUtils;
import com.chua.hibernate.support.database.resolver.HibernateMetadataResolver;
import com.chua.starter.common.support.annotations.DS;
import com.chua.starter.oss.support.pojo.OssSystem;
import com.chua.starter.oss.support.properties.OssProperties;
import com.chua.starter.oss.support.provider.OssProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.MultiValueMap;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 自动建表
 *
 * @author CH
 * @since 2022/8/1 17:07
 */
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(OssProperties.class)
public class OssConfiguration implements ApplicationContextAware, BeanDefinitionRegistryPostProcessor {

    private static final Log log = Log.getLogger(OssConfiguration.class);
    OssProperties ossProperties;
    private Map<String, DataSource> beansOfType;

    @Bean
    @ConditionalOnMissingBean
    public OssProvider ossProvider() {
        return new OssProvider();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ossProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(OssProperties.PRE, OssProperties.class);
        this.beansOfType = applicationContext.getBeansOfType(DataSource.class);
        AutoMetadata autoMetadata = AutoMetadata.builder()
                .metadataResolver(new HibernateMetadataResolver()).build();

        DataSource dataSource = getDataSource(OssSystem.class);
        if (null == dataSource) {
            log.warn("数据源不存在");
            return;
        }
        MetadataExecutor metadataExecutor = autoMetadata.doExecute(OssSystem.class);
        try {
            metadataExecutor.execute(dataSource, Action.UPDATE);
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage());
        }
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {

    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    private DataSource getDataSource(Class<?> aClass) {
        if (beansOfType.size() == 1) {
            return beansOfType.values().iterator().next();
        }

        if (!AnnotatedElementUtils.hasAnnotation(aClass, DS.class)) {
            if (beansOfType.containsKey("master")) {
                log.debug("建表数据源 :{}", "master");
                return beansOfType.get("master");
            } else {
                for (Map.Entry<String, DataSource> entry : beansOfType.entrySet()) {
                    DataSource source = entry.getValue();
                    if (
                            ClassUtils.isPresent("org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource") &&
                                    ClassUtils.isAssignableFrom(source, ClassUtils.forName("org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource"))
                    ) {
                        continue;
                    }
                    log.debug("建表数据源 :{}", entry.getKey());
                    return source;
                }
            }
            return null;
        }

        MultiValueMap<String, Object> annotationAttributes = AnnotatedElementUtils.getAllAnnotationAttributes(aClass, DS.class.getTypeName());
        Object value = annotationAttributes.getFirst("value");
        log.debug("建表数据源 :{}", value);
        if (null == value) {
            return null;
        }
        return beansOfType.get(value.toString());
    }
}
