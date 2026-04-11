package com.chua.starter.soft.support.service;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.soft.support.constants.SoftSocketEvents;
import com.chua.starter.soft.support.model.SoftRealtimeEnvelope;
import com.chua.starter.soft.support.model.SoftRealtimePayload;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class SoftRealtimePublisher {

    private final ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider;

    public SoftRealtimePublisher(ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider) {
        this.socketSessionTemplateProvider = socketSessionTemplateProvider;
    }

    public void publish(String event, Object dataId, SoftRealtimePayload payload) {
        SocketSessionTemplate socketSessionTemplate = socketSessionTemplateProvider.getIfAvailable();
        if (socketSessionTemplate == null) {
            return;
        }
        SoftRealtimeEnvelope envelope = SoftRealtimeEnvelope.builder()
                .module(SoftSocketEvents.MODULE)
                .event(event)
                .dataId(dataId)
                .data(payload)
                .timestamp(System.currentTimeMillis())
                .build();
        socketSessionTemplate.broadcastObject("message", envelope);
    }
}
