package com.chua.starter.ai.support.configuration.runtime;

import com.chua.common.support.ai.agent.Agent;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Agent 引擎工厂：按管理员「对话路由 - Agent 引擎实现」配置创建 Agent。
 *
 * <p>默认使用 {@value #DEFAULT_PROVIDER}；配置了未知实现时回退默认值，避免管理员误填
 * 一个未注册 SPI 后整个 AI 会话链路直接不可用。</p>
 *
 * <p>opencode 引擎统一由常驻 {@code opencode serve} 单实例 + HTTP 会话承载
 * （SPI 名 {@value #OPENCODE_SERVER_PROVIDER}），不再实例化基于 {@code opencode run}
 * 子进程的旧实现；旧配置值 {@code opencode} 作为兼容别名归一到这里。</p>
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
     * 基于 opencode 本地服务的 Agent 实现（单 server + HTTP + SSE）。
     */
    public static final String OPENCODE_SERVER_PROVIDER = "opencode-server";

    /**
     * 已注册的 Agent 实现白名单。
     */
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("agentscope", OPENCODE_SERVER_PROVIDER);

    /**
     * 历史引擎名到实际运行时 SPI 的映射。
     *
     * <p>{@code opencode} 早期指向 {@code opencode run} CLI 子进程实现：该实现是自治叶子
     * 执行器，无法复用 MCP / 技能 / 记忆 / 规划等工具链。自本版本起，opencode 能力统一由
     * 常驻 {@code opencode serve} 单实例 + HTTP 会话提供，因此把历史配置值与界面旧值都
     * 归一为 {@value #OPENCODE_SERVER_PROVIDER}，避免同一引擎出现两套能力口径。</p>
     */
    private static final Map<String, String> LEGACY_PROVIDER_ALIASES =
            Map.of("opencode", OPENCODE_SERVER_PROVIDER);

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
     * 把历史引擎名映射为实际要实例化的运行时 SPI。
     *
     * @param selectedProvider 界面或接口给出的引擎选择值（已归一化的小写值）
     * @return 实际运行时 SPI 名称
     */
    public static String runtimeProvider(String selectedProvider) {
        if (selectedProvider == null) {
            return null;
        }
        return LEGACY_PROVIDER_ALIASES.getOrDefault(selectedProvider, selectedProvider);
    }

    /**
     * 计算某个「界面/接口选择值」真正生效的运行时 SPI。
     *
     * <p>这是唯一的归一化入口：先按白名单校验（空值或未注册值回退默认），再把历史引擎名
     * 映射到实际 SPI。{@link #createAgent()} 与设置接口回带的 {@code __runtime.effective}
     * 都走这里，保证「所报的生效运行时」与「真正实例化的运行时」永不漂移——即使有人绕过
     * 界面、直接往 {@code agent_impl} 写入未注册的引擎名，两者也始终一致。</p>
     *
     * @param selectedProvider 界面或接口给出的引擎选择值
     * @return 实际运行时 SPI 名称
     */
    public static String effectiveRuntime(String selectedProvider) {
        if (selectedProvider == null || selectedProvider.isBlank()) {
            return DEFAULT_PROVIDER;
        }
        String runtime = runtimeProvider(selectedProvider.trim().toLowerCase(Locale.ROOT));
        return SUPPORTED_PROVIDERS.contains(runtime) ? runtime : DEFAULT_PROVIDER;
    }

    /**
     * 解析当前实际生效的 Agent 运行时 SPI。
     *
     * @return 实际运行时 SPI 名称
     */
    public String resolveProvider() {
        String provider = globalSettingService
                .getValue(RuntimeGlobalSettingProvider.GROUP, RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL)
                .orElse(DEFAULT_PROVIDER);
        return effectiveRuntime(provider);
    }
}
