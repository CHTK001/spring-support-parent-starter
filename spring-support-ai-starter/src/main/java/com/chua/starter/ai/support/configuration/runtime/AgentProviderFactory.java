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
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("agentscope", "opencode");

    /**
     * 以「兼容引擎」身份复用完整工具链运行时（{@value #DEFAULT_PROVIDER}）的实现。
     *
     * <p>opencode 的外部 CLI 是一个自治叶子执行器：SINGLE 模式、显式忽略 MCP / 技能 / 记忆 /
     * 规划，并对普通账号的 shell 工具走安全拒绝。管理员选择它时若真落到该实现，等于静默丢能力。
     * 自本版本起将 opencode 收敛为「全功能平替」：实际实例化复用完整工具链运行时，
     * 因此 MCP、技能、记忆、规划、子 Agent 编排与 shell 闸门全部与默认引擎一致。</p>
     */
    private static final Set<String> FULL_TOOLCHAIN_COMPAT_PROVIDERS = Set.of("opencode");

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
     * <p>被收敛的兼容引擎（见 {@link #FULL_TOOLCHAIN_COMPAT_PROVIDERS}）不直接实例化其降级实现，
     * 而是复用完整工具链运行时：管理员仍可在界面上选择这些引擎，但拿到的能力与默认引擎一致。</p>
     *
     * @return Agent 实例
     */
    public Agent createAgent() {
        return Agent.create(resolveProvider());
    }

    /**
     * 把界面选择的引擎映射为实际要实例化的运行时 SPI。
     *
     * @param selectedProvider 界面选择的引擎实现
     * @return 实际运行时 SPI 名称
     */
    public static String runtimeProvider(String selectedProvider) {
        if (selectedProvider != null && FULL_TOOLCHAIN_COMPAT_PROVIDERS.contains(selectedProvider)) {
            return DEFAULT_PROVIDER;
        }
        return selectedProvider;
    }

    /**
     * 计算某个「界面/接口选择值」真正生效的运行时 SPI。
     *
     * <p>这是唯一的归一化入口：先按白名单校验（空值或未注册值回退默认），再做兼容引擎收敛。
     * {@link #createAgent()} 与设置接口回带的 {@code __runtime.effective} 都走这里，
     * 保证「所报的生效运行时」与「真正实例化的运行时」永不漂移——即使有人绕过界面、
     * 直接往 {@code agent_impl} 写入未注册的引擎名，两者也始终一致。</p>
     *
     * @param selectedProvider 界面或接口给出的引擎选择值
     * @return 实际运行时 SPI 名称
     */
    public static String effectiveRuntime(String selectedProvider) {
        if (selectedProvider == null || selectedProvider.isBlank()
                || !SUPPORTED_PROVIDERS.contains(selectedProvider.trim().toLowerCase())) {
            return DEFAULT_PROVIDER;
        }
        return runtimeProvider(selectedProvider.trim().toLowerCase());
    }

    /**
     * 解析当前实际生效的 Agent 运行时 SPI。
     *
     * @return 实际运行时 SPI 名称（兼容引擎已收敛）
     */
    public String resolveProvider() {
        String provider = globalSettingService
                .getValue(RuntimeGlobalSettingProvider.GROUP, RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL)
                .orElse(DEFAULT_PROVIDER);
        return effectiveRuntime(provider);
    }
}