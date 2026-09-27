package com.chua.starter.osgi.configuration;

import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.deploy.BundleDeployWatcher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.osgi.controller.OsgiMonitorController;
import com.chua.starter.osgi.properties.OsgiProperties;
import com.chua.starter.osgi.shell.GogoSshShellServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * osgi 自动配置。
 *
 * <p>集成 Apache Felix OSGi 框架，提供以下核心能力：</p>
 * <ul>
 *   <li>启动 Felix 框架并管理 bundle 生命周期</li>
 *   <li>自动安装 Gogo shell bundle（runtime/command/shell），启用远程管理</li>
 *   <li>优先使用 Apache SSHD OSGi bundle 提供 SSH 远程管理（端口默认 8122）</li>
 *   <li>Telnet 回退：当 SSHD bundle 不可用时，使用 Gogo telnet（端口 6666）</li>
 *   <li>目录热部署：周期扫描部署目录，自动安装新 bundle、卸载已移除的 bundle</li>
 *   <li>将 OSGi 服务桥接到 Spring {@link ObjectContext}，跨容器查询 OSGi 注册的服务</li>
 * </ul>
 *
 * <p>远程管理协议：通过 {@code plugin.osgi.shell.protocol} 指定，
 * 支持 {@code ssh}（默认）或 {@code telnet}，同一实例只启用一种。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({FelixOsgiLauncher.class, OsgiLauncher.class})
@ConditionalOnProperty(prefix = "plugin.osgi", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OsgiProperties.class)
public class OsgiAutoConfiguration {

    /**
     * Gogo runtime 类标记。
     */
    private static final String GOGO_RUNTIME_CLASS = "org.apache.felix.gogo.runtime.Closure";

    /**
     * Gogo 命令类标记。
     */
    private static final String GOGO_COMMAND_CLASS = "org.apache.felix.gogo.command.Basic";

    /**
     * Gogo Shell 类标记。
     */
    private static final String GOGO_SHELL_CLASS = "org.apache.felix.gogo.shell.Shell";

    /**
     * SSHD osgi 类标记。
     */
    private static final String SSHD_OSGI_CLASS = "org.apache.sshd.agent.SshAgent";

    /**
     * SSH 默认端口。
     */
    private static final int DEFAULT_SSH_PORT = 8122;

    /**
     * Telnet 默认端口。
     */
    private static final int DEFAULT_TELNET_PORT = 6666;

    /**
     * 创建并启动 Felix osgi 框架。
     *
     * @param properties osgi 配置
     * @return 已启动的 Felix 启动器
     */
    @Bean(destroyMethod = "stop")
    public FelixOsgiLauncher osgiLauncher(OsgiProperties properties) {
        FelixOsgiLauncher launcher = new FelixOsgiLauncher();
        try {
            launcher.start(buildFelixConfig(properties));
            installShellBundles(launcher, properties);
            log.info("[OSGi] Felix 框架已启动，OSGi 集成就绪");
        } catch (Exception e) {
            log.error("[OSGi] Felix 框架启动失败", e);
        }
        return launcher;
    }

