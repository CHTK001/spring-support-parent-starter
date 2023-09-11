package com.chua.starter.gateway.support.configuration;

import com.chua.starter.gateway.support.properties.WebSocketSynProperties;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.*;

import java.util.*;

/**
 * @author CH
 */
public class WebSocketSynEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>(2);
        properties.put("shenyu.sync.websocket.urls", environment.getProperty(WebSocketSynProperties.PRE + ".urls"));
        properties.put("shenyu.sync.websocket.allowOrigin", environment.getProperty(WebSocketSynProperties.PRE + ".allowOrigin", environment.getProperty(WebSocketSynProperties.PRE + ".allow-origin")));
        PropertySource propertiesPropertySource = new MapPropertySource("gateway-sync-websocket", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }
}
