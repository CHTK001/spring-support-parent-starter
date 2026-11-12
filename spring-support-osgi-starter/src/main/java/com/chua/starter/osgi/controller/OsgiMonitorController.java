package com.chua.starter.osgi.controller;

import com.chua.common.support.osgi.OsgiBundle;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.common.support.osgi.OsgiServiceDescriptor;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import com.chua.starter.osgi.properties.OsgiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * OSGi 运行时只读观测端点。
 *
 * <p>暴露 bundle 清单、服务清单、框架统计与单个 bundle 详情，便于在生产环境
 * 无需进入 SSH/Telnet shell 即可确认插件化运行状态。</p>
 *
 * <p><b>变更类操作</b>：上传安装、升组、启动/停止、卸载与下载也在本控制器，
 * 但需要 {@code plugin.osgi.endpoint.manage-enabled=true}（默认 true）。
 * 端点本身默认关闭，开启后即由既有接口鉴权体系保护；生产环境请确保
 * {@code /osgi/**} 只对管理员开放。</p>
 *
 * <p>仅在 {@code plugin.osgi.endpoint.enabled=true} 且 classpath 中存在 spring-web 时装配。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@RestController
@RequestMapping("/osgi")
public class OsgiMonitorController {

    /** OSGi manifest 符号名属性。 */
    private static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";

    /** OSGi manifest 版本属性。 */
    private static final String BUNDLE_VERSION = "Bundle-Version";

    /** OSGi 未声明版本时的缺省版本。 */
    private static final String DEFAULT_BUNDLE_VERSION = "0.0.0";

    /**
     * osgi 启动器。
     */
    private final OsgiLauncher launcher;

    /**
     * Bean 桥接注册器，可能未装配。
     */
    private final OsgiBeanDefinitionRegister register;

    /**
     * osgi 配置（上传目录与变更开关）。
     */
    private final OsgiProperties properties;

    /**
     * 构造 osgi 观测端点。
     *
     * @param launcher   osgi 启动器
     * @param register   Bean 桥接注册器，可为 {@code null}
     * @param properties osgi 配置
     */
    public OsgiMonitorController(OsgiLauncher launcher, OsgiBeanDefinitionRegister register,
                                 OsgiProperties properties) {
        this.launcher = launcher;
        this.register = register;
        this.properties = properties;
    }

    /**
     * 获取框架统计信息。
     *
     * @return 统计信息
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>(launcher.getFrameworkStats());
        stats.put("registeredServiceTypes", launcher.getRegisteredServiceTypes());
        return stats;
    }

    /**
     * 获取全部已安装 bundle 清单。
     *
     * @return bundle 清单
     */
    @GetMapping("/bundles")
    public List<Map<String, Object>> bundles() {
        return launcher.getBundles().stream().map(this::describe).toList();
    }

    /**
     * 获取单个 bundle 详情。
     *
     * @param symbolicName bundle 符号名
     * @return bundle 详情，不存在时返回 {@code null}
     */
    @GetMapping("/bundles/{symbolicName}")
    public Map<String, Object> bundle(@PathVariable String symbolicName) {
        OsgiBundle bundle = launcher.getBundle(symbolicName);
        if (bundle == null) {
            return null;
        }
        Map<String, Object> detail = describe(bundle);
        detail.put("headers", bundle.getHeaders());
        detail.put("registeredServiceTypes", bundle.getRegisteredServiceTypes());
        return detail;
    }

    /**
     * 获取服务注册表中的全部服务登记。
     *
     * <p>结果已按 {@code service.ranking} 降序排列，首项即当前生效的服务。</p>
     *
     * @return 服务登记清单
     */
    @GetMapping("/services")
    public List<Map<String, Object>> services() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (OsgiServiceDescriptor descriptor : launcher.getServiceDescriptors()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", descriptor.typeName());
            item.put("pid", descriptor.pid());
            item.put("implClass", descriptor.implClassName());
            item.put("stableId", descriptor.stableId());
            item.put("osgiServiceId", descriptor.osgiServiceId());
            item.put("ranking", descriptor.ranking());
            item.put("properties", descriptor.properties());
            result.add(item);
        }
        return result;
    }

    /**
     * 获取容器侧桥接快照状态。
     *
     * <p>返回桥接定义的当前视图（扩展对象）、框架版本号与验收拒绝记录，
     * 用于确认「OSGi 侧变化是否已传导到容器侧」。</p>
     *
     * @return 快照状态
     */
    @GetMapping("/snapshot")
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("frameworkActive", launcher.isActive());
        result.put("autoStartFailures", launcher.getFrameworkStats().get("autoStartFailures"));
        if (register == null) {
            result.put("bridgeAvailable", false);
            return result;
        }
        result.put("bridgeAvailable", true);
        result.put("frameworkVersion", register.getFrameworkVersion());
        result.put("acceptanceEnabled", register.isAcceptanceEnabled());
        result.put("invalidateOnBundleLifecycle", register.isInvalidateOnBundleLifecycle());
        result.put("definitionCount", register.getBeanDefinitionNames().size());
        result.put("definitions", register.getBeanDefinitionNames());
        result.put("rejected", register.getRejectedSnapshots());
        return result;
    }

    /**
     * 获取框架上下文原始对象，便于框架外组件按名查找服务。
     *
     * @return 框架上下文描述信息
     */
    @GetMapping("/context")
    public Map<String, Object> context() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", launcher.isActive());
        result.put("serviceTypes", launcher.getRegisteredServiceTypes());
        if (launcher instanceof FelixOsgiLauncher felix) {
            result.put("bundleContextAvailable", felix.getFrameworkBundleContext() != null);
        } else {
            result.put("bundleContextAvailable", false);
        }
        return result;
    }

    /**
     * 上传并安装 bundle（同位置的已有 bundle 转原地升级）。
     *
     * @param file  插件 jar
     * @param start 安装后是否启动，默认 true
     * @return 安装后的 bundle 描述
     * @throws IOException 保存上传文件失败
     */
    @PostMapping("/bundles/install")
    public Map<String, Object> install(@RequestParam("file") MultipartFile file,
                                       @RequestParam(defaultValue = "true") boolean start) throws IOException {
        requireManageEnabled();
        BundleHeaders headers = readBundleHeaders(file);
        Path target = targetPath(file);
        String url = target.toUri().toString();
        OsgiBundle sameLocation = findBundleByLocation(url);
        if (sameLocation == null) {
            OsgiBundle sameName = findBundleBySymbolicName(headers.symbolicName());
            if (sameName != null) {
                throw new IllegalStateException("插件已存在: " + headers.symbolicName() + ":"
                        + sameName.getVersion() + "，请使用升级操作");
            }
        }
        OsgiBundle installed;
        if (sameLocation != null) {
            installed = replaceBundle(sameLocation, file, target);
        } else {
            save(file, target);
            try {
                installed = launcher.installBundle(url);
            } catch (RuntimeException ex) {
                Files.deleteIfExists(target);
                throw new IllegalArgumentException("插件安装失败: " + rootMessage(ex), ex);
            }
        }
        if (installed == null) {
            throw new IllegalArgumentException("插件安装失败: 框架未返回 bundle");
        }
        if (start && !installed.isActive()) {
            installed.start();
        }
        OsgiBundle fresh = launcher.getBundle(installed.getSymbolicName());
        return describe(fresh == null ? installed : fresh);
    }

    /**
     * 上传新版本并原地升级 bundle，保留 bundle 标识。
     *
     * @param symbolicName bundle 符号名
     * @param file         新版本 jar
     * @return 升级后的 bundle 描述
     * @throws IOException 保存上传文件失败
     */
    @PostMapping("/bundles/{symbolicName}/update")
    public Map<String, Object> update(@PathVariable String symbolicName,
                                      @RequestParam("file") MultipartFile file) throws IOException {
        requireManageEnabled();
        OsgiBundle existing = requireBundle(symbolicName);
        BundleHeaders headers = readBundleHeaders(file);
        if (!symbolicName.equals(headers.symbolicName())) {
            throw new IllegalArgumentException("jar 的 Bundle-SymbolicName 与目标不一致: "
                    + headers.symbolicName() + " -> " + symbolicName);
        }
        Path target = resolveLocalJar(existing.getLocation());
        if (target == null) {
            throw new IllegalArgumentException("该 bundle 不是部署目录内的本地 jar，无法升级: "
                    + existing.getLocation());
        }
        OsgiBundle updated = replaceBundle(existing, file, target);
        if (updated == null) {
            throw new IllegalArgumentException("插件升级失败: 框架未返回 bundle");
        }
        OsgiBundle fresh = launcher.getBundle(symbolicName);
        return describe(fresh == null ? updated : fresh);
    }

    /**
     * 启动 bundle。
     *
     * @param symbolicName bundle 符号名
     * @return 启动后的 bundle 描述
     */
    @PostMapping("/bundles/{symbolicName}/start")
    public Map<String, Object> start(@PathVariable String symbolicName) {
        requireManageEnabled();
        OsgiBundle bundle = requireBundle(symbolicName);
        if (!bundle.isActive()) {
            bundle.start();
        }
        return describe(bundle);
    }

    /**
     * 停止 bundle。
     *
     * @param symbolicName bundle 符号名
     * @return 停止后的 bundle 描述
     */
    @PostMapping("/bundles/{symbolicName}/stop")
    public Map<String, Object> stop(@PathVariable String symbolicName) {
        requireManageEnabled();
        OsgiBundle bundle = requireBundle(symbolicName);
        bundle.stop();
        return describe(bundle);
    }

    /**
     * 卸载 bundle。
     *
     * @param symbolicName bundle 符号名
     * @return 卸载结果
     */
    @PostMapping("/bundles/{symbolicName}/uninstall")
    public Map<String, Object> uninstall(@PathVariable String symbolicName) {
        requireManageEnabled();
        requireBundle(symbolicName);
        boolean success = launcher.uninstallBundle(symbolicName);
        if (!success) {
            throw new IllegalStateException("bundle 卸载失败: " + symbolicName);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbolicName", symbolicName);
        result.put("success", true);
        return result;
    }

    /**
     * 下载 bundle 对应的本地 jar。
     *
     * @param symbolicName bundle 符号名
     * @return jar 文件流
     * @throws IOException 读取文件失败
     */
    @GetMapping("/bundles/{symbolicName}/download")
    public ResponseEntity<Resource> download(@PathVariable String symbolicName) throws IOException {
        OsgiBundle bundle = requireBundle(symbolicName);
        Path path = resolveLocalJar(bundle.getLocation());
        if (path == null) {
            throw new IllegalArgumentException("该 bundle 不是本地 jar，无法下载: " + bundle.getLocation());
        }
        InputStream input = Files.newInputStream(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + path.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(path))
                .body(new InputStreamResource(input));
    }

    /**
     * 取 bundle，不存在时抛 400。
     *
     * @param symbolicName bundle 符号名
     * @return bundle
     */
    private OsgiBundle requireBundle(String symbolicName) {
        OsgiBundle bundle = launcher.getBundle(symbolicName);
        if (bundle == null) {
            throw new IllegalArgumentException("bundle 不存在: " + symbolicName);
        }
        return bundle;
    }

    /**
     * 变更类操作开关校验。
     */
    private void requireManageEnabled() {
        if (!properties.getEndpoint().isManageEnabled()) {
            throw new IllegalStateException("OSGi 变更操作未开启: plugin.osgi.endpoint.manage-enabled=true");
        }
    }

    /**
     * 把上传的 jar 落到部署目录，文件名只取 basename，拒绝路径穿越。
     *
     * @param file 上传文件
     * @return 落盘路径
     * @throws IOException 写入失败
     */
    private Path save(MultipartFile file) throws IOException {
        return save(file, targetPath(file));
    }

    /**
     * 把上传的 jar 写到指定路径；路径必须位于部署目录内。
     *
     * @param file   插件 jar
     * @param target 目标路径
     * @return 落盘路径
     * @throws IOException 写入失败
     */
    private Path save(MultipartFile file, Path target) throws IOException {
        jarName(file);
        Path dir = deployDirectory();
        Files.createDirectories(dir);
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(dir)) {
            throw new IllegalArgumentException("非法文件名: " + target);
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("[OSGi] 插件 jar 已上传: {} ({} bytes)", normalized, Files.size(normalized));
        return normalized;
    }

    /**
     * 替换现有 bundle 的 jar；失败时恢复原包，避免升级失败留下损坏文件。
     *
     * @param existing 现有 bundle
     * @param file     新 jar
     * @param target   现有 bundle 的本地 jar
     * @return 更新后的 bundle
     * @throws IOException 文件读写失败
     */
    private OsgiBundle replaceBundle(OsgiBundle existing, MultipartFile file, Path target) throws IOException {
        Path backup = target.resolveSibling(target.getFileName() + ".osgi-backup");
        boolean backupCreated = false;
        try {
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            backupCreated = true;
            save(file, target);
            OsgiBundle updated = launcher.updateBundle(existing.getSymbolicName(), target.toUri().toString());
            if (updated != null && existing.isActive() && !updated.isActive()) {
                updated.start();
            }
            return updated;
        } catch (IOException | RuntimeException ex) {
            if (backupCreated) {
                try {
                    Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException restoreError) {
                    ex.addSuppressed(restoreError);
                }
            }
            throw new IllegalArgumentException("插件安装/升级失败，原包已恢复: " + rootMessage(ex), ex);
        } finally {
            if (backupCreated) {
                Files.deleteIfExists(backup);
            }
        }
    }

    /**
     * 解析上传 jar 的 OSGi 符号名和版本，拒绝非 OSGi jar。
     *
     * @param file 上传文件
     * @return manifest 头信息
     */
    private BundleHeaders readBundleHeaders(MultipartFile file) {
        String name = jarName(file);
        try (InputStream input = file.getInputStream(); JarInputStream jar = new JarInputStream(input)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                throw new IllegalArgumentException("不是有效的 OSGi bundle: 缺少 META-INF/MANIFEST.MF - " + name);
            }
            String symbolicName = manifest.getMainAttributes().getValue(BUNDLE_SYMBOLIC_NAME);
            if (symbolicName == null || symbolicName.isBlank()) {
                throw new IllegalArgumentException("不是有效的 OSGi bundle: 缺少 Bundle-SymbolicName - " + name);
            }
            symbolicName = symbolicName.split(";", 2)[0].trim();
            String version = manifest.getMainAttributes().getValue(BUNDLE_VERSION);
            version = version == null || version.isBlank() ? DEFAULT_BUNDLE_VERSION : version.trim();
            return new BundleHeaders(symbolicName, version);
        } catch (IOException ex) {
            throw new IllegalArgumentException("不是有效的 jar 文件: " + name, ex);
        }
    }

    /**
     * 计算上传文件在部署目录中的目标路径。
     *
     * @param file 上传文件
     * @return 目标路径
     */
    private Path targetPath(MultipartFile file) {
        String name = jarName(file);
        Path dir = deployDirectory();
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw new IllegalArgumentException("非法文件名: " + name);
        }
        return target;
    }

    /**
     * 取上传文件名并校验 .jar 后缀。
     *
     * @param file 上传文件
     * @return 文件名
     */
    private String jarName(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("插件文件不能为空");
        }
        String original = file.getOriginalFilename() == null ? "plugin.jar" : file.getOriginalFilename();
        String name = Paths.get(original).getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("只支持 .jar 文件: " + name);
        }
        return name;
    }

    /**
     * 部署目录绝对路径。
     *
     * @return 部署目录
     */
    private Path deployDirectory() {
        return Paths.get(properties.getDeploy().getDirectory()).toAbsolutePath().normalize();
    }

    /**
     * 按 location 查 bundle。
     *
     * @param location bundle location
     * @return 找到的 bundle，未找到返回 null
     */
    private OsgiBundle findBundleByLocation(String location) {
        for (OsgiBundle bundle : launcher.getBundles()) {
            if (location.equals(bundle.getLocation())) {
                return bundle;
            }
        }
        return null;
    }

    /**
     * 按符号名查 bundle。
     *
     * @param symbolicName 符号名
     * @return 找到的 bundle，未找到返回 null
     */
    private OsgiBundle findBundleBySymbolicName(String symbolicName) {
        for (OsgiBundle bundle : launcher.getBundles()) {
            if (symbolicName.equals(bundle.getSymbolicName())) {
                return bundle;
            }
        }
        return null;
    }

    /**
     * 取最底层异常信息，便于返回给页面。
     *
     * @param error 异常
     * @return 错误信息
     */
    private String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? error.getMessage() : message;
    }

    /**
     * 上传 jar 的 OSGi 头信息。
     *
     * @param symbolicName 符号名
     * @param version      版本
     */
    private record BundleHeaders(String symbolicName, String version) {
    }

    /**
     * 把 bundle location 解析成本地 jar 路径。
     *
     * @param location bundle 位置
     * @return 本地路径，非本地文件返回 {@code null}
     */
    private Path resolveLocalJar(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String value = location.startsWith("reference:")
                ? location.substring("reference:".length()) : location;
        try {
            Path path;
            if (value.startsWith("file:")) {
                path = Paths.get(URI.create(value));
            } else {
                path = Paths.get(value);
            }
            return Files.isRegularFile(path) ? path : null;
        } catch (RuntimeException ex) {
            log.debug("[OSGi] bundle location 无法解析为本地文件: {} ({})", location, ex.getMessage());
            return null;
        }
    }

    /**
     * 组装 bundle 描述信息。
     *
     * @param bundle bundle
     * @return 描述信息
     */
    private Map<String, Object> describe(OsgiBundle bundle) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("bundleId", bundle.getBundleId());
        item.put("symbolicName", bundle.getSymbolicName());
        item.put("version", bundle.getVersion());
        item.put("state", bundle.getState());
        item.put("location", bundle.getLocation());
        return item;
    }
}
