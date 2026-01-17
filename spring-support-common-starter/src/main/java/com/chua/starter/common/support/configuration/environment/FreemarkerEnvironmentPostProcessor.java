package com.chua.starter.common.support.configuration.environment;

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
public class FreemarkerEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.setProperty("spring.freemarker.templateLoaderPath", "classpath:/templates/");
        properties.setProperty("spring.freemarker.suffix", ".ftl");
        properties.setProperty("spring.freemarker.charset", "UTF-8");
        properties.setProperty("spring.freemarker.request-context-attribute", "request");
        properties.setProperty("spring.freemarker.number_format", "0.##########");
        if("prod".equals(environment.getProperty("spring.profiles.active"))) {
            properties.setProperty("spring.freemarker.cache", "true");
        } else {
            properties.setProperty("spring.freemarker.cache", "false");
        }
        properties.setProperty("spring.mvc.pathmatch.matching-strategy", "ant_path_matcher");
        properties.setProperty("spring.resources.static-locations", "classpath:/static/,classpath:/webjar/");
        properties.setProperty("spring.mvc.static-path-pattern", "/static/**,/webjar/**");
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("knife4j", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }


}

