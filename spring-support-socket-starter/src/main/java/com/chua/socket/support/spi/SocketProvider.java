package com.chua.socket.support.spi;

import com.chua.common.support.annotations.Spi;
import com.chua.socket.support.SocketListener;
import com.chua.socket.support.SocketProtocol;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSessionTemplate;

import java.util.List;

/**
 * Socket 服务提供者接口（SPI）
 * 定义 Socket 服务的创建和生命周期管理
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Spi("socketio")
public interface SocketProvider {

    /**
     * 获取协议类型
     *
     * @return 协议类型
     */
    SocketProtocol getProtocol();

    /**
     * 创建会话模板
     *
     * @param properties 配置属性
     * @param listeners  监听器列表
     * @return 会话模板实例
     */
    SocketSessionTemplate createSessionTemplate(
            SocketProperties properties,
            List<SocketListener> listeners
    );

    /**
     * 启动服务
     *
     * @param template 会话模板
     */
    void start(SocketSessionTemplate template);

    /**
     * 停止服务
     *
     * @param template 会话模板
     */
    void stop(SocketSessionTemplate template);

    /**
     * 是否支持该协议
     *
     * @param protocol 协议类型
     * @return 是否支持
     */
    default boolean supports(SocketProtocol protocol) {
        return getProtocol() == protocol;
    }
}
