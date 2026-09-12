package com.chua.starter.osgi.configuration;

import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.osgi.properties.OsgiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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

    /** Gogo runtime 类标记 */
    private static final String GOGO_RUNTIME_CLASS = "org.apache.felix.gogo.runtime.Closure";
    /** Gogo 命令 类标记 */
    private static final String GOGO_COMMAND_CLASS = "org.apache.felix.gogo.command.Basic";
    /** Gogo Shell 类标记 */
    private static final String GOGO_SHELL_CLASS = "org.apache.felix.gogo.shell.Shell";
    /** SSHD osgi 类标记 */
    private static final String SSHD_OSGI_CLASS = "org.apache.sshd.agent.SshAgent";

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
            /**
              * installshellbundles。
             * @param launcher launcher
             * @param properties 属性
             */
            installShellBundles(launcher, properties);
            log.info("[OSGi] Felix 框架已启动，OSGi 集成就绪");
        } catch (Exception e) {
            log.error("[OSGi] Felix 框架启动失败", e);
        }
        return launcher;
    }

    /**
      * Gogo SSH Shell 服务器：SSH 模式远程管理（bundle 安装/卸载等 Gogo 命令）。
     * 补齐 {@code osgi.shell.ssh.*} 配置无消费方的缺失装配——MINA SSHD 2.x 直连启动，
      * SSH 通道会话接线到框架内 Gogo 命令处理器（按名服务查找 + 反射调用，
     * 规避 bundle/应用类加载器类身份差异）。
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "plugin.osgi.shell", name = "enabled", havingValue = "true")
    public com.chua.starter.osgi.shell.GogoSshShellServer gogoSshShellServer(
            FelixOsgiLauncher osgiLauncher, OsgiProperties properties) {
        OsgiProperties.Shell shell = properties.getShell();
        if (!"ssh".equalsIgnoreCase(shell.getProtocol())) {
            return null;
        }
        int port = shell.getPort() > 0 ? shell.getPort() : 8122;
        com.chua.starter.osgi.shell.GogoSshShellServer server =
                new com.chua.starter.osgi.shell.GogoSshShellServer(
                        shell.getHost(), port, shell.getUsername(), shell.getPassword(),
                        osgiLauncher.getFrameworkBundleContext());
        try {
            server.start();
        } catch (java.io.IOException e) {
            /**
             * illegal状态异常。
             * @param 启动失败" 启动失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("[OSGi] SSH shell 启动失败", e);
        }
        return server;
    }

    /**
       * 创建 osgibeandefinition注册 Bean，并将其挂接到核心 对象上下文。
     *
     * @param launcher              Felix osgi 启动器
     * @param objectContextProvider 对象上下文 注入
     * @return OsgiBeanDefinitionRegister 实例
     */
    @Bean
    public OsgiBeanDefinitionRegister osgiBeanDefinitionRegister(
            FelixOsgiLauncher launcher,
            ObjectProvider<ObjectContext> objectContextProvider) {
        OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
        register.setOsgiLauncher(launcher);
        register.initialize();

        ObjectContext objectContext = objectContextProvider.getIfAvailable();
        if (objectContext != null) {
            boolean added = objectContext.registerBean(register);
            log.info("[OSGi] OsgiBeanDefinitionRegister 已注册到 ObjectContext: {}", added);
        } else {
            log.warn("[OSGi] ObjectContext 未注册，OsgiBeanDefinitionRegister 仅作为 Spring Bean 暴露，未挂接到 ObjectContext");
        }
        return register;
    }

    /**
     * 构建 Felix 框架配置。
     * @param properties 属性
     * @return 构建felix配置的结果
     */
    private Map<String, String> buildFelixConfig(OsgiProperties properties) {
        Map<String, String> config = new HashMap<>();
        config.put("org.osgi.framework.storage", properties.getStorage());
        config.put("org.osgi.framework.startlevel.beginning", String.valueOf(properties.getStartLevel()));
        config.put("org.osgi.framework.storage.clean", "onFirstInit");

 // 透传自定义 Felix 框架配置（如 org.osgi.框架.系统.包.extra 导出应用接口包）
        properties.getFrameworkProperties().forEach((k, v) -> {
            if (v != null) {
                config.put(k, String.valueOf(v));
            }
        });

        OsgiProperties.Shell shell = properties.getShell();
        if (shell != null && shell.isEnabled()) {
            String protocol = shell.getProtocol();
            int port = shell.getPort();
            String host = shell.getHost();
            if ("telnet".equalsIgnoreCase(protocol)) {
                config.put("osgi.shell.telnet.host", host);
                config.put("osgi.shell.telnet.port", String.valueOf(port > 0 ? port : 6666));
                log.info("[OSGi] Telnet shell 已启用: {}:{}" , host, port > 0 ? port : 6666);
            } else {
                // SSH 模式（默认）：通过 SSHD osgi bundle 提供 SSH 远程管理
                config.put("osgi.shell.ssh.host", host);
                config.put("osgi.shell.ssh.port", String.valueOf(port > 0 ? port : 8122));
                if (shell.getUsername() != null && !shell.getUsername().isEmpty()) {
                    config.put("osgi.shell.ssh.username", shell.getUsername());
                }
                if (shell.getPassword() != null && !shell.getPassword().isEmpty()) {
                    config.put("osgi.shell.ssh.password", shell.getPassword());
                }
                log.info("[OSGi] SSH shell 已启用: {}:{}" , host, port > 0 ? port : 8122);
            }
        }
        return config;
    }

    /**
     * 安装远程管理 bundle：优先 SSHD，回退 Gogo telnet。
     *
     * <p>安装顺序：先装 runtime → command → shell，再装 SSHD（若启用）。</p>
     * @param launcher launcher
     * @param properties 属性
     */
    private void installShellBundles(FelixOsgiLauncher launcher, OsgiProperties properties) {
        if (!launcher.isActive()) {
            return;
        }
        OsgiProperties.Shell shell = properties.getShell();
        if (shell == null || !shell.isEnabled()) {
            return;
        }

 // 始终安装 Gogo runtime/命令/Shell（SSHD 依赖 Gogo）
        /**
          * installgogobundles。
         * @param launcher launcher
         */
        installGogoBundles(launcher);

 // 根据 协议 决定启用哪种远程管理
        String protocol = shell.getProtocol();
        if ("telnet".equalsIgnoreCase(protocol)) {
            log.info("[OSGi] 使用 Telnet 远程管理（端口 {}）", shell.getPort() > 0 ? shell.getPort() : 6666);
        } else {
 // SSH 模式（默认）：检查 SSHD osgi bundle 是否在 类路径
            if (isClassAvailable(SSHD_OSGI_CLASS)) {
                /**
                  * installsshdbundles。
                 * @param launcher launcher
                 */
                installSshdBundles(launcher);
                log.info("[OSGi] 使用 SSH 远程管理（端口 {}）", shell.getPort() > 0 ? shell.getPort() : 8122);
            } else {
                log.warn("[OSGi] SSHD bundle 不在 classpath，回退到 Telnet 远程管理");
            }
        }
    }

    /**
     * 安装 Gogo bundle 套件。
     * @param launcher launcher
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
     * <p>安装顺序必须遵守依赖关系：sshd-core → sshd-osgi → sshd-sftp → sshd-scp。</p>
     * @param launcher launcher
     */
    private void installSshdBundles(FelixOsgiLauncher launcher) {
 // sshd-核心 是其他 SSHD bundle 的基础依赖，必须先安装
        /**
         * install从记号笔。
         * @param launcher launcher
         * @param "org.apache.sshd.server.SshServer" "org.Apache.sshd.服务端.ssh服务端"
         * @param "sshd-core" "sshd-核心"
         */
        installFromMarker(launcher, "org.apache.sshd.server.SshServer", "sshd-core");
        /**
         * install从记号笔。
         * @param launcher launcher
         * @param SSHD_OSGI_CLASS sshdosgi类
         * @param "sshd-osgi" "sshd-osgi"
         */
        installFromMarker(launcher, SSHD_OSGI_CLASS, "sshd-osgi");
        /**
         * install从记号笔。
         * @param launcher launcher
         * @param "org.apache.sshd.sftp.server.SftpSubsystemFactory" "org.Apache.sshd.sftp.服务端.sftpsubsystem工厂"
         * @param "sshd-sftp" "sshd-sftp"
         */
        installFromMarker(launcher, "org.apache.sshd.sftp.server.SftpSubsystemFactory", "sshd-sftp");
        /**
         * install从记号笔。
         * @param launcher launcher
         * @param "org.apache.sshd.scp.ScpSubsystemFactory" "org.Apache.sshd.scp.scpsubsystem工厂"
         * @param "sshd-scp" "sshd-scp"
         */
        installFromMarker(launcher, "org.apache.sshd.scp.ScpSubsystemFactory", "sshd-scp");
    }

    /**
      * 根据 记号笔 类定位 jar 并安装。
     * @param launcher launcher
     * @param markerClass 记号笔类
     * @param logName 日志名称
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
      * 根据类名定位其所在 jar 的 文件 URL。
     * @param className 类名称
     * @return findJarUrl的结果
     */
    private String findJarUrl(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
            if (location != null && "file".equals(location.getProtocol())) {
                return location.toExternalForm();
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            log.warn("[OSGi] 无法定位 bundle 类 {}: {}", className, e.getMessage());
        }
        return null;
    }

    /**
      * 检查类是否在 类路径 中。
     * @param className 类名称
     * @return 是否类可用的结果
     */
    private boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
      * 注册 osgi 模块服务信息。
     * @return osgi服务信息contributor的结果
     */
    @Bean
    public ServiceInfoContributor osgiServiceInfoContributor() {
        return registry -> registry.registerFlag("osgi", "OSGi", true, "OSGi 插件框架（Felix + Gogo + SSHD）");
    }
}
