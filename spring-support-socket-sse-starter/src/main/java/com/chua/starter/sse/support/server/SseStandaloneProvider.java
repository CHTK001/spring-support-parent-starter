package com.chua.starter.sse.support.server;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.socket.support.SocketListener;
import com.chua.socket.support.SocketProtocol;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.spi.SocketProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 独立端口 SSE 协议提供者。
 *
 * <p>与嵌入式 {@code sse} 的区别只在连接承载：本提供者用 {@code NioHttpServer}
 * 在 {@code plugin.socket.room[0].port} 单独监听，不占主应用的 HTTP 连接和
 * 读写事件；业务侧仍是同一个 {@link SocketSessionTemplate} 接口。</p>
 *
 * <p>配置示例：</p>
 * <pre>
 * plugin:
 *   socket:
 *     enable: true
 *     protocols:
 *       - protocol: sse-server
 *         room:
 *           - port: 29612
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Spi("sse-server")
public class SseStandaloneProvider implements SocketProvider {

    /**
     * 默认 SSE 独立端口
     */
    private static final int DEFAULT_SSE_PORT = 29612;

    @Override
    public SocketProtocol getProtocol() {
        return SocketProtocol.SSE_SERVER;
    }

    @Override
    public SocketSessionTemplate createSessionTemplate(
            SocketProperties properties,
            List<SocketListener> listeners) {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost(properties.getHost());
        setting.setPort(resolvePort(properties));
        // SSE 连接是长连接：并发名额必须放开，否则几条连接就把服务打成 503
        setting.setMaxConcurrency(0);
        // 停机不等待活跃请求：SSE 连接在 stop 前会统一 close，不值得再等 30s
        setting.setShutdownQuietPeriod(0);
        log.info("[SSE-Standalone] 创建独立 SSE 模板: {}:{}",
                setting.getHost(), setting.getPort());
        return new SseStandaloneSessionTemplate(setting);
    }

    /**
     * 取第一个配置了端口的房间，缺省 29612（与 socket.io 推送端口同一位）。
     *
     * @param properties 配置
     * @return 端口
     */
    private int resolvePort(SocketProperties properties) {
        if (properties.getRoom() != null) {
            for (SocketProperties.Room room : properties.getRoom()) {
                if (room.getPort() > 0) {
                    return room.getPort();
                }
            }
        }
        return DEFAULT_SSE_PORT;
    }

    @Override
    public void start(SocketSessionTemplate template) {
        template.start();
    }

    @Override
    public void stop(SocketSessionTemplate template) {
        template.stop();
    }

    @Override
    public boolean supports(SocketProtocol protocol) {
        return protocol == SocketProtocol.SSE_SERVER;
    }
}
