package com.chua.starter.swagger.support.environment;

import com.chua.starter.swagger.support.Knife4jProperties;
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
 * freemarker
 *
 * @author CH
 */
public class SwaggerEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.setProperty("springdoc.api-docs.enabled", "true");
        properties.setProperty("knife4j.enable", "${plugin.swagger.enable:true}");
        properties.setProperty("knife4j.basic.enable", "true");
        properties.setProperty("knife4j.basic.username", "${plugin.swagger.username:root}");
        Knife4jProperties knife4jProperties = Binder.get(environment).bindOrCreate("plugin.swagger", Knife4jProperties.class);
        properties.setProperty("knife4j.basic.password", "${plugin.swagger.password:root123}");
        List<Knife4jProperties.Knife4j> j = knife4jProperties.getKnife4j();
        if(!CollectionUtils.isEmpty(j)) {
            for (int i = 0, jSize = j.size(); i < jSize; i++) {
                Knife4jProperties.Knife4j knife4j = j.get(i);
                properties.setProperty("knife4j.documents["+ i +"].group", knife4j.getGroupName());
            }
        }
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("knife4j", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }


}
