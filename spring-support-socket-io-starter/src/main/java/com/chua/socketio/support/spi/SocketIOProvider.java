package com.chua.socketio.support.spi;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.socket.support.SocketListener;
import com.chua.socket.support.SocketProtocol;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.spi.SocketProvider;
import com.chua.socketio.support.session.SocketIOSessionTemplate;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
* Socket.IO 协议提供者
* 实现基础 Socket 模块的 SPI 接口
*
* @版本 1.0.0
* @since 2024-12-04
* @author CH
 */
@Slf4j
@Spi("socketio")
public class SocketIOProvider implements SocketProvider {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(SocketIOProvider.class);

    @Override
    /** 获取协议 */
    public SocketProtocol getProtocol() {
        return SocketProtocol.SOCKETIO;
    }

    @Override
    /**
    * 创建会话template
    * @param properties 属性
    * @param listeners 监听器
    * @param listeners 监听器
    * @param template template
    * @param template template
    */
    public SocketSessionTemplate createSessionTemplate(
            SocketProperties properties,
            List<SocketListener> listeners) {
        log.info("[SocketIO] 创建 Socket.IO 会话模板");
        /**
        * Socketio会话template。
        * @param properties 属性
        * @param listeners 监听器
        * @return Socketio会话template的结果
        */
        return new SocketIOSessionTemplate(properties, listeners);
    }

    @Override
    /** 开始 */
    public void start(SocketSessionTemplate template) {
        log.info("[SocketIO] 启动 Socket.IO 服务");
        template.start();
    }

    @Override
    /** 停止 */
    public void stop(SocketSessionTemplate template) {
        log.info("[SocketIO] 停止 Socket.IO 服务");
        template.stop();
    }
}
