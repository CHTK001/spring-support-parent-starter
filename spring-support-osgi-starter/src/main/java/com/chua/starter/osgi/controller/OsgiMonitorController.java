package com.chua.starter.osgi.controller;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonNode;
import com.chua.common.support.osgi.OsgiBundle;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.common.support.osgi.OsgiServiceDescriptor;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import com.chua.starter.osgi.properties.OsgiProperties;
import com.chua.starter.logger.event.OsgiLogEvent;
import com.chua.starter.osgi.support.realtime.OsgiLogPublisher;
import com.chua.starter.osgi.support.stats.OsgiActionStat;
import com.chua.starter.osgi.support.stats.OsgiStatSample;
import com.chua.starter.osgi.support.stats.OsgiStatsCollector;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.wiring.FrameworkWiring;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * OSGi 运行时只读观测端点。
 *
 * <p>暴露 bundle 清单、服务清单、框架统计与单个 bundle 详情，便于在生产环境
 * 无需进入 SSH/Telnet shell 即可确认插件化运行状态。</p>
 *
 * <p><b>变更类操作</b>：上传安装、升级、启动/停止、批量启停/卸载、卸载、jar 删除、
 * 下载与框架刷新都在本控制器，但需要 {@code plugin.osgi.endpoint.manage-enabled=true}
 * （默认 true）。端点本身默认关闭，开启后即由既有接口鉴权体系保护；
 * 生产环境请确保 {@code /osgi/**} 只对管理员开放。</p>
 *
 * <p><b>并发语义</b>：所有变更类操作经同一把生命周期锁（{@link #lifecycleLock}）串行执行，
 * 避免「查重再安装」「查状态再启停」这类复合动作在并发下交错；单个 bundle 的启动/停止
 * 受 {@code plugin.osgi.endpoint.lifecycle-timeout-seconds} 约束，不会因 activator
 * 挂死而长期占用请求线程。</p>
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

    /** 回收站元数据边车文件后缀。 */
    private static final String RECYCLE_META_SUFFIX = ".meta.json";

    /** 回收站重名文件追加的时间戳后缀格式。 */
    private static final DateTimeFormatter RECYCLE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 批量操作单次允许的最大目标数；防止一次超大批量长期持有生命周期锁。 */
    private static final int MAX_BATCH_SIZE = 100;

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
     * 插件操作日志发布器（实时日志抽屉的数据源）。
     */
    private final OsgiLogPublisher logPublisher;

    /**
     * 框架体征采样器与操作统计聚合器（趋势图 / 操作统计图的数据源）。
     */
    private final OsgiStatsCollector statsCollector;

    /**
     * 生命周期操作互斥锁：安装/升级/启停/卸载/删文件/刷新/批量串行执行。
     *
     * <p>这些操作普遍是「先查重/查状态，再动框架」的复合动作，且 OSGi 框架对
     * 并发生命周期调用的结果不可依赖：并发上传同一插件可能同时通过查重造成
     * 重复安装，并发启停同一 bundle 可能交错出不确定状态。统一串行化，
     * 代价只是管理操作本就低频，完全可接受。</p>
     */
    private final Object lifecycleLock = new Object();

    /**
     * 构造 osgi 观测端点。
     *
     * @param launcher       osgi 启动器
     * @param register       Bean 桥接注册器，可为 {@code null}
     * @param properties     osgi 配置
     * @param logPublisher   操作日志发布器
     * @param statsCollector 体征采样器与操作统计聚合器
     */
    public OsgiMonitorController(OsgiLauncher launcher, OsgiBeanDefinitionRegister register,
                                 OsgiProperties properties, OsgiLogPublisher logPublisher,
                                 OsgiStatsCollector statsCollector) {
        this.launcher = launcher;
        this.register = register;
        this.properties = properties;
        this.logPublisher = logPublisher;
        this.statsCollector = statsCollector;
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
     * 获取框架体征历史采样（时间升序），供前端趋势图绘制。
     *
     * <p>只采样、不落库：宿主重启后历史清零；{@code plugin.osgi.stats.enabled=false}
     * 时返回空列表，前端展示空态。</p>
     *
     * @return 采样点列表，时间升序
     */
    @GetMapping("/stats/history")
    public List<OsgiStatSample> statsHistory() {
        return statsCollector.history();
    }

    /**
     * 获取插件操作统计（按总次数降序），供前端操作统计图绘制。
     *
     * <p>只统计 success / failure 终态事件，start 阶段不计数；暂无操作时返回空列表。</p>
     *
     * @return 操作统计列表
     */
    @GetMapping("/stats/actions")
    public List<OsgiActionStat> statsActions() {
        return statsCollector.actionStats();
    }

    /**
     * 获取全部已安装 bundle 清单。
     *
     * <p>读操作默认静默；只在失败时推一条实时日志——清单都取不到时，
     * 「为什么页面是空的」必须能在日志里直接看到原因。</p>
     *
     * @return bundle 清单
     */
    @GetMapping("/bundles")
    public List<Map<String, Object>> bundles() {
        try {
            return launcher.getBundles().stream().map(this::describe).toList();
        } catch (RuntimeException ex) {
            logPublisher.publish(OsgiLogEvent.warn("list", "", "读取 bundle 清单失败: " + rootMessage(ex)));
            throw ex;
        }
    }

    /**
     * 部署目录 jar 清单。
     *
     * <p>「卸载」只把 bundle 从框架里摘掉，jar 仍留在部署目录；要真正删掉文件，
     * 必须先卸载（见 {@link #deleteJar(String)}）。本端点把「文件是否还在、是否仍在框架中」
     * 一次讲清，页面据此决定「删除」按钮是否可用，避免用户点了才看到报错。</p>
     *
     * @return 部署目录 jar 清单，按文件名排序
     */
    @GetMapping("/jars")
    public List<Map<String, Object>> jars() {
        Path dir = deployDirectory();
        if (!Files.isDirectory(dir)) {
            return new ArrayList<>();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            // 一次遍历建立 location 索引，避免每个 jar 都全量扫描 bundle 清单（O(jar×bundle) → O(jar+bundle)）
            Map<String, OsgiBundle> byLocation = indexByLocation();
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> describeJar(path, byLocation))
                    .toList();
        } catch (IOException | RuntimeException ex) {
            logPublisher.publish(OsgiLogEvent.warn("jars", "", "读取部署目录失败: " + rootMessage(ex)));
            throw new IllegalStateException("读取部署目录失败: " + rootMessage(ex), ex);
        }
    }

    /**
     * 获取单个 bundle 详情。
     *
     * @param symbolicName bundle 符号名
     * @return bundle 详情
     * @throws IllegalArgumentException bundle 不存在时抛出（HTTP 400，消息携带符号名）
     */
    @GetMapping("/bundles/{symbolicName}")
    public Map<String, Object> bundle(@PathVariable String symbolicName) {
        OsgiBundle bundle = requireBundle(symbolicName);
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
        synchronized (lifecycleLock) {
            return doInstall(file, start);
        }
    }

    /**
     * 执行上传安装/原地升级（调用方须已持有 {@link #lifecycleLock}）。
     *
     * @param file  插件 jar
     * @param start 安装后是否启动
     * @return 安装后的 bundle 描述
     * @throws IOException 保存上传文件失败
     */
    private Map<String, Object> doInstall(MultipartFile file, boolean start) throws IOException {
        long startedAt = System.currentTimeMillis();
        String fileName = file == null ? "" : String.valueOf(file.getOriginalFilename());
        logPublisher.publish(OsgiLogEvent.start("install", fileName));
        try {
            requireUploadSizeOk(file);
            BundleHeaders headers = readBundleHeaders(file);
            String declaredName = headers.symbolicName() == null || headers.symbolicName().isBlank()
                    ? deriveBundleSymbolicName(fileName) : headers.symbolicName();
            Path target = targetPath(file);
            String url = target.toUri().toString();
            OsgiBundle sameLocation = findBundleByLocation(url);
            if (sameLocation == null) {
                OsgiBundle sameName = findBundleBySymbolicName(declaredName);
                if (sameName != null) {
                    throw new IllegalStateException("插件已存在: " + declaredName + ":"
                            + sameName.getVersion() + "，请使用升级操作");
                }
            }
            OsgiBundle installed;
            if (sameLocation != null) {
                installed = replaceBundle(sameLocation, file, target);
            } else {
                save(file, target);
                try {
                    ensureBundleManifest(target, declaredName);
                    installed = launcher.installBundle(url, start);
                } catch (IOException | RuntimeException ex) {
                    Files.deleteIfExists(target);
                    throw new IllegalArgumentException("插件安装失败: " + rootMessage(ex), ex);
                }
            }
            if (installed == null) {
                throw new IllegalArgumentException("插件安装失败: 框架未返回 bundle");
            }
            if (start && !installed.isActive()) {
                try {
                    runLifecycleWithTimeout("start", installed.getSymbolicName(), installed::start);
                } catch (RuntimeException ex) {
                    // bundle 已落框架（安装副作用成功），失败只发生在 start：
                    // 必须把真实原因带回给页面，而不是落入兜底处理器变成「系统繁忙」
                    throw new IllegalStateException("插件已安装（" + installed.getSymbolicName()
                            + ":" + installed.getVersion() + "）但启动失败: " + rootMessage(ex), ex);
                }
            }
            OsgiBundle fresh = launcher.getBundle(installed.getSymbolicName());
            Map<String, Object> result = describe(fresh == null ? installed : fresh);
            logPublisher.publish(OsgiLogEvent.success("install", String.valueOf(result.get("symbolicName")),
                    startedAt, "已安装 " + result.get("symbolicName") + ":" + result.get("version")));
            return result;
        } catch (IOException | RuntimeException ex) {
            logPublisher.publish(OsgiLogEvent.failure("install", fileName, startedAt,
                    "安装失败: " + rootMessage(ex)));
            throw ex;
        }
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
        synchronized (lifecycleLock) {
            return doUpdate(symbolicName, file);
        }
    }

    /**
     * 执行原地升级（调用方须已持有 {@link #lifecycleLock}）。
     *
     * @param symbolicName bundle 符号名
     * @param file         新版本 jar
     * @return 升级后的 bundle 描述
     * @throws IOException 保存上传文件失败
     */
    private Map<String, Object> doUpdate(String symbolicName, MultipartFile file) throws IOException {
        long startedAt = System.currentTimeMillis();
        logPublisher.publish(OsgiLogEvent.start("update", symbolicName));
        try {
            requireUploadSizeOk(file);
            OsgiBundle existing = requireBundle(symbolicName);
            BundleHeaders headers = readBundleHeaders(file);
            String declaredName = headers.symbolicName() == null || headers.symbolicName().isBlank()
                    ? symbolicName : headers.symbolicName();
            if (!symbolicName.equals(declaredName)) {
                throw new IllegalArgumentException("jar 的 Bundle-SymbolicName 与目标不一致: "
                        + declaredName + " -> " + symbolicName);
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
            Map<String, Object> result = describe(fresh == null ? updated : fresh);
            logPublisher.publish(OsgiLogEvent.success("update", symbolicName, startedAt,
                    "已升级到 " + result.get("version")));
            return result;
        } catch (IOException | RuntimeException ex) {
            logPublisher.publish(OsgiLogEvent.failure("update", symbolicName, startedAt,
                    "升级失败: " + rootMessage(ex)));
            throw ex;
        }
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
        synchronized (lifecycleLock) {
            return doStart(symbolicName);
        }
    }

    /**
     * 执行启动（调用方须已持有 {@link #lifecycleLock}）。
     *
     * @param symbolicName bundle 符号名
     * @return 启动后的 bundle 描述
     */
    private Map<String, Object> doStart(String symbolicName) {
        long startedAt = System.currentTimeMillis();
        logPublisher.publish(OsgiLogEvent.start("start", symbolicName));
        try {
            OsgiBundle bundle = requireBundle(symbolicName);
            if (!bundle.isActive()) {
                runLifecycleWithTimeout("start", symbolicName, bundle::start);
            }
            Map<String, Object> result = describe(bundle);
            logPublisher.publish(OsgiLogEvent.success("start", symbolicName, startedAt, "已启动"));
            return result;
        } catch (RuntimeException ex) {
            logPublisher.publish(OsgiLogEvent.failure("start", symbolicName, startedAt,
                    "启动失败: " + rootMessage(ex)));
            throw ex;
        }
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
        synchronized (lifecycleLock) {
            return doStop(symbolicName);
        }
    }

    /**
     * 执行停止（调用方须已持有 {@link #lifecycleLock}）。
     *
     * @param symbolicName bundle 符号名
     * @return 停止后的 bundle 描述
     */
    private Map<String, Object> doStop(String symbolicName) {
        long startedAt = System.currentTimeMillis();
        logPublisher.publish(OsgiLogEvent.start("stop", symbolicName));
        try {
            OsgiBundle bundle = requireBundle(symbolicName);
            runLifecycleWithTimeout("stop", symbolicName, bundle::stop);
            Map<String, Object> result = describe(bundle);
            logPublisher.publish(OsgiLogEvent.success("stop", symbolicName, startedAt, "已停止"));
            return result;
        } catch (RuntimeException ex) {
            logPublisher.publish(OsgiLogEvent.failure("stop", symbolicName, startedAt,
                    "停止失败: " + rootMessage(ex)));
            throw ex;
        }
    }

    /**
     * 卸载 bundle。
     *
     * <p>只把 bundle 从框架里摘掉，<b>不删部署目录里的 jar</b>：卸载后仍可重新安装同一份文件。
     * 要清理文件请再调 {@link #deleteJar(String)}。</p>
     *
     * @param symbolicName bundle 符号名
     * @return 卸载结果
     */
    @PostMapping("/bundles/{symbolicName}/uninstall")
    public Map<String, Object> uninstall(@PathVariable String symbolicName) {
        requireManageEnabled();
        synchronized (lifecycleLock) {
            return doUninstall(symbolicName);
        }
    }

    /**
     * 执行卸载（调用方须已持有 {@link #lifecycleLock}）。
     *
     * @param symbolicName bundle 符号名
     * @return 卸载结果
     */
    private Map<String, Object> doUninstall(String symbolicName) {
        long startedAt = System.currentTimeMillis();
        logPublisher.publish(OsgiLogEvent.start("uninstall", symbolicName));
        try {
            requireBundle(symbolicName);
            boolean success = launcher.uninstallBundle(symbolicName);
            if (!success) {
                throw new IllegalStateException("bundle 卸载失败: " + symbolicName);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("symbolicName", symbolicName);
            result.put("success", true);
            logPublisher.publish(OsgiLogEvent.success("uninstall", symbolicName, startedAt,
                    "已卸载，jar 仍保留在部署目录"));
            return result;
        } catch (RuntimeException ex) {
            logPublisher.publish(OsgiLogEvent.failure("uninstall", symbolicName, startedAt,
                    "卸载失败: " + rootMessage(ex)));
            throw ex;
        }
    }

    /**
     * 删除部署目录中的 jar 文件。
     *
     * <p><b>为什么要「先卸载再删除」</b>：bundle 还在框架里时删文件，框架下次启动会因为
     * 指向的 jar 不存在而解析失败，而当前进程却仍持有旧内容——同一份配置在「重启前/重启后」
     * 表现不一致。所以这里强制要求先卸载，把「运行时状态」和「磁盘状态」一起推进到可解释的位置。</p>
     *
     * @param fileName 部署目录中的文件名（只取 basename）
     * @return 删除结果
     */
    @PostMapping("/jars/{fileName}/delete")
    public Map<String, Object> deleteJar(@PathVariable String fileName) {
        requireManageEnabled();
        synchronized (lifecycleLock) {
            return doDeleteJar(fileName);
        }
    }

    /**
     * 执行 jar 文件删除（调用方须已持有 {@link #lifecycleLock}）。
     *
     * @param fileName 部署目录中的文件名（只取 basename）
     * @return 删除结果
     */
    private Map<String, Object> doDeleteJar(String fileName) {
        String name = Paths.get(fileName).getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("只支持删除 .jar 文件: " + name);
        }
        Path dir = deployDirectory();
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw new IllegalArgumentException("非法文件名: " + name);
        }
        long startedAt = System.currentTimeMillis();
        logPublisher.publish(OsgiLogEvent.start("delete-jar", name));
        try {
            OsgiBundle installed = findBundleByLocation(target.toUri().toString());
            if (installed != null) {
                throw new IllegalStateException("插件仍在框架中（" + installed.getSymbolicName()
                        + "），请先卸载再删除 jar");
            }
            boolean deleted;
            boolean recycled = false;
            if (properties.getRecycle().isEnabled() && Files.exists(target)) {
                // 回收站模式：移入部署目录下的回收站目录，可恢复或彻底清除
                moveToRecycle(target);
                deleted = true;
                recycled = true;
            } else {
                deleted = Files.deleteIfExists(target);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", name);
            result.put("deleted", deleted);
            result.put("recycled", recycled);
            if (deleted && recycled) {
                logPublisher.publish(OsgiLogEvent.success("delete-jar", name, startedAt,
                        "已移入回收站，可在车辆段恢复或彻底删除"));
            } else if (deleted) {
                logPublisher.publish(OsgiLogEvent.success("delete-jar", name, startedAt, "已删除 jar 文件"));
            } else {
                logPublisher.publish(OsgiLogEvent.warn("delete-jar", name, "jar 文件已不存在: " + name));
            }
            return result;
        } catch (IOException | RuntimeException ex) {
            logPublisher.publish(OsgiLogEvent.failure("delete-jar", name, startedAt,
                    "删除失败: " + rootMessage(ex)));
            throw new IllegalStateException("删除 jar 失败: " + rootMessage(ex), ex);
        }
    }

    // ==================== jar 回收站 ====================

    /**
     * 查看回收站清单。
     *
     * <p>回收站位于部署目录之下（目录名见 {@code plugin.osgi.recycle.dir-name}，默认
     * {@code .recycle}），删除的 jar 连同 {@code .meta.json} 元数据边车一起放在这里。
     * 清单按删除时间倒序排列；个别边车损坏只跳过该条目，不影响整表。</p>
     *
     * @return 回收站条目清单
     */
    @GetMapping("/jars/recycle")
    public List<Map<String, Object>> recycleJars() {
        requireManageEnabled();
        Path recycleDir = recycleDirectory();
        if (!Files.isDirectory(recycleDir)) {
            return new ArrayList<>();
        }
        try (Stream<Path> stream = Files.list(recycleDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .map(this::describeRecycleJar)
                    .filter(item -> item != null)
                    .sorted(Comparator.comparing((Map<String, Object> item) ->
                            ((Number) item.get("deletedAt")).longValue()).reversed())
                    .toList();
        } catch (IOException | RuntimeException ex) {
            logPublisher.publish(OsgiLogEvent.warn("recycle-list", "", "读取回收站失败: " + rootMessage(ex)));
            throw new IllegalStateException("读取回收站失败: " + rootMessage(ex), ex);
        }
    }

    /**
     * 从回收站恢复 jar 到部署目录。
     *
     * <p>恢复即把文件移回部署目录的原始文件名（元数据里记录的 {@code originalName}）；
     * 部署目录已存在同名文件时拒绝恢复，由用户先处理冲突。恢复后 jar 重新回到
     * 热部署扫描范围，是否立刻安装由扫描器与 {@code plugin.osgi.deploy} 配置决定。</p>
     *
     * @param fileName 回收站中的文件名
     * @return 恢复结果
     */
    @PostMapping("/jars/recycle/{fileName}/restore")
    public Map<String, Object> restoreRecycleJar(@PathVariable String fileName) {
        requireManageEnabled();
        synchronized (lifecycleLock) {
            return doRestoreRecycleJar(fileName);
        }
    }

    /**
     * 彻底删除回收站中的 jar（连同元数据边车）。
     *
     * <p>这是不可逆操作：文件删除后无法再恢复，前端必须二次确认后才调用。</p>
     *
     * @param fileName 回收站中的文件名
     * @return 删除结果
     */
    @DeleteMapping("/jars/recycle/{fileName}")
    public Map<String, Object> purgeRecycleJar(@PathVariable String fileName) {
        requireManageEnabled();
        synchronized (lifecycleLock) {
            return doPurgeRecycleJar(fileName);
        }
    }

    /**
     * 执行回收站恢复（调用方须已持有 {@link #lifecycleLock}）。
     *
     * @param fileName 回收站中的文件名
     * @return 恢复结果
     */
    private Map<String, Object> doRestoreRecycleJar(String fileName) {
        String name = Paths.get(fileName).getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("只支持恢复 .jar 文件: " + name);
        }
        Path recycleDir = recycleDirectory();
        Path source = recycleDir.resolve(name).normalize();
        if (!source.startsWith(recycleDir)) {
            throw new IllegalArgumentException("非法文件名: " + name);
        }
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("回收站中不存在该文件: " + name);
        }
        long startedAt = System.currentTimeMillis();
        logPublisher.publish(OsgiLogEvent.start("restore-jar", name));
        try {
            Map<String, String> meta = readRecycleMeta(source);
            String originalName = meta.getOrDefault("originalName", name);
            Path dir = deployDirectory();
            Path dest = dir.resolve(originalName).normalize();
            if (!dest.startsWith(dir)) {
                throw new IllegalArgumentException("非法文件名: " + originalName);
            }
            if (Files.exists(dest)) {
                throw new IllegalStateException("部署目录已存在同名文件: " + originalName
                        + "，请先处理冲突文件再恢复");
            }
            Files.move(source, dest);
            Files.deleteIfExists(recycleMetaPath(source));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", originalName);
            result.put("restored", true);
            logPublisher.publish(OsgiLogEvent.success("restore-jar", originalName, startedAt,
                    "已从回收站恢复到部署目录"));
            return result;
        } catch (IOException | RuntimeException ex) {
            logPublisher.publish(OsgiLogEvent.failure("restore-jar", name, startedAt,
                    "恢复失败: " + rootMessage(ex)));
            throw new IllegalStateException("恢复 jar 失败: " + rootMessage(ex), ex);
        }
    }

    /**
     * 执行回收站彻底删除（调用方须已持有 {@link #lifecycleLock}）。
     *
     * @param fileName 回收站中的文件名
     * @return 删除结果
     */
    private Map<String, Object> doPurgeRecycleJar(String fileName) {
        String name = Paths.get(fileName).getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("只支持删除 .jar 文件: " + name);
        }
        Path recycleDir = recycleDirectory();
        Path source = recycleDir.resolve(name).normalize();
        if (!source.startsWith(recycleDir)) {
            throw new IllegalArgumentException("非法文件名: " + name);
        }
        long startedAt = System.currentTimeMillis();
        logPublisher.publish(OsgiLogEvent.start("purge-jar", name));
        try {
            boolean deleted = Files.deleteIfExists(source);
            Files.deleteIfExists(recycleMetaPath(source));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", name);
            result.put("deleted", deleted);
            if (deleted) {
                logPublisher.publish(OsgiLogEvent.success("purge-jar", name, startedAt, "已彻底删除回收站文件"));
            } else {
                logPublisher.publish(OsgiLogEvent.warn("purge-jar", name, "回收站文件已不存在: " + name));
            }
            return result;
        } catch (IOException | RuntimeException ex) {
            logPublisher.publish(OsgiLogEvent.failure("purge-jar", name, startedAt,
                    "彻底删除失败: " + rootMessage(ex)));
            throw new IllegalStateException("彻底删除 jar 失败: " + rootMessage(ex), ex);
        }
    }

    /**
     * 把部署目录中的 jar 移入回收站目录，并写入元数据边车。
     *
     * <p>回收站里重名时在扩展名前追加「日期时间-毫秒」后缀，保证先后删除的
     * 同名文件共存；元数据记录原始文件名、删除时间与 manifest 头信息，
     * 供恢复与展示使用。边车写失败不回滚移动——文件已在回收站，元数据缺失时
     * 按回收站文件名兜底展示。</p>
     *
     * @param target 部署目录中的 jar 文件（调用方保证存在）
     * @throws IOException 移动失败
     */
    private void moveToRecycle(Path target) throws IOException {
        Path recycleDir = recycleDirectory();
        Files.createDirectories(recycleDir);
        String originalName = target.getFileName().toString();
        Path recycled = uniqueRecycleTarget(recycleDir, originalName);
        Files.move(target, recycled);
        try {
            writeRecycleMeta(recycleMetaPath(recycled), originalName, readBundleHeadersQuietly(recycled));
        } catch (IOException ex) {
            log.warn("回收站元数据写入失败: {}", recycled, ex);
        }
    }

    /**
     * 计算回收站中不重复的目标文件名。
     *
     * @param recycleDir   回收站目录
     * @param originalName 原始文件名（basename）
     * @return 不与现有文件冲突的目标路径
     */
    private Path uniqueRecycleTarget(Path recycleDir, String originalName) {
        Path candidate = recycleDir.resolve(originalName).normalize();
        if (!Files.exists(candidate)) {
            return candidate;
        }
        String base = originalName.substring(0, originalName.length() - ".jar".length());
        String stamp = LocalDateTime.now().format(RECYCLE_STAMP);
        candidate = recycleDir.resolve(base + "-" + stamp + ".jar").normalize();
        if (!Files.exists(candidate)) {
            return candidate;
        }
        return recycleDir.resolve(base + "-" + stamp + "-" + System.currentTimeMillis() + ".jar").normalize();
    }

    /**
     * 回收站目录绝对路径。
     *
     * <p>目录名相对部署目录解析，越出部署目录（如 {@code ../}）直接拒绝。</p>
     *
     * @return 回收站目录
     */
    private Path recycleDirectory() {
        String dirName = properties.getRecycle().getDirName();
        if (dirName == null || dirName.isBlank()) {
            dirName = ".recycle";
        }
        Path dir = deployDirectory();
        Path recycleDir = dir.resolve(dirName).normalize();
        if (!recycleDir.startsWith(dir)) {
            throw new IllegalArgumentException("回收站目录名非法: " + dirName);
        }
        return recycleDir;
    }

    /**
     * 回收站条目对应的元数据边车路径。
     *
     * @param jarFile 回收站中的 jar 文件
     * @return 边车文件路径
     */
    private Path recycleMetaPath(Path jarFile) {
        return jarFile.resolveSibling(jarFile.getFileName().toString() + RECYCLE_META_SUFFIX);
    }

    /**
     * 写回收站元数据边车。
     *
     * @param metaPath     边车文件路径
     * @param originalName 删除前的原始文件名
     * @param headers      jar 的 OSGi manifest 头信息
     * @throws IOException 写入失败
     */
    private void writeRecycleMeta(Path metaPath, String originalName, BundleHeaders headers) throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("originalName", originalName);
        meta.put("deletedAt", System.currentTimeMillis());
        meta.put("symbolicName", headers.symbolicName());
        meta.put("version", headers.version());
        Files.writeString(metaPath, Json.toJson(meta), StandardCharsets.UTF_8);
    }

    /**
     * 读取回收站元数据边车（容错版）。
     *
     * @param jarFile 回收站中的 jar 文件
     * @return 元数据键值；边车缺失或损坏时返回尽量少的键
     */
    private Map<String, String> readRecycleMeta(Path jarFile) {
        Map<String, String> meta = new HashMap<>();
        Path metaPath = recycleMetaPath(jarFile);
        if (!Files.isRegularFile(metaPath)) {
            return meta;
        }
        try {
            JsonNode node = Json.parse(Files.readString(metaPath, StandardCharsets.UTF_8));
            meta.put("originalName", node.get("originalName").toStringValue(jarFile.getFileName().toString()));
            meta.put("deletedAt", String.valueOf(node.get("deletedAt").toLongValue(0L)));
            meta.put("symbolicName", node.get("symbolicName").toStringValue(""));
            meta.put("version", node.get("version").toStringValue(""));
        } catch (IOException | RuntimeException ex) {
            log.warn("回收站元数据读取失败: {}", metaPath, ex);
        }
        return meta;
    }

    /**
     * 描述回收站中的一个 jar 条目。
     *
     * @param jarFile 回收站中的 jar 文件
     * @return 条目描述；文件不可读时返回 null（跳过该条目）
     */
    private Map<String, Object> describeRecycleJar(Path jarFile) {
        try {
            Map<String, Object> item = new LinkedHashMap<>();
            Map<String, String> meta = readRecycleMeta(jarFile);
            item.put("fileName", jarFile.getFileName().toString());
            item.put("originalName", meta.getOrDefault("originalName", jarFile.getFileName().toString()));
            item.put("size", sizeQuietly(jarFile));
            long deletedAt = 0L;
            try {
                deletedAt = Long.parseLong(meta.getOrDefault("deletedAt", "0"));
            } catch (NumberFormatException ignored) {
                // 边车损坏时退化用文件修改时间
            }
            if (deletedAt <= 0) {
                deletedAt = lastModifiedQuietly(jarFile);
            }
            item.put("deletedAt", deletedAt);
            item.put("symbolicName", meta.getOrDefault("symbolicName", ""));
            item.put("version", meta.getOrDefault("version", ""));
            return item;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 读取 jar 的 OSGi 头信息（容错版）：缺 manifest 或解析失败返回空串。
     *
     * @param jarPath jar 文件路径
     * @return 符号名与版本（读不到时为空串）
     */
    private BundleHeaders readBundleHeadersQuietly(Path jarPath) {
        try (InputStream input = Files.newInputStream(jarPath); JarInputStream jar = new JarInputStream(input)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return new BundleHeaders("", "");
            }
            String symbolicName = manifest.getMainAttributes().getValue(BUNDLE_SYMBOLIC_NAME);
            symbolicName = symbolicName == null ? "" : symbolicName.split(";", 2)[0].trim();
            String version = manifest.getMainAttributes().getValue(BUNDLE_VERSION);
            version = version == null || version.isBlank() ? "" : version.trim();
            return new BundleHeaders(symbolicName, version);
        } catch (IOException | RuntimeException ex) {
            return new BundleHeaders("", "");
        }
    }

    /**
     * 下载部署目录中的 jar 文件（按文件名）。
     *
     * <p>与按符号名的 bundle 下载互补：bundle 卸载后符号名入口失效，而文件还在
     * 部署目录里——删除前备份、误删后取回都走这里。路径校验与删除同口径：
     * 只取 basename、限定部署目录内、只放行 .jar。</p>
     *
     * @param fileName 部署目录中的文件名（只取 basename）
     * @return jar 文件流
     * @throws IOException 读取文件失败
     */
    @GetMapping("/jars/{fileName}/download")
    public ResponseEntity<Resource> downloadJar(@PathVariable String fileName) throws IOException {
        String name = Paths.get(fileName).getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("只支持下载 .jar 文件: " + name);
        }
        Path dir = deployDirectory();
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw new IllegalArgumentException("非法文件名: " + name);
        }
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("jar 文件不存在: " + name);
        }
        InputStream input = Files.newInputStream(target);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(target))
                .body(new InputStreamResource(input));
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
     * 批量启动 bundle。
     *
     * <p>逐项尽力执行：某一项失败不影响其余项，结果逐项返回。
     * 单项的超时与错误语义与单个启动接口一致；已是 ACTIVE 的 bundle 跳过启动。</p>
     *
     * @param symbolicNames bundle 符号名列表
     * @return 逐项结果与汇总计数
     */
    @PostMapping("/bundles/batch-start")
    public Map<String, Object> batchStart(@RequestBody List<String> symbolicNames) {
        requireManageEnabled();
        synchronized (lifecycleLock) {
            return doBatch("batch-start", symbolicNames, bundle -> {
                if (!bundle.isActive()) {
                    runLifecycleWithTimeout("start", bundle.getSymbolicName(), bundle::start);
                }
            });
        }
    }

    /**
     * 批量停止 bundle。
     *
     * <p>逐项尽力执行；单项超时/失败不影响其余项。</p>
     *
     * @param symbolicNames bundle 符号名列表
     * @return 逐项结果与汇总计数
     */
    @PostMapping("/bundles/batch-stop")
    public Map<String, Object> batchStop(@RequestBody List<String> symbolicNames) {
        requireManageEnabled();
        synchronized (lifecycleLock) {
            return doBatch("batch-stop", symbolicNames,
                    bundle -> runLifecycleWithTimeout("stop", bundle.getSymbolicName(), bundle::stop));
        }
    }

    /**
     * 批量卸载 bundle。
     *
     * <p>语义与单个卸载一致：只把 bundle 从框架里摘掉，不删部署目录里的 jar。
     * 逐项尽力执行；单项失败不影响其余项。</p>
     *
     * @param symbolicNames bundle 符号名列表
     * @return 逐项结果与汇总计数
     */
    @PostMapping("/bundles/batch-uninstall")
    public Map<String, Object> batchUninstall(@RequestBody List<String> symbolicNames) {
        requireManageEnabled();
        synchronized (lifecycleLock) {
            return doBatch("batch-uninstall", symbolicNames, bundle -> {
                if (!launcher.uninstallBundle(bundle.getSymbolicName())) {
                    throw new IllegalStateException("bundle 卸载失败: " + bundle.getSymbolicName());
                }
            });
        }
    }

    /**
     * 刷新框架装配（refresh bundles）。
     *
     * <p>bundle 升级/卸载后，受影响的 bundle 需要一次框架刷新才会按新装配关系重新解析。
     * 本端点发起全量刷新，并可等待完成事件（最多 30 秒），便于升级流程收尾时确认装配已收敛。</p>
     *
     * @param wait 是否等待刷新完成事件（默认 true，最多等 30 秒）
     * @return 刷新结果：initiated=已发起；completed=是否在限时内收到完成事件
     * @throws InterruptedException 等待完成事件时被中断
     */
    @PostMapping("/framework/refresh")
    public Map<String, Object> refreshFramework(@RequestParam(defaultValue = "true") boolean wait)
            throws InterruptedException {
        requireManageEnabled();
        synchronized (lifecycleLock) {
            return doRefreshFramework(wait);
        }
    }

    /**
     * 执行框架装配刷新（调用方须已持有 {@link #lifecycleLock}）。
     *
     * @param wait 是否等待刷新完成事件（最多 30 秒）
     * @return 刷新结果：initiated=已发起；completed=是否在限时内收到完成事件
     * @throws InterruptedException 等待完成事件时被中断
     */
    private Map<String, Object> doRefreshFramework(boolean wait) throws InterruptedException {
        long startedAt = System.currentTimeMillis();
        logPublisher.publish(OsgiLogEvent.start("refresh", "framework"));
        try {
            BundleContext bundleContext = frameworkBundleContext();
            if (bundleContext == null) {
                throw new IllegalStateException("OSGi 框架未启动或上下文不可用，无法执行 refresh");
            }
            Bundle systemBundle = bundleContext.getBundle(0);
            FrameworkWiring wiring = systemBundle == null ? null : systemBundle.adapt(FrameworkWiring.class);
            if (wiring == null) {
                throw new IllegalStateException("当前框架不支持 FrameworkWiring，无法执行 refresh");
            }
            CountDownLatch completed = new CountDownLatch(1);
            wiring.refreshBundles(null, (FrameworkListener) event -> {
                if (event.getType() == FrameworkEvent.PACKAGES_REFRESHED) {
                    completed.countDown();
                }
            });
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("initiated", true);
            if (wait) {
                boolean done = completed.await(30, TimeUnit.SECONDS);
                result.put("completed", done);
                if (!done) {
                    result.put("note", "30 秒内未收到刷新完成事件，刷新可能仍在进行");
                }
            }
            logPublisher.publish(OsgiLogEvent.success("refresh", "framework", startedAt,
                    "已发起 bundle 装配刷新"));
            return result;
        } catch (InterruptedException ex) {
            logPublisher.publish(OsgiLogEvent.failure("refresh", "framework", startedAt, "刷新等待被中断"));
            throw ex;
        } catch (RuntimeException ex) {
            logPublisher.publish(OsgiLogEvent.failure("refresh", "framework", startedAt,
                    "刷新失败: " + rootMessage(ex)));
            throw ex;
        }
    }

    /**
     * 取框架 BundleContext（非 Felix 实现时不可得）。
     *
     * @return 框架 BundleContext，不可用时返回 {@code null}
     */
    private BundleContext frameworkBundleContext() {
        if (launcher instanceof FelixOsgiLauncher felix) {
            return felix.getFrameworkBundleContext();
        }
        return null;
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
     * 上传大小上限校验。
     *
     * <p>在读取/落盘前先拒绝超大文件，错误信息直接给出上限配置项，
     * 便于使用方自行调整。&le;0 表示不限制。</p>
     *
     * @param file 上传文件
     */
    private void requireUploadSizeOk(MultipartFile file) {
        long max = properties.getEndpoint().getMaxUploadBytes();
        if (max <= 0 || file == null) {
            return;
        }
        long size = file.getSize();
        if (size > max) {
            throw new IllegalArgumentException("插件文件超过大小上限: " + size + " > " + max
                    + " 字节（plugin.osgi.endpoint.max-upload-bytes）");
        }
    }

    /**
     * 在独立守护线程上执行生命周期动作，并限时等待。
     *
     * <p>为什么不用调用线程直接跑：{@code Bundle#start()} / {@code Bundle#stop()}
     * 会同步执行 activator，一个等待外部资源的坏插件会把请求线程永久挂住。
     * 这里把动作放到短命守护线程，调用方最多等
     * {@code plugin.osgi.endpoint.lifecycle-timeout-seconds} 秒。</p>
     *
     * <p><b>超时语义</b>：超时只代表「放弃等待」，会尽力中断工作线程；若框架底层
     * 调用不响应中断，该 bundle 的真实状态可能仍不确定（例如稍后才启动完成），
     * 调用方应以查询接口返回的状态为准。</p>
     *
     * @param op           操作名（用于线程名与错误信息）
     * @param symbolicName bundle 符号名（用于线程名与错误信息）
     * @param action       生命周期动作
     */
    private void runLifecycleWithTimeout(String op, String symbolicName, Runnable action) {
        int timeoutSeconds = properties.getEndpoint().getLifecycleTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            action.run();
            return;
        }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "osgi-" + op + "-" + symbolicName);
        worker.setDaemon(true);
        worker.start();
        try {
            worker.join(timeoutSeconds * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(op + " 被中断: " + symbolicName, ex);
        }
        if (worker.isAlive()) {
            worker.interrupt();
            throw new LifecycleTimeoutException(op + " 超过 " + timeoutSeconds
                    + "s 未完成，已放弃等待（bundle 状态可能不确定）: " + symbolicName);
        }
        Throwable error = failure.get();
        if (error != null) {
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (error instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(op + " 失败: " + rootMessage(error), error);
        }
    }

    /**
     * 生命周期操作超时：以 504 返回，与参数类 400 错误区分——
     * 前者说明插件本身启动过慢/挂死，后者说明调用方请求有误。
     *
     * @param ex 超时异常
     * @return 携带原始错误信息的错误体
     */
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    @ExceptionHandler(LifecycleTimeoutException.class)
    public Map<String, Object> handleLifecycleTimeout(LifecycleTimeoutException ex) {
        log.warn("[OSGi] 生命周期操作超时: {}", ex.getMessage());
        return Map.of("error", String.valueOf(ex.getMessage()));
    }

    /**
     * 参数/状态类错误统一映射为 400，并把具体原因写进响应体
     * （如 max-upload-bytes 上限、批量数量上限、bundle 不存在），
     * 避免落入容器兜底变成无信息的 500。
     *
     * @param ex 参数或状态类异常
     * @return 携带原始错误信息的错误体
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Map<String, Object> handleBadRequest(RuntimeException ex) {
        log.warn("[OSGi] 请求被拒绝: {}", ex.getMessage());
        return Map.of("error", String.valueOf(ex.getMessage()));
    }

    /**
     * 生命周期操作在配置时限内未完成的信号异常。
     *
     * <p>单独建类型是为了让 {@code @ExceptionHandler} 能把它与普通
     * {@link IllegalStateException} 区分开，返回更准确的 504。</p>
     */
    private static final class LifecycleTimeoutException extends IllegalStateException {
        private LifecycleTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * 批量操作通用执行器：逐项尽力执行，单项失败不影响其余项。
     *
     * <p>逐项结果（含失败原因）全部返回，调用方无需为「部分成功」再做二次猜测。
     * 目标列表为空或超过 {@link #MAX_BATCH_SIZE} 时直接拒绝：空列表多半是调用方 bug，
     * 超大列表则会长期持有生命周期锁。</p>
     *
     * @param op            操作名（用于日志）
     * @param symbolicNames 目标 bundle 符号名
     * @param action        单项动作
     * @return 逐项结果与汇总计数
     */
    private Map<String, Object> doBatch(String op, List<String> symbolicNames, Consumer<OsgiBundle> action) {
        List<String> names = symbolicNames == null ? List.of() : symbolicNames;
        if (names.isEmpty()) {
            throw new IllegalArgumentException("批量操作目标不能为空");
        }
        if (names.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("批量操作一次最多 " + MAX_BATCH_SIZE + " 个 bundle: " + names.size());
        }
        long startedAt = System.currentTimeMillis();
        logPublisher.publish(OsgiLogEvent.start(op, names.size() + " 个 bundle"));
        List<Map<String, Object>> items = new ArrayList<>();
        int success = 0;
        for (String name : names) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("symbolicName", name);
            try {
                OsgiBundle bundle = requireBundle(name);
                action.accept(bundle);
                item.put("success", true);
                success++;
            } catch (RuntimeException ex) {
                item.put("success", false);
                item.put("error", rootMessage(ex));
            }
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", names.size());
        result.put("successCount", success);
        result.put("failureCount", names.size() - success);
        result.put("items", items);
        logPublisher.publish(OsgiLogEvent.success(op, names.size() + " 个 bundle", startedAt,
                "成功 " + success + " / 失败 " + (names.size() - success)));
        return result;
    }

    /**
     * 按 location 建立当前 bundle 索引。
     *
     * <p>部署目录可能有多个 jar，逐一调用 {@link #findBundleByLocation(String)}
     * 会退化成 O(jar 数 × bundle 数) 的重复全扫；一次遍历建索引后，
     * 每个文件只做一次哈希查找。单个 bundle 的按名/按 location 查找
     * 仍是线性扫描——对一次性查询这就是最优做法。</p>
     *
     * @return location -> bundle 索引
     */
    private Map<String, OsgiBundle> indexByLocation() {
        Map<String, OsgiBundle> index = new HashMap<>();
        for (OsgiBundle bundle : launcher.getBundles()) {
            if (bundle.getLocation() != null) {
                index.putIfAbsent(bundle.getLocation(), bundle);
            }
        }
        return index;
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
            ensureBundleManifest(target, existing.getSymbolicName());
            OsgiBundle updated = launcher.updateBundle(existing.getSymbolicName(), target.toUri().toString());
            if (updated != null && existing.isActive() && !updated.isActive()) {
                runLifecycleWithTimeout("start", updated.getSymbolicName(), updated::start);
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
     * 解析上传 jar 的 OSGi 符号名和版本。
     *
     * <p>缺少 {@code Bundle-SymbolicName} 的普通 jar 不再直接拒绝：
     * 返回空符号名，由安装流程自动补齐 bundle manifest（见
     * {@link #ensureBundleManifest}），实现「普通 SPI jar 免 OSGi 头上传」。</p>
     *
     * @param file 上传文件
     * @return manifest 头信息（符号名可能为空串）
     */
    private BundleHeaders readBundleHeaders(MultipartFile file) {
        String name = jarName(file);
        try (InputStream input = file.getInputStream(); JarInputStream jar = new JarInputStream(input)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return new BundleHeaders("", DEFAULT_BUNDLE_VERSION);
            }
            String symbolicName = manifest.getMainAttributes().getValue(BUNDLE_SYMBOLIC_NAME);
            symbolicName = symbolicName == null ? "" : symbolicName.split(";", 2)[0].trim();
            String version = manifest.getMainAttributes().getValue(BUNDLE_VERSION);
            version = version == null || version.isBlank() ? DEFAULT_BUNDLE_VERSION : version.trim();
            return new BundleHeaders(symbolicName, version);
        } catch (IOException ex) {
            throw new IllegalArgumentException("不是有效的 jar 文件: " + name, ex);
        }
    }

    /**
     * 为缺少 OSGi 头的 jar 补齐 bundle manifest。
     *
     * <p>普通 SPI jar（无 {@code Bundle-SymbolicName}）经 HTTP 上传时自动补齐
     * {@code Bundle-SymbolicName}（由文件名派生或沿用既有 bundle 符号名）、
     * {@code Bundle-Version}（缺省 0.0.0）与 {@code DynamicImport-Package: *}，
     * 使其无需预先编写 OSGi manifest 即可安装进 Felix。已带符号名的 jar
     * 原样保留，不做任何改写。</p>
     *
     * @param jarFile              部署目录中的 jar 文件
     * @param fallbackSymbolicName 兜底符号名（空则不处理）
     * @throws IOException jar 读写失败
     */
    private void ensureBundleManifest(Path jarFile, String fallbackSymbolicName) throws IOException {
        if (fallbackSymbolicName == null || fallbackSymbolicName.isBlank()) {
            return;
        }
        Manifest manifest;
        try (JarFile jar = new JarFile(jarFile.toFile())) {
            manifest = jar.getManifest();
        } catch (IOException ex) {
            throw new IOException("读取 jar manifest 失败: " + jarFile.getFileName(), ex);
        }
        if (manifest == null) {
            manifest = new Manifest();
        }
        Attributes attributes = manifest.getMainAttributes();
        String existingName = attributes.getValue(BUNDLE_SYMBOLIC_NAME);
        if (existingName != null && !existingName.isBlank()) {
            return;
        }
        attributes.putValue(BUNDLE_SYMBOLIC_NAME, fallbackSymbolicName);
        String version = attributes.getValue(BUNDLE_VERSION);
        if (version == null || version.isBlank()) {
            attributes.putValue(BUNDLE_VERSION, DEFAULT_BUNDLE_VERSION);
        }
        attributes.putValue("DynamicImport-Package", "*");
        rewriteJarWithManifest(jarFile, manifest);
        log.info("[OSGi] 已为 jar {} 补齐 bundle manifest: {}:{}, DynamicImport-Package=*",
                jarFile.getFileName(), fallbackSymbolicName, attributes.getValue(BUNDLE_VERSION));
    }

    /**
     * 用给定 manifest 重写 jar（保留其余条目内容）。
     *
     * @param jarFile  jar 文件
     * @param manifest 新 manifest
     * @throws IOException 写入失败
     */
    private void rewriteJarWithManifest(Path jarFile, Manifest manifest) throws IOException {
        Path tmp = jarFile.resolveSibling(jarFile.getFileName() + ".manifest-tmp");
        try (JarInputStream input = new JarInputStream(Files.newInputStream(jarFile));
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(tmp), manifest)) {
            JarEntry entry;
            while ((entry = input.getNextJarEntry()) != null) {
                if (JarFile.MANIFEST_NAME.equals(entry.getName())) {
                    continue;
                }
                output.putNextEntry(new JarEntry(entry.getName()));
                input.transferTo(output);
                output.closeEntry();
            }
        } catch (IOException ex) {
            Files.deleteIfExists(tmp);
            throw ex;
        }
        Files.move(tmp, jarFile, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 由上传文件名派生 bundle 符号名（缺 OSGi 头的 jar 使用）。
     *
     * <p>取文件名去掉路径与 {@code .jar} 后缀，非法字符替换为 {@code -}；
     * 结果为空或全为 {@code -} 时退回 {@code external-spi}。</p>
     *
     * @param fileName 上传文件名
     * @return 派生的符号名
     */
    private String deriveBundleSymbolicName(String fileName) {
        String name = fileName == null ? "" : fileName.trim();
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0) {
            name = name.substring(separator + 1);
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        StringBuilder symbolic = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            symbolic.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? c : '-');
        }
        String result = symbolic.toString();
        if (result.isBlank() || result.chars().allMatch(c -> c == '-')) {
            return "external-spi";
        }
        return result;
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

    /**
     * 组装部署目录 jar 描述信息。
     *
     * <p>这里直接带上「是否仍在框架中」：页面据此把「删除」按钮置灰并给出原因，
     * 用户不必点了才收到「请先卸载」的报错。</p>
     *
     * @param path       jar 文件路径
     * @param byLocation 当前 bundle 的 location 索引（见 {@link #indexByLocation()}）
     * @return 描述信息
     */
    private Map<String, Object> describeJar(Path path, Map<String, OsgiBundle> byLocation) {
        Map<String, Object> item = new LinkedHashMap<>();
        String fileName = path.getFileName().toString();
        item.put("fileName", fileName);
        item.put("size", sizeQuietly(path));
        item.put("lastModified", lastModifiedQuietly(path));
        OsgiBundle installed = byLocation.get(path.toUri().toString());
        item.put("installed", installed != null);
        item.put("symbolicName", installed == null ? null : installed.getSymbolicName());
        item.put("state", installed == null ? null : installed.getState());
        return item;
    }

    /**
     * 读取文件大小，失败时返回 -1（只用于展示，不影响业务判断）。
     *
     * @param path 文件路径
     * @return 字节数，读取失败返回 -1
     */
    private long sizeQuietly(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return -1L;
        }
    }

    /**
     * 读取最后修改时间，失败时返回 0。
     *
     * @param path 文件路径
     * @return 毫秒时间戳，读取失败返回 0
     */
    private long lastModifiedQuietly(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }
}
