package com.chua.starter.common.support.objects;

import org.springframework.context.ApplicationContext;
import java.util.Map;

/**
 * 对象上下文配置接口。
 */
public interface ConfigureObjectContext {

    static ConfigureObjectContext from(ApplicationContext applicationContext) {
        return new SpringConfigureObjectContext(applicationContext);
    }

    <T> T getBean(String name, Class<T> requiredType);

    <T> T getBeanOfType(Class<T> requiredType);

    <T> Map<String, T> getBeansOfType(Class<T> requiredType);

    class SpringConfigureObjectContext implements ConfigureObjectContext {

        private final ApplicationContext applicationContext;

        public SpringConfigureObjectContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        @Override
        public <T> T getBean(String name, Class<T> requiredType) {
            return applicationContext.getBean(name, requiredType);
        }

        @Override
        public <T> T getBeanOfType(Class<T> requiredType) {
            return applicationContext.getBean(requiredType);
        }

        @Override
        public <T> Map<String, T> getBeansOfType(Class<T> requiredType) {
            return applicationContext.getBeansOfType(requiredType);
        }
    }
}
