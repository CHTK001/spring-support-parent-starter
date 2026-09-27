package com.chua.starter.bundle.service;

import com.chua.common.support.osgi.OsgiBundle;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.starter.bundle.properties.BundleProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * osgi Bundle 管理器服务。
 *
 * <p>提供基于 Maven 坐标的 Bundle 安装、卸载、原地升级和状态查询功能。</p>
 *
 * <h3>升级策略</h3>
 * <p>
 * 检测到同符号名的高版本坐标时，优先走框架原生 update 完成原地升级
 * （保留 bundle 标识与已解析依赖）；仅当原生升级失败时才回退到
 * 「卸载旧版 + 安装新版」，且回退路径上会等待旧 bundle 真正进入已卸载状态，
 * 而非使用固定时长休眠。
 * </p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
public class BundleManagerService {

    /**
     * Maven 坐标正则：群体标识:artifactid:版本。
     */
    private static final Pattern MAVEN_COORDS_PATTERN = Pattern.compile("^([^:]+):([^:]+):(.+)$");

    /**
     * 版本号正则（仅取前三段数字，忽略限定符）。
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    /**
     * 卸载后等待 bundle 真正消失的最长时长（毫秒）。
     */
    private static final long UNINSTALL_WAIT_TIMEOUT_MS = 5000L;

    /**
     * 卸载后轮询间隔（毫秒）。
     */
    private static final long UNINSTALL_POLL_INTERVAL_MS = 50L;

    /**
     * osgi 启动器。
     */
    private final OsgiLauncher launcher;

    /**
     * Bundle 配置。
     */
    private final BundleProperties properties;

    /**
     * 已托管 bundle 记录：<b>符号名 → artifactId</b>。
     *
     * <p>内部以符号名为键，与 {@link #uninstall(String)} 的入参口径一致，
     * 从而修掉「安装时记 artifactId、卸载时按符号名删」导致条目永久残留的问题；
     * 对外 {@link #getManagedBundles()} 仍返回 artifactId，保持既有契约。</p>
     */
    private final Map<String, String> managedBundles = new ConcurrentHashMap<>();

    /**
     * 构造 bundle 管理器服务。
     *
     * @param launcher   osgi 启动器
     * @param properties Bundle 配置
     */
    public BundleManagerService(OsgiLauncher launcher, BundleProperties properties) {
        this.launcher = launcher;
        this.properties = properties;
    }

    // ==================== 安装 ====================

    /**
     * 安装指定 Maven 坐标的 bundle。
     *
     * <p>若已安装同符号名的更高版本，则执行原地升级（见类注释升级策略）。</p>
     *
     * @param coords Maven 坐标，格式：{@code groupId:artifactId:version}
     * @return 安装结果
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
            OsgiBundle existing = findExisting(coord.artifactId());
            if (existing != null) {
                String existingVersion = existing.getVersion();
                markManaged(existing.getSymbolicName(), coord.artifactId());
                if (!properties.isAutoUpgrade()
                        || compareVersions(coord.version(), existingVersion) <= 0) {
                    log.info("[Bundle] {} 已是最新（已装 {}，请求 {}），跳过安装",
                            coord.artifactId(), existingVersion, coord.version());
                    return InstallResult.success(existing.getSymbolicName(), existingVersion);
                }
                log.info("[Bundle] 检测到升级: {} {} -> {}", existing.getSymbolicName(), existingVersion, coord.version());
                if (tryUpdateInPlace(existing.getSymbolicName(), url, coord)) {
                    return InstallResult.success(existing.getSymbolicName(), coord.version());
                }
                if (!reinstall(existing.getSymbolicName(), url, coord)) {
                    return InstallResult.fail(coords, "升级失败且回退重装未成功");
                }
                return InstallResult.success(existing.getSymbolicName(), coord.version());
            }
            OsgiBundle installed = launcher.installBundle(url);
            markManaged(installed.getSymbolicName(), coord.artifactId());
            log.info("[Bundle] 已安装: {} ({})", coord.artifactId(), coord.version());
            return InstallResult.success(installed.getSymbolicName(), installed.getVersion());
        } catch (Exception e) {
            log.error("[Bundle] 安装失败 [{}]: {}", coords, e.getMessage());
            return InstallResult.fail(coords, e.getMessage());
        }
    }

    /**
     * 尝试以框架原生 update 完成原地升级。
     *
     * @param symbolicName bundle 符号名
     * @param url          新版本来源
     * @param coord        Maven 坐标
     * @return 升级成功返回 true
     */
    private boolean tryUpdateInPlace(String symbolicName, String url, MavenCoord coord) {
        try {
            OsgiBundle updated = launcher.updateBundle(symbolicName, url);
            markManaged(symbolicName, coord.artifactId());
            log.info("[Bundle] 原地升级成功: {} -> {}", symbolicName, updated.getVersion());
            return true;
        } catch (Exception e) {
            log.warn("[Bundle] 原地升级失败 [{}]，回退到卸载重装: {}", symbolicName, e.getMessage());
            return false;
        }
    }

