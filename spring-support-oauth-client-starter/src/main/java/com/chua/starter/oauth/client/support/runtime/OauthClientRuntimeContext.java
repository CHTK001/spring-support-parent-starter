package com.chua.starter.oauth.client.support.runtime;

import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 * OAuth Client 运行时上下文。
 *
 * @author CH
 */
public final class OauthClientRuntimeContext {
    private static volatile ApplicationContext applicationContext;
    private static volatile Environment environment;
    private static volatile ConfigurableBeanFactory beanFactory;
    private static volatile AuthClientProperties authClientProperties;

    private OauthClientRuntimeContext() {
    }

    public static void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
        if (context != null) {
            environment = context.getEnvironment();
        }
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static void setEnvironment(Environment env) {
        environment = env;
    }

    public static Environment getEnvironment() {
        return environment;
    }

    public static void setBeanFactory(ConfigurableBeanFactory factory) {
        beanFactory = factory;
    }

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

    public static void setAuthClientProperties(AuthClientProperties properties) {
        authClientProperties = properties;
    }

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

    public static String resolvePlaceholders(String value) {
        if (value == null) {
            return null;
        }
        return environment == null ? value : environment.resolvePlaceholders(value);
    }

    public static String getApplicationName() {
        String value = resolvePlaceholders("${spring.application.name:}");
        return value == null ? "" : value;
    }

    public static String getContextPath() {
        String value = resolvePlaceholders("${server.servlet.context-path:}");
        return value == null ? "" : value;
    }
}
