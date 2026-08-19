package com.chua.starter.oauth.client.support.runtime;

import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 * OAuth Client 运行时上下文。
 *
 */
public final class OauthClientRuntimeContext {
    private static volatile ApplicationContext applicationContext;
    private static volatile Environment environment;
    private static volatile ConfigurableBeanFactory beanFactory;
    private static volatile AuthClientProperties authClientProperties;

    /** 创建 OauthClientRuntimeContext 实例 */
    private OauthClientRuntimeContext() {
    }

    /** 设置ApplicationContext */
    public static void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
        if (context != null) {
            environment = context.getEnvironment();
        }
    }

    /** 获取ApplicationContext */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /** 设置Environment */
    public static void setEnvironment(Environment env) {
        environment = env;
    }

    /** 获取Environment */
    public static Environment getEnvironment() {
        return environment;
    }

    /** 设置BeanFactory */
    public static void setBeanFactory(ConfigurableBeanFactory factory) {
        beanFactory = factory;
    }

    /** 获取BeanFactory */
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

    /** 设置AuthClientProperties */
    public static void setAuthClientProperties(AuthClientProperties properties) {
        authClientProperties = properties;
    }

    /** 获取AuthClientProperties */
    public static AuthClientProperties getAuthClientProperties() {
        if (authClientProperties != null) {
            return authClientProperties;
        }
        if (environment == null) {
            return new AuthClientProperties();
        }
        authClientProperties = Binder.get(environment).bindOrCreate(AuthClientProperties.PRE, AuthClientProperties.class);
        return authClientProperties;
    }

    /** 解析Placeholders */
    public static String resolvePlaceholders(String value) {
        if (value == null) {
            return null;
        }
        return environment == null ? value : environment.resolvePlaceholders(value);
    }

    /** 获取ApplicationName */
    public static String getApplicationName() {
        String value = resolvePlaceholders("${spring.application.name:}");
        return value == null ? "" : value;
    }

    /** 获取ContextPath */
    public static String getContextPath() {
        String value = resolvePlaceholders("${server.servlet.context-path:}");
        return value == null ? "" : value;
    }
}
