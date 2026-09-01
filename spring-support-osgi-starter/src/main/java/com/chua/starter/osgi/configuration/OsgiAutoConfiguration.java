package com.chua.starter.osgi.configuration;

import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.osgi.properties.OsgiProperties;
import com.chua.starter.osgi.properties.OsgiShellBundleProperties;
import com.chua.starter.osgi.service.OsgiShellBundleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.Map;

/**
 * OSGi 自动配置。
 *
 * <p>集成 Apache Felix OSGi 框架，提供以下核心能力：</p>
 * <ul>
 *   <li>启动 Felix 框架并管理 bundle 生命周期</li>
 *   <li>自动安装 Gogo shell bundle（runtime/command/shell），启用 telnet 远程管理</li>
 *   <li>自动安装 Apache SSHD OSGi bundle，启用 SSH 远程管理</li>
 *   <li>通过 {@link OsgiShellBundleManager} 支持 bundle 的在线安装/卸载/版本升级</li>
 *   <li>将 OSGi 服务桥接到 Spring {@link ObjectContext}，跨容器查询 OSGi 注册的服务</li>
 * </ul>
 *
 * <p>远程管理端口：</p>
 * <ul>
 *   <li>Telnet：{@code plugin.osgi.shell.telnet-port}（默认 6666）</li>
 *   <li>SSH (Gogo)：{@code plugin.osgi.shell.ssh-port}（默认 0，关闭）</li>
 *   <li>SSH (SSHD bundle)：{@code plugin.osgi.shell-bundles.sshd-port}（默认 8122）</li>
 * </ul>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({FelixOsgiLauncher.class, OsgiLauncher.class})
@ConditionalOnProperty(prefix = "plugin.osgi", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({OsgiProperties.class, OsgiShellBundleProperties.class})
public class OsgiAutoConfiguration {

    /**
     * 创建并启动 Felix OSGi 框架。
     *
     * @param properties OSGi 配置
     * @return 已启动的 Felix 启动器
     */
    @Bean(destroyMethod = "stop")
    public FelixOsgiLauncher osgiLauncher(OsgiProperties properties) {
        FelixOsgiLauncher launcher = new FelixOsgiLauncher();
        try {
            launcher.start(buildFelixConfig(properties));
            log.info("[OSGi] Felix 框架已启动，OSGi 集成就绪");
        } catch (Exception e) {
            log.error("[OSGi] Felix 框架启动失败", e);
        }
        return launcher;
    }

    /**
     * 创建 OSGi Shell Bundle 管理器，负责 Gogo/SSHD bundle 的自动安装和版本管理。
     *
     * <p>高版本优先策略：若已安装 bundle 版本低于 Maven 仓库中的版本，
     * 则自动卸载旧版并安装新版。</p>
     *
     * @param launcher     Felix OSGi 启动器
     * @param bundleProps  Bundle 安装配置
     * @return Shell Bundle 管理器
     */
    @Bean
    public OsgiShellBundleManager osgiShellBundleManager(
            FelixOsgiLauncher launcher,
            OsgiShellBundleProperties bundleProps) {
        OsgiShellBundleManager manager = new OsgiShellBundleManager(launcher, bundleProps);
        manager.afterPropertiesSet();
        log.info("[OSGi] Shell Bundle 管理器已初始化，Gogo={}, SSHD={}",
                bundleProps.isInstallGogo(), bundleProps.isInstallSshd());
        return manager;
    }

    /**
     * 创建 OsgiBeanDefinitionRegister Bean，并将其挂接到核心 ObjectContext。
     *
     * <p>OsgiBeanDefinitionRegister 是 {@link com.chua.common.support.spi.annotations.SpiIgnore}，
     * 不会通过 SPI 自动加入注册中心。本方法以编程方式将其注入到 ObjectContext，
     * 替代之前 {@code OsgiLauncherHolder} 静态单例持有 launcher 的做法。</p>
     *
     * @param launcher              Felix OSGi 启动器
     * @param objectContextProvider ObjectContext 注入（由 ObjectContextAutoConfiguration 创建）
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
     *
     * @param properties OSGi 配置
     * @return Felix 配置
     */
    private Map<String, String> buildFelixConfig(OsgiProperties properties) {
        Map<String, String> config = new HashMap<>();
        config.put("org.osgi.framework.storage", properties.getStorage());
        config.put("org.osgi.framework.startlevel.beginning", String.valueOf(properties.getStartLevel()));
        config.put("org.osgi.framework.storage.clean", "onFirstInit");

        // 配置 Gogo shell 远程管理
        OsgiProperties.Shell shell = properties.getShell();
        if (shell != null && shell.isEnabled()) {
            config.put("osgi.shell.telnet.host", shell.getHost());
            config.put("osgi.shell.telnet.port", String.valueOf(shell.getTelnetPort()));
            if (shell.isSshEnabled() && shell.getSshPort() > 0) {
                config.put("osgi.shell.ssh.host", shell.getHost());
                config.put("osgi.shell.ssh.port", String.valueOf(shell.getSshPort()));
                config.put("osgi.shell.ssh.username", shell.getUsername());
                config.put("osgi.shell.ssh.password", shell.getPassword());
                String keysFile = shell.getSshKeys();
                if (keysFile != null && !keysFile.isEmpty()) {
                    config.put("osgi.shell.ssh.keys", keysFile);
                }
            }
        }
        return config;
    }

    /**
     * 注册 OSGi 模块服务信息。
     *
     * @return 服务信息贡献器
     */
    @Bean
    public ServiceInfoContributor osgiServiceInfoContributor() {
        return registry -> registry.registerFlag("osgi", "OSGi", true, "OSGi 插件框架（Felix + Gogo + SSHD）");
    }
}
