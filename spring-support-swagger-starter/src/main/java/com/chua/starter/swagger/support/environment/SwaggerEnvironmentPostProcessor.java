package com.chua.starter.swagger.support.environment;

import com.chua.starter.swagger.support.Knife4jProperties;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Properties;

/**
 * freemarker
 *
 * @author CH
 */
public class SwaggerEnvironmentPostProcessor implements EnvironmentPostProcessor {
    private static final String ATTACHED_PROPERTY_SOURCE_NAME = "configurationProperties";

    @Override
    @SuppressWarnings("ALL")
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.setProperty("springdoc.api-docs.enabled", "true");
        properties.setProperty("springdoc.api-docs.groups.enabled", "true");
        properties.setProperty("springdoc.default-flat-param-object", "true");
        properties.setProperty("knife4j.enable", "${plugin.swagger.enable:true}");
        properties.setProperty("knife4j.basic.enable", "true");
        properties.setProperty("knife4j.basic.username", "${plugin.swagger.username:root}");
        Knife4jProperties knife4jProperties = Binder.get(environment).bindOrCreate("plugin.swagger", Knife4jProperties.class);
        properties.setProperty("knife4j.basic.password", "${plugin.swagger.password:root123}");
        List<Knife4jProperties.Knife4j> j = knife4jProperties.getKnife4j();
        if (!CollectionUtils.isEmpty(j)) {
            for (int i = 0, jSize = j.size(); i < jSize; i++) {
                Knife4jProperties.Knife4j knife4j = j.get(i);
                properties.setProperty("knife4j.documents[" + i + "].group", StringUtils.defaultIfEmpty(knife4j.getGroup(), knife4j.getGroupName()));
                properties.setProperty("knife4j.documents[" + i + "].name", StringUtils.defaultIfEmpty(knife4j.getGroup(), knife4j.getGroupName()));
                properties.setProperty("springdoc.group-configs[" + i + "].group", knife4j.getGroupName());
                properties.setProperty("springdoc.group-configs[" + i + "].title", knife4j.getTitle());
                properties.setProperty("springdoc.group-configs[" + i + "].description", knife4j.getDescription());
                properties.setProperty("springdoc.group-configs[" + i + "].version", knife4j.getVersion());
                properties.setProperty("springdoc.group-configs[" + i + "].display-name", knife4j.getGroupName());
                properties.setProperty("springdoc.group-configs[" + i + "].paths-to-match", StringUtils.defaultIfEmpty(joiner(knife4j.getPathsToMatch(), ","), "/**"));
                properties.setProperty("springdoc.group-configs[" + i + "].packages-to-scan", joiner(knife4j.getBasePackage(), ","));
//                registerPackages(properties, i, knife4j.getBasePackage());
            }
        }
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("knife4j-extension", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        try {
            PropertySource<?> propertySource = propertySources.get(ATTACHED_PROPERTY_SOURCE_NAME);
            Iterable<PropertySource<?>> rs = (Iterable<PropertySource<?>>)propertySource.getSource();
            Field field = ReflectionUtils.findField(rs.getClass(), "sources");
            ReflectionUtils.makeAccessible(field);
            MutablePropertySources newPropertySources = (MutablePropertySources) field.get(rs);
            newPropertySources.addLast(propertiesPropertySource);
        } catch (IllegalAccessException e) {
            propertySources.addLast(propertiesPropertySource);
        }
    }

    private String joiner(String[] basePackage, String s) {
        StringBuilder stringBuilder = new StringBuilder();
        if (ArrayUtils.isEmpty(basePackage)) {
            return stringBuilder.toString();
        }
        for (String s1 : basePackage) {
            stringBuilder.append(s1).append(s);
        }
        return stringBuilder.substring(0, stringBuilder.length() - 1);
    }

    private void registerPackages(Properties properties, int i, String[] basePackage) {
        if (null == basePackage) {
            return;
        }
        for (int j = 0; j < basePackage.length; j++) {
            String s = basePackage[j];
            properties.setProperty("springdoc.group-configs[" + i + "].packages-to-scan[" + j + "]", s);
        }
    }


}
