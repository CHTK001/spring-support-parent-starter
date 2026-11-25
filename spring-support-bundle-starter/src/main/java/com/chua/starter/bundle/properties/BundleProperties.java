package com.chua.starter.bundle.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * osgi Bundle 管理配置属性。
 *
 * <p>支持通过 Maven 坐标批量安装/卸载 OSGi bundle。</p>
 *
 * <h3>完整配置示例</h3>
 *
 * <p>配置前缀为 {@code plugin.bundle}：</p>
 *
 * <pre>{@code
 * plugin:
 *   bundle:
 *     # 总开关：默认 false，必须显式置 true，自动配置才会注册任何 Bean
 *     enabled: true
 *     # 启动时先卸载（按 Bundle 符号名），再安装（按 Maven 坐标）；单项失败仅 warn
 *     uninstall-at-startup:
 *       - org.apache.felix.gogo.runtime
 *     install-at-startup:
 *       - org.apache.felix:org.apache.felix.gogo.runtime:1.1.6
 *     # 已装同名 Bundle 时是否允许升级到更高版本
 *     auto-upgrade: true
 *     # 远程 Maven 仓库基址（末尾多余的 / 会被去掉一次）
 *     repository-url: https://repo1.maven.org/maven2
 * }</pre>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.bundle")
public class BundleProperties {

    /**
     * 是否启用 Bundle 管理器。
     *
     * <p>该开关同时被 {@code @ConditionalOnProperty(prefix = "plugin.bundle",
     * name = "enabled", havingValue = "true")} 消费：为 {@code false} 时
     * {@code BundleAutoConfiguration} 整体不生效，不注册任何 Bean。</p>
     *
     * <p>默认值：{@code false}（禁用，需显式开启）。</p>
     *
     * <pre>{@code
     * plugin:
     *   bundle:
     *     enabled: true
     * }</pre>
     */
    private boolean enabled = false;

    /**
     * 启动时自动安装的 Bundle Maven 坐标列表。
     *
     * <p>格式：{@code groupId:artifactId:version}，例如：
     * {@code org.apache.felix:org.apache.felix.gogo.runtime:1.1.6}。
     * 启动期处理器在容器单例就绪后按列表顺序逐项安装，单项失败仅记录 warn，
     * 不阻断应用启动；执行时机在 {@link #uninstallAtStartup} 之后。</p>
     *
     * <p>默认值：空列表（未配置或配置为空时，启动期不自动安装任何 Bundle）。</p>
     *
     * <pre>{@code
     * plugin:
     *   bundle:
     *     install-at-startup:
     *       - org.apache.felix:org.apache.felix.gogo.runtime:1.1.6
     * }</pre>
     */
    private List<String> installAtStartup = new ArrayList<>();

    /**
     * 启动时自动卸载的 Bundle 符号名列表。
     *
     * <p>按符号名卸载（与 {@code BundleManagerService#uninstall(String)} 同口径），
     * 空白字符串条目会被跳过；执行时机早于 {@link #installAtStartup}。</p>
     *
     * <p>默认值：空列表（未配置或配置为空时，启动期不自动卸载任何 Bundle）。</p>
     *
     * <pre>{@code
     * plugin:
     *   bundle:
     *     uninstall-at-startup:
     *       - org.apache.felix.gogo.runtime
     * }</pre>
     */
    private List<String> uninstallAtStartup = new ArrayList<>();

    /**
     * 是否允许把已安装的同名 Bundle 升级到更高版本。
     *
     * <p>安装时若已存在同 artifactId 的 Bundle：为 {@code true} 且请求版本更高时，
     * 优先走框架原生 update 完成原地升级（保留 bundle 标识与已解析依赖），
     * 失败再回退「卸载旧版 + 安装新版」；为 {@code false} 或请求版本不高于已装版本时
     * 跳过安装（返回已装版本的成功结果）。版本比较只取前三段数字，
     * 取不到三段数字时视为相等。</p>
     *
     * <p>默认值：{@code true}。</p>
     *
     * <pre>{@code
     * plugin:
     *   bundle:
     *     auto-upgrade: true
     * }</pre>
     */
    private boolean autoUpgrade = true;

    /**
     * 远程 Maven 仓库基址，用于把 Maven 坐标拼成 bundle jar 的下载 URL。
     *
     * <p>拼接规则：基址 + {@code /groupId(点换斜杠)/artifactId/version/artifactId-version.jar}；
     * 基址末尾多余的 {@code /} 会被去掉一次。仅做 URL 拼接：不支持私服认证、
     * 不解析 POM、不安装传递依赖。</p>
     *
     * <p>默认值：{@code https://repo1.maven.org/maven2}（Maven Central）。</p>
     *
     * <pre>{@code
     * plugin:
     *   bundle:
     *     repository-url: https://repo1.maven.org/maven2
     * }</pre>
     */
    private String repositoryUrl = "https://repo1.maven.org/maven2";
}