    /**
     * 卸载旧版后安装新版（原地升级失败时的回退路径）。
     *
     * @param symbolicName 旧 bundle 符号名
     * @param url          新版本来源
     * @param coord        Maven 坐标
     * @return 安装成功返回 true
     */
    private boolean reinstall(String symbolicName, String url, MavenCoord coord) {
        try {
            if (!launcher.uninstallBundle(symbolicName)) {
                log.warn("[Bundle] 回退重装：旧版 {} 未找到", symbolicName);
            }
        } catch (Exception e) {
            log.warn("[Bundle] 回退重装：卸载 {} 异常: {}", symbolicName, e.getMessage());
        }
        if (!awaitUninstalled(symbolicName)) {
            log.warn("[Bundle] 回退重装：旧版 {} 在 {}ms 内未进入已卸载状态，仍尝试安装新版",
                    symbolicName, UNINSTALL_WAIT_TIMEOUT_MS);
        }
        try {
            OsgiBundle installed = launcher.installBundle(url);
            markManaged(installed.getSymbolicName(), coord.artifactId());
            return true;
        } catch (Exception e) {
            log.error("[Bundle] 回退重装：安装 {} 失败: {}", url, e.getMessage());
            return false;
        }
    }

    /**
     * 登记托管关系。
     *
     * <p>符号名缺失时退化为以 artifactId 为键，保证「已安装即已托管」这一语义
     * 在任何 bundle 形态下都成立。</p>
     *
     * @param symbolicName bundle 符号名
     * @param artifactId   Maven 坐标中的 artifactId
     */
    private void markManaged(String symbolicName, String artifactId) {
        String key = symbolicName == null || symbolicName.isEmpty() ? artifactId : symbolicName;
        if (key != null && !key.isEmpty()) {
            managedBundles.put(key, artifactId);
        }
    }

