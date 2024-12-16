package com.chua.websockify.support.configuration;

import com.chua.common.support.protocol.ServerSetting;
import com.chua.jetty.support.server.WebsockifyServer;
import com.chua.websockify.support.properties.WebsockfiyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
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


    private WebsockfiyProperties websockfiyProperties;
    private ServerProperties serverProperties;



    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(name = "websockify.enable", havingValue = "true", matchIfMissing = false)
    public WebsockifyServer websockifyServer() {
        return new WebsockifyServer(ServerSetting
                .builder()
                .host(websockfiyProperties.getHost())
                .port(websockfiyProperties.getPort())
                .build());
    }

}
