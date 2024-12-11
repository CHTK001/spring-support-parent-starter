package com.chua.socketio.support.register;

import com.chua.socketio.support.SocketIOListener;
import com.chua.socketio.support.properties.SocketIoProperties;
import com.chua.socketio.support.resolver.DefaultSocketSessionResolver;
import com.chua.socketio.support.resolver.SocketSessionResolver;
import com.chua.socketio.support.server.DelegateSocketIOServer;
import com.chua.socketio.support.session.DelegateSocketSessionFactory;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.corundumstudio.socketio.Configuration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.StringUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * @author CH
 * @since 2024/12/11
 */
@Slf4j
public class SocketIoRegistration implements InitializingBean, DisposableBean {

    private List<Configuration> configurations;
    private final SocketIoProperties properties;
    private final SocketSessionTemplate socketSessionTemplate;
    private final List<SocketIOListener> listenerList;
    private final List<SocketInfo> socketIOServers = new LinkedList<>();

    public SocketIoRegistration(List<Configuration> configurations, SocketIoProperties properties, SocketSessionTemplate socketSessionTemplate, List<SocketIOListener> listenerList) {
        this.configurations = configurations;
        this.properties = properties;
        this.socketSessionTemplate = socketSessionTemplate;
        this.listenerList = listenerList;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        for (Configuration configuration : configurations) {
            DelegateSocketIOServer socketIOServer = null;
            try {
                socketIOServer = new DelegateSocketIOServer(configuration);
                SocketSessionResolver socketSessionResolver = new DefaultSocketSessionResolver(listenerList, properties);
                socketIOServer.addConnectListener(client -> {
                    socketSessionTemplate.save(String.valueOf(configuration.getPort()), client);
                    socketSessionResolver.doConnect(client);
                });
                socketIOServer.addDisconnectListener(client -> {
                    socketSessionTemplate.remove(String.valueOf(configuration.getPort()), client);
                    socketSessionResolver.disConnect(client);
                });
                socketSessionResolver.registerEvent(socketIOServer);
                String namespace = properties.getNamespace();

                if(StringUtils.hasText(namespace)) {
                    socketIOServer.addNamespace(namespace.startsWith("/") ? namespace : "/" + namespace);
                }
                socketIOServer.start();
                socketIOServers.add(new SocketInfo(socketIOServer, socketSessionResolver, socketSessionTemplate));
            } catch (Exception e) {
                log.error(e.getLocalizedMessage());
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        for (SocketInfo socketIOServer : socketIOServers) {
            try {
                socketIOServer.getServer().stop();
            } catch (Exception ignored) {
            }
        }
    }

    @Data
    @AllArgsConstructor
    private static class SocketInfo {
        private DelegateSocketIOServer server;
        private SocketSessionResolver resolver;
        private SocketSessionTemplate template;
    }
}
