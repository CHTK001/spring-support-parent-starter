package com.chua.socket.support.ndjson;

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
 * NDJSON 连接端点自动配置。
 *
 * <p>{@code SocketConfiguration} 通过 SPI 创建的模板只放在
 * {@code Map<String, SocketSessionTemplate>}（bean 名 socketSessionTemplates）里，
 * 不是可按具体类型注入的 Bean。这里按 bean 名注入该 Map，取 {@code ndjson}
 * 模板创建控制器，保证 {@code /ndjson/connect} 与业务
 * {@code broadcast("message", ...)} 用的是同一个会话实例。</p>
 *
 * <p>注意：不能注入 {@code ObjectProvider<Map<String, SocketSessionTemplate>>}——
 * Spring 会把它解析成「所有 SocketSessionTemplate 类型 Bean 按名收集」的 Map
 * （key 是 bean 名 socketSessionTemplate，不是协议名 ndjson），永远取不到模板；
 * 必须用 {@code @Qualifier("socketSessionTemplates")} 按名注入 Map Bean。</p>
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
     * @param templates 协议模板映射（SocketConfiguration 产出，按 bean 名注入）
     * @return 控制器；没有 ndjson 模板时为 null
     */
    @Bean
    @ConditionalOnProperty(prefix = SocketProperties.PRE, name = "enable", havingValue = "true")
    public NdjsonSocketController ndjsonSocketController(
            @Qualifier("socketSessionTemplates") Map<String, SocketSessionTemplate> templates) {
        SocketSessionTemplate template = templates.get("ndjson");
        if (template instanceof NdjsonSocketSessionTemplate ndjsonTemplate) {
            return new NdjsonSocketController(ndjsonTemplate);
        }
        return null;
    }
}
