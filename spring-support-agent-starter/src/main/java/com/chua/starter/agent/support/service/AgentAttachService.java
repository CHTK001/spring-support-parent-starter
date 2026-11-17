package com.chua.starter.agent.support.service;

import com.chua.runtime.starter.RuntimeBoot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 自注入服务 — 引入依赖即向<b>当前 JVM</b> 注入 Runtime Agent。
 *
 * <p>实现 {@link SmartInitializingSingleton}：全部单例初始化完成后触发注入，
 * 异步模式（默认）在虚拟线程中执行，agentmain 的字节码 retransform 不阻塞启动。</p>
 *
 * <p>agent jar 定位统一由 {@link RuntimeBoot} 内建探测完成
 * （classpath 类扫描 → Spring Boot fat jar {@code BOOT-INF/lib/} 提取 → ProtectionDomain 回退），
 * 本模块不再自实现定位逻辑。</p>
 *
 * <p>注入失败仅告警、不抛异常，保证宿主应用正常启动；通过 {@link #isAgentActive()}
 * 查询注入状态，业务方可据此降级。</p>
 *
 * <p>运行时增强：{@link #reattach()} 允许注入失败后或配置变更后重试，
 * {@link #getStatus()} 为 APM Agent 中心提供状态快照。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AgentAttachService implements SmartInitializingSingleton {

    /**
     * 是否已尝试注入（幂等保护）。
     */
    private final AtomicBoolean attachAttempted = new AtomicBoolean(false);

    /**
     * 注入是否成功。
     */
    private volatile boolean agentActive = false;

    /**
     * 用户侧是否允许注入/采集（可被 HTTP 端点运行时关闭）。
     */
    private volatile boolean agentEnabled = true;

    /**
     * 最近一次注入尝试时间（epoch ms；0 表示尚未尝试）。
     */
    private final AtomicLong lastAttachTime = new AtomicLong(0L);

    /**
     * 注入尝试次数。
     */
    private final AtomicInteger attachAttempts = new AtomicInteger(0);

    /**
     * 最近一次注入失败信息（无失败为 null）。
     */
    private volatile String lastAttachError = null;

    /**
     * 配置项。
     */
    private final com.chua.starter.agent.support.config.AgentAttachProperties properties;

    /**
     * 创建自注入服务。
     *
     * @param properties 配置项
     */
    public AgentAttachService(com.chua.starter.agent.support.config.AgentAttachProperties properties) {
        this.properties = properties;
    }

    /**
     * 全部单例就绪后触发自注入（幂等；失败不阻断应用）。
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.isEnabled()) {
            log.info("[AgentAttach] 自注入被配置禁用 (plugin.agent.enabled=false)");
            return;
        }
        if (properties.isAsync()) {
            Thread.ofVirtual().name("agent-attach").start(this::attach);
        } else {
            attach();
        }
    }

    /**
     * 执行自注入（幂等）。
     */
    public void attach() {
        if (!attachAttempted.compareAndSet(false, true)) {
            return;
        }
        doAttach();
    }

    /**
     * 运行时重新注入：允许 attach 失败后、配置变更后、或应用停用后重新启用时重试。
     *
     * <p>与 {@link #attach()} 不同，该入口不限制调用次数，仅保证注入成功后不再重复执行。</p>
     */
    public void reattach() {
        if (agentActive) {
            log.info("[AgentAttach] Agent 已注入，跳过重复注入");
            return;
        }
        doAttach();
    }

    /**
     * 实际执行 attach。
     */
    private void doAttach() {
        if (!agentEnabled || !properties.isEnabled()) {
            log.info("[AgentAttach] 自注入被禁用，跳过注入");
            return;
        }
        attachAttempts.incrementAndGet();
        lastAttachTime.set(System.currentTimeMillis());
        try {
            String options = properties.getOptions();
            if (!StringUtils.hasText(options)) {
                options = "port=" + properties.getShellPort();
            }
            RuntimeBoot.create()
                    .withAgentOptions(options)
                    .attachSelf();
            agentActive = true;
            lastAttachError = null;
            log.info("[AgentAttach] Agent 自注入完成，options={}", options);
        } catch (Throwable t) {
            agentActive = false;
            lastAttachError = t.getMessage();
            log.warn("[AgentAttach] Agent 自注入失败（运行时观测将不可用）: {}", t.getMessage());
        }
    }

    /**
     * Agent 是否注入成功。
     *
     * @return 是否注入成功
     */
    public boolean isAgentActive() {
        return agentActive;
    }

    /**
     * 自注入是否启用。
     *
     * @return 是否启用的结果
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 获取 Shell 端口。
     *
     * @return Shell 端口
     */
    public int getShellPort() {
        return properties.getShellPort();
    }

    /**
     * 运行时是否允许采集（HTTP 端点可切换）。
     *
     * @return 是否允许采集
     */
    public boolean isAgentEnabled() {
        return agentEnabled;
    }

    /**
     * 运行时启用/停用采集。停用仅关闭后续注入与状态上报，
     * 已注入的 Instrumentation 实例不会强制卸载。
     *
     * @param enabled 是否允许采集
     */
    public void setAgentEnabled(boolean enabled) {
        this.agentEnabled = enabled;
        if (enabled) {
            reattach();
        } else {
            agentActive = false;
        }
        log.info("[AgentAttach] Agent 采集已{}", enabled ? "启用" : "停用");
    }

    /**
     * 构建 APM Agent 中心所需的状态快照。
     *
     * @return 状态 Map
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("agentActive", agentActive);
        status.put("agentEnabled", agentEnabled);
        status.put("version", "4.0.0.42");
        long attachTs = lastAttachTime.get();
        status.put("startedAt", attachTs == 0L ? null : attachTs);
        status.put("lastHeartbeat", System.currentTimeMillis());
        status.put("heartbeatLostMs", 0L);
        status.put("attachAttempts", attachAttempts.get());
        status.put("lastAttachError", lastAttachError);
        return status;
    }

}
