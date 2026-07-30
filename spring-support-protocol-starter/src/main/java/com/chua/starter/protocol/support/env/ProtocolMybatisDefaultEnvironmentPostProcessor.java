package com.chua.starter.protocol.support.env;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 在未显式配置 {@code mybatis-plus.mapper-locations} 时，为本模块提供默认 Mapper XML 扫描路径，
 * 避免引用方重复书写 classpath 通配符。
 *
 * @author CH
 */
public class ProtocolMybatisDefaultEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String MAPPER_LOCATIONS_KEY = "mybatis-plus.mapper-locations";
    private static final String DEFAULT_MAPPER_LOCATIONS = "classpath*:/mapper/**/*.xml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String existing = environment.getProperty(MAPPER_LOCATIONS_KEY);
        if (existing != null && !existing.isBlank()) {
            return;
        }
        Map<String, Object> defaults = new HashMap<>();
        defaults.put(MAPPER_LOCATIONS_KEY, DEFAULT_MAPPER_LOCATIONS);
        environment.getPropertySources()
                .addLast(new MapPropertySource("protocolStarterMybatisPlusDefaults", defaults));
    }
}
