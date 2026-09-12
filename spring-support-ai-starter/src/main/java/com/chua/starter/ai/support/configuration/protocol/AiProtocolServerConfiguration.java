package com.chua.starter.ai.support.configuration.protocol;

import com.chua.common.support.ai.chat.protocol.AiProtocolServerFilter;
import com.chua.common.support.spi.ServiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
* AI 协议服务器自动配置 — 打开AI 兼容协议接口。
*
* <p>当 {@code plugin.protocol.enable=true} 时，自动注册 AI 协议服务器过滤器，
* 提供 打开AI 兼容的 对话 Completions API（/v1/对话/completions）。
*
* <p>如果 {@code spring-support-ai-protocol-starter} 在 classpath 中，
* 则由该模块接管 AI 协议服务端 生命周期（本配置不生效）。
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(name = "plugin.protocol.enable", havingValue = "true", matchIfMissing = false)
public class AiProtocolServerConfiguration {

    /**
    * 收集所有 AI 协议服务器过滤器。
    *
    * @return 过滤器列表
     */
    @Bean("aiProtocolServerFilters")
    public List<AiProtocolServerFilter> aiProtocolServerFilters() {
        List<AiProtocolServerFilter> filters = ServiceProvider.of(AiProtocolServerFilter.class).collect();
        log.info("[AiProtocolServer] 加载 {} 个 AI 协议服务器过滤器", filters.size());
        return filters;
    }
}