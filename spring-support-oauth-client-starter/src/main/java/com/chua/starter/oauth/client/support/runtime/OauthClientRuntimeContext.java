package com.chua.starter.oauth.client.support.runtime;

import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 * o认证 客户端 运行时上下文。
 *
 * @author CH
 * @since 4.0.0
 */
public final class OauthClientRuntimeContext {
    private static volatile ApplicationContext applicationContext; // application上下文
    private static volatile Environment environment; // 环境
    private static volatile ConfigurableBeanFactory beanFactory; // Bean工厂
    private static volatile AuthClientProperties authClientProperties; // 认证客户端属性

    /**
     * 创建 OAuth客户端runtime上下文 实例
    */
    private OauthClientRuntimeContext() {
    }

    /**
     * 设置application上下文
     *
     * @param context 上下文
     */
    public static void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
        if (context != null) {
            environment = context.getEnvironment();
        }
    }

    /**
     * 获取application上下文
     *
     * @return 获取application上下文的结果
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 设置环境
     *
     * @param env env
     */
    public static void setEnvironment(Environment env) {
        environment = env;
    }

    /**
     * 获取环境
     *
     * @return 获取环境的结果
     */
    public static Environment getEnvironment() {
        return environment;
    }

    /**
     * 设置Bean工厂
     *
     * @param factory 工厂
     */
    public static void setBeanFactory(ConfigurableBeanFactory factory) {
        beanFactory = factory;
    }

    /**
     * 获取Bean工厂
     *
     * @return 获取Bean工厂的结果
     */
    public static ConfigurableBeanFactory getBeanFactory() {
        if (beanFactory != null) {
            return beanFactory;
        }
        if (applicationContext != null && applicationContext.getAutowireCapableBeanFactory() instanceof ConfigurableBeanFactory factory) {
            beanFactory = factory;
            return factory;
        }
        return null;
    }

    /**
     * 设置认证客户端属性
     *
     * @param properties 属性
     */
    public static void setAuthClientProperties(AuthClientProperties properties) {
        authClientProperties = properties;
    }

    /**
     * 获取认证客户端属性
     *
     * @return 获取认证客户端属性的结果
     */
    public static AuthClientProperties getAuthClientProperties() {
        if (authClientProperties != null) {
            return authClientProperties;
        }
        if (environment == null) {
            /**
             * 认证客户端属性。
             * @return 认证客户端属性的结果
             */
            return new AuthClientProperties();
        }
        authClientProperties = Binder.get(environment).bindOrCreate(AuthClientProperties.PRE, AuthClientProperties.class);
        return authClientProperties;
    }

    /**
     * 解析Placeholders
     *
     * @param value 值
     * @return resolvePlaceholders的结果
     */
    public static String resolvePlaceholders(String value) {
        if (value == null) {
            return null;
        }
        return environment == null ? value : environment.resolvePlaceholders(value);
    }

    /**
     * 获取application名称
     *
     * @return 获取application名称的结果
     */
    public static String getApplicationName() {
        String value = resolvePlaceholders("${spring.application.name:}");
        return value == null ? "" : value;
    }

    /**
     * 获取上下文路径
     *
     * @return 获取上下文路径的结果
     */
    public static String getContextPath() {
        String value = resolvePlaceholders("${server.servlet.context-path:}");
        return value == null ? "" : value;
    }
}
