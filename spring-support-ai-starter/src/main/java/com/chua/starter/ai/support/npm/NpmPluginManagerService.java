package com.chua.starter.ai.support.npm;

import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * npm 插件安装管理服务。
 *
 * <p>负责把 npm 包安装到统一目录（{@link NpmPluginProperties#getInstallDir()}，
 * 默认 {@code ${user.dir}/npm-plugins}）、卸载、扫描已装清单，并把安装台账
 * 持久化到 {@code sys_ai_global_setting}（{@code plugins} 组的 {@code npm.registry} 字段），
 * 重启后不丢。安装/卸载通过真实执行 npm 命令完成，不做任何模拟。</p>
 *
 * <p>插件约定：npm 包的默认入口（package.json main 或 index.js）导出
 * {@code onEvent(ev, ctx)} / {@code onResponse(body, ctx)} 即可作为响应流中间件。
 * Node 侧车（{@link NpmPluginHostService}）在运行时按包名 require。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NpmPluginManagerService {

    /**
     * 全局设置分组：与既有插件开关同组，便于统一管理。
     */
    private static final String PLUGIN_GROUP = "plugins";

    /**
     * 安装台账字段：{name: {version, installedAt, enabled}} 的 JSON。
     */
    private static final String REGISTRY_FIELD = "npm.registry";

    /**
     * npm 包名合法性：支持 scoped 包（@scope/name），拒绝路径穿越与空白符。
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("^(@[A-Za-z0-9][A-Za-z0-9-._]*/)?[A-Za-z0-9][A-Za-z0-9-._]*$");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NpmPluginProperties properties;
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * npm 插件安装根目录。
     *
     * @return 安装目录（不存在不自动创建，安装时创建）
     */
    public Path installDir() {
        String configured = properties.getInstallDir();
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath();
        }
        return Paths.get(System.getProperty("user.dir"), "npm-plugins").toAbsolutePath();
    }

    /**
     * node 可执行文件命令。
     *
     * @return 配置的 nodePath，为空时取 {@code node}
     */
    public String nodeCommand() {
        String configured = properties.getNodePath();
        return configured == null || configured.isBlank() ? "node" : configured.trim();
    }

    /**
     * node 所在目录（用于给 npm 子进程补 PATH）。
     *
     * @return nodePath 的父目录；未配置 nodePath 时返回 null（默认已在 PATH）
     */
    public String nodeDirForPath() {
        String configured = properties.getNodePath();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path node = Paths.get(configured.trim()).toAbsolutePath();
        Path parent = node.getParent();
        return parent == null ? null : parent.toString();
    }

    /**
     * 探测 node 是否可用。
     *
     * @return node --version 能否成功执行
     */
    public boolean nodeAvailable() {
        try {
            runCommand(List.of(nodeCommand(), "--version"), 15);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 读取 node 版本号。
     *
     * @return 版本串（如 v22.11.0）；不可用时返回中文原因
     */
    public String nodeVersion() {
        try {
            return runCommand(List.of(nodeCommand(), "--version"), 15).trim();
        } catch (Exception e) {
            return "不可用: " + e.getMessage();
        }
    }

    /**
     * 安装 npm 插件（真实执行 npm install）。
     *
     * @param name    npm 包名（支持 @scope/name）
     * @param version 版本号或 tag，空则装最新版
     * @return success=true 时含 name/version/output；失败含 message
     */
    public synchronized Map<String, Object> install(String name, String version) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            result.put("success", false);
            result.put("message", "非法的 npm 包名: " + name);
            return result;
        }
        String spec = version == null || version.isBlank() ? name : name + "@" + version.trim();
        try {
            Files.createDirectories(installDir());
            List<String> cmd = new ArrayList<>(npmCommand());
            cmd.add("install");
            cmd.add(spec);
            cmd.add("--prefix");
            cmd.add(installDir().toString());
            cmd.add("--no-audit");
            cmd.add("--no-fund");
            cmd.add("--loglevel=error");
            if (properties.getNpmRegistry() != null && !properties.getNpmRegistry().isBlank()) {
                cmd.add("--registry");
                cmd.add(properties.getNpmRegistry().trim());
            }
            String output = runCommand(cmd, properties.getInstallTimeoutSeconds());
            Map<String, Object> pkg = readPackageJson(name);
            String installedVersion = pkg == null ? version : Objects.toString(pkg.get("version"), version);
            updateRegistry(name, installedVersion, true);
            result.put("success", true);
            result.put("version", installedVersion);
            result.put("output", tail(output, 2000));
            log.info("[NpmPlugin] 安装成功: {}@{}", name, installedVersion);
        } catch (Exception e) {
            log.warn("[NpmPlugin] 安装失败: {} - {}", name, e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 卸载 npm 插件（真实执行 npm uninstall）。
     *
     * @param name npm 包名
     * @return success 与 message/output
     */
    public synchronized Map<String, Object> uninstall(String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            result.put("success", false);
            result.put("message", "非法的 npm 包名: " + name);
            return result;
        }
        try {
            List<String> cmd = new ArrayList<>(npmCommand());
            cmd.add("uninstall");
            cmd.add(name);
            cmd.add("--prefix");
            cmd.add(installDir().toString());
            cmd.add("--no-audit");
            cmd.add("--no-fund");
            cmd.add("--loglevel=error");
            String output = runCommand(cmd, properties.getInstallTimeoutSeconds());
            updateRegistry(name, null, false);
            result.put("success", true);
            result.put("output", tail(output, 2000));
            log.info("[NpmPlugin] 卸载成功: {}", name);
        } catch (Exception e) {
            log.warn("[NpmPlugin] 卸载失败: {} - {}", name, e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 扫描已安装插件（node_modules 一级 + scoped 二级），并合并安装台账元数据。
     *
     * @return 插件条目列表：name/version/description/enabled/installedAt
     */
    public List<Map<String, Object>> listInstalled() {
        Map<String, Map<String, Object>> registry = readRegistry();
        List<Map<String, Object>> list = new ArrayList<>();
        Path nodeModules = installDir().resolve("node_modules");
        if (!Files.isDirectory(nodeModules)) {
            return list;
        }
        try (Stream<Path> entries = Files.list(nodeModules)) {
            List<Path> sorted = entries.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
            for (Path entry : sorted) {
                String fileName = entry.getFileName().toString();
                if (fileName.startsWith("@")) {
                    try (Stream<Path> scoped = Files.list(entry)) {
                        for (Path sub : scoped.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                            addPackage(list, registry, sub);
                        }
                    } catch (IOException e) {
                        log.debug("[NpmPlugin] 跳过不可读目录: {}", entry);
                    }
                } else {
                    addPackage(list, registry, entry);
                }
            }
        } catch (IOException e) {
            log.warn("[NpmPlugin] 扫描已安装插件失败: {}", e.getMessage());
        }
        return list;
    }

    /**
     * 校验插件可被 Node require（目录存在且入口可解析）。
     *
     * @param name npm 包名
     * @return 就绪返回 true
     */
    public boolean isResolvable(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        Path dir = installDir().resolve(Paths.get("node_modules", name));
        if (!Files.isDirectory(dir)) {
            return false;
        }
        return Files.isRegularFile(dir.resolve("index.js")) || readPackageJson(name) != null;
    }

    /**
     * npm 命令（含 Windows 下的 npm.cmd 兜底）。
     *
     * @return npm 命令首词
     */
    public List<String> npmCommand() {
        String configured = properties.getNpmPath();
        if (configured != null && !configured.isBlank()) {
            return List.of(configured.trim());
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return List.of(windows ? "npm.cmd" : "npm");
    }

    /**
     * 执行外部命令并回收输出。
     *
     * @param cmd            命令与参数
     * @param timeoutSeconds 超时秒数
     * @return 合并后的标准输出/错误文本
     * @throws IOException          进程启动失败
     * @throws InterruptedException 等待被中断
     * @throws IllegalStateException 非零退出或超时
     */
    private String runCommand(List<String> cmd, long timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        String nodeDir = nodeDirForPath();
        if (nodeDir != null) {
            pb.environment().put("PATH", nodeDir + File.pathSeparator + pb.environment().getOrDefault("PATH", ""));
        }
        pb.environment().put("npm_config_cache", installDir().resolve(".npm-cache").toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        Thread pump = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (output.length() < 100_000) {
                        output.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException ignored) {
                // 进程结束时流自然关闭
            }
        }, "npm-cmd-pump");
        pump.setDaemon(true);
        pump.start();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("命令执行超时(" + timeoutSeconds + "s): " + String.join(" ", cmd));
        }
        pump.join(5000);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("命令失败(exit=" + process.exitValue() + "): " + tail(output.toString(), 1500));
        }
        return output.toString();
    }

    /**
     * 读取某插件的 package.json。
     *
     * @param name npm 包名
     * @return 解析后的对象；不存在返回 null
     */
    private Map<String, Object> readPackageJson(String name) {
        Path pkgFile = installDir().resolve(Paths.get("node_modules", name, "package.json"));
        if (!Files.isRegularFile(pkgFile)) {
            return null;
        }
        try {
            return MAPPER.readValue(Files.readString(pkgFile, StandardCharsets.UTF_8), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.debug("[NpmPlugin] 读取 package.json 失败: {} - {}", pkgFile, e.getMessage());
            return null;
        }
    }

    /**
     * 把单个 node_modules 包目录加入清单。
     *
     * @param list     输出列表
     * @param registry 安装台账
     * @param dir      包目录
     */
    private void addPackage(List<Map<String, Object>> list, Map<String, Map<String, Object>> registry, Path dir) {
        String name = dir.getFileName().toString();
        String scopedName = dir.getParent().getFileName().toString().startsWith("@")
                ? dir.getParent().getFileName() + "/" + name : name;
        Map<String, Object> pkg = readPackageJson(scopedName);
        if (pkg == null) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", scopedName);
        item.put("version", pkg.get("version"));
        item.put("description", pkg.get("description"));
        Map<String, Object> meta = registry.get(scopedName);
        item.put("enabled", meta == null || !Boolean.FALSE.equals(meta.get("enabled")));
        item.put("installedAt", meta == null ? null : meta.get("installedAt"));
        item.put("tracked", meta != null);
        list.add(item);
    }

    /**
     * 读取安装台账。
     *
     * @return name → {version, installedAt, enabled}
     */
    private Map<String, Map<String, Object>> readRegistry() {
        String json = globalSettingService.getValue(PLUGIN_GROUP, REGISTRY_FIELD).orElse(null);
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> raw = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            Map<String, Map<String, Object>> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> me : m.entrySet()) {
                        copy.put(String.valueOf(me.getKey()), me.getValue());
                    }
                    out.put(e.getKey(), copy);
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("[NpmPlugin] 解析安装台账失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 更新安装台账并持久化到全局设置。
     *
     * @param name     npm 包名
     * @param version  版本（卸载时忽略）
     * @param install  true=登记，false=移除
     */
    private void updateRegistry(String name, String version, boolean install) {
        Map<String, Map<String, Object>> registry = readRegistry();
        if (install) {
            Map<String, Object> meta = registry.computeIfAbsent(name, k -> new LinkedHashMap<>());
            meta.put("version", version);
            meta.put("installedAt", Instant.now().toString());
            meta.put("enabled", true);
        } else {
            registry.remove(name);
        }
        try {
            globalSettingService.upsertValue(PLUGIN_GROUP, REGISTRY_FIELD, MAPPER.writeValueAsString(registry), "npm 插件安装台账");
        } catch (Exception e) {
            log.warn("[NpmPlugin] 写入安装台账失败: {}", e.getMessage());
        }
    }

    /**
     * 截断长文本，保留末尾（npm 报错通常在末尾）。
     *
     * @param text 原文
     * @param max  最大长度
     * @return 截断后的文本
     */
    private String tail(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : "..." + trimmed.substring(trimmed.length() - max);
    }
}