    /**
     * 创建目录热部署监听器。
     *
     * @param osgiLauncher Felix osgi 启动器
     * @param properties   osgi 配置
     * @return 目录热部署监听器，配置未启用时返回 {@code null}
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "plugin.osgi.deploy", name = "enabled", havingValue = "true")
    public BundleDeployWatcher bundleDeployWatcher(FelixOsgiLauncher osgiLauncher, OsgiProperties properties) {
        OsgiProperties.Deploy deploy = properties.getDeploy();
        if (deploy == null) {
            return null;
        }
        BundleDeployWatcher watcher = new BundleDeployWatcher(osgiLauncher, deploy.getDirectory(),
                deploy.getPollingInterval(), deploy.isUninstallOnRemoved(), deploy.isUpdateOnChanged());
        watcher.start();
        return watcher;
    }

    /**
     * Gogo SSH Shell 服务器：SSH 模式远程管理（bundle 安装/卸载等 Gogo 命令）。
     *
     * <p>补齐 {@code osgi.shell.ssh.*} 配置无消费方的缺失装配——MINA SSHD 2.x 直连启动，
     * SSH 通道会话接线到框架内 Gogo 命令处理器（按名服务查找 + 反射调用，
     * 规避 bundle/应用类加载器类身份差异）。</p>
     *
     * @param osgiLauncher Felix osgi 启动器
     * @param properties   osgi 配置
     * @return SSH shell 服务端，协议非 ssh 时返回 {@code null}
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "plugin.osgi.shell", name = "enabled", havingValue = "true")
    public GogoSshShellServer gogoSshShellServer(FelixOsgiLauncher osgiLauncher, OsgiProperties properties) {
        OsgiProperties.Shell shell = properties.getShell();
        if (shell == null || !"ssh".equalsIgnoreCase(shell.getProtocol())) {
            return null;
        }
        int port = shell.getPort() > 0 ? shell.getPort() : DEFAULT_SSH_PORT;
        GogoSshShellServer server = new GogoSshShellServer(
                shell.getHost(), port, shell.getUsername(), shell.getPassword(),
                osgiLauncher.getFrameworkBundleContext(), shell.getHostKey(),
                shell.isAllowEmptyCredential());
        try {
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException("[OSGi] SSH shell 启动失败", e);
        }
        return server;
    }

    /**
     * 创建 osgi bean 定义注册 Bean，并将其挂接到核心对象上下文。
     * <p>
     * 同时把注册器挂为框架生命周期监听器：bundle 的安装、启动、停止、升级、卸载
     * 都会推进其内部快照版本，使桥接产出的扩展对象随之切换——
     * 这是「OSGi 侧变化 → 容器侧扩展对象变化」的传导链路。
     * </p>
     *
     * @param launcher              Felix osgi 启动器
     * @param objectContextProvider 对象上下文注入
     * @return OsgiBeanDefinitionRegister 实例
     */
    @Bean
    public OsgiBeanDefinitionRegister osgiBeanDefinitionRegister(
            FelixOsgiLauncher launcher,
            OsgiProperties properties,
            ObjectProvider<ObjectContext> objectContextProvider) {
        OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
        register.setOsgiLauncher(launcher);
        OsgiProperties.Bridge bridge = properties.getBridge();
        if (bridge != null) {
            register.setAcceptanceEnabled(bridge.isAcceptanceEnabled());
            register.setInvalidateOnBundleLifecycle(bridge.isInvalidateOnBundleLifecycle());
        }
        register.initialize();
        if (bridge == null || bridge.isInvalidateOnBundleLifecycle()) {
            launcher.addListener(register);
        }

        ObjectContext objectContext = objectContextProvider.getIfAvailable();
        if (objectContext != null) {
            boolean added = objectContext.registerBean(register);
            log.info("[OSGi] OsgiBeanDefinitionRegister 已注册到 ObjectContext: {}（验收 {}，生命周期联动 {}）",
                    added, register.isAcceptanceEnabled(), register.isInvalidateOnBundleLifecycle());
        } else {
            log.warn("[OSGi] ObjectContext 未注册，OsgiBeanDefinitionRegister 仅作为 Spring Bean 暴露，未挂接到 ObjectContext");
        }
        return register;
    }

    /**
     * 注册 osgi 模块服务信息。
     *
     * @return osgi 服务信息 contributor
     */
    @Bean
    public ServiceInfoContributor osgiServiceInfoContributor() {
        return registry -> registry.registerFlag("osgi", "OSGi", true, "OSGi 插件框架（Felix + Gogo + SSHD）");
    }

