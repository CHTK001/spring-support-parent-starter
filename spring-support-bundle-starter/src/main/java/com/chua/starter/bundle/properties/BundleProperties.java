package com.chua.starter.bundle.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * OSGi Bundle 管理配置属性。
 *
 * <p>支持通过 Maven 坐标批量安装/卸载 OSGi bundle，自动处理依赖顺序。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.bundle")
public class BundleProperties {

    /**
     * 是否启用 Bundle 管理器（默认禁用，需显式开启）。
     */
    private boolean enabled = false;

    /**
     * 启动时自动安装的 Bundle Maven 坐标列表。
     *
     * <p>格式：{@code groupId:artifactId:version}，例如：
     * {@code org.apache.felix:org.apache.felix.gogo.runtime:1.1.6}</p>
     */
    private List<String> installAtStartup = new ArrayList<>();

    /**
     * 启动时自动卸载的 Bundle 符号名列表。
     */
    private List<String> uninstallAtStartup = new ArrayList<>();

    /**
     * 是否允许自动升级已有 bundle（默认 true）。
     */
    private boolean autoUpgrade = true;

    /**
     * Maven 仓库 URL（用于从远程解析 bundle）。
     */
    private String repositoryUrl = "https://repo1.maven.org/maven2";
}
