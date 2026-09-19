package com.chua.starter.agent.support.service;

import com.chua.runtime.starter.RuntimeBoot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 自注入服务 — 引入依赖即向<b>当前 JVM</b> 注入 Runtime Agent。
 *
 * <p>实现 {@link SmartInitializingSingleton}：全部单例初始化完成后触发注入，
 * 异步模式（默认）在虚拟线程中执行，agentmain 的字节码 retransform 不阻塞启动。</p>
 *
 * <p>agent jar 定位顺序：</p>
 * <ol>
 *   <li>classpath 扫描（{@code com/chua/runtime/agent/RuntimeAgent.class}）</li>
 *   <li>Spring Boot fat jar 的 {@code BOOT-INF/lib/} 内 agent jar，解压到临时目录</li>
 *   <li>回退 {@link RuntimeBoot} 内建探测（{@code resolveSelfAgentPath()}）</li>
 * </ol>
 *
 * <p>注入失败仅告警、不抛异常，保证宿主应用正常启动；通过 {@link #isAgentActive()}
 * 查询注入状态，业务方可据此降级。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AgentAttachService implements SmartInitializingSingleton {

    /** 是否已尝试注入（幂等保护）。 */
    private final AtomicBoolean attachAttempted = new AtomicBoolean(false);

    /** 注入是否成功。 */
    private volatile boolean agentActive = false;

    /** 配置项。 */
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
            log.info("[AgentAttach] 自注入被配置禁用 (runtime.agent.enabled=false)");
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
        try {
            String options = properties.getOptions();
            if (!StringUtils.hasText(options)) {
                options = "port=" + properties.getShellPort();
            }
            RuntimeBoot.create()
                    .withAgent(resolveAgentJar())
                    .withAgentOptions(options)
                    .attachSelf();
            agentActive = true;
            log.info("[AgentAttach] Agent 自注入完成，options={}", options);
        } catch (Throwable t) {
            agentActive = false;
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
     * 定位 agent jar 物理路径（classpath → fat jar BOOT-INF/lib）。
     *
     * <p>返回 {@code null} 时交由 {@link RuntimeBoot} 内建探测兜底
     * （LaunchedClassLoader 不回填 {@code java.class.path} 的场景）。</p>
     *
     * @return agent jar 路径；定位不到返回 null
     */
    private static Path resolveAgentJar() {
        String classPath = System.getProperty("java.class.path");
        if (classPath == null) {
            return null;
        }
        for (String entry : classPath.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(entry);
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try (java.util.jar.JarFile outer = new java.util.jar.JarFile(candidate.toFile())) {
                java.util.jar.JarEntry target = null;
                java.util.Enumeration<java.util.jar.JarEntry> entries = outer.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry e = entries.nextElement();
                    String name = e.getName();
                    if (name.startsWith("BOOT-INF/lib/")
                            && name.contains("utils-support-runtime-agent")
                            && name.endsWith(".jar")) {
                        target = e;
                        break;
                    }
                }
                if (target == null) {
                    continue;
                }
                java.io.File dest = Files.createTempFile("agent-attach-", ".jar").toFile();
                try (java.io.InputStream is = outer.getInputStream(target);
                     java.io.OutputStream os = java.nio.file.Files.newOutputStream(dest.toPath())) {
                    is.transferTo(os);
                }
                log.info("[AgentAttach] Agent jar 提取自 fat jar {} -> {}",
                        target.getName(), dest.getAbsolutePath());
                return dest.toPath();
            } catch (Exception e) {
                log.debug("[AgentAttach] 检查 {} 失败: {}", candidate, e.getMessage());
            }
        }
        log.info("[AgentAttach] classpath 未见 agent jar，交由 RuntimeBoot 内建探测");
        return null;
    }
}
