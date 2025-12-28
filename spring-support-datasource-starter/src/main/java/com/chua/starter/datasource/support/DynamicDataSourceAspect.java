package com.chua.starter.datasource.support;

import com.chua.common.support.objects.aop.JoinPoint;
import com.chua.starter.datasource.annotation.DS;
import com.chua.starter.datasource.datasource.MultiDataSource;
import com.chua.starter.datasource.properties.MultiDataSourceSettingProperties;
import lombok.Setter;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 自定义注解 + AOP的方式实现数据源动态切换
 *
 * @author CH
 */
public class DynamicDataSourceAspect {
    /**
     * 上下文
     */
    @Setter
    private ApplicationContext applicationContext;

    /**
     * 切入点
     *
     * @param methodInvocation          切入点
     * @param multiDataSourceSettingProperties
     */
    public void beforeSwitchDS(MethodInvocation methodInvocation, MultiDataSourceSettingProperties multiDataSourceSettingProperties) {
        //默认数据源
        String dataSource = getDataSourceType(methodInvocation, multiDataSourceSettingProperties);
        //分析处理多数据源
        doAnalysisDataSource(dataSource);
        // 切换数据源
        DataSourceContextSupport.setDbType(dataSource);
    }

    /**
     * 分析处理多数据源
     *
     * @param dataSource 数据源
     */
    private void doAnalysisDataSource(String dataSource) {
        String[] split = dataSource.split(",");
        if (split.length == 1) {
            return;
        }

        if (DataSourceContextSupport.hasDbType(dataSource)) {
            return;
        }

        boolean hasAll = true;
        List<DataSource> dataSources = new LinkedList<>();
        for (String s : split) {
            dataSources.add(DataSourceContextSupport.getDatasource(s));
            if (!DataSourceContextSupport.hasDbType(s)) {
                hasAll = false;
            }
        }

        if (!hasAll) {
            return;
        }

        MultiDataSource multiDataSource = new MultiDataSource(dataSources, applicationContext);
        DataSourceContextSupport.addDatasource(dataSource, multiDataSource);

    }

    /**
     * 获取切换的数据源
     *
     * @param methodInvocation          切入点
     * @param multiDataSourceSettingProperties
     * @return 数据源
     */
    private String getDataSourceType(MethodInvocation methodInvocation, MultiDataSourceSettingProperties multiDataSourceSettingProperties) {
        //類是否包含註解
        String dsName = DataSourceContextSupport.DEFAULT_DATASOURCE;
        //类上的注解
        dsName = analysisClass(dsName, methodInvocation);
        //方法上的注解
        dsName = analysisMethod(dsName, methodInvocation);

        return createDataSource(dsName, multiDataSourceSettingProperties);
    }

    /**
     * 数据源
     *
     * @param dsName                    数据源名称
     * @param multiDataSourceSettingProperties 数据源配置
     * @return 数据源名称
     */
    private String createDataSource(String dsName, MultiDataSourceSettingProperties multiDataSourceSettingProperties) {
        if (DataSourceContextSupport.hasDbType(dsName)) {
            return dsName;
        }

        if (applicationContext.containsBean(dsName)) {
            DataSourceContextSupport.addDatasource(dsName, applicationContext.getBean(dsName, DataSource.class));
            return dsName;
        }

        Map<String, DataSource> beansOfType = applicationContext.getBeansOfType(DataSource.class);
        for (Map.Entry<String, DataSource> entry : beansOfType.entrySet()) {
            if (entry.getValue() instanceof AbstractRoutingDataSource) {
                continue;
            }
            DataSourceContextSupport.addDatasource(entry.getKey(), entry.getValue());
        }

        if (DataSourceContextSupport.hasDbType(dsName)) {
            return dsName;
        }

        if (dsName.contains(",")) {
            return dsName;
        }

        if (multiDataSourceSettingProperties.isForceAnnotation()) {
            return dsName;
        }
        return DataSourceContextSupport.DEFAULT_DATASOURCE;
    }

    /**
     * 数据源
     *
     * @param dsName           名称
     * @param methodInvocation 类
     * @return 数据源
     */
    private String analysisMethod(String dsName, MethodInvocation methodInvocation) {
        Method method = methodInvocation.getMethod();
        try {
            // 判断是否存在@DS注解
            if (AnnotatedElementUtils.hasAnnotation(method, DS.class)) {
                DS ds = MergedAnnotations.from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none()).get(DS.class).synthesize();
                // 取出注解中的数据源名
                return StringUtils.hasLength(ds.value()) ? ds.value() : dsName;
            }
        } catch (Exception ignored) {
        }
        return dsName;
    }

    /**
     * 数据源
     *
     * @param dsName           名称
     * @param methodInvocation 类
     * @return 数据源
     */
    private String analysisClass(String dsName, MethodInvocation methodInvocation) {
        //获得当前访问的class
        Class<?> className = methodInvocation.getThis().getClass();
        //数据源注解
        if (!AnnotatedElementUtils.hasAnnotation(className, DS.class)) {
            return dsName;
        }
        DS ds = MergedAnnotations.from(className, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none()).get(DS.class).synthesize();
        return StringUtils.hasLength(ds.value()) ? ds.value() : dsName;
    }

    /**
     * 重置
     *
     * @param point 切入点
     */
    public synchronized void afterSwitchDS(JoinPoint point) {
        DataSourceContextSupport.clearDbType();
        DataSourceContextSupport.setDbType(DataSourceContextSupport.DEFAULT_DATASOURCE);
    }

}
