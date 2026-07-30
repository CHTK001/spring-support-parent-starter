package com.chua.starter.server.support.service;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.server.support.model.ServerRealtimeEnvelope;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ServerRealtimePublisher {

    private final ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider;

    public ServerRealtimePublisher(ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider) {
        this.socketSessionTemplateProvider = socketSessionTemplateProvider;
    }

    public void publish(String module, String event, Object dataId, Object payload) {
        SocketSessionTemplate socketSessionTemplate = socketSessionTemplateProvider.getIfAvailable();
        if (socketSessionTemplate == null) {
            return;
        }
        ServerRealtimeEnvelope envelope = ServerRealtimeEnvelope.builder()
                .module(module)
                .event(event)
                .dataId(dataId)
                .data(payload)
                .timestamp(System.currentTimeMillis())
                .build();
        socketSessionTemplate.broadcastObject("message", envelope);
    }
}
