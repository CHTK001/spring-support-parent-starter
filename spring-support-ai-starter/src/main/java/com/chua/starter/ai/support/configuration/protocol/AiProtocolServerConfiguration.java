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
     * <p>两级开关都打开才注册：部署级 {@code plugin.protocol.enable}（YAML，重启生效）
     * 与管理员级 {@code runtime.protocol_server_enabled}（设置表，热改后下次重启生效）。
     * 后者此前只在设置页登记、后端从不读取 —— 关掉它，协议端点照样对外服务。</p>
     *
     * @param settingService 全局设置服务
     * @return 过滤器列表；关闭时为空列表
     */
    @Bean("aiProtocolServerFilters")
    public List<AiProtocolServerFilter> aiProtocolServerFilters(
            com.chua.starter.ai.support.service.SysAiGlobalSettingService settingService) {
        boolean enabled;
        try {
            // 缺省启用：该开关此前未被读取，协议入口一直可用；默认关闭会让升级即停服
            enabled = settingService.getBoolean("runtime", "protocol_server_enabled", true);
        } catch (Exception e) {
            // 设置表异常按关闭处理：对外协议入口不能因为读配置失败就默认放开
            log.warn("[AiProtocolServer] 读取 protocol_server_enabled 失败，按关闭处理: {}", e.getMessage());
            enabled = false;
        }
        if (!enabled) {
            log.info("[AiProtocolServer] runtime.protocol_server_enabled=false，不注册协议服务器过滤器");
            return List.of();
        }
        List<AiProtocolServerFilter> filters = ServiceProvider.of(AiProtocolServerFilter.class).collect();
        log.info("[AiProtocolServer] 加载 {} 个 AI 协议服务器过滤器", filters.size());
        return filters;
    }
}
