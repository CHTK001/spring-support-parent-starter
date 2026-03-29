package com.chua.socket.support.configuration;

import com.chua.socket.support.SocketProtocol;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.session.SocketUser;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SocketConfigurationTest {

    @Test
    void shouldStartTemplateWhenAutoStartEnabled() throws Exception {
        CountingTemplate template = new CountingTemplate();
        SocketProperties properties = createProperties(true);
        SocketConfiguration.SocketLifecycle lifecycle = new SocketConfiguration.SocketLifecycle(
                new LinkedHashMap<>(Map.of("socketio", template)),
                properties
        );

        lifecycle.afterPropertiesSet();
        lifecycle.destroy();

        assertThat(template.startCount).isEqualTo(1);
        assertThat(template.stopCount).isEqualTo(1);
    }

    @Test
    void shouldSkipStartWhenAutoStartDisabled() throws Exception {
        CountingTemplate template = new CountingTemplate();
        SocketProperties properties = createProperties(false);
        SocketConfiguration.SocketLifecycle lifecycle = new SocketConfiguration.SocketLifecycle(
                new LinkedHashMap<>(Map.of("socketio", template)),
                properties
        );

        lifecycle.afterPropertiesSet();
        lifecycle.destroy();

        assertThat(template.startCount).isZero();
        assertThat(template.stopCount).isEqualTo(1);
    }

    private SocketProperties createProperties(boolean autoStart) {
        SocketProperties properties = new SocketProperties();
        properties.setAutoStart(autoStart);

        SocketProperties.ProtocolConfig protocolConfig = new SocketProperties.ProtocolConfig();
        protocolConfig.setProtocol(SocketProtocol.SOCKETIO);
        protocolConfig.setRoom(List.of(new SocketProperties.Room()));
        properties.setProtocols(List.of(protocolConfig));
        return properties;
    }

    private static final class CountingTemplate implements SocketSessionTemplate {

        private int startCount;
        private int stopCount;

        @Override
        public SocketSession save(String clientId, SocketSession session) {
            return session;
        }

        @Override
        public void remove(String clientId, SocketSession session) {
        }

        @Override
        public SocketSession getSession(String sessionId) {
            return null;
        }

        @Override
        public void send(String sessionId, String event, String msg) {
        }

        @Override
        public void broadcast(String event, String msg) {
        }

        @Override
        public void sendToUser(String userId, String event, String msg) {
        }

        @Override
        public List<SocketSession> getOnlineSessions() {
            return Collections.emptyList();
        }

        @Override
        public List<SocketSession> getOnlineSession(String type, String roomId) {
            return Collections.emptyList();
        }

        @Override
        public List<SocketUser> getOnlineUsers(String type) {
            return Collections.emptyList();
        }

        @Override
        public int getOnlineCount() {
            return 0;
        }

        @Override
        public void start() {
            startCount++;
        }

        @Override
        public void stop() {
            stopCount++;
        }
    }
}
