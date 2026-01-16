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
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Properties;

/**
 * Swagger环境后处理器
 * 用于配置Knife4j和SpringDoc的相关属性
 *
 * @author CH
 */
public class SwaggerEnvironmentPostProcessor implements EnvironmentPostProcessor {
    
    /**
     * 属性源名称
     */
    private static final String PROPERTY_SOURCE_NAME = "knife4j-extension";

    /**
     * 后处理环境
     *
     * @param environment 可配置环境
     * @param application Spring应用
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.setProperty("springdoc.api-docs.enabled", "true");
        properties.setProperty("springdoc.api-docs.groups.enabled", "true");
        properties.setProperty("springdoc.default-flat-param-object", "true");
        properties.setProperty("knife4j.enable", "${plugin.swagger.enable:true}");
        properties.setProperty("knife4j.basic.enable", "true");
        properties.setProperty("knife4j.basic.username", "${plugin.swagger.username:root}");
        Knife4jProperties knife4jProperties = Binder.get(environment)
                .bindOrCreate("plugin.swagger", Knife4jProperties.class);
        properties.setProperty("knife4j.basic.password", "${plugin.swagger.password:root123}");
        
        List<Knife4jProperties.Knife4j> knife4jList = knife4jProperties.getKnife4j();
        if (!CollectionUtils.isEmpty(knife4jList)) {
            for (int i = 0; i < knife4jList.size(); i++) {
                Knife4jProperties.Knife4j knife4j = knife4jList.get(i);
                String group = StringUtils.defaultIfEmpty(knife4j.getGroup(), knife4j.getGroupName());
                properties.setProperty("knife4j.documents[" + i + "].group", group);
                properties.setProperty("knife4j.documents[" + i + "].name", group);
                properties.setProperty("springdoc.group-configs[" + i + "].group", knife4j.getGroupName());
                properties.setProperty("springdoc.group-configs[" + i + "].title", knife4j.getTitle());
                properties.setProperty("springdoc.group-configs[" + i + "].description", knife4j.getDescription());
                properties.setProperty("springdoc.group-configs[" + i + "].version", knife4j.getVersion());
                properties.setProperty("springdoc.group-configs[" + i + "].display-name", knife4j.getGroupName());
                properties.setProperty("springdoc.group-configs[" + i + "].paths-to-match", 
                        StringUtils.defaultIfEmpty(joinArray(knife4j.getPathsToMatch(), ","), "/**"));
                properties.setProperty("springdoc.group-configs[" + i + "].packages-to-scan", 
                        joinArray(knife4j.getBasePackage(), ","));
            }
        }
        
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource(PROPERTY_SOURCE_NAME, properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        // 直接添加到属性源列表，不需要使用反射
        propertySources.addLast(propertiesPropertySource);
    }

    /**
     * 连接字符串数组
     *
     * @param array 字符串数组
     * @param delimiter 分隔符
     * @return 连接后的字符串
     */
    private String joinArray(String[] array, String delimiter) {
        if (ArrayUtils.isEmpty(array)) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (String item : array) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(delimiter);
            }
            stringBuilder.append(item);
        }
        return stringBuilder.toString();
    }
}
