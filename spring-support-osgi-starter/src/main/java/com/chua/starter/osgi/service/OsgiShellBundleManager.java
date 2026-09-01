package com.chua.starter.osgi.service;

import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.common.support.osgi.OsgiBundle;
import com.chua.starter.osgi.properties.OsgiShellBundleProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * OSGi Shell Bundle 管理器。
 *
 * <p>负责在 Felix OSGi 框架中管理 SSH/Telnet Gogo shell 相关 bundle 的安装、卸载和版本升级。
 * 支持以下功能：</p>
 * <ul>
 *   <li>安装 Gogo runtime/command/shell bundle（SSH/Telnet 远程管理基础）</li>
 *   <li>安装 Apache SSHD bundle（提供 SSH 远程管理，替代 telnet）</li>
 *   <li>卸载指定 bundle</li>
 *   <li>版本比较，优先安装高版本 bundle</li>
 *   <li>查询当前已安装的 bundle 列表</li>
 * </ul>
 *
 * <p>配置属性前缀：{@code plugin.osgi.shell-bundles}。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
public class OsgiShellBundleManager implements InitializingBean, DisposableBean {

    /**
     * Gogo 核心 bundle 定义：类标记 -> Maven 坐标。
     * 安装顺序必须遵循依赖关系：runtime -> command -> shell。
     */
    private static final Map<String, MavenCoords> GOGO_BUNDLES = Map.of(
            "org.apache.felix.gogo.runtime", new MavenCoords(
                    "org.apache.felix", "org.apache.felix.gogo.runtime", "1.1.6"),
            "org.apache.felix.gogo.command", new MavenCoords(
                    "org.apache.felix", "org.apache.felix.gogo.command", "1.1.2"),
            "org.apache.felix.gogo.shell", new MavenCoords(
                    "org.apache.felix", "org.apache.felix.gogo.shell", "1.1.4")
    );

    /**
     * Apache SSHD OSGi bundle 定义。
     * 提供 SSH 远程管理替代 telnet，需配合 Gogo 使用。
     */
    private static final Map<String, MavenCoords> SSHD_BUNDLES = Map.of(
            "org.apache.sshd.osgi", new MavenCoords(
                    "org.apache.sshd", "sshd-osgi", "2.13.2"),
            "org.apache.sshd.sftp", new MavenCoords(
                    "org.apache.sshd", "sshd-sftp", "2.13.2"),
            "org.apache.sshd scp", new MavenCoords(
                    "org.apache.sshd", "sshd-scp", "2.13.2")
    );

    /** 版本号正则：提取 main version (x.y.z) */
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    /** launcher */
    private final OsgiLauncher launcher;
    /** 配置 */
    private final OsgiShellBundleProperties properties;
    /** 已安装 bundle 符号名缓存 */
    private final ConcurrentHashMap<String, Long> installedBundles = new ConcurrentHashMap<>();
    /** 是否已启动 */
    private volatile boolean started = false;

