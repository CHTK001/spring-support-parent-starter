package com.chua.starter.sse.support.socket;

import com.chua.common.support.core.annotation.Spi;
import com.chua.socket.support.SocketListener;
import com.chua.socket.support.SocketProtocol;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.spi.SocketProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
 * SSE Socket 服务提供者
 * 实现 SocketProvider SPI，提供 SSE 协议支持
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-24
 */
@Slf4j
@Spi("sse")
public class SseSocketProvider implements SocketProvider {

    @Override
    public SocketProtocol getProtocol() {
        return SocketProtocol.SSE;
    }

    @Override
    public SocketSessionTemplate createSessionTemplate(
            SocketProperties properties,
            List<SocketListener> listeners) {

        // 获取超时配置，默认30分钟
        long timeout = 30 * 60 * 1000L;
        if (properties != null && properties.getPingTimeout() > 0) {
            timeout = properties.getPingTimeout();
        }

        SseSocketSessionTemplate template = new SseSocketSessionTemplate(timeout);
        log.info("[SSE] 创建 SseSocketSessionTemplate, timeout={}ms", highlight(timeout));

        return template;
    }

    @Override
    public void start(SocketSessionTemplate template) {
        if (template != null) {
            template.start();
            log.info("[SSE] Socket 服务已启动");
        }
    }

    @Override
    public void stop(SocketSessionTemplate template) {
        if (template != null) {
            template.stop();
            log.info("[SSE] Socket 服务已停止");
        }
    }

    @Override
    public boolean supports(SocketProtocol protocol) {
        return protocol == SocketProtocol.SSE;
    }
}
