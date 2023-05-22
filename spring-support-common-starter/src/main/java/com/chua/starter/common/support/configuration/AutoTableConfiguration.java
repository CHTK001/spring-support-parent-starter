package com.chua.starter.common.support.configuration;

import com.chua.common.support.log.Log;
import com.chua.starter.common.support.properties.AutoTableProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;

/**
 * 自动建表
 *
 * @author CH
 * @since 2022/8/1 17:07
 */
@EnableConfigurationProperties(AutoTableProperties.class)
public class AutoTableConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    private static final Log log = Log.getLogger(AutoTableProperties.class);
    AutoTableProperties autoTableProperties;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (autoTableProperties.isOpen()) {
            refresh(registry);
        }
    }

    private void refresh(BeanDefinitionRegistry registry) {
        log.info(">>>>>>>>>> 开始自动建表");
        String[] scann = autoTableProperties.getScann();
        for (String s : scann) {
            refresh(s);
        }
        log.info(">>>>>>>> 自动建表完成");

    }

    private void refresh(String s) {
        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = new Resource[0];
        try {
            resources = resourcePatternResolver.getResources(s);
        } catch (IOException e) {
            return;
        }

        for (Resource resource : resources) {
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        autoTableProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(AutoTableProperties.PRE, AutoTableProperties.class);
    }
}
