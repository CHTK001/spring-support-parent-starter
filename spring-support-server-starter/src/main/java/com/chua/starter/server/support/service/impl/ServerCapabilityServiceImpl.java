package com.chua.starter.server.support.service.impl;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.server.support.config.ServerManagementProperties;
import com.chua.starter.server.support.model.ServerCapabilityView;
import com.chua.starter.server.support.service.ServerCapabilityService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class ServerCapabilityServiceImpl implements ServerCapabilityService {

    private final ServerManagementProperties properties;
    private final ApplicationContext applicationContext;
    private final ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider;

    public ServerCapabilityServiceImpl(ServerManagementProperties properties,
                                       ApplicationContext applicationContext,
                                       ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider) {
        this.properties = properties;
        this.applicationContext = applicationContext;
        this.socketSessionTemplateProvider = socketSessionTemplateProvider;
    }

    @Override
    public ServerCapabilityView getCapabilities() {
        AiCapabilityState aiCapabilityState = resolveAiCapabilityState();
        return ServerCapabilityView.builder()
                .aiEnabled(aiCapabilityState.enabled())
                .aiChatClientReady(aiCapabilityState.enabled())
                .aiStatusText(aiCapabilityState.statusText())
                .softEnabled(hasBean("com.chua.starter.soft.support.service.SoftManagementService"))
                .remoteGatewayEnabled(properties.getRemoteGateway().isEnable())
                .fileWatchEnabled(properties.getFileWatch().isEnable()
                        && hasBean("com.chua.starter.server.support.service.ServerFileWatchService"))
                .socketEnabled(socketSessionTemplateProvider.getIfAvailable() != null)
                .serviceAutoDetectEnabled(properties.getServiceOperation().isAutoDetectEnabled())
                .build();
    }

    private AiCapabilityState resolveAiCapabilityState() {
        boolean chatClientReady = hasBean("com.chua.starter.ai.support.chat.ChatClient");
        if (chatClientReady) {
            return new AiCapabilityState(true, "已激活");
        }
        return new AiCapabilityState(false, "未激活");
    }

    private boolean hasBean(String className) {
        try {
            Class<?> type = Class.forName(className);
            return !applicationContext.getBeansOfType(type).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private record AiCapabilityState(
            boolean enabled,
            String statusText
    ) {
    }
}
