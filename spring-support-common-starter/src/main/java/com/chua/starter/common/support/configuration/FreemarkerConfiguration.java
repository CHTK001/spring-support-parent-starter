package com.chua.starter.common.support.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

/**
 * freemarker
 *
 * @author CH
 */
public class FreemarkerConfiguration implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.setProperty("spring.freemarker.templateLoaderPath", "classpath:/templates/");
        properties.setProperty("spring.freemarker.suffix", ".ftl");
        properties.setProperty("spring.freemarker.charset", "UTF-8");
        properties.setProperty("spring.freemarker.request-context-attribute", "request");
        properties.setProperty("spring.freemarker.number_format", "0.##########");
        properties.setProperty("spring.freemarker.cache", "false");
        properties.setProperty("spring.mvc.pathmatch.matching-strategy", "ant_path_matcher");
        properties.setProperty("spring.resources.static-locations", "classpath:/static/,classpath:/webjar/");
        properties.setProperty("spring.mvc.static-path-pattern", "/static/**,/webjar/**");
        properties.setProperty("knife4j.production", "true");
        properties.setProperty("knife4j.enable", "true");
        properties.setProperty("knife4j.basic.enable", "true");
        properties.setProperty("knife4j.basic.username", "root");
        properties.setProperty("knife4j.basic.password", "RoOt");
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("core", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }


}
