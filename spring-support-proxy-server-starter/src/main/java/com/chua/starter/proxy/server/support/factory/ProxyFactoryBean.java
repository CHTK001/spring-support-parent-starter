package com.chua.starter.proxy.server.support.factory;

import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.common.support.protocol.server.Server;
import com.chua.netty.support.proxy.filter.AsyncHttpRoutingGatewayFilter;
import com.chua.netty.support.proxy.filter.AsyncWebSocketRoutingGatewayFilter;
import org.springframework.beans.factory.FactoryBean;

/**
 * @author CH
 * @since 2024/5/11
 */
public class ProxyFactoryBean implements FactoryBean<Server> {

    private final Server server;
    public ProxyFactoryBean(ServiceDiscovery serviceDiscovery, int port) {
        this.server = Server.create("proxy", port);
        server.addDefinition(serviceDiscovery);
        server.addFilter(AsyncHttpRoutingGatewayFilter.class);
        server.addFilter(AsyncWebSocketRoutingGatewayFilter.class);
    }

    @Override
    public Server getObject() throws Exception {
        return server;
    }

    @Override
    public Class<?> getObjectType() {
        return server.getClass();
    }
}