    /**
     * 等待指定符号名的 bundle 真正离开已安装集合。
     *
     * <p>框架的卸载是异步完成的，固定时长休眠无法保证此时新版本可以装上，
     * 因此改为按状态轮询，超时才继续。</p>
     *
     * @param symbolicName bundle 符号名
     * @return 在超时前已完成卸载返回 true
     */
    private boolean awaitUninstalled(String symbolicName) {
        long deadline = System.currentTimeMillis() + UNINSTALL_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            OsgiBundle bundle = launcher.getBundle(symbolicName);
            if (bundle == null || "UNINSTALLED".equals(bundle.getState())) {
                return true;
            }
            try {
                Thread.sleep(UNINSTALL_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
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
        if (!launcher.isActive() || symbolicName == null || symbolicName.isBlank()) {
            return false;
        }
        try {
            boolean removed = launcher.uninstallBundle(symbolicName.trim());
            if (removed) {
                managedBundles.remove(symbolicName.trim());
                log.info("[Bundle] 已卸载: {}", symbolicName);
            } else {
                log.warn("[Bundle] 卸载失败：bundle 不存在 {}", symbolicName);
            }
            return removed;
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
     *
     * @return 状态列表
     */
    public List<Map<String, Object>> getStatus() {
        if (!launcher.isActive()) {
            return Collections.emptyList();
        }
        return launcher.getBundles().stream().map(b -> {
            String name = b.getSymbolicName();
            Map<String, Object> info = new java.util.LinkedHashMap<>();
            info.put("bundleId", b.getBundleId());
            info.put("symbolicName", name);
            info.put("state", b.getState());
            info.put("version", b.getVersion());
            info.put("location", b.getLocation());
            info.put("managed", name != null && managedBundles.containsKey(name));
            return info;
        }).collect(Collectors.toList());
    }

    /**
     * 检查指定符号名的 bundle 是否已安装。
     *
     * @param symbolicName 符号名
     * @return 已安装返回 true
     */
    public boolean isInstalled(String symbolicName) {
        return launcher.getBundle(symbolicName) != null;
    }

    /**
     * 获取已托管的 bundle artifactId 列表。
     *
     * <p>返回的是 Maven 坐标中的 artifactId（与安装入参同口径），
     * 按登记顺序去重。</p>
     *
     * @return 托管 artifactId 列表
     */
    public List<String> getManagedBundles() {
        return new ArrayList<>(new LinkedHashSet<>(managedBundles.values()));
    }

    // ==================== 私有辅助 ====================

    /**
     * 按 artifactId 精确查找已安装的 bundle。
     *
     * <p>先用「符号名恰好等于 artifactId」与「来源位置以 artifactId 结尾的 jar」两条精确规则，
     * 都未命中时再退化为子串包含匹配，避免直接子串匹配带来的误命中
     * （例如 {@code core} 会误配 {@code core-ext}）。</p>
     *
     * @param artifactId artifactId
     * @return 命中的 bundle，未命中返回 {@code null}
     */
    private OsgiBundle findExisting(String artifactId) {
        if (artifactId == null || artifactId.isBlank()) {
            return null;
        }
        List<OsgiBundle> bundles = launcher.getBundles();
        for (OsgiBundle bundle : bundles) {
            if (artifactId.equals(bundle.getSymbolicName())) {
                return bundle;
            }
        }
        String suffix = "-" + artifactId + "-";
        String suffixAlt = "-" + artifactId + ".";
        // Maven 构件文件名以 artifactId 开头（{artifactId}-{version}.jar），
        // 上面前导横线写法匹配不到；此处补充「文件名以 artifactId- 开头且后接版本号数字」的判断。
        // 必须校验后接数字：否则查 greet 时会把 greet-http-2.0.0.jar 误判为同一构件。
        String prefix = artifactId + "-";
        for (OsgiBundle bundle : bundles) {
            String location = bundle.getLocation();
            String fileName = location.substring(location.lastIndexOf('/') + 1);
            if (fileName.contains(suffix) || fileName.contains(suffixAlt)
                    || isArtifactFile(fileName, prefix)) {
                return bundle;
            }
        }
        for (OsgiBundle bundle : bundles) {
            String name = bundle.getSymbolicName();
            if (name != null && name.contains(artifactId)) {
                log.debug("[Bundle] 符号名 {} 仅为子串命中 artifactId {}", name, artifactId);
                return bundle;
            }
        }
        return null;
    }

    /**
     * 判断构件文件名是否属于指定 artifactId。
     *
     * <p>标准 Maven 构件文件名为 {@code {artifactId}-{version}.jar}，
     * 即 artifactId 位于<b>开头</b>。此方法要求 {@code artifactId-} 之后紧跟版本号数字，
     * 以免查 {@code greet} 时把 {@code greet-http-2.0.0.jar} 误判为同一构件。</p>
     *
     * @param fileName   构件文件名
     * @param namePrefix {@code artifactId-} 前缀
     * @return 属于该 artifactId 时返回 {@code true}
     */
    private static boolean isArtifactFile(String fileName, String namePrefix) {
        if (fileName == null || !fileName.startsWith(namePrefix)) {
            return false;
        }
        String remainder = fileName.substring(namePrefix.length());
        return !remainder.isEmpty() && Character.isDigit(remainder.charAt(0));
    }

    /**
     * 解析 Maven 坐标。
     *
     * @param coords 坐标字符串
     * @return 坐标对象，格式非法时返回 {@code null}
     */
    private MavenCoord parseCoords(String coords) {
        if (coords == null || coords.isBlank()) {
            return null;
        }
        Matcher m = MAVEN_COORDS_PATTERN.matcher(coords.trim());
        if (!m.matches()) {
            return null;
        }
        return new MavenCoord(m.group(1), m.group(2), m.group(3));
    }

    /**
     * 转换为 Maven 仓库 URL。
     *
     * @param coord 坐标对象
     * @return 仓库 URL
     */
    private String toMavenUrl(MavenCoord coord) {
        String path = coord.groupId().replace('.', '/')
                + "/" + coord.artifactId() + "/" + coord.version()
                + "/" + coord.artifactId() + "-" + coord.version() + ".jar";
        String base = properties.getRepositoryUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/" + path;
    }

    /**
     * 提取版本号的前三段数字。
     *
     * @param version 版本字符串
     * @return 三段式版本号，无法提取时返回 {@code null}
     */
    private String extractVersion(String version) {
        if (version == null) {
            return null;
        }
        Matcher m = VERSION_PATTERN.matcher(version);
        return m.find() ? m.group(0) : null;
    }

    /**
     * 比较两个版本号。
     *
     * <p>逐段比较而非直接相减，避免版本号较大时数值相减溢出为错误的正负号。</p>
     *
     * @param v1 版本号一
     * @param v2 版本号二
     * @return v1 高于 v2 返回正数，低于返回负数，相等返回 0
     */
    private int compareVersions(String v1, String v2) {
        String left = extractVersion(v1);
        String right = extractVersion(v2);
        if (left == null || right == null) {
            return 0;
        }
        Matcher m1 = VERSION_PATTERN.matcher(left);
        Matcher m2 = VERSION_PATTERN.matcher(right);
        if (!m1.matches() || !m2.matches()) {
            return 0;
        }
        for (int i = 1; i <= 3; i++) {
            long a = Long.parseLong(m1.group(i));
            long b = Long.parseLong(m2.group(i));
            if (a != b) {
                return a > b ? 1 : -1;
            }
        }
        return 0;
    }

    // ==================== 内部类 ====================

    /**
     * 安装结果。
     *
     * @param artifactId 目标坐标的 artifactId
     * @param version    实际生效的版本号
     * @param success    是否成功
     * @param message    失败原因，成功时为 {@code null}
     */
    public record InstallResult(String artifactId, String version, boolean success, String message) {

        /**
         * 构造成功结果。
         *
         * @param artifactId artifactId
         * @param version    版本号
         * @return 成功结果
         */
        public static InstallResult success(String artifactId, String version) {
            return new InstallResult(artifactId, version, true, null);
        }

        /**
         * 构造失败结果。
         *
         * @param artifactId artifactId
         * @param message    失败原因
         * @return 失败结果
         */
        public static InstallResult fail(String artifactId, String message) {
            return new InstallResult(artifactId, null, false, message);
        }
    }

    /**
     * Maven 坐标。
     *
     * @param groupId    群体标识
     * @param artifactId artifactId
     * @param version    版本号
     */
    private record MavenCoord(String groupId, String artifactId, String version) {
        // 内部坐标载体
    }
}
