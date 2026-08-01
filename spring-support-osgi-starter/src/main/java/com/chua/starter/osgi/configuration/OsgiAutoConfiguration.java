package com.chua.starter.osgi.configuration;

import com.chua.common.support.objects.ObjectContextConfig;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.starter.osgi.context.OsgiObjectContext;
import com.chua.starter.osgi.properties.OsgiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OSGi 自动配置。
 *
 * <p>集成 Felix OSGi 框架，支持 Gogo shell 远程管理（SSH/Telnet），
 * 并暴露实时查询 OSGi 服务的 {@link OsgiObjectContext} Bean。</p>
 *
 * @author CH
 * @since 4.0.0.42
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
     * 再装 command/shell（依赖该包），最后装 remote shell。</p>
     */
    private static final String[] GOGO_MARKER_CLASSES = {
            "org.apache.felix.gogo.runtime.Closure",
            "org.apache.felix.gogo.command.Basic",
            "org.apache.felix.gogo.shell.Shell",
            "org.apache.felix.shell.remote.Activator"
    };

    /**
     * 创建 OSGi 启动器并启动框架。
     *
     * @param properties OSGi 配置
     * @return OSGi 启动器
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
     * 构建 Felix 框架配置。
     *
     * @param properties OSGi 配置
     * @return Felix 配置
     */
    private Map<String, String> buildFelixConfig(OsgiProperties properties) {
        Map<String, String> config = new HashMap<>();
        config.put("org.osgi.framework.storage", properties.getStorage());
        config.put("org.osgi.framework.startlevel.beginning", String.valueOf(properties.getStartLevel()));

        // 配置 Gogo shell 远程管理（SSH/Telnet）
        OsgiProperties.Shell shell = properties.getShell();
        if (shell != null && shell.isEnabled()) {
            config.put("osgi.shell.telnet.host", shell.getHost());
            config.put("osgi.shell.telnet.port", String.valueOf(shell.getTelnetPort()));
            config.put("osgi.shell.ssh.host", shell.getHost());
            int sshPort = shell.isSshEnabled() ? shell.getSshPort() : 0;
            config.put("osgi.shell.ssh.port", String.valueOf(sshPort));
            config.put("osgi.shell.ssh.username", shell.getUsername());
            config.put("osgi.shell.ssh.password", shell.getPassword());
            String keysFile = shell.getSshKeys();
            if (keysFile != null && !keysFile.isEmpty()) {
                config.put("osgi.shell.ssh.keys", keysFile);
            }
        }
        return config;
    }

    /**
     * 将 Gogo shell / remote shell bundle 安装并启动到 Felix 框架。
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
     * 创建 OSGi 对象上下文 Bean。
     *
     * <p>实时查询 OSGi 服务注册表（不缓存），OSGi 动态安装的 bundle 服务立即可见。</p>
     *
     * @param launcher OSGi 启动器
     * @return OSGi 对象上下文
     */
    @Bean
    public OsgiObjectContext osgiObjectContext(FelixOsgiLauncher launcher) {
        return new OsgiObjectContext(ObjectContextConfig.defaults(), launcher);
    }
}
