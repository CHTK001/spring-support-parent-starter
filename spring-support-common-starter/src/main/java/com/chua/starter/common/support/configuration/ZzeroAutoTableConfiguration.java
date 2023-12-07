//package com.chua.starter.common.support.configuration;
//
//import com.chua.common.support.constant.Action;
//import com.chua.common.support.database.AutoMetadata;
//import com.chua.common.support.database.executor.MetadataExecutor;
//import com.chua.common.support.log.Log;
//import com.chua.common.support.utils.ClassUtils;
//import com.chua.common.support.utils.StringUtils;
//import com.chua.hibernate.support.database.resolver.HibernateMetadataResolver;
//import com.chua.starter.common.support.annotations.DS;
//import com.chua.starter.common.support.annotations.EnableAutoTable;
//import com.chua.starter.common.support.properties.AutoTableProperties;
//import com.google.common.cache.Cache;
//import com.google.common.cache.CacheBuilder;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
//import org.springframework.boot.autoconfigure.AutoConfigureAfter;
//import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
//import org.springframework.boot.context.properties.EnableConfigurationProperties;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.ApplicationContextAware;
//import org.springframework.core.Ordered;
//import org.springframework.core.PriorityOrdered;
//import org.springframework.core.annotation.AnnotatedElementUtils;
//import org.springframework.core.annotation.Order;
//import org.springframework.core.io.Resource;
//import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
//import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
//import org.springframework.core.type.classreading.MetadataReader;
//import org.springframework.core.type.classreading.MetadataReaderFactory;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//
//import javax.sql.DataSource;
//import java.io.IOException;
//import java.sql.Connection;
//import java.util.*;
//import java.util.concurrent.CopyOnWriteArrayList;
//import java.util.concurrent.TimeUnit;
//
///**
// * 自动建表
// *
// * @author CH
// * @since 2022/8/1 17:07
// */
//@Order(Ordered.HIGHEST_PRECEDENCE + 1)
//@AutoConfigureAfter(DataSourceAutoConfiguration.class)
//@EnableConfigurationProperties(AutoTableProperties.class)
//public class ZzeroAutoTableConfiguration implements PriorityOrdered, ApplicationContextAware , SmartInstantiationAwareBeanPostProcessor{
//
//    private static final Log log = Log.getLogger(AutoTableProperties.class);
//    AutoTableProperties autoTableProperties;
//    private ApplicationContext applicationContext;
//
//    private static final List<Object> DEAL = new CopyOnWriteArrayList<>();
//    PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
//    MetadataReaderFactory metaReader = new CachingMetadataReaderFactory();
//    private static final Cache<String, Connection> CONNECTION_CACHE = CacheBuilder.newBuilder().expireAfterWrite(20, TimeUnit.SECONDS).build();
//    private static final Cache<String, DataSource> DATA_SOURCE_CACHE = CacheBuilder.newBuilder().expireAfterWrite(20, TimeUnit.SECONDS).build();
//
//    @Override
//    public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
//        return SmartInstantiationAwareBeanPostProcessor.super.postProcessAfterInstantiation(bean, beanName);
//    }
//
//    private void refresh() {
//        if(!autoTableProperties.isOpen()) {
//            return;
//        }
//        Resource[] resources = new Resource[0];
//        try {
//            resources = resourcePatternResolver.getResources("classpath*:com/**/*.class");
//        } catch (IOException ignored) {
//        }
//        for (Resource resource : resources) {
//            try {
//                MetadataReader reader = metaReader.getMetadataReader(resource);
//                String className = reader.getClassMetadata().getClassName();
//                Class<?> clazz = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
//                refresh(clazz, "");
//            } catch (Throwable ignored) {
//            }
//        }
//
//    }
//
//
//    private void refresh(Class<?> aClass, String beanName) {
//        if(null == aClass) {
//            return;
//        }
//
//        EnableAutoTable enableAutoTable = aClass.getDeclaredAnnotation(EnableAutoTable.class);
//        if(null == enableAutoTable) {
//            return;
//        }
//        String[] scann = getScan(Collections.singletonList(aClass));
//        log.info(">>>>>>>>>> 开始自动建表: {}", scann);
//        for (String s : scann) {
//            refresh(s);
//        }
//
//        refresh(Arrays.asList(enableAutoTable.packageType()));
//
//        log.info(">>>>>>>> 自动建表完成");
//
//    }
//
//    private String[] getScan(Collection<Class<?>> values) {
//        List<String> rs = new LinkedList<>();
//        String[] scan = autoTableProperties.getScan();
//        if (null != scan) {
//            rs.addAll(Arrays.asList(scan));
//        }
//        for (Class<?> value : values) {
//            EnableAutoTable enableAutoTable = value.getDeclaredAnnotation(EnableAutoTable.class);
//            if(null == enableAutoTable) {
//                continue;
//            }
//            rs.addAll(Arrays.asList(enableAutoTable.value()));
//        }
//
//        return rs.toArray(new String[0]);
//    }
//
//    private void refresh(String s) {
//        if(DEAL.contains(s)) {
//            return;
//        }
//        DEAL.add(s);
//        Resource[] resources;
//        try {
//            resources = resourcePatternResolver.getResources(s.replace(".", "/") + "/**/*.class");
//        } catch (IOException e) {
//            return;
//        }
//
//        ClassLoader loader = ClassLoader.getSystemClassLoader();
//        List<Class<?>> classList = new ArrayList<>();
//        for (Resource resource : resources) {
//            MetadataReader reader;
//            try {
//                reader = metaReader.getMetadataReader(resource);
//            } catch (IOException ignored) {
//                continue;
//            }
//            String className = reader.getClassMetadata().getClassName();
//            Class<?> clazz = ClassUtils.forName(className, loader);
//            classList.add(clazz);
//        }
//
//        refresh(classList);
//    }
//
//    private void refresh(Collection<Class<?>> classList) {
//        if(classList.isEmpty()) {
//            return;
//        }
//        AutoMetadata autoMetadata = AutoMetadata.builder()
//                .metadataResolver(new HibernateMetadataResolver())
//                .prefix(autoTableProperties.getPrefix())
//                .suffix(autoTableProperties.getSuffix()).build();
//
//
//        for (Class<?> aClass : classList) {
//            if(DEAL.contains(aClass)) {
//                continue;
//            }
//            DEAL.add(aClass);
//            DataSource dataSource = getDataSource(aClass);
//            if (null == dataSource) {
////                log.warn("数据源不存在");
//                continue;
//            }
//            MetadataExecutor metadataExecutor = autoMetadata.doExecute(aClass);
//            try {
//                metadataExecutor.execute(dataSource, Action.valueOf(autoTableProperties.getAuto()));
//            } catch (IllegalArgumentException e) {
//                log.error(e.getMessage());
//            }
//        }
//    }
//
//    private DataSource getDataSource(Class<?> aClass) {
//
//        if(StringUtils.isNotEmpty(autoTableProperties.getGlobal())) {
//            return applicationContext.getBean(autoTableProperties.getGlobal(), DataSource.class);
//        }
//
//
//        if (!AnnotatedElementUtils.hasAnnotation(aClass, DS.class)) {
//            try {
//                return applicationContext.getBean("master", DataSource.class);
//            } catch (BeansException ignored) {
//            }
//
//            return null;
//        }
//
//        MultiValueMap annotationAttributes = Optional.ofNullable(AnnotatedElementUtils.getAllAnnotationAttributes(aClass, DS.class.getTypeName())).orElse(new LinkedMultiValueMap<>());
//        Object value = annotationAttributes.getFirst("value");
//        log.debug("建表数据源 :{}", value);
//        if (null == value) {
//            return null;
//        }
//        return applicationContext.getBean(value.toString(), DataSource.class);
//    }
//
//
//    @Override
//    public int getOrder() {
//        return Ordered.HIGHEST_PRECEDENCE + 1;
//    }
//
//    @Override
//    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
//        this.applicationContext = applicationContext;
//        autoTableProperties = SpringBeanUtils.bindOrCreate(applicationContext, AutoTableProperties.PRE, AutoTableProperties.class);
//        refresh();
//    }
//}
