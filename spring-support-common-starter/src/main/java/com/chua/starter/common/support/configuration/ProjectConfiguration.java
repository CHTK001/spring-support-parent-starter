package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.project.Project;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * 项目配置
 * @author CH
 * @since 2024/9/6
 */
public class ProjectConfiguration implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, CommandLineRunner, ApplicationContextAware {
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
    }

    @Override
    public void run(String... args) throws Exception {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

    }

    @Override
    public void setEnvironment(Environment environment) {
        Project.getInstance().setEnvironment(environment);

    }
}

