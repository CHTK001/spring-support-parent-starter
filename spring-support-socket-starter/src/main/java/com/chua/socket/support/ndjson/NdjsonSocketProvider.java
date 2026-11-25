package com.chua.socket.support.ndjson;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.socket.support.SocketListener;
import com.chua.socket.support.SocketProtocol;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.spi.SocketProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
 * NDJSON Socket 服务提供者
 * 实现 Socket提供者 SPI，提供 NDJSON（Newline-Delimited JSON，别名 jsonl）协议支持。
 *
 * <p>与 {@link com.chua.starter.sse.support.socket.SseSocketProvider} 行为对齐：
 * HTTP 长连接、服务端推送；区别仅在于传输编码是「每行一个 JSON 帧」
 * （{@code application/x-ndjson}），而不是 SSE 的 event-stream 文本协议。</p>
 *
 * @since 2026-11-25
 * @author CH
 */
@Slf4j
@Spi("ndjson")
public class NdjsonSocketProvider implements SocketProvider {

    @Override
    /**
     * 获取协议
     * @return 返回值
     */
    public SocketProtocol getProtocol() {
        return SocketProtocol.NDJSON;
    }

    @Override
    /**
     * 创建会话template
     * @param properties 属性
     * @param listeners 监听器
     * @return 返回值
     */
    public SocketSessionTemplate createSessionTemplate(
            SocketProperties properties,
            List<SocketListener> listeners) {

        // 获取超时配置，默认30分钟
        // [P3C A20 豁免]
        long timeout = 30 * 60 * 1000L;
        if (properties != null && properties.getPingTimeout() > 0) {
            timeout = properties.getPingTimeout();
        }

        NdjsonSocketSessionTemplate template = new NdjsonSocketSessionTemplate(timeout);
        log.info("[NDJSON] 创建 NdjsonSocketSessionTemplate, timeout={}ms", highlight(timeout));

        return template;
    }

    @Override
    /**
     * 开始
     * @param template 参数
     * @return 返回值
     */
    public void start(SocketSessionTemplate template) {
        if (template != null) {
            template.start();
            log.info("[NDJSON] Socket 服务已启动");
        }
    }

    @Override
    /**
     * 停止
     * @param template 参数
     * @return 返回值
     */
    public void stop(SocketSessionTemplate template) {
        if (template != null) {
            template.stop();
            log.info("[NDJSON] Socket 服务已停止");
        }
    }

    @Override
    /**
     * 支持
     * @param protocol 参数
     * @return 返回值
     */
    public boolean supports(SocketProtocol protocol) {
        return protocol == SocketProtocol.NDJSON;
    }
}
