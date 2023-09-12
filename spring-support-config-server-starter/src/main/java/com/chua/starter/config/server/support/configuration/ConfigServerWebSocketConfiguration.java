package com.chua.starter.config.server.support.configuration;


import com.chua.starter.config.server.support.endpoint.WebSocketEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.util.List;
import java.util.Map;

/**
 * 配置服务器web套接字配置
 *
 * @author CH
 */
public class ConfigServerWebSocketConfiguration extends ServerEndpointConfig.Configurator implements ServletContextInitializer {


    @Bean
    @ConditionalOnMissingBean
    public ServerEndpointExporter serverEndpointExporter () {
        return new ServerEndpointExporter();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebSocketEndpoint webSocketEndpoint () {
        return new WebSocketEndpoint();
    }

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {

    }

    /**
     * 建立握手时，连接前的操作
     */
    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        // 这个userProperties 可以通过 session.getUserProperties()获取
        final Map<String, Object> userProperties = sec.getUserProperties();
        Map<String, List<String>> headers = request.getHeaders();
        List<String> remoteIp = headers.get("x-oauth-token");
        userProperties.put("x-oauth-token", remoteIp.get(0));
    }

    /**
     * 初始化端点对象,也就是被@ServerEndpoint所标注的对象
     */
    @Override
    public <T> T getEndpointInstance(Class<T> clazz) throws InstantiationException {
        return super.getEndpointInstance(clazz);
    }
}
