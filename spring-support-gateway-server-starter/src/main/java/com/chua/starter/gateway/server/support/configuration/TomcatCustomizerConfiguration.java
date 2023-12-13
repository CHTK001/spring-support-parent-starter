package com.chua.starter.gateway.server.support.configuration;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * tomcat自定义程序配置
 *
 * @author CH
 */
@Configuration
public class TomcatCustomizerConfiguration implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {
    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        ((TomcatServletWebServerFactory)factory).setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }
}
