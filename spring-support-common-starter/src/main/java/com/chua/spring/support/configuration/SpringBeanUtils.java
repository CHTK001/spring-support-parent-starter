package com.chua.spring.support.configuration;

import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Spring Bean 工具类
 * <p>
 * 提供获取 Spring Bean 的静态方法
 * </p>
 *
 * @author CH
 */
public class SpringBeanUtils {

    /** Application上下文 */
    private static ApplicationContext applicationContext;

    /**
     * 设置 ApplicationContext
     *
     * @param ctx ApplicationContext 实例
     */
    public static void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
    }

    /**
     * 根据名称获取 Bean
     *
     * @param name Bean 名称
     * @param <T>  Bean 类型
     * @return Bean 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        if (applicationContext == null) {
            return null;
        }
        return (T) applicationContext.getBean(name);
    }

    /**
     * 根据类型获取 Bean
     *
     * @param clz Bean 类型
     * @param <T> Bean 类型
     * @return Bean 实例
     */
    public static <T> T getBean(Class<T> clz) {
        if (applicationContext == null) {
            return null;
        }
        return applicationContext.getBean(clz);
    }

    /**
     * 获取 ApplicationContext（允许返回 null）
     *
     * @return ApplicationContext 实例
     */
    public static ApplicationContext getApplicationContextOrNull() {
        return applicationContext;
    }

    /**
     * 获取 ApplicationContext
     *
     * @return ApplicationContext 实例
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 获取 Environment
     *
     * @return Environment 实例
     */
    public static Environment getEnvironment() {
        return getBean(Environment.class);
    }

    /**
     * 获取 RequestMappingHandlerMapping
     *
     * @return HandlerMapping 实例
     */
    public static RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        HandlerMapping handlerMapping = getBean("requestMappingHandlerMapping", HandlerMapping.class);
        if (handlerMapping instanceof RequestMappingHandlerMapping rmhm) {
            return rmhm;
        }
        return null;
    }

    /**
     * 根据名称和类型获取 Bean
     *
     * @param name Bean 名称
     * @param clz  Bean 类型
     * @param <T>  Bean 类型
     * @return Bean 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name, Class<T> clz) {
        if (applicationContext == null) {
            return null;
        }
        return (T) applicationContext.getBean(name);
    }
}
