package com.chua.starter.ai.support.configuration.runtime;

import com.chua.common.support.ai.agent.Agent;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Agent 引擎工厂：按管理员「对话路由 - Agent 引擎实现」配置创建 Agent。
 *
 * <p>默认使用 {@value #DEFAULT_PROVIDER}；配置了未知实现时回退默认值，避免管理员误填
 * 一个未注册 SPI 后整个 AI 会话链路直接不可用。</p>
 *
 * @since 4.0.0.48
 * @author CH
 */
@Component
public class AgentProviderFactory {

    /**
     * 默认 Agent 实现。
     */
    public static final String DEFAULT_PROVIDER = RuntimeGlobalSettingProvider.DEFAULT_AGENT_IMPL;

    /**
     * 已注册的 Agent 实现白名单。
     */
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("agentscope", "opencode", "langchain4j");

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService globalSettingService;

    public AgentProviderFactory(SysAiGlobalSettingService globalSettingService) {
        this.globalSettingService = globalSettingService;
    }

    /**
     * 创建当前配置的 Agent 实例。
     *
     * @return Agent 实例
     */
    public Agent createAgent() {
        return Agent.create(resolveProvider());
    }

    /**
     * 解析当前启用的 Agent 引擎实现。
     *
     * @return Agent SPI 名称
     */
    public String resolveProvider() {
        String provider = globalSettingService
                .getValue(RuntimeGlobalSettingProvider.GROUP, RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL)
                .orElse(DEFAULT_PROVIDER);
        if (provider == null || provider.isBlank() || !SUPPORTED_PROVIDERS.contains(provider.trim().toLowerCase())) {
            return DEFAULT_PROVIDER;
        }
        return provider.trim().toLowerCase();
    }
}