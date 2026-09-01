package com.chua.starter.bundle.service;

import com.chua.common.support.osgi.OsgiBundle;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.starter.bundle.properties.BundleProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * OSGi Bundle 管理器服务。
 *
 * <p>提供基于 Maven 坐标的 Bundle 安装、卸载、版本升级和状态查询功能。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
public class BundleManagerService {

    /** Maven 坐标正则：groupId:artifactId:version */
    private static final Pattern MAVEN_COORDS_PATTERN = Pattern.compile(
            "^([^:]+):([^:]+):(.+)$");

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private final OsgiLauncher launcher;
    private final BundleProperties properties;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> installedBundles = new java.util.concurrent.ConcurrentHashMap<>();

    public BundleManagerService(OsgiLauncher launcher, BundleProperties properties) {
        this.launcher = launcher;
        this.properties = properties;
    }

    // ==================== 安装 ====================

    /**
     * 安装指定 Maven 坐标的 bundle。
     *
     * <p>若已安装且新坐标版本更高，则先卸载旧版再安装新版（高版本优先）。</p>
     *
     * @param coords Maven 坐标，格式：{@code groupId:artifactId:version}
     * @return 安装结果，包含 symbolicName 和版本号
     */
    public InstallResult install(String coords) {
        if (!launcher.isActive()) {
            return InstallResult.fail(coords, "OSGi 框架未激活");
        }
        MavenCoord coord = parseCoords(coords);
        if (coord == null) {
            return InstallResult.fail(coords, "Maven 坐标格式错误，应为 groupId:artifactId:version");
        }
        try {
            String url = toMavenUrl(coord);
            OsgiBundle existing = findExisting(coord.artifactId);
            if (existing != null) {
                String existingVersion = extractVersion(existing.getVersion());
                if (properties.isAutoUpgrade() && compareVersions(coord.version, existingVersion) > 0) {
                    log.info("[Bundle] 检测到升级: {} {} -> {}, 卸载旧版...", coord.artifactId, existingVersion, coord.version);
                    launcher.uninstallBundle(existing.getSymbolicName());
                    Thread.sleep(300);
                } else {
                    log.info("[Bundle] {} 已是最新版 ({})", coord.artifactId, existingVersion);
                    return InstallResult.success(existing.getSymbolicName(), existing.getVersion());
                }
            }
            launcher.installBundle(url);
            installedBundles.put(coord.artifactId, System.currentTimeMillis());
            log.info("[Bundle] 已安装: {} ({})", coord.artifactId, coord.version);
            return InstallResult.success(coord.artifactId, coord.version);
        } catch (Exception e) {
            log.error("[Bundle] 安装失败 [{}]: {}", coords, e.getMessage());
            return InstallResult.fail(coords, e.getMessage());
        }
    }

    /**
     * 批量安装 Maven 坐标列表。
     *
     * @param coordsList Maven 坐标列表
     * @return 每个坐标的安装结果
     */
    public List<InstallResult> installAll(List<String> coordsList) {
        List<InstallResult> results = new ArrayList<>();
        for (String coords : coordsList) {
            results.add(install(coords));
        }
        return results;
    }

    // ==================== 卸载 ====================

    /**
     * 卸载指定符号名的 bundle。
     *
     * @param symbolicName bundle 符号名
     * @return 是否成功卸载
     */
    public boolean uninstall(String symbolicName) {
        if (!launcher.isActive()) {
            return false;
        }
        try {
            launcher.uninstallBundle(symbolicName);
            installedBundles.remove(symbolicName);
            log.info("[Bundle] 已卸载: {}", symbolicName);
            return true;
        } catch (Exception e) {
            log.warn("[Bundle] 卸载失败 [{}]: {}", symbolicName, e.getMessage());
            return false;
        }
    }

    /**
     * 批量卸载多个 bundle。
     *
     * @param symbolicNames bundle 符号名列表
     * @return 每个 bundle 的卸载结果
     */
    public List<Boolean> uninstallAll(List<String> symbolicNames) {
        List<Boolean> results = new ArrayList<>();
        for (String name : symbolicNames) {
            results.add(uninstall(name));
        }
        return results;
    }

    // ==================== 查询 ====================

    /**
     * 获取所有已安装 bundle 的状态。
     */
    public List<Map<String, Object>> getStatus() {
        if (!launcher.isActive()) {
            return Collections.emptyList();
        }
        return launcher.getBundles().stream().map(b -> {
            Map<String, Object> info = new java.util.HashMap<>();
            info.put("symbolicName", b.getSymbolicName());
            info.put("state", b.getState());
            info.put("version", b.getVersion());
            info.put("managed", installedBundles.containsKey(b.getSymbolicName()));
            return info;
        }).collect(Collectors.toList());
    }

    /**
     * 检查指定符号名的 bundle 是否已安装。
     */
    public boolean isInstalled(String symbolicName) {
        return launcher.getBundles().stream()
                .anyMatch(b -> symbolicName.equals(b.getSymbolicName()));
    }

    /**
     * 获取已管理的 bundle 符号名列表。
     */
    public List<String> getManagedBundles() {
        return new ArrayList<>(installedBundles.keySet());
    }

    // ==================== 私有辅助 ====================

    private OsgiBundle findExisting(String artifactId) {
        return launcher.getBundles().stream()
                .filter(b -> b.getSymbolicName().contains(artifactId))
                .findFirst()
                .orElse(null);
    }

    private MavenCoord parseCoords(String coords) {
        Matcher m = MAVEN_COORDS_PATTERN.matcher(coords.trim());
        if (!m.matches()) return null;
        return new MavenCoord(m.group(1), m.group(2), m.group(3));
    }

    private String toMavenUrl(MavenCoord coord) {
        String path = coord.groupId.replace('.', '/')
                + "/" + coord.artifactId + "/" + coord.version
                + "/" + coord.artifactId + "-" + coord.version + ".jar";
        return properties.getRepositoryUrl() + "/" + path;
    }

    private String extractVersion(String version) {
        if (version == null) return "0.0.0";
        Matcher m = VERSION_PATTERN.matcher(version);
        return m.find() ? m.group(0) : "0.0.0";
    }

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

    // ==================== 内部类 ====================

    public record InstallResult(String artifactId, String version, boolean success, String message) {
        public static InstallResult success(String artifactId, String version) {
            return new InstallResult(artifactId, version, true, null);
        }
        public static InstallResult fail(String artifactId, String message) {
            return new InstallResult(artifactId, null, false, message);
        }
    }

    private record MavenCoord(String groupId, String artifactId, String version) {}
}
