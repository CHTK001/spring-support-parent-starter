package com.chua.socketio.support.spi;

import com.chua.common.support.spi.Spi;
import com.chua.socket.support.SocketListener;
import com.chua.socket.support.SocketProtocol;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.spi.SocketProvider;
import com.chua.socketio.support.session.SocketIOSessionTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Socket.IO 协议提供者
 * 实现基础 Socket 模块的 SPI 接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Slf4j
@Spi("socketio")
public class SocketIOProvider implements SocketProvider {

    @Override
    public SocketProtocol getProtocol() {
        return SocketProtocol.SOCKETIO;
    }

    @Override
    public SocketSessionTemplate createSessionTemplate(
            SocketProperties properties,
            List<SocketListener> listeners) {
        log.info("[SocketIO] 创建 Socket.IO 会话模板");
        return new SocketIOSessionTemplate(properties, listeners);
    }

    @Override
    public void start(SocketSessionTemplate template) {
        log.info("[SocketIO] 启动 Socket.IO 服务");
        template.start();
    }

    @Override
    public void stop(SocketSessionTemplate template) {
        log.info("[SocketIO] 停止 Socket.IO 服务");
        template.stop();
    }
}
