package com.chua.starter.common.support.configuration.environment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

/**
 * 动态环境
 * @author CH
 * @since 2024/12/4
 */
public class ProfileEnvironmentPostProcessor implements EnvironmentPostProcessor {
    private static final String ATTACHED_PROPERTY_SOURCE_NAME = "configurationProperties";

    @Override
    @SuppressWarnings("ALL")
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String active = environment.getProperty("spring.profiles.active");
        PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resource = pathMatchingResourcePatternResolver.getResources("classpath:/" + active + "/**");
            MutablePropertySources propertySources = environment.getPropertySources();
            try {
                PropertySource<?> propertySource = propertySources.get(ATTACHED_PROPERTY_SOURCE_NAME);
                Iterable<PropertySource<?>> rs = (Iterable<PropertySource<?>>)propertySource.getSource();
                Field field = ReflectionUtils.findField(rs.getClass(), "sources");
                ReflectionUtils.makeAccessible(field);
                propertySources = (MutablePropertySources) field.get(rs);
            } catch (IllegalAccessException ignored) {
            }
            for (Resource resource1 : resource) {
                registerPropertySource(propertySources, resource1);
            }
        } catch (IOException ignored) {
        }

    }

    private void registerPropertySource(MutablePropertySources propertySources, Resource resource1) throws IOException {
        String filename = resource1.getFilename();
        if(filename.endsWith("yaml") || filename.endsWith("yml")) {
            YamlPropertySourceLoader yamlPropertySourceLoader = new YamlPropertySourceLoader();
            List<PropertySource<?>> load = yamlPropertySourceLoader.load(resource1.getFilename(), resource1);
            load.forEach(propertySources::addFirst);
            return;
        }

        if(filename.endsWith("properties")) {
            PropertiesPropertySourceLoader propertiesPropertySourceLoader = new PropertiesPropertySourceLoader();
            List<PropertySource<?>> load = propertiesPropertySourceLoader.load(resource1.getFilename(), resource1);
            load.forEach(propertySources::addFirst);
        }
    }
}
