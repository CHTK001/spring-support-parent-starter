package com.chua.starter.ai.support.configuration.runtime;

import com.chua.common.support.ai.agent.Agent;
import com.chua.deeplearning.support.agent.opencode.OpencodeServerProcess;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 引擎工厂：按管理员「对话路由 - Agent 引擎实现」配置创建 Agent。
 *
 * <p>默认使用 {@value #DEFAULT_PROVIDER}（本机常驻 {@code opencode serve}）；所选引擎
 * 不可用时自动降级到 {@value #FALLBACK_PROVIDER}，并把降级原因写进日志与
 * {@link #resolution()}，供设置接口与管理台展示。</p>
 *
 * <p>「不可用」的判定口径：opencode 可执行文件不存在（且自动下载失败 / 已关闭自动下载）、
 * server 启动失败、健康检查失败。判定结果在 {@link OpencodeServerProcess} 内带 60 秒冷却，
 * 避免每轮会话都重复等待一次十几秒到九十秒的启动过程。</p>
 *
 * <p>opencode 引擎统一由常驻 {@code opencode serve} 单实例 + HTTP 会话承载
 * （SPI 名 {@value #OPENCODE_SERVER_PROVIDER}），不再实例化基于 {@code opencode run}
 * 子进程的旧实现；旧配置值 {@code opencode} 作为兼容别名归一到这里。</p>
 *
 * @since 4.0.0.48
 * @author CH
 */
@Slf4j
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
     * opencode 不可用时的降级实现。
     */
    public static final String FALLBACK_PROVIDER = "agentscope";

    /**
     * 已注册的 Agent 实现白名单。
     *
     * <p>只收当前应用真实装配的 SPI：{@code agentscope} 与 {@code opencode-server}。
     * {@code langchain4j} 的 SPI 实现存在，但 spring-support-ai-starter 并未依赖
     * 对应 starter，部署包里根本没有该实现——把它列进白名单只会让选中的引擎
     * 在实例化时返回 null，所以这里不登记，界面也不再提供该选项。</p>
     */
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(FALLBACK_PROVIDER, OPENCODE_SERVER_PROVIDER);

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

    /**
     * 是否已有后台线程在执行「下载 + 启动」，避免并发重复拉进程。
     */
    private final AtomicBoolean preparingAsync = new AtomicBoolean(false);

    public AgentProviderFactory(SysAiGlobalSettingService globalSettingService) {
        this.globalSettingService = globalSettingService;
    }

    /**
     * 创建当前配置的 Agent 实例（必要时降级）。
     *
     * @return Agent 实例
     */
    public Agent createAgent() {
        RuntimeResolution resolution = resolution();
        if (resolution.degraded()) {
            log.warn("[AgentProviderFactory] {} 不可用，已降级为 {}：{}",
                    resolution.requested(), resolution.effective(), resolution.reason());
        }
        return Agent.create(resolution.effective());
    }

    /**
     * 计算当前「界面选择 → 实际生效运行时」的完整解析结果。
     *
     * <p>这是设置接口 {@code __runtime} 与管理台状态展示的唯一数据来源：
     * 所报的生效运行时与真正实例化的运行时始终一致。</p>
     *
     * @return 解析结果
     */
    public RuntimeResolution resolution() {
        String selected = configuredProvider();
        String requested = effectiveRuntime(selected);
        if (!OPENCODE_SERVER_PROVIDER.equals(requested)) {
            // 非 opencode 引擎完全不需要探测本机 CLI / 常驻服务：多探一次就是多读一次文件系统，
            // 而且会把「opencode 装没装」这种与当前选择无关的信息混进状态块。
            return new RuntimeResolution(selected, requested, requested,
                    false, null, null, null, false);
        }
        OpencodeServerProcess.Availability availability = OpencodeServerProcess.getInstance().availability();
        if (availability.serverReady()) {
            return new RuntimeResolution(selected, requested, requested,
                    false, null, null, availability, false);
        }
        boolean autoInstall = autoInstallEnabled();
        if (autoInstall) {
            prepareAsync();
        }
        String reason = availability.reason();
        if (reason == null || reason.isBlank()) {
            reason = availability.installed()
                    ? "opencode 服务尚未就绪（正在启动）"
                    : "opencode 未安装（正在自动下载）";
        }
        if (!autoInstall && !availability.installed()) {
            reason = "opencode 未安装，且已关闭自动下载";
        }
        return new RuntimeResolution(selected, requested, FALLBACK_PROVIDER,
                true, FALLBACK_PROVIDER, reason, availability, preparingAsync.get());
    }

    /**
     * 已配置的引擎值（未归一化，可能是历史值）。
     *
     * @return 配置值；未配置返回 {@code null}
     */
    public String configuredProvider() {
        return globalSettingService
                .getValue(RuntimeGlobalSettingProvider.GROUP, RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL)
                .orElse(null);
    }

    /**
     * 当前配置真正生效的运行时 SPI（含降级）。
     *
     * @return 实际运行时 SPI 名称
     */
    public String resolveProvider() {
        return resolution().effective();
    }

    /**
     * 是否允许自动下载并启动 opencode（默认允许）。
     *
     * @return true 表示允许
     */
    public boolean autoInstallEnabled() {
        return globalSettingService.getBoolean(RuntimeGlobalSettingProvider.GROUP,
                RuntimeGlobalSettingProvider.FIELD_OPENCODE_AUTO_INSTALL, true);
    }

    /**
     * 后台触发一次「下载 + 启动」，立即返回。
     *
     * @return true 表示本次真正发起了后台准备
     */
    public boolean prepareAsync() {
        if (!preparingAsync.compareAndSet(false, true)) {
            return false;
        }
        Thread.ofVirtual().name("opencode-prepare").start(() -> {
            try {
                // 必须走 tryEnsureStarted：它带失败冷却，避免「每读一次设置 / 每开一轮会话」
                // 都重新下载并等满启动超时。只有管理员显式点「立即下载并启动」才会 clearFailure()。
                OpencodeServerProcess.ServerInfo info = OpencodeServerProcess.getInstance()
                        .tryEnsureStarted(autoInstallEnabled());
                if (info == null) {
                    log.warn("[AgentProviderFactory] opencode 尚未就绪，会话继续降级为 {}", FALLBACK_PROVIDER);
                } else {
                    log.info("[AgentProviderFactory] opencode 运行时已就绪: {}", info.baseUrl());
                }
            } catch (Exception e) {
                log.warn("[AgentProviderFactory] opencode 自动下载/启动失败，会话将降级为 {}：{}",
                        FALLBACK_PROVIDER, e.getMessage());
            } finally {
                preparingAsync.set(false);
            }
        });
        return true;
    }

    /**
     * 启动期预热：配置指向 opencode 且允许自动下载时，提前在后台准备，别等第一条会话才开始拉。
     *
     * <p>只在配置里确实存在 {@code agent_impl} 记录时预热：设置表尚未初始化（例如单元测试、
     * 首次启动的极早期）时不做任何联网动作。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void warmUpOpencode() {
        if (configuredProvider() == null) {
            return;
        }
        if (!OPENCODE_SERVER_PROVIDER.equals(effectiveRuntime(configuredProvider()))) {
            return;
        }
        if (!autoInstallEnabled()) {
            return;
        }
        prepareAsync();
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
     * 计算某个「界面/接口选择值」配置的运行时 SPI。
     *
     * <p>这是唯一的归一化入口：先按白名单校验（空值或未注册值回退默认），再把历史引擎名
     * 映射到实际 SPI。注意它只做「配置归一」，不含可用性降级——是否真的能实例化由
     * {@link #resolution()} 判断。</p>
     *
     * @param selectedProvider 界面或接口给出的引擎选择值
     * @return 归一化后的运行时 SPI 名称
     */
    public static String effectiveRuntime(String selectedProvider) {
        if (selectedProvider == null || selectedProvider.isBlank()) {
            return DEFAULT_PROVIDER;
        }
        String runtime = runtimeProvider(selectedProvider.trim().toLowerCase(Locale.ROOT));
        return SUPPORTED_PROVIDERS.contains(runtime) ? runtime : DEFAULT_PROVIDER;
    }

    /**
     * 引擎解析结果。
     *
     * @param selected     配置里读到的原始值（可能是历史别名）
     * @param requested    归一化后的目标运行时 SPI
     * @param effective    实际实例化的运行时 SPI（不可用时为降级实现）
     * @param degraded     是否发生降级
     * @param fallback     降级到的实现；未降级为 {@code null}
     * @param reason       降级原因；未降级为 {@code null}
     * @param opencode     opencode 运行时可用性快照
     * @param preparing    是否有后台准备任务在执行
     */
    public record RuntimeResolution(String selected, String requested, String effective,
                                    boolean degraded, String fallback, String reason,
                                    OpencodeServerProcess.Availability opencode,
                                    boolean preparing) {
    }
}
