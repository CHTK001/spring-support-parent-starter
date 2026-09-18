package com.chua.rsocket.support.spi;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.rsocket.support.session.RSocketSessionTemplateImpl;
import com.chua.socket.support.SocketListener;
import com.chua.socket.support.SocketProtocol;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.spi.SocketProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
* rSocket 协议提供者
* 实现基础 Socket 模块的 SPI 接口
*
* @版本 1.0.0
* @since 2024-12-04
* @author CH
 */
@Slf4j
@Spi("rsocket")
public class RSocketProvider implements SocketProvider {

    @Override
    /** 获取协议 */
    public SocketProtocol getProtocol() {
        return SocketProtocol.RSOCKET;
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
        log.info("[RSocket] 创建 RSocket 会话模板");
        /**
        * rSocket会话templateimpl。
        * @param properties 属性
        * @param listeners 监听器
        * @return rSocket会话templateimpl的结果
        */
        return new RSocketSessionTemplateImpl(properties, listeners);
    }

    @Override
    /** 开始 */
    public void start(SocketSessionTemplate template) {
        log.info("[RSocket] 启动 RSocket 服务");
        template.start();
    }

    @Override
    /** 停止 */
    public void stop(SocketSessionTemplate template) {
        log.info("[RSocket] 停止 RSocket 服务");
        template.stop();
    }
}