    /**
     * 构造 OsgiShellBundleManager。
     *
     * @param launcher   OSGi 启动器
     * @param properties 配置属性
     */
    public OsgiShellBundleManager(OsgiLauncher launcher, OsgiShellBundleProperties properties) {
        this.launcher = launcher;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!launcher.isActive()) {
            log.warn("[OSGi-Shell] Launcher 未激活，跳过 Bundle 管理初始化");
            return;
        }
        if (properties.isEnabled()) {
            installDefaultBundles();
            scanExistingBundles();
            started = true;
            log.info("[OSGi-Shell] 管理器已启动，已安装 bundles: {}", installedBundles.keySet());
        } else {
            log.info("[OSGi-Shell] 管理器已禁用");
        }
    }

    @Override
    public void destroy() {
        started = false;
        installedBundles.clear();
        log.info("[OSGi-Shell] 管理器已停止");
    }

    // ==================== 安装 ====================

    /**
     * 安装默认 Gogo bundle 套件。
     */
    public void installDefaultBundles() {
        if (!properties.isInstallGogo()) {
            log.debug("[OSGi-Shell] gogo 安装已禁用");
            return;
        }
        for (Map.Entry<String, MavenCoords> entry : GOGO_BUNDLES.entrySet()) {
            installOrUpgrade(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 安装 SSHD bundle 套件。
     */
    public void installSshdBundles() {
        if (!properties.isInstallSshd()) {
            log.debug("[OSGi-Shell] sshd 安装已禁用");
            return;
        }
        for (Map.Entry<String, MavenCoords> entry : SSHD_BUNDLES.entrySet()) {
            installOrUpgrade(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 根据符号名安装或升级 bundle。
     *
     * <p>若已安装且新坐标版本更高，则先卸载旧 bundle 再安装新的。</p>
     *
     * @param symbolicName bundle 符号名（用于查找已有实例）
     * @param coords       Maven 坐标
     */
    public void installOrUpgrade(String symbolicName, MavenCoords coords) {
        try {
            String mavenUrl = toMavenUrl(coords);
            OsgiBundle existing = findBundleBySymbolicName(symbolicName);

            if (existing != null) {
                String existingVersion = extractVersion(existing.getVersion());
                String newVersion = extractVersion(coords.version);
                if (compareVersions(newVersion, existingVersion) > 0) {
                    log.info("[OSGi-Shell] 检测到版本升级: {} {} -> {}, 正在卸载旧版...",
                            symbolicName, existingVersion, coords.version);
                    launcher.uninstallBundle(symbolicName);
                    Thread.sleep(500);
                    doInstallBundle(symbolicName, mavenUrl, coords);
                } else {
                    log.debug("[OSGi-Shell] {} 已是最新版 ({})", symbolicName, existingVersion);
                }
            } else {
                doInstallBundle(symbolicName, mavenUrl, coords);
            }
        } catch (Exception e) {
            log.warn("[OSGi-Shell] 安装/升级 bundle 失败 [{}]: {}", symbolicName, e.getMessage());
        }
    }

    private void doInstallBundle(String symbolicName, String mavenUrl, MavenCoords coords) {
        launcher.installBundle(mavenUrl);
        installedBundles.put(symbolicName, System.currentTimeMillis());
        log.info("[OSGi-Shell] Bundle 已安装: {} ({})", symbolicName, coords.version);
    }

    // ==================== 卸载 ====================

    /**
     * 卸载指定符号名的 bundle。
     *
     * @param symbolicName bundle 符号名
     * @return 是否成功卸载
     */
    public boolean uninstallBundle(String symbolicName) {
        try {
            launcher.uninstallBundle(symbolicName);
            installedBundles.remove(symbolicName);
            log.info("[OSGi-Shell] Bundle 已卸载: {}", symbolicName);
            return true;
        } catch (Exception e) {
            log.warn("[OSGi-Shell] 卸载 bundle 失败 [{}]: {}", symbolicName, e.getMessage());
            return false;
        }
    }

    /**
     * 批量卸载所有已安装的 Gogo bundle。
     */
    public void uninstallAllGogo() {
        for (String name : GOGO_BUNDLES.keySet()) {
            uninstallBundle(name);
        }
    }

    /**
     * 批量卸载所有已安装的 SSHD bundle。
     */
    public void uninstallAllSshd() {
        for (String name : SSHD_BUNDLES.keySet()) {
            uninstallBundle(name);
        }
    }

    // ==================== 查询 ====================

    /**
     * 获取所有已安装 bundle 的符号名列表。
     */
    public List<String> getInstalledBundleNames() {
        return new ArrayList<>(installedBundles.keySet());
    }

    /**
     * 查询是否已安装指定符号名的 bundle。
     */
    public boolean isInstalled(String symbolicName) {
        return installedBundles.containsKey(symbolicName) || findBundleBySymbolicName(symbolicName) != null;
    }

    /**
     * 获取当前所有 OSGi bundle 的状态快照。
     */
    public List<Map<String, Object>> getBundleStatus() {
        if (!launcher.isActive()) {
            return Collections.emptyList();
        }
        return launcher.getBundles().stream().map(b -> {
            Map<String, Object> info = new ConcurrentHashMap<>();
            info.put("symbolicName", b.getSymbolicName());
            info.put("state", b.getState());
            info.put("version", b.getVersion());
            info.put("installedViaManager", installedBundles.containsKey(b.getSymbolicName()));
            return info;
        }).collect(Collectors.toList());
    }

    // ==================== 私有辅助 ====================

    /**
     * 根据符号名查找 OSGi bundle。
     */
    private OsgiBundle findBundleBySymbolicName(String symbolicName) {
        return launcher.getBundles().stream()
                .filter(b -> b.getSymbolicName().contains(symbolicName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 扫描现有已安装 bundle 并更新缓存。
     */
    private void scanExistingBundles() {
        for (OsgiBundle bundle : launcher.getBundles()) {
            String name = bundle.getSymbolicName();
            for (String key : GOGO_BUNDLES.keySet()) {
                if (name.contains(key)) {
                    installedBundles.putIfAbsent(key, System.currentTimeMillis());
                }
            }
            for (String key : SSHD_BUNDLES.keySet()) {
                if (name.contains(key)) {
                    installedBundles.putIfAbsent(key, System.currentTimeMillis());
                }
            }
        }
    }

    /**
     * 将 Maven 坐标转为 Maven repository URL。
     */
    private String toMavenUrl(MavenCoords coords) {
        String base = "https://repo1.maven.org/maven2";
        String path = coords.groupId.replace('.', '/')
                + "/" + coords.artifactId + "/" + coords.version
                + "/" + coords.artifactId + "-" + coords.version + ".jar";
        return base + "/" + path;
    }

    /**
     * 从 bundle version string 中提取主要版本号。
     */
    private String extractVersion(String version) {
        if (version == null) return "0.0.0";
        Matcher m = VERSION_PATTERN.matcher(version);
        return m.find() ? m.group(0) : "0.0.0";
    }

    /**
     * 比较两个版本号，返回正数表示 v1 > v2，负数表示 v1 < v2，0 表示相等。
     */
    private int compareVersions(String v1, String v2) {
        Matcher m1 = VERSION_PATTERN.matcher(v1);
        Matcher m2 = VERSION_PATTERN.matcher(v2);
        if (!m1.find() || !m2.find()) return 0;
        for (int i = 1; i <= 3; i++) {
            int a = Integer.parseInt(m1.group(i));
            int b = Integer.parseInt(m2.group(i));
            if (a != b) return a - b;
        }
        return 0;
    }

    /**
     * 是否已启动。
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Maven 坐标内部类。
     */
    private record MavenCoords(String groupId, String artifactId, String version) {
    }
}