    /**
     * 创建 osgi 运行时只读观测端点。
     *
     * <p>仅在显式开启 {@code plugin.osgi.endpoint.enabled} 且 classpath 中存在
     * spring-web 的 Web 应用环境下装配，避免在非 Web 场景引入 web 依赖。</p>
     *
     * @param osgiLauncher Felix osgi 启动器
     * @return 观测端点
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
    @ConditionalOnProperty(prefix = "plugin.osgi.endpoint", name = "enabled", havingValue = "true")
    public OsgiMonitorController osgiMonitorController(FelixOsgiLauncher osgiLauncher,
                                                       ObjectProvider<OsgiBeanDefinitionRegister> registerProvider) {
        log.info("[OSGi] 运行时观测端点已启用: /osgi/stats, /osgi/bundles, /osgi/services, /osgi/snapshot");
        return new OsgiMonitorController(osgiLauncher, registerProvider.getIfAvailable());
    }

    /**
     * 构建 Felix 框架配置。
     *
     * @param properties osgi 配置
     * @return Felix 框架配置
     */
    private Map<String, String> buildFelixConfig(OsgiProperties properties) {
        Map<String, String> config = new HashMap<>();
        config.put("org.osgi.framework.storage", properties.getStorage());
        config.put("org.osgi.framework.startlevel.beginning", String.valueOf(properties.getStartLevel()));
        config.put("org.osgi.framework.storage.clean",
                properties.getStorageClean() == null || properties.getStorageClean().isBlank()
                        ? "onFirstInit" : properties.getStorageClean());

        // 禁止 Gogo shell 在退出时停止框架。
        // 框架内安装的 org.apache.felix.gogo.shell 会读取 JVM 标准输入作为交互控制台，
        // 一旦标准输入到达 EOF（典型场景：Linux 下以 systemd/docker 服务方式启动，
        // stdin 指向 /dev/null；或 CI/SSH 非交互式会话），gosh 会执行
        // "stopping shell and framework" 并把整个 OSGi 框架停掉——
        // 表现为框架刚启动即失效、后续 bundle 操作抛 Invalid BundleContext。
        // 此处交由 gosh.args 传入 --noshutdown：会话结束只停 shell，不停框架。
        // 该默认值先于用户自定义属性写入，仍可通过框架属性覆盖。
        config.put("gosh.args", "--noshutdown");

        // 透传自定义 Felix 框架配置（如 org.osgi.framework.system.packages.extra 导出应用接口包）
        properties.getFrameworkProperties().forEach((key, value) -> {
            if (value != null) {
                config.put(key, String.valueOf(value));
            }
        });

        OsgiProperties.Shell shell = properties.getShell();
        if (shell != null && shell.isEnabled()) {
            String host = shell.getHost();
            if ("telnet".equalsIgnoreCase(shell.getProtocol())) {
                config.put("osgi.shell.telnet.host", host);
                config.put("osgi.shell.telnet.port", String.valueOf(
                        shell.getPort() > 0 ? shell.getPort() : DEFAULT_TELNET_PORT));
                log.info("[OSGi] Telnet shell 已启用: {}:{}", host,
                        shell.getPort() > 0 ? shell.getPort() : DEFAULT_TELNET_PORT);
            } else {
                // SSH 模式（默认）：通过 SSHD osgi bundle 提供 SSH 远程管理
                config.put("osgi.shell.ssh.host", host);
                config.put("osgi.shell.ssh.port", String.valueOf(
                        shell.getPort() > 0 ? shell.getPort() : DEFAULT_SSH_PORT));
                if (shell.getUsername() != null && !shell.getUsername().isEmpty()) {
                    config.put("osgi.shell.ssh.username", shell.getUsername());
                }
                if (shell.getPassword() != null && !shell.getPassword().isEmpty()) {
                    config.put("osgi.shell.ssh.password", shell.getPassword());
                }
                log.info("[OSGi] SSH shell 已启用: {}:{}", host,
                        shell.getPort() > 0 ? shell.getPort() : DEFAULT_SSH_PORT);
            }
        }
        return config;
    }

    /**
     * 安装远程管理 bundle：优先 SSHD，回退 Gogo telnet。
     *
     * <p>安装顺序：先装 runtime → command → shell，再装 SSHD（若启用）。</p>
     *
     * @param launcher   Felix osgi 启动器
     * @param properties osgi 配置
     */
    private void installShellBundles(FelixOsgiLauncher launcher, OsgiProperties properties) {
        if (!launcher.isActive()) {
            return;
        }
        OsgiProperties.Shell shell = properties.getShell();
        if (shell == null || !shell.isEnabled()) {
            return;
        }

        launcher.setAutoStartInstalledBundles(properties.isAutoStartInstalledBundles());

        // 始终安装 Gogo runtime/命令/Shell（SSHD 依赖 Gogo）
        installGogoBundles(launcher);

        // 根据协议决定启用哪种远程管理
        if ("telnet".equalsIgnoreCase(shell.getProtocol())) {
            log.info("[OSGi] 使用 Telnet 远程管理（端口 {}）", shell.getPort() > 0 ? shell.getPort() : DEFAULT_TELNET_PORT);
            return;
        }
        // SSH 模式（默认）：SSH 由应用层 GogoSshShellServer 提供，不依赖框架内 sshd bundle
        if (!shell.isInstallFrameworkSshdBundles()) {
            log.info("[OSGi] 使用 SSH 远程管理（端口 {}），由应用层 GogoSshShellServer 提供；"
                            + "未安装框架内 sshd bundle（其 sftp/scp 在 sshd 2.13.2 下因导出包缺 version 必然解析失败）",
                    shell.getPort() > 0 ? shell.getPort() : DEFAULT_SSH_PORT);
            return;
        }
        if (isClassAvailable(SSHD_OSGI_CLASS)) {
            installSshdBundles(launcher);
            log.info("[OSGi] 已安装框架内 sshd bundle（端口 {}）", shell.getPort() > 0 ? shell.getPort() : DEFAULT_SSH_PORT);
        } else {
            log.warn("[OSGi] 已显式开启 install-framework-sshd-bundles，但 SSHD bundle 不在 classpath，已跳过；"
                    + "如需远程管理请改用 plugin.osgi.shell.protocol=telnet");
        }
    }

