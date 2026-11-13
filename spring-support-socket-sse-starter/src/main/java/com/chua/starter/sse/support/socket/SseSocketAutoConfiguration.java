package com.chua.starter.sse.support.socket;

import com.chua.socket.support.configuration.SocketConfiguration;
import com.chua.socket.support.session.SocketSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * SSE 连接端点自动配置。
 *
 * <p>{@code SocketConfiguration} 通过 SPI 创建的模板只放在
 * {@code Map<String, SocketSessionTemplate>} 里，不是可按具体类型注入的 Bean。
 * 这里直接从该 Map 取 {@code sse} 模板来创建控制器，保证 {@code /sse/connect}
 * 与业务 {@code broadcast("message", ...)} 用的是同一个会话实例。</p>
 *
 * <p>没有 sse 模板时返回 null，不注册端点；socketio 单协议部署不受影响。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@AutoConfiguration
@AutoConfigureAfter(SocketConfiguration.class)
public class SseSocketAutoConfiguration {

    /**
     * 创建 SSE 连接控制器（仅在启用 sse 协议时生效）。
     *
     * @param templatesProvider 协议模板映射（SocketConfiguration 产出）
     * @return 控制器；没有 sse 模板时为 null
     */
    @Bean
    public SseSocketController sseSocketController(
            ObjectProvider<Map<String, SocketSessionTemplate>> templatesProvider) {
        Map<String, SocketSessionTemplate> templates = templatesProvider.getIfAvailable();
        if (templates == null) {
            return null;
        }
        SocketSessionTemplate template = templates.get("sse");
        if (template instanceof SseSocketSessionTemplate sseTemplate) {
            return new SseSocketController(sseTemplate);
        }
        return null;
    }
}
