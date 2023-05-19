package com.chua.starter.common.support.application;

import com.chua.starter.common.support.configuration.SpringBeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 * binder
 *
 * @author CH
 */
public class Binder {

    private final ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
    private Environment environment = applicationContext.getEnvironment();
    private final String pre;


    public Binder(String pre) {
        this.pre = pre;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * 绑定配置文件
     * <pre>
     *     Binder.binder("server", ServerProperties.class)
     * </pre>
     *
     * @param environment 环境变量
     * @param pre         配置名称.  e.q.  server.host -> server
     * @param type        绑定类型. e.q.  org.springframework.boot.autoconfigure.web.ServerProperties
     * @param <T>         T
     * @return T
     */
    public static <T> T binder(Environment environment, String pre, Class<T> type) {
        Binder binder = new Binder(pre);
        binder.setEnvironment(environment);
        return binder.binder(type);
    }

    /**
     * 绑定配置文件
     * <pre>
     *     Binder.binder("server", ServerProperties.class)
     * </pre>
     *
     * @param pre  配置名称.  e.q.  server.host -> server
     * @param type 绑定类型. e.q.  org.springframework.boot.autoconfigure.web.ServerProperties
     * @param <T>  T
     * @return T
     */
    public static <T> T binder(String pre, Class<T> type) {
        Binder binder = new Binder(pre);
        return binder.binder(type);
    }


    /**
     * 绑定配置文件
     *
     * @param type 绑定类型. e.q.  org.springframework.boot.autoconfigure.web.ServerProperties
     * @param <T>  T
     * @return T
     */
    public <T> T binder(Class<T> type) {
        return org.springframework.boot.context.properties.bind.Binder.get(environment).bind(pre, type).get();
    }


}