    /**
     * 安装 Gogo bundle 套件。
     *
     * @param launcher Felix osgi 启动器
     */
    private void installGogoBundles(FelixOsgiLauncher launcher) {
        for (String marker : new String[]{GOGO_RUNTIME_CLASS, GOGO_COMMAND_CLASS, GOGO_SHELL_CLASS}) {
            String jarUrl = findJarUrl(marker);
            if (jarUrl == null) {
                log.warn("[OSGi] 未找到 bundle 类: {}", marker);
                continue;
            }
            try {
                launcher.installBundle(jarUrl);
                log.debug("[OSGi] Gogo bundle 已安装: {}", marker);
            } catch (Exception e) {
                log.warn("[OSGi] Gogo bundle 安装失败 [{}]: {}", marker, e.getMessage());
            }
        }
    }

    /**
     * 安装 SSHD osgi bundle。
     *
     * <p><b>已知限制（实测结论）</b>：以下 bundle 在 sshd 2.13.2 下无法全部启动。
     * {@code sshd-sftp} 与 {@code sshd-scp} 声明 Import
     * {@code org.apache.sshd.client;version="[2.13.2,2.14)"}，
     * 而唯一导出该包的 {@code sshd-osgi} 对 {@code org.apache.sshd.client} 的导出不带
     * version（视作 0.0.0），版本区间无法满足 → 解析失败、停留在 {@code INSTALLED}。
     * 该结论与安装顺序无关。</p>
     *
     * <p>因此本方法默认不被调用（见
     * {@code OsgiProperties.Shell#installFrameworkSshdBundles}）。
     * SSH 远程管理由应用层 {@code GogoSshShellServer} 提供，不依赖这些 bundle。</p>
     *
     * @param launcher Felix osgi 启动器
     */
    private void installSshdBundles(FelixOsgiLauncher launcher) {
        // sshd-core 是其他 SSHD bundle 的基础依赖，先装
        installFromMarker(launcher, "org.apache.sshd.server.SshServer", "sshd-core");
        // sshd-osgi 导出 org.apache.sshd.client 等包，是 sftp/scp 的依赖来源
        installFromMarker(launcher, SSHD_OSGI_CLASS, "sshd-osgi");
        // 以下两个因上游导出版本缺失而预期解析失败（见方法注释），仍尝试安装以保持可观测
        installFromMarker(launcher, "org.apache.sshd.scp.ScpSubsystemFactory", "sshd-scp");
        installFromMarker(launcher, "org.apache.sshd.sftp.server.SftpSubsystemFactory", "sshd-sftp");
    }

    /**
     * 根据标记类定位 jar 并安装。
     *
     * @param launcher    Felix osgi 启动器
     * @param markerClass 标记类
     * @param logName     日志名称
     */
    private void installFromMarker(FelixOsgiLauncher launcher, String markerClass, String logName) {
        String jarUrl = findJarUrl(markerClass);
        if (jarUrl == null) {
            log.warn("[OSGi] 未找到 bundle: {}", logName);
            return;
        }
        try {
            launcher.installBundle(jarUrl);
            log.info("[OSGi] Bundle 已安装: {}", logName);
        } catch (Exception e) {
            log.warn("[OSGi] Bundle 安装失败 [{}]: {}", logName, e.getMessage());
        }
    }

    /**
     * 根据类名定位其所在 jar 的文件 URL。
     *
     * @param className 类名称
     * @return jar 的文件 URL，定位失败时返回 {@code null}
     */
    private String findJarUrl(String className) {
        try {
            Class<?> clazz = ReflectUtils.forName(className);
            if (clazz == null) {
                return null;
            }
            URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
            if (location != null && "file".equals(location.getProtocol())) {
                return location.toExternalForm();
            }
        } catch (Exception e) {
            log.warn("[OSGi] 无法定位 bundle 类 {}: {}", className, e.getMessage());
        }
        return null;
    }

    /**
     * 检查类是否在类路径中。
     *
     * @param className 类名称
     * @return 类可用返回 true
     */
    private boolean isClassAvailable(String className) {
        return ReflectUtils.forName(className) != null;
    }
}
