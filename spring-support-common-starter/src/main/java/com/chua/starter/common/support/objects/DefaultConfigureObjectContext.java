package com.chua.starter.common.support.objects;

import org.springframework.context.ApplicationContext;

/**
 * 默认对象上下文配置。
 */
public class DefaultConfigureObjectContext implements ConfigureObjectContext {

    private final ObjectContextSetting setting;

    public DefaultConfigureObjectContext(ObjectContextSetting setting) {
        this.setting = setting == null ? ObjectContextSetting.builder().build() : setting;
    }

    @Override
    public <T> T getBean(String name, Class<T> requiredType) {
        return null;
    }

    @Override
    public <T> T getBeanOfType(Class<T> requiredType) {
        return null;
    }

    @Override
    public <T> java.util.Map<String, T> getBeansOfType(Class<T> requiredType) {
        return java.util.Collections.emptyMap();
    }
}
