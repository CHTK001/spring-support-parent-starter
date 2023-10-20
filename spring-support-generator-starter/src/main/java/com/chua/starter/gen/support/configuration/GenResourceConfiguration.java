package com.chua.starter.gen.support.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

/**
 * @author CH
 */
public class GenResourceConfiguration implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.put("spring.autoconfigure.exclude", new String[] {"org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration"});
        environment.getPropertySources().addFirst(new PropertiesPropertySource("gen-resource", properties));
    }
}
