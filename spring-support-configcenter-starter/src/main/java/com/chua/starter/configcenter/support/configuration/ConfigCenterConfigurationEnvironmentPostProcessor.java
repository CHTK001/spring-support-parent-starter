package com.chua.starter.configcenter.support.configuration;

import com.chua.common.support.config.ConfigCenter;
import com.chua.common.support.config.setting.ConfigCenterSetting;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.configcenter.support.properties.ConfigCenterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.env.SystemEnvironmentPropertySourceEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.List;
import java.util.Map;

/**
 * 发现服务
 * @author CH
 * @since 2024/9/9
 */
@Slf4j
@EnableConfigurationProperties(ConfigCenterProperties.class)
public class ConfigCenterConfigurationEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {


    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ConfigCenterProperties configCenterProperties = Binder.get(environment).bindOrCreate(ConfigCenterProperties.PRE, ConfigCenterProperties.class);
        if (!configCenterProperties.isEnabled()) {
            return;
        }
        String active = environment.getProperty("spring.profiles.active");
        ConfigCenter configCenter = ServiceProvider.of(ConfigCenter.class)
                .getNewExtension(configCenterProperties.getProtocol(), ConfigCenterSetting.builder()
                .address(configCenterProperties.getAddress())
                .username(configCenterProperties.getUsername())
                .password(configCenterProperties.getPassword())
                .profile(StringUtils.defaultString(configCenterProperties.getNamespaceId(), active))
                .build());
        if (null == configCenter) {
            log.warn("暂不支持{}, 请重新设置!", configCenterProperties.getProtocol());
            return;
        }

        configCenter.start();

        String include = environment.getProperty("spring.profiles.include");
        List<String> strings = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(include);
        for (String string : strings) {
            String newName = "application-" + string + ".yml";
            Map<String, Object> stringObjectMap = configCenter.get(newName);
            if (null == stringObjectMap) {
                continue;
            }

            environment.getPropertySources()
                    .addLast(new OriginTrackedMapPropertySource(newName, stringObjectMap));
        }
    }


    @Override
    public int getOrder() {
        return SystemEnvironmentPropertySourceEnvironmentPostProcessor.DEFAULT_ORDER - 100 ;
    }
}
