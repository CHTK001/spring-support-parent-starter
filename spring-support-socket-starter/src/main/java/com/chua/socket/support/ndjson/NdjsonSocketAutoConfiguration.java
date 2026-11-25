package com.chua.socket.support.ndjson;

import com.chua.socket.support.configuration.SocketConfiguration;
import com.chua.socket.support.session.SocketSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * NDJSON 连接端点自动配置。
 *
 * <p>{@code SocketConfiguration} 通过 SPI 创建的模板只放在
 * {@code Map<String, SocketSessionTemplate>} 里，不是可按具体类型注入的 Bean。
 * 这里直接从该 Map 取 {@code ndjson} 模板来创建控制器，保证
 * {@code /ndjson/connect} 与业务 {@code broadcast("message", ...)}
 * 用的是同一个会话实例。</p>
 *
 * <p>没有 ndjson 模板时返回 null，不注册端点；其它协议部署不受影响。</p>
 *
 * @author CH
 * @since 2026-11-25
 */
@AutoConfiguration
@AutoConfigureAfter(SocketConfiguration.class)
public class NdjsonSocketAutoConfiguration {

    /**
     * 创建 NDJSON 连接控制器（仅在启用 ndjson 协议时生效）。
     *
     * @param templatesProvider 协议模板映射（SocketConfiguration 产出）
     * @return 控制器；没有 ndjson 模板时为 null
     */
    @Bean
    public NdjsonSocketController ndjsonSocketController(
            ObjectProvider<Map<String, SocketSessionTemplate>> templatesProvider) {
        Map<String, SocketSessionTemplate> templates = templatesProvider.getIfAvailable();
        if (templates == null) {
            return null;
        }
        SocketSessionTemplate template = templates.get("ndjson");
        if (template instanceof NdjsonSocketSessionTemplate ndjsonTemplate) {
            return new NdjsonSocketController(ndjsonTemplate);
        }
        return null;
    }
}
