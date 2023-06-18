package com.chua.starter.common.support.configuration;

import com.chua.common.support.constant.Action;
import com.chua.common.support.database.AutoMetadata;
import com.chua.common.support.database.executor.MetadataExecutor;
import com.chua.common.support.log.Log;
import com.chua.common.support.utils.ClassUtils;
import com.chua.hibernate.support.database.resolver.HibernateMetadataResolver;
import com.chua.starter.common.support.annotations.DS;
import com.chua.starter.common.support.annotations.EnableAutoTable;
import com.chua.starter.common.support.properties.AutoTableProperties;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.MultiValueMap;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.*;

/**
 * 自动建表
 *
 * @author CH
 * @since 2022/8/1 17:07
 */
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(AutoTableProperties.class)
public class AutoTableConfiguration implements ApplicationContextAware {

    private static final Log log = Log.getLogger(AutoTableProperties.class);
    AutoTableProperties autoTableProperties;

    private Map<String, DataSource> beansOfType;
    private ApplicationContext applicationContext;
    private Collection<Object> values;

    private void refresh() {
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(EnableAutoTable.class);
        this.values = beansWithAnnotation.values();
        String[] scann = getScan(values);
        if (null == scann) {
            return;
        }
        log.info(">>>>>>>>>> 开始自动建表");
        for (String s : scann) {
            refresh(s);
        }

        for (Object value : values) {
            EnableAutoTable enableAutoTable = value.getClass().getDeclaredAnnotation(EnableAutoTable.class);
            refresh(Arrays.asList(enableAutoTable.packageType()));
        }

        log.info(">>>>>>>> 自动建表完成");

    }

    private String[] getScan(Collection<Object> values) {
        List<String> rs = new LinkedList<>();
        String[] scan = autoTableProperties.getScan();
        if (null != scan) {
            rs.addAll(Arrays.asList(scan));
        }
        for (Object value : values) {
            EnableAutoTable enableAutoTable = value.getClass().getDeclaredAnnotation(EnableAutoTable.class);
            rs.addAll(Arrays.asList(enableAutoTable.value()));
        }

        return rs.toArray(new String[0]);
    }

    private void refresh(String s) {
        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = new Resource[0];
        MetadataReaderFactory metaReader = new CachingMetadataReaderFactory();
        try {
            resources = resourcePatternResolver.getResources(s.replace(".", "/") + "/**/*.class");
        } catch (IOException e) {
            return;
        }

        ClassLoader loader = ClassLoader.getSystemClassLoader();
        List<Class<?>> classList = new ArrayList<>();
        for (Resource resource : resources) {
            MetadataReader reader = null;
            try {
                reader = metaReader.getMetadataReader(resource);
            } catch (IOException ignored) {
                continue;
            }
            String className = reader.getClassMetadata().getClassName();
            Class<?> clazz = ClassUtils.forName(className, loader);
            classList.add(clazz);
        }

        refresh(classList);
    }

    private void refresh(Collection<Class<?>> classList) {
        AutoMetadata autoMetadata = AutoMetadata.builder()
                .metadataResolver(new HibernateMetadataResolver())
                .prefix(autoTableProperties.getPrefix())
                .suffix(autoTableProperties.getSuffix()).build();


        for (Class<?> aClass : classList) {
            DataSource dataSource = getDataSource(aClass);
            if (null == dataSource) {
                log.warn("数据源不存在");
                continue;
            }
            MetadataExecutor metadataExecutor = autoMetadata.doExecute(aClass);
            try {
                metadataExecutor.execute(dataSource, Action.valueOf(autoTableProperties.getAuto()));
            } catch (IllegalArgumentException e) {
                log.error(e.getMessage());
            }
        }
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

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        autoTableProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(AutoTableProperties.PRE, AutoTableProperties.class);
        this.beansOfType = applicationContext.getBeansOfType(DataSource.class);
        if (autoTableProperties.isOpen()) {
            refresh();
        }
    }
}
