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
 * OSGi 自动配置。
 *
 * <p>集成 Felix OSGi 框架，启动后通过 {@link ObjectContext#registerBean(com.chua.common.support.objects.register.BeanDefinitionRegister)}
 * 将 OSGi 服务桥接到核心容器，使 OSGi bundle 注册的服务可通过 ObjectContext 实时查询。</p>
 *
 * <p>终端与远程管理：依赖 Felix Gogo shell（{@code org.apache.felix.gogo.runtime} +
 * {@code org.apache.felix.gogo.command} + {@code org.apache.felix.gogo.shell}），
 * 其内置 telnet 服务（默认 6666）。如需 SSH 远程管理，请引入
 * {@code org.apache.felix:org.apache.felix.shell.remote} 依赖并配置
 * {@code osgi.shell.ssh.*} 属性。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({FelixOsgiLauncher.class, OsgiLauncher.class})
@ConditionalOnProperty(prefix = "plugin.osgi", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OsgiProperties.class)
public class OsgiAutoConfiguration {

    /**
     * Gogo shell bundle 类标记（用于定位 jar 路径）。
     *
     * <p>顺序即安装顺序：先装 runtime（导出 org.apache.felix.service.command 包），
     * 再装 command/shell（依赖该包）。</p>
     */
    private static final String[] GOGO_MARKER_CLASSES = {
            "org.apache.felix.gogo.runtime.Closure",
            "org.apache.felix.gogo.command.Basic",
            "org.apache.felix.gogo.shell.Shell"
    };

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
            if (properties.getShell() != null && properties.getShell().isEnabled()) {
                installGogoBundles(launcher);
            }
            log.info("[OSGi] Felix 框架已启动，OSGi 集成就绪");
        } catch (Exception e) {
            log.error("[OSGi] Felix 框架启动失败", e);
        }
        return launcher;
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

        // 配置 Gogo shell 远程管理（依赖 org.apache.felix.shell.remote）
        OsgiProperties.Shell shell = properties.getShell();
        if (shell != null && shell.isEnabled()) {
            config.put("osgi.shell.telnet.host", shell.getHost());
            config.put("osgi.shell.telnet.port", String.valueOf(shell.getTelnetPort()));
            if (shell.isSshEnabled()) {
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
     * 将 Gogo shell bundle 安装并启动到 Felix 框架。
     *
     * @param launcher OSGi 启动器
     */
    private void installGogoBundles(FelixOsgiLauncher launcher) {
        if (!launcher.isActive()) {
            return;
        }
        for (String marker : GOGO_MARKER_CLASSES) {
            String jarUrl = findJarUrl(marker);
            if (jarUrl == null) {
                log.warn("[OSGi] 未找到 bundle 类: {}", marker);
                continue;
            }
            try {
                launcher.installBundle(jarUrl);
                log.info("[OSGi] Gogo bundle 已安装: {}", marker);
            } catch (Exception e) {
                log.warn("[OSGi] Gogo bundle 安装失败 [{}]: {}", marker, e.getMessage());
            }
        }
    }

    /**
     * 根据类名定位其所在 jar 的 file URL。
     *
     * @param className 类全名
     * @return jar 的 file URL，未找到返回 null
     */
    private String findJarUrl(String className) {
        try {
            Class<?> clazz = Class.forName(className);
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
     * 注册 OSGi 模块服务信息。
     *
     * @return 服务信息贡献器
     */
    @Bean
    public ServiceInfoContributor osgiServiceInfoContributor() {
        return registry -> registry.registerFlag("osgi", "OSGi", true, "OSGi 插件框架");
    }
}
