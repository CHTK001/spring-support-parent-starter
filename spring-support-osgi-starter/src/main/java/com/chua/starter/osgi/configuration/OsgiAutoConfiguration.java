package com.chua.starter.osgi.configuration;

import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.deploy.BundleDeployWatcher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import com.chua.osgi.support.source.SourceContextHolder;
import com.chua.osgi.support.source.SourceExtensionScheduler;
import com.chua.osgi.support.source.SourceGroovyLoader;
import com.chua.osgi.support.source.SourceJavaCompiler;
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
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 *   <li>源码扩展点热重载：源码目录下的 Java/Groovy 改动自动编译并注册为 SPI 扩展点，
 *       无需先打包成 bundle jar（见 {@link SourceExtensionScheduler}）</li>
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
     * 创建源码扩展点热重载调度器。
     *
     * <p>默认即开启：把 Java/Groovy 源码放进
     * {@code plugin.osgi.source.directory}，改动会被自动编译并注册成 SPI 扩展点。
     * 不依赖 Felix 框架是否启动成功——源码扩展点走
     * {@code ServiceProvider} 扩展点表，与 bundle 机制相互独立，
     * 这样即便框架启动失败，源码里的实现仍然可用。</p>
     *
     * <p>调度器作为 {@code BeanFactoryPostProcessor} 的收尾动作启动：源码可能需要
     * 取容器 Bean（见 {@link SourceContextHolder}），而单例 Bean 可能尚未全部就绪，
     * 因此在容器 refresh 末尾统一安装上下文并启动首轮编译，此时 Bean 已可用于注入。</p>
     *
     * @param properties osgi 配置
     * @param context    Spring 容器上下文，用于把 Bean 暴露给源码
     * @return 调度器，配置未启用时返回 {@code null}
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "plugin.osgi.source", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SourceExtensionScheduler sourceExtensionScheduler(OsgiProperties properties,
                                                             ConfigurableApplicationContext context) {
        OsgiProperties.Source source = properties.getSource();
        // 开关判断不能只依赖 @ConditionalOnProperty：该注解只在 Spring 按注解创建 bean 时生效，
        // 直接调用本方法（测试、按名字取 bean、其他自动配置复用）会绕过它，
        // 导致未启用却仍创建出后台线程。
        if (source == null || !source.isEnabled()) {
            return null;
        }
        Path sourceDirectory = Paths.get(blankToDefault(source.getDirectory(), "./osgi-source"));
        Path outputDirectory = Paths.get(blankToDefault(source.getOutputDirectory(),
                sourceDirectory.toAbsolutePath().getParent() + File.separator + "osgi-source-classes"));

        var javaCompiler = new SourceJavaCompiler(sourceDirectory, outputDirectory, null);
        SourceGroovyLoader groovyLoader = source.isGroovyEnabled()
                ? new SourceGroovyLoader(sourceDirectory, source.getScriptEngine())
                : null;

        var scheduler = new SourceExtensionScheduler(javaCompiler, groovyLoader,
                source.getPollingInterval());
        // 顺序至关重要：先安装容器上下文，再启动调度器。
        // scheduler.start() 会以 0 延迟立即跑首轮编译，此时源码会去取容器 Bean
        // （典型如 Mapper）；若上下文尚未安装，扩展点字段拿到 null 被跳过，
        // 表现为「编译成功但扩展点为空」——且不会报错，极易误判。
        // 装完上下文后显式同步跑一轮，避开 start() 的异步首轮。
        SourceContextHolder.install(contextBeans(context));
        scheduler.start();
        scheduler.reloadIfChanged();
        log.info("[OSGi] 源码扩展点热重载已启用: {} -> {}（Groovy {}，间隔 {}ms）",
                sourceDirectory.toAbsolutePath(), outputDirectory.toAbsolutePath(),
                source.isGroovyEnabled() ? source.getScriptEngine() : "关闭",
                source.getPollingInterval());
        return scheduler;
    }

    /**
     * 收集容器内可安全暴露给源码的单例 Bean。
     *
     * <p>逐个 {@code getBean} 而非一次性导出，是为了跳过正在创建中的 Bean
     * （否则会触发提前初始化，把「懒加载」变成「启动即加载」）与懒加载原型。</p>
     *
     * @param context 容器上下文
     * @return Bean 名到实例的映射
     */
    private Map<String, Object> contextBeans(ConfigurableApplicationContext context) {
        Map<String, Object> beans = new HashMap<>();
        for (String name : context.getBeanDefinitionNames()) {
            try {
                if (context.isSingleton(name)) {
                    beans.put(name, context.getBean(name));
                }
            } catch (Exception e) {
                // 单个 Bean 正在创建中或初始化失败不应阻断整个上下文暴露
                log.debug("[OSGi] 跳过无法暴露给源码的 Bean {}: {}", name, e.getMessage());
            }
        }
        return beans;
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

    /**
     * 取非空默认值。
     *
     * @param value        配置值
     * @param defaultValue 默认值
     * @return 值非空白时返回该值，否则返回默认值
     */
    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
