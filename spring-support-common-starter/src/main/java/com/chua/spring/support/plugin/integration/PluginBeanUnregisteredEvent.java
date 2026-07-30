package com.chua.spring.support.plugin.integration;

import org.springframework.context.ApplicationEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * 插件 Bean 取消注册事件
 *
 * @author CH
 */
public class PluginBeanUnregisteredEvent extends ApplicationEvent {

    /**
     * Bean 名称
     */
    private final String beanName;

    /**
     * Bean 实例
     */
    private final Object beanInstance;

    /**
     * Bean 名称集合
     */
    private final Set<String> beanNames = new HashSet<>();

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
    public PluginBeanUnregisteredEvent(Object source, String beanName, Object beanInstance) {
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
     * 获取 Bean 实例
     *
     * @return Bean 实例
     */
    public Object getBeanInstance() {
        return beanInstance;
    }

    /**
     * 获取 Bean 名称集合
     *
     * @return Bean 名称集合
     */
    public Set<String> getBeanNames() {
        return beanNames;
    }

    /**
     * 添加 Bean 名称到集合
     *
     * @param name Bean 名称
     */
    public void addBeanName(String name) {
        beanNames.add(name);
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
