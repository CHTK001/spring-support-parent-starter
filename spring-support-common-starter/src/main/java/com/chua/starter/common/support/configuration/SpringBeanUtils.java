package com.chua.starter.common.support.configuration;

import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Spring Bean工具类
 * <p>
 * 提供获取Spring容器中Bean的静态方法。
 * </p>
 *
 * @author CH
 * @since 2024/12/07
 */
public class SpringBeanUtils {

    private static ApplicationContext applicationContext;
    private static Environment environment;
    private static RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * 设置ApplicationContext
     *
     * @param context ApplicationContext
     */
    public static void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
        if (context != null) {
            environment = context.getEnvironment();
        }
    }

    /**
     * 获取ApplicationContext
     *
     * @return ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext未初始化，请确保Spring容器已启动");
        }
        return applicationContext;
    }

    /**
     * 获取Environment
     *
     * @return Environment
     */
    public static Environment getEnvironment() {
        if (environment == null) {
            ApplicationContext context = getApplicationContext();
            environment = context.getEnvironment();
        }
        return environment;
    }

    /**
     * 设置RequestMappingHandlerMapping
     *
     * @param mapping RequestMappingHandlerMapping
     */
    public static void setRequestMappingHandlerMapping(RequestMappingHandlerMapping mapping) {
        requestMappingHandlerMapping = mapping;
    }

    /**
     * 获取RequestMappingHandlerMapping
     *
     * @return RequestMappingHandlerMapping
     */
    public static RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        if (requestMappingHandlerMapping == null) {
            ApplicationContext context = getApplicationContext();
            requestMappingHandlerMapping = context.getBean(RequestMappingHandlerMapping.class);
        }
        return requestMappingHandlerMapping;
    }

    /**
     * 获取Bean
     *
     * @param name Bean名称
     * @return Bean实例
     */
    public static Object getBean(String name) {
        return getApplicationContext().getBean(name);
    }

    /**
     * 获取Bean
     *
     * @param type Bean类型
     * @param <T>  Bean类型
     * @return Bean实例
     */
    public static <T> T getBean(Class<T> type) {
        return getApplicationContext().getBean(type);
    }
}

