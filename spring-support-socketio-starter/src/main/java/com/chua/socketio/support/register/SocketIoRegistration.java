package com.chua.socketio.support.register;

import com.chua.common.support.printer.TablePrinter;
import com.chua.socketio.support.SocketIOListener;
import com.chua.socketio.support.properties.SocketIoProperties;
import com.chua.socketio.support.resolver.DefaultSocketSessionResolver;
import com.chua.socketio.support.resolver.SocketSessionResolver;
import com.chua.socketio.support.server.DelegateSocketIOServer;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.socketio.support.wrapper.WrapperConfiguration;
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

    private final List<WrapperConfiguration> configurations;
    private final SocketIoProperties properties;
    private final SocketSessionTemplate socketSessionTemplate;
    private final List<SocketIOListener> listenerList;
    private final List<SocketInfo> socketIOServers = new LinkedList<>();

    public SocketIoRegistration(List<WrapperConfiguration> configurations, SocketIoProperties properties, SocketSessionTemplate socketSessionTemplate, List<SocketIOListener> listenerList) {
        this.configurations = configurations;
        this.properties = properties;
        this.socketSessionTemplate = socketSessionTemplate;
        this.listenerList = listenerList;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        if (configurations.isEmpty()) {
            return;
        }
        TablePrinter printer = new TablePrinter("客户端ID", "上下文", "端口", "状态");
        for (WrapperConfiguration wrapperConfiguration : configurations) {
            Configuration configuration = wrapperConfiguration.getConfiguration();
            String clientId = wrapperConfiguration.getClientId();
            configuration.setContext(wrapperConfiguration.getContentPath());
            DelegateSocketIOServer socketIOServer = null;
            try {
                socketIOServer = new DelegateSocketIOServer(configuration);
                SocketSessionResolver socketSessionResolver = new DefaultSocketSessionResolver(listenerList, properties);
                socketIOServer.addConnectListener(client -> {
                    socketSessionTemplate.save(clientId, client);
                    socketSessionResolver.doConnect(client);
                });
                socketIOServer.addDisconnectListener(client -> {
                    socketSessionTemplate.remove(clientId, client);
                    socketSessionResolver.disConnect(client);
                });
                socketSessionResolver.registerEvent(socketIOServer);
                String namespace = properties.getNamespace();

                if(StringUtils.hasText(namespace)) {
                    socketIOServer.addNamespace(namespace.startsWith("/") ? namespace : "/" + namespace);
                }
                socketIOServer.start();
                printer.addRow(clientId, configuration.getContext(), String.valueOf(configuration.getPort()), "已启动");
                socketIOServers.add(new SocketInfo(socketIOServer, socketSessionResolver, socketSessionTemplate));
            } catch (Exception e) {
                log.error(e.getLocalizedMessage());
                printer.addRow(clientId, configuration.getContext(), String.valueOf(configuration.getPort()), "启动失败");
            }
        }
        log.info("当前启动socket.io端口: \n{}", printer.print());
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
