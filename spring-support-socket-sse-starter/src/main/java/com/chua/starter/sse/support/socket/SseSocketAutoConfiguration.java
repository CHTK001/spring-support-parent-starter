package com.chua.starter.sse.support.socket;

import com.chua.socket.support.configuration.SocketConfiguration;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * SSE 连接端点自动配置。
 *
 * <p>{@code SocketConfiguration} 通过 SPI 创建的模板只放在
 * {@code Map<String, SocketSessionTemplate>}（bean 名 socketSessionTemplates）里，
 * 不是可按具体类型注入的 Bean。这里按 bean 名注入该 Map，取 {@code sse}
 * 模板来创建控制器，保证 {@code /sse/connect}
 * 与业务 {@code broadcast("message", ...)} 用的是同一个会话实例。</p>
 *
 * <p>注意：不能注入 {@code ObjectProvider<Map<String, SocketSessionTemplate>>}——
 * Spring 会把它解析成「所有 SocketSessionTemplate 类型 Bean 按名收集」的 Map
 * （key 是 bean 名 socketSessionTemplate，不是协议名 sse），永远取不到模板；
 * 必须用 {@code @Qualifier("socketSessionTemplates")} 按名注入 Map Bean。</p>
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
     * 创建 SSE 连接控制器（仅在启用 socket 时生效；socket 关闭时 Map Bean 不存在，
     * 条件装配直接跳过本方法，避免按名注入失败）。
     *
     * @param templates 协议模板映射（SocketConfiguration 产出，按 bean 名注入）
     * @return 控制器；没有 sse 模板时为 null
     */
    @Bean
    @ConditionalOnProperty(prefix = SocketProperties.PRE, name = "enable", havingValue = "true")
    public SseSocketController sseSocketController(
            @Qualifier("socketSessionTemplates") Map<String, SocketSessionTemplate> templates) {
        SocketSessionTemplate template = templates.get("sse");
        if (template instanceof SseSocketSessionTemplate sseTemplate) {
            return new SseSocketController(sseTemplate);
        }
        return null;
    }
}
