package com.chua.spring.support.plugin.integration;

import org.springframework.context.ApplicationEvent;

/**
 * 插件 Bean 注册事件
 * <p>
 * 当插件中的 Bean 注册到 Spring 容器时发布该事件，
 * 可用于监听插件 Bean 的注册行为，进行后续处理（如自动注册 MyBatis Mapper）。
 * </p>
 *
 * @author CH
 */
public class PluginBeanRegisteredEvent extends ApplicationEvent {

    /**
     * Bean 名称
     */
    private final String beanName;

    /**
     * Bean 实例对象
     */
    private final Object beanInstance;

    /**
     * 插件文件路径
     */
    private String pluginFilePath;

    /**
     * 构造事件
     *
     * @param source       事件源
     * @param beanName     Bean 名称
     * @param beanInstance Bean 实例
     */
    public PluginBeanRegisteredEvent(Object source, String beanName, Object beanInstance) {
        super(source);
        this.beanName = beanName;
        this.beanInstance = beanInstance;
    }

    /**
     * 获取 Bean 名称
     *
     * @return Bean 名称
     */
    public String getBeanName() {
        return beanName;
    }

    /**
     * 获取 Bean 实例对象
     *
     * @return Bean 实例
     */
    public Object getBeanInstance() {
        return beanInstance;
    }

    /**
     * 获取插件文件路径
     *
     * @return 插件文件路径
     */
    public String getPluginFilePath() {
        return pluginFilePath;
    }

    /**
     * 设置插件文件路径
     *
     * @param pluginFilePath 插件文件路径
     */
    public void setPluginFilePath(String pluginFilePath) {
        this.pluginFilePath = pluginFilePath;
    }
}
