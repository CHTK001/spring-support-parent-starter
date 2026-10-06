package com.chua.starter.ai.support.manage.agent;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.service.SysAiAgentService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 智能体安装包服务。
 *
 * <p>对标技能的「安装」能力：智能体以目录形式分发，目录内含 {@code AGENT.md}
 * （YAML frontmatter + 正文），子智能体放在 {@code agents/<name>/AGENT.md}。
 * 安装即把包落到 {@code {nomifun.agents-root}/user/<name>}，并把主/子智能体
 * 写入 {@code sys_ai_agent}（复用 {@link SysAiAgentService}，含运行时重建）。</p>
 *
 * <p>包结构示例：</p>
 * <pre>
 * coordinator/
 * ├── AGENT.md              # 主智能体：frontmatter 声明 name/model/kind/skills/mcp/strategy，正文作为 instruction
 * └── agents/
 *     ├── researcher/AGENT.md
 *     └── writer/AGENT.md
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPackageService {

    /**
     * 智能体包清单文件名（大小写不敏感匹配 AGENT.md）
     */
    public static final String MANIFEST_NAME = "AGENT.md";

    /**
     * 子智能体所在子目录名
     */
    public static final String SUB_AGENT_DIR = "agents";

    private final SysAiAgentService agentService;

    /**
     * 智能体包根目录，布局与技能一致：{@code {root}/user}、{@code {root}/builtin}
     */
    @Value("${nomifun.agents-root:./agents}")
    private String agentsRoot;

    /**
     * 智能体市场地址（供市场源缺省使用）
     */
    @Value("${ai.agents-market-url:}")
    private String agentsMarketUrl;

    /**
     * 把 Spring 解析出的根目录发布为系统属性。
     *
     * <p>市场源实现由 SPI 直接实例化、不参与 Spring 注入，只能读系统属性；
     * 若 {@code nomifun.agents-root} 只写在 {@code application.yml}，两条链路会
     * 指向不同目录（市场枚举 A 处、安装落到 B 处）。这里以 Spring 解析结果为准
     * 统一，保证「枚举 → 安装 → 落盘」始终同一根目录。</p>
     */
    @PostConstruct
    public void publishAgentsRoot() {
        String absolute = root().toString();
        System.setProperty("nomifun.agents-root", absolute);
        log.info("[AgentPackage] agents-root={}", absolute);
    }

    /**
     * 返回智能体包相关目录。
     *
     * @return {root, user, builtin}
     */
    public Map<String, Object> paths() {
        Path root = root();
        Map<String, Object> map = new LinkedHashMap<>(4);
        map.put("root", root.toString());
        map.put("user", root.resolve("user").toString());
        map.put("builtin", root.resolve("builtin").toString());
        return map;
    }

    /**
     * 列出磁盘上已安装的智能体包（user 优先，其次 builtin）。
     *
     * @return 包列表
     */
    public List<Map<String, Object>> listInstalled() {
        List<Map<String, Object>> items = new ArrayList<>();
        Path root = root();
        items.addAll(scan(root.resolve("user"), "user"));
        items.addAll(scan(root.resolve("builtin"), "builtin"));
        return items;
    }

    /**
     * 从本地目录导入（复制）一个智能体包。
     *
     * @param sourcePath 源目录（须含 AGENT.md 或 agents/*&#47;AGENT.md）
     * @return 安装结果
     */
    public Map<String, Object> importFromDirectory(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "source_path 不能为空");
        }
        Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "目录不存在: " + source);
        }
        return install(source);
    }

    /**
     * 从 zip 流导入一个智能体包（zip 内可含一层父目录）。
     *
     * @param in zip 输入流
     * @return 安装结果
     */
    public Map<String, Object> importFromZip(InputStream in) {
        Path tmp;
        try {
            tmp = Files.createTempDirectory("agent-pkg-");
            unzip(in, tmp);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "解压失败: " + e.getMessage());
        }
        Path packageDir = locatePackageRoot(tmp);
        try {
            return install(packageDir);
        } finally {
            deleteQuietly(tmp);
        }
    }

    /**
     * 启用/禁用已安装的智能体包（同步 DB 中主/子智能体 enabled）。
     *
     * @param name    包名
     * @param enabled 是否启用
     * @return 更新结果
     */
    public Map<String, Object> toggle(String name, boolean enabled) {
        Map<String, Object> master = findMasterByName(name);
        if (master == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "智能体不存在: " + name);
        }
        String id = String.valueOf(master.get("agent_id"));
        Map<String, Object> body = new LinkedHashMap<>(2);
        body.put("enabled", enabled);
        agentService.updateAgent(id, body);
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("name", name);
        result.put("agent_id", id);
        result.put("enabled", enabled);
        return result;
    }

    /**
     * 卸载智能体包：删除 DB 记录（级联子智能体）并移除磁盘目录。
     *
     * @param name 包名
     * @return 卸载结果
     */
    public Map<String, Object> uninstall(String name) {
        Map<String, Object> master = findMasterByName(name);
        if (master != null) {
            agentService.deleteAgent(String.valueOf(master.get("agent_id")));
        }
        Path dir = resolveUserPackage(name);
        deleteQuietly(dir);
        Map<String, Object> result = new LinkedHashMap<>(3);
        result.put("name", name);
        result.put("removed_agent", master != null);
        result.put("removed_dir", !Files.exists(dir));
        return result;
    }

    /**
     * 智能体市场地址（供市场源实现取用）。
     *
     * @return 市场地址，未配置时为空
     */
    public String marketUrl() {
        return agentsMarketUrl;
    }

    // ==================== 安装实现 ====================

    /**
     * 安装一个已就绪的包目录。
     *
     * @param source 包目录
     * @return 安装结果
     */
    private Map<String, Object> install(Path source) {
        Path manifest = findManifest(source);
        if (manifest == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "不是合法的智能体包：缺少 " + MANIFEST_NAME);
        }
        Map<String, Object> meta = parseManifest(manifest);
        String name = str(meta.get("name"));
        if (name == null || name.isBlank()) {
            name = source.getFileName().toString();
        }
        // 落盘：复制到 user/<name>
        Path target = resolveUserPackage(name);
        try {
            deleteQuietly(target);
            Files.createDirectories(target.getParent());
            copyDirectory(source, target);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "复制智能体包失败: " + e.getMessage());
        }

        // 写库：主智能体 upsert + 子智能体 upsert
        Map<String, Object> masterBody = toAgentBody(meta);
        Map<String, Object> existing = findMasterByName(name);
        Map<String, Object> master = existing == null
                ? agentService.createAgent(masterBody)
                : agentService.updateAgent(String.valueOf(existing.get("agent_id")), masterBody);
        String masterId = String.valueOf(master.get("agent_id"));

        List<String> subNames = new ArrayList<>();
        Path subDir = target.resolve(SUB_AGENT_DIR);
        if (Files.isDirectory(subDir)) {
            try (Stream<Path> stream = Files.list(subDir)) {
                for (Path child : stream.filter(Files::isDirectory).toList()) {
                    Path subManifest = findManifest(child);
                    if (subManifest == null) {
                        continue;
                    }
                    Map<String, Object> subMeta = parseManifest(subManifest);
                    String subName = str(subMeta.get("name"));
                    if (subName == null || subName.isBlank()) {
                        subName = child.getFileName().toString();
                    }
                    Map<String, Object> subBody = toAgentBody(subMeta);
                    Map<String, Object> existingSub = findSubByName(masterId, subName);
                    if (existingSub == null) {
                        agentService.createSubAgent(masterId, subBody);
                    } else {
                        agentService.updateSubAgent(masterId, String.valueOf(existingSub.get("agent_id")), subBody);
                    }
                    subNames.add(subName);
                }
            } catch (IOException e) {
                log.warn("[AgentPackage] 扫描子智能体失败: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>(6);
        result.put("name", name);
        result.put("agent_id", masterId);
        result.put("installed_path", target.toString());
        result.put("sub_agents", subNames);
        result.put("updated", existing != null);
        log.info("[AgentPackage] 安装智能体包 name={} subs={} updated={}", name, subNames.size(), existing != null);
        return result;
    }

    /**
     * 把清单元数据转换为 Agent 请求体（后端蛇形键）。
     *
     * @param meta 清单元数据
     * @return Agent 请求体
     */
    private Map<String, Object> toAgentBody(Map<String, Object> meta) {
        Map<String, Object> body = new LinkedHashMap<>(12);
        body.put("name", meta.get("name"));
        body.put("description", meta.get("description"));
        body.put("role", meta.get("role"));
        body.put("model", meta.get("model"));
        body.put("provider_id", meta.get("provider"));
        Object instruction = meta.get("instruction");
        body.put("instruction", instruction);
        body.put("system_prompt", instruction);
        body.put("skill_ids", meta.get("skills"));
        body.put("mcp_ids", meta.get("mcp"));
        if (meta.get("strategy") != null) {
            body.put("strategy", meta.get("strategy"));
        }
        return body;
    }

    // ==================== 清单解析 ====================

    /**
     * 解析清单文件：YAML frontmatter + 正文（正文作 instruction 兜底）。
     *
     * <p>支持的 frontmatter 键：{@code name / description / role / model / provider /
     * kind / instruction / skills / mcp / strategy.*}；列表用 {@code [a, b]} 或
     * 多行 {@code - a}，strategy 用缩进子键。</p>
     *
     * @param manifest 清单文件
     * @return 元数据
     */
    public Map<String, Object> parseManifest(Path manifest) {
        String raw;
        try {
            raw = Files.readString(manifest, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "读取清单失败: " + e.getMessage());
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        String body = raw;
        if (raw.startsWith("---")) {
            int end = raw.indexOf("\n---", 3);
            if (end > 0) {
                String front = raw.substring(3, end);
                body = raw.substring(raw.indexOf('\n', end + 1) + 1);
                parseFrontMatter(front, meta);
            }
        }
        String instruction = str(meta.get("instruction"));
        if (instruction == null || instruction.isBlank()) {
            String trimmed = body == null ? "" : body.trim();
            if (!trimmed.isEmpty()) {
                meta.put("instruction", trimmed);
            }
        }
        return meta;
    }

    /**
     * 解析 frontmatter 文本为键值对。
     *
     * @param front frontmatter 文本
     * @param meta  输出元数据
     */
    private void parseFrontMatter(String front, Map<String, Object> meta) {
        List<String> list = new ArrayList<>();
        Map<String, Object> strategy = new LinkedHashMap<>();
        int listIndent = -1;
        for (String line : front.split("\\R")) {
            if (line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            String trimmed = line.trim();
            // 列表项
            if (trimmed.startsWith("- ") && listIndent >= 0) {
                list.add(unquote(trimmed.substring(2).trim()));
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = trimmed.substring(colon + 1).trim();
            // strategy 缩进子键
            if (indent > 0 && "strategy".equals(lastParent(meta))) {
                strategy.put(key, coerce(value));
                continue;
            }
            listIndent = -1;
            if (value.isEmpty()) {
                // 可能是列表或嵌套块的开头
                list = new ArrayList<>();
                meta.put(key, list);
                listIndent = indent;
                if ("strategy".equals(key)) {
                    meta.put("strategy", strategy);
                }
                continue;
            }
            if (value.startsWith("[") && value.endsWith("]")) {
                meta.put(key, splitInline(value.substring(1, value.length() - 1)));
            } else {
                meta.put(key, valueOf(key, unquote(value)));
            }
        }
        if (!strategy.isEmpty()) {
            meta.put("strategy", strategy);
        }
    }

    /**
     * 取当前用于判断 strategy 子块的父键（简化实现：固定返回 strategy 若已存在）。
     *
     * @param meta 元数据
     * @return 父键名
     */
    private String lastParent(Map<String, Object> meta) {
        return meta.containsKey("strategy") ? "strategy" : null;
    }

    /**
     * 按键做标量类型归一（skills/mcp 归为列表，strategy 归为 Map）。
     *
     * @param key   键
     * @param value 字符串值
     * @return 归一后的值
     */
    private Object valueOf(String key, String value) {
        if ("skills".equals(key) || "mcp".equals(key)) {
            List<String> one = new ArrayList<>();
            if (value != null && !value.isBlank()) {
                for (String part : value.split(",")) {
                    one.add(part.trim());
                }
            }
            return one;
        }
        return value;
    }

    /**
     * 标量强制转换（bool/int/字符串）。
     *
     * @param value 原始值
     * @return 转换后的值
     */
    private Object coerce(String value) {
        String v = unquote(value);
        if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
            return Boolean.parseBoolean(v);
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ignore) {
            return v;
        }
    }

    /**
     * 解析 {@code [a, b]} 行内列表。
     *
     * @param inner 括号内文本
     * @return 列表
     */
    private List<String> splitInline(String inner) {
        List<String> list = new ArrayList<>();
        for (String part : inner.split(",")) {
            String v = unquote(part.trim());
            if (!v.isEmpty()) {
                list.add(v);
            }
        }
        return list;
    }

    /**
     * 去掉包裹的引号。
     *
     * @param value 原始值
     * @return 去引号后的值
     */
    private String unquote(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        char c = value.charAt(0);
        if ((c == '"' || c == '\'') && value.charAt(value.length() - 1) == c) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    // ==================== 文件工具 ====================

    /**
     * 定位包根目录（zip 可能多包一层）。
     *
     * @param dir 解压根
     * @return 含清单的目录
     */
    private Path locatePackageRoot(Path dir) {
        if (findManifest(dir) != null) {
            return dir;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path child : stream.filter(Files::isDirectory).toList()) {
                if (findManifest(child) != null) {
                    return child;
                }
            }
        } catch (IOException ignore) {
            // 落到下面报错
        }
        throw new ApiException(ApiErrorCode.INVALID_PARAM, "压缩包内未找到 " + MANIFEST_NAME);
    }

    /**
     * 在目录（含一层子目录）中查找清单文件，大小写不敏感。
     *
     * @param dir 目录
     * @return 清单路径，未找到返回 空
     */
    public Path findManifest(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> MANIFEST_NAME.equalsIgnoreCase(p.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 扫描目录下的智能体包。
     *
     * @param dir    目录
     * @param source user / builtin
     * @return 包列表
     */
    private List<Map<String, Object>> scan(Path dir, String source) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return items;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path child : stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList()) {
                Path manifest = findManifest(child);
                if (manifest == null) {
                    continue;
                }
                Map<String, Object> meta = parseManifest(manifest);
                String name = str(meta.get("name"));
                if (name == null || name.isBlank()) {
                    name = child.getFileName().toString();
                }
                Map<String, Object> db = findMasterByName(name);
                Map<String, Object> item = new LinkedHashMap<>(10);
                item.put("name", name);
                item.put("description", meta.get("description"));
                item.put("model", meta.get("model"));
                item.put("role", meta.get("role"));
                item.put("source", source);
                item.put("location", child.toString());
                item.put("installed", db != null);
                item.put("enabled", db == null || !Boolean.FALSE.equals(db.get("enabled")));
                item.put("agent_id", db == null ? null : db.get("agent_id"));
                item.put("sub_agents", countSubPackages(child));
                items.add(item);
            }
        } catch (IOException e) {
            log.warn("[AgentPackage] 扫描目录失败 {}: {}", dir, e.getMessage());
        }
        return items;
    }

    /**
     * 统计包内子智能体数量。
     *
     * @param packageDir 包目录
     * @return 子智能体数量
     */
    private int countSubPackages(Path packageDir) {
        Path subDir = packageDir.resolve(SUB_AGENT_DIR);
        if (!Files.isDirectory(subDir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(subDir)) {
            return (int) stream.filter(Files::isDirectory)
                    .filter(p -> findManifest(p) != null)
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 解压 zip 到目标目录（防目录穿越）。
     *
     * @param in  输入流
     * @param dir 目标目录
     * @throws IOException 解压失败
     */
    private void unzip(InputStream in, Path dir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = dir.resolve(entry.getName()).normalize();
                if (!target.startsWith(dir)) {
                    throw new IOException("非法的压缩包条目: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * 递归复制目录。
     *
     * @param source 源
     * @param target 目标
     * @throws IOException 复制失败
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path dest = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest);
                }
            }
        }
    }

    /**
     * 静默删除目录（递归）。
     *
     * @param dir 目录
     */
    private void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("[AgentPackage] 删除目录失败 {}: {}", dir, e.getMessage());
        }
    }

    /**
     * 解析 {@code {root}/user} 下的包目录。
     *
     * <p>包名来自清单 frontmatter 或请求路径，可能携带 {@code ..} 段；规范化后
     * 必须仍落在 {@code user} 目录内，否则拒绝（防目录穿越写入/删除）。</p>
     *
     * @param name 包名
     * @return 规范化后的包目录
     */
    private Path resolveUserPackage(String name) {
        Path userRoot = root().resolve("user");
        if (name == null || name.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "包名不能为空");
        }
        Path dir = userRoot.resolve(name).normalize();
        if (!dir.startsWith(userRoot) || dir.getNameCount() <= userRoot.getNameCount()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "非法的包名: " + name);
        }
        return dir;
    }

    /**
     * 包根目录。
     *
     * @return 根目录路径
     */
    private Path root() {
        return Paths.get(agentsRoot).toAbsolutePath().normalize();
    }

    /**
     * 按名称查找主 Agent（parent_id 为空的记录）。
     *
     * @param name 名称
     * @return Agent 记录，未找到返回 空
     */
    private Map<String, Object> findMasterByName(String name) {
        return agentService.listAgents().stream()
                .filter(a -> name.equals(String.valueOf(a.get("name"))))
                .findFirst()
                .orElse(null);
    }

    /**
     * 在主 Agent 下按名称查找子 Agent。
     *
     * @param masterId 主 Agent 标识
     * @param name     子 Agent 名称
     * @return 子 Agent 记录，未找到返回 空
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findSubByName(String masterId, String name) {
        Map<String, Object> master = agentService.getAgent(masterId);
        Object subs = master.get("sub_agents");
        if (!(subs instanceof List<?> list)) {
            return null;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> m && name.equals(String.valueOf(((Map<String, Object>) m).get("name")))) {
                return (Map<String, Object>) m;
            }
        }
        return null;
    }

    /**
     * 对象转字符串（空安全）。
     *
     * @param value 值
     * @return 字符串，空返回 空
     */
    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
