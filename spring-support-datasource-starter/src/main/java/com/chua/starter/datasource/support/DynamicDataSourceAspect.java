package com.chua.starter.datasource.support;

import com.chua.starter.datasource.annotation.DS;
import com.chua.starter.datasource.datasource.MultiDataSource;
import com.chua.starter.datasource.properties.MultiDataSourceSettingProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 自定义注解 + AOP的方式实现数据源动态切换
 *
 * @author CH
 */
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamicDataSourceAspect {
    /**
     * 上下文
     */
    private ApplicationContext applicationContext;

    /**
     * 设置应用上下文
     *
     * @param applicationContext 应用上下文
     */
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 切入点
     *
     * @param methodInvocation          切入点
     * @param multiDataSourceSettingProperties
     */
    public void beforeSwitchDS(MethodInvocation methodInvocation, MultiDataSourceSettingProperties multiDataSourceSettingProperties) {
        //默认数据源
        String dataSource = getDataSourceType(methodInvocation, multiDataSourceSettingProperties);
        switchTo(dataSource, multiDataSourceSettingProperties);
    }

    public void switchTo(String dataSource, MultiDataSourceSettingProperties multiDataSourceSettingProperties) {
        dataSource = createDataSource(dataSource, multiDataSourceSettingProperties);
        //分析处理多数据源
        doAnalysisDataSource(dataSource);
        // 切换数据源
        DataSourceContextSupport.setDbType(dataSource);
        // 如果处于 @DSTransactional 上下文中，额外推入栈用于嵌套恢复
        if (MultiDSTransactionContext.isInTransaction()) {
            DataSourceContextSupport.pushDbType(dataSource);
        }
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
            if (AnnotatedElementUtils.hasAnnotation(method, DS.class)) {
                DS ds = MergedAnnotations.from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none()).get(DS.class).synthesize();
                String value = ds.value();
                if (!StringUtils.hasLength(value)) {
                    return dsName;
                }
                // SpEL 表达式支持：以 # 开头的值进行 SpEL 解析
                if (value.startsWith("#")) {
                    return resolveSpelExpression(value, methodInvocation);
                }
                return value;
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
        Class<?> className = methodInvocation.getThis().getClass();
        if (!AnnotatedElementUtils.hasAnnotation(className, DS.class)) {
            return dsName;
        }
        DS ds = MergedAnnotations.from(className, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none()).get(DS.class).synthesize();
        String value = ds.value();
        if (!StringUtils.hasLength(value)) {
            return dsName;
        }
        // SpEL 表达式支持
        if (value.startsWith("#")) {
            return resolveSpelExpression(value, methodInvocation);
        }
        return value;
    }

    /**
     * 解析 SpEL 表达式
     * <p>
     * 支持以下上下文变量：
     * <ul>
     *   <li>方法参数：按参数名注入（如 #tenantId）</li>
     *   <li>#header：HttpServletRequest 请求头（如 #header.xTenantId）</li>
     *   <li>#session：HttpSession 属性（如 #session.userType）</li>
     * </ul>
     * </p>
     *
     * @param expression SpEL 表达式
     * @param invocation 方法调用
     * @return 解析后的字符串值
     */
    private String resolveSpelExpression(String expression, MethodInvocation invocation) {
        try {
            ExpressionParser parser = new SpelExpressionParser();
            StandardEvaluationContext context = new StandardEvaluationContext();

            // 注入方法参数
            Object[] args = invocation.getArguments();
            Method method = invocation.getMethod();
            Parameter[] params = method.getParameters();
            for (int i = 0; i < params.length && i < args.length; i++) {
                context.setVariable(params[i].getName(), args[i]);
            }

            // 注入 header/session 上下文
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                context.setVariable("header", new HeaderWrapper(request));
                context.setVariable("session", new SessionWrapper(request));
                context.setVariable("request", request);
            }

            Object value = parser.parseExpression(expression).getValue(context);
            return value != null ? value.toString() : DataSourceContextSupport.DEFAULT_DATASOURCE;
        } catch (Exception e) {
            log.warn("SpEL 表达式解析失败: expression={}, 使用默认数据源", expression, e);
            return DataSourceContextSupport.DEFAULT_DATASOURCE;
        }
    }

    /**
     * HttpServletRequest 请求头包装器（使用 Map，支持 SpEL #header['key'] 和 #header.key 两种语法）
     */
    public static class HeaderWrapper extends java.util.HashMap<String, String> {
        private final HttpServletRequest request;
        public HeaderWrapper(HttpServletRequest request) {
            this.request = request;
            var names = request.getHeaderNames();
            while (names != null && names.hasMoreElements()) {
                String name = names.nextElement();
                put(name, request.getHeader(name));
            }
        }
    }

    /**
     * HttpSession 属性包装器（使用 Map，支持 SpEL #session['key'] 和 #session.key 两种语法）
     */
    public static class SessionWrapper extends java.util.HashMap<String, Object> {
        public SessionWrapper(HttpServletRequest request) {
            var session = request.getSession(false);
            if (session != null) {
                var names = session.getAttributeNames();
                while (names != null && names.hasMoreElements()) {
                    String name = names.nextElement();
                    put(name, session.getAttribute(name));
                }
            }
        }
    }

    /**
     * 重置
     */
    public synchronized void afterSwitchDS() {
        // 如果处于 @DSTransactional 上下文中，从栈恢复上一级数据源
        if (MultiDSTransactionContext.isInTransaction()) {
            String previous = DataSourceContextSupport.popDbType();
            if (previous != null) {
                DataSourceContextSupport.setDbType(previous);
            } else {
                DataSourceContextSupport.clearDbType();
            }
        } else {
            DataSourceContextSupport.clearDbType();
        }
    }

}
