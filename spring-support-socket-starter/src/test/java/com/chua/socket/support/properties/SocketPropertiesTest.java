package com.chua.socket.support.properties;

import com.chua.socket.support.SocketProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SocketPropertiesTest {

    @Test
    void shouldDefaultToAutoStartAndSocketIoContextPath() {
        SocketProperties properties = new SocketProperties();
        SocketProperties.Room room = new SocketProperties.Room();

        assertThat(properties.isAutoStart()).isTrue();
        assertThat(room.getContextPath()).isEqualTo("/socket.io");
    }

    @Test
    void shouldResolveRelativePortsAgainstBasePort() {
        SocketProperties.Room room = new SocketProperties.Room();

        room.setPort(-1);
        assertThat(room.getActualPort(9000)).isEqualTo(9000);

        room.setPort(-2);
        assertThat(room.getActualPort(9000)).isEqualTo(9001);

        room.setPort(-4);
        assertThat(room.getActualPort(9000)).isEqualTo(9003);

        room.setPort(19170);
        assertThat(room.getActualPort(9000)).isEqualTo(19170);
    }

    @Test
    void shouldPreferExplicitProtocolsOverLegacyConfiguration() {
        SocketProperties properties = new SocketProperties();
        properties.setProtocol(SocketProtocol.SSE);

        SocketProperties.ProtocolConfig protocolConfig = new SocketProperties.ProtocolConfig();
        protocolConfig.setProtocol(SocketProtocol.SOCKETIO);
        protocolConfig.setRoom(List.of(new SocketProperties.Room()));
        properties.setProtocols(List.of(protocolConfig));

        List<SocketProperties.ProtocolConfig> effectiveProtocols = properties.getEffectiveProtocols();

        assertThat(effectiveProtocols).hasSize(1);
        assertThat(effectiveProtocols.getFirst().getProtocol()).isEqualTo(SocketProtocol.SOCKETIO);
    }
}
