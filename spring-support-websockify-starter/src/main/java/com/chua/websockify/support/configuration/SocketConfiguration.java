package com.chua.websockify.support.configuration;

import com.chua.common.support.chain.filter.StandardProxyFilter;
import com.chua.common.support.protocol.ServerSetting;
import com.chua.jetty.support.server.WebsockifyServer;
import com.chua.websockify.support.properties.WebsockfiyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * socket.io配置
 *
 * @author CH
 */
@Slf4j
@SuppressWarnings("ALL")
@EnableConfigurationProperties(WebsockfiyProperties.class)
public class SocketConfiguration {



    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(name = "plugin.websockify.enable", havingValue = "true", matchIfMissing = false)
    public WebsockifyServer websockifyServer(WebsockfiyProperties websockfiyProperties) {
        WebsockifyServer websockifyServer = new WebsockifyServer(ServerSetting
                .builder()
                .host(websockfiyProperties.getHost())
                .port(websockfiyProperties.getPort())
                .build());


        websockifyServer.addFilter(new StandardProxyFilter(websockfiyProperties.getTargetHost(), websockfiyProperties.getTargetPort()));
        return websockifyServer;
    }

}
