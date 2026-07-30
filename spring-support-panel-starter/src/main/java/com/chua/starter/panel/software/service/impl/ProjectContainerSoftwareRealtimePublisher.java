package com.chua.starter.panel.software.service.impl;

import com.chua.socket.support.session.SocketSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ProjectContainerSoftwareRealtimePublisher
        implements com.chua.starter.panel.software.service.ProjectContainerSoftwareRealtimePublisher {

    private static final String MODULE = "PROJECT";

    private final ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider;

    public ProjectContainerSoftwareRealtimePublisher(ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider) {
        this.socketSessionTemplateProvider = socketSessionTemplateProvider;
    }

    @Override
    public void publish(String event, String operationId, Map<String, Object> payload) {
        SocketSessionTemplate socketSessionTemplate = socketSessionTemplateProvider.getIfAvailable();
        if (socketSessionTemplate == null) {
            return;
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("module", MODULE);
        envelope.put("event", event);
        envelope.put("dataId", operationId);
        envelope.put("data", payload);
        envelope.put("timestamp", System.currentTimeMillis());
        socketSessionTemplate.broadcastObject("message", envelope);
    }
}
