package com.chua.starter.datasource.support;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 环境变量
 *
 * @author CH
 */
public class EnvironmentSupport {
    /**
     * 环境变量
     */
    public Environment environment;

    private final PathMatcher pathMatcher = new AntPathMatcher();

    public EnvironmentSupport(Environment environment) {
        this.environment = environment;
    }

    /**
     * 根据名字获取值
     *
     * @param name 名称
     * @return 值
     */
    public Map<String, Object> getProperties(String name) {
        if (null == name || null == environment) {
            return Collections.emptyMap();
        }
        if (name.contains("*")) {
            return getValueProperties(name);
        }
        Map<String, Object> result = new HashMap<>();
        result.put(name, environment.getProperty(name));
        return result;
    }

    /**
     * 获取满足条件的配置项
     *
     * @param name 名称
     * @return 配置项
     */
    private Map<String, Object> getValueProperties(String name) {
        if (!(environment instanceof ConfigurableEnvironment)) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>();
        ConfigurableEnvironment configurableEnvironment = (ConfigurableEnvironment) environment;

        MutablePropertySources propertySources = configurableEnvironment.getPropertySources();
        propertySources.stream().iterator().forEachRemaining(propertySource -> {
            Object source = propertySource.getSource();
            if (source instanceof Map) {
                result.putAll(analysisPropertySource((Map) source, name));
            }
        });
        return result;
    }


    /**
     * 獲取配置
     *
     * @param source 配置
     * @param name   名稱
     * @return 配置
     */
    private Map<String, Object> analysisPropertySource(Map source, String name) {
        Map<String, Object> result = new HashMap<>();
        source.forEach((key, value) -> {
            if (pathMatcher.match(name, key.toString())) {
                result.put(key.toString(), environment.getProperty(key.toString()));
            }
        });
        return result;
    }
}
