package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.skill.SkillDefinition;
import com.chua.common.support.datasearch.skill.model.SkillMarketListing;
import com.chua.common.support.datasearch.skill.spi.SkillOfflineProvider;
import com.chua.common.support.datasearch.skill.spi.SkillOnlineProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.chat.ChatSkillSupport;
import com.chua.starter.ai.support.market.SkillMarketEntry;
import com.chua.starter.ai.support.market.SkillMarketProvider;
import com.chua.starter.ai.support.service.SkillCatalogService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * 技能来源清单服务实现。
 *
 * <p>本地扫描需真实读取磁盘，按 60 秒缓存「工具 → 技能条目」快照，开关变更后立即作废；
 * 在线聚合检索为实时外部请求，不缓存。单个来源失败只告警，不影响其余来源。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillCatalogServiceImpl implements SkillCatalogService {

    /**
     * 设置分组
     */
    private static final String GROUP = "skills";

    /**
     * 本地扫描快照缓存有效期
     */
    private static final long LOCAL_CACHE_TTL_MILLIS = 60_000L;

    /**
     * 在线聚合检索每源最多返回条数
     */
    private static final int AGG_LIMIT = 12;

    private final SysAiGlobalSettingService globalSettingService;
    private final ChatSkillSupport chatSkillSupport;

    /**
     * 技能根目录（项目内置 + 用户安装）
     */
    @Value("${nomifun.skills-root:./skills}")
    private String skillsRoot;

    /**
     * Agent 技能根目录（Agent 生态提供的离线技能，如 ~/.agents/skills）
     */
    @Value("${nomifun.skills-agent-root:~/.agents/skills}")
    private String skillsAgentRoot;

    /**
     * 本地快照：工具键 → 技能条目
     */
    private volatile Map<String, List<Map<String, Object>>> localSnapshot;
    private volatile long localSnapshotAt;

    // ============================ 配置 ============================

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 本地工具来源（全部 43 种，含未安装）
        List<Map<String, Object>> local = new ArrayList<>();
        Map<String, SkillOfflineProvider> off = offlineProviders();
        Map<String, List<Map<String, Object>>> snapshot = localSnapshotMap();
        for (SkillOfflineProvider provider : off.values()) {
            String tool = safeName(provider);
            boolean installed = safeInstalled(provider);
            boolean enabled = isLocalEnabled(tool);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", "local_" + tool);
            item.put("tool", tool);
            item.put("name", tool);
            item.put("installed", installed);
            item.put("enabled", enabled);
            item.put("count", installed && enabled ? snapshot.getOrDefault(tool, List.of()).size() : 0);
            local.add(item);
        }

        // 在线市场（内置官网）
        Map<String, Object> market = new LinkedHashMap<>();
        market.put("key", "market");
        market.put("enabled", isMarketEnabled());

        // 在线聚合源
        List<Map<String, Object>> aggs = new ArrayList<>();
        for (SkillOnlineProvider provider : onlineProviders().values()) {
            String src = safeName(provider);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", "agg_" + src);
            item.put("source", src);
            item.put("name", src);
            item.put("enabled", isAggEnabled(src));
            aggs.add(item);
        }

        result.put("local", local);
        result.put("market", market);
        result.put("aggregators", aggs);
        result.put("localCount", local.size());
        result.put("aggregatorCount", aggs.size());
        return result;
    }

    @Override
    public void updateConfig(Map<String, Boolean> config) {
        if (config == null) {
            return;
        }
        for (Map.Entry<String, Boolean> entry : config.entrySet()) {
            String key = entry.getKey();
            Boolean value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }
            try {
                globalSettingService.upsertValue(GROUP, key, String.valueOf(value), "Skill source " + key);
            } catch (Exception e) {
                log.warn("[SkillCatalog] 保存来源开关失败 key={}: {}", key, e.getMessage());
            }
        }
        refresh();
    }

    // ============================ 本地检索 ============================

    @Override
    public List<Map<String, Object>> listLocal(String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : localSnapshotMap().entrySet()) {
            String tool = entry.getKey();
            if (!isLocalEnabled(tool)) {
                continue;
            }
            for (Map<String, Object> raw : entry.getValue()) {
                String name = String.valueOf(raw.get("name"));
                String desc = raw.get("description") == null ? "" : String.valueOf(raw.get("description"));
                if (!kw.isEmpty() && !(name.toLowerCase(Locale.ROOT).contains(kw) || desc.toLowerCase(Locale.ROOT).contains(kw))) {
                    continue;
                }
                String dedupeKey = name.toLowerCase(Locale.ROOT);
                Map<String, Object> merged = byName.get(dedupeKey);
                if (merged == null) {
                    merged = new LinkedHashMap<>(raw);
                    List<String> sources = new ArrayList<>();
                    sources.add(tool);
                    merged.put("sources", sources);
                    merged.put("installed", isInstalledHere(name));
                    byName.put(dedupeKey, merged);
                } else {
                    @SuppressWarnings("unchecked")
                    List<String> sources = (List<String>) merged.get("sources");
                    if (!sources.contains(tool)) {
                        sources.add(tool);
                    }
                }
            }
        }
        return new ArrayList<>(byName.values());
    }

    // ============================ 在线聚合检索 ============================

    @Override
    public List<Map<String, Object>> searchAggregators(String keyword) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return result;
        }
        for (SkillOnlineProvider provider : onlineProviders().values()) {
            String src = safeName(provider);
            if (!isAggEnabled(src)) {
                continue;
            }
            try {
                List<SkillMarketListing> listings = provider.searchMarket(keyword.trim(), 0, AGG_LIMIT, "zh");
                if (listings == null) {
                    continue;
                }
                for (SkillMarketListing l : listings) {
                    if (l == null) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", l.getId());
                    item.put("name", l.getName());
                    item.put("description", l.getDescription());
                    item.put("source", src);
                    item.put("url", l.getUrl());
                    item.put("path", l.getPath());
                    item.put("installs", l.getInstalls());
                    item.put("stars", l.getStars());
                    result.add(item);
                }
            } catch (Exception e) {
                log.warn("[SkillCatalog] 聚合源 {} 检索失败: {}", src, e.getMessage());
            }
        }
        return result;
    }

    // ============================ 在线市场 ============================

    @Override
    public List<SkillMarketEntry> listMarket(String keyword) {
        // 总开关关闭则直接返回空
        if (!isMarketEnabled()) {
            return new ArrayList<>();
        }
        List<SkillMarketEntry> aggregated = new ArrayList<>();
        ServiceLoader<SkillMarketProvider> loader = ServiceLoader.load(SkillMarketProvider.class);
        for (SkillMarketProvider provider : loader) {
            String code = provider.code();
            boolean enabled;
            try {
                enabled = globalSettingService.getBoolean(GROUP, "market_" + code, provider.defaultEnabled());
            } catch (Exception e) {
                enabled = provider.defaultEnabled();
            }
            if (!enabled) {
                continue;
            }
            try {
                List<SkillMarketEntry> entries = provider.list(keyword);
                if (entries != null) {
                    aggregated.addAll(entries);
                }
            } catch (Exception e) {
                log.warn("[SkillCatalog] 市场源 {} 加载失败: {}", code, e.getMessage());
            }
        }
        return aggregated;
    }

    // ============================ 内置技能 ============================

    /**
     * 内置技能目录 → 市场分类映射（SKILL.md frontmatter 显式声明 category 时优先）
     */
    private static final Map<String, String> BUILTIN_CATEGORY = Map.ofEntries(
            Map.entry("docx", "文档处理"),
            Map.entry("excel", "文档处理"),
            Map.entry("pdf", "文档处理"),
            Map.entry("agent-browser", "浏览器自动化"),
            Map.entry("playwright", "浏览器自动化"),
            // 研发效能
            Map.entry("java-dev", "研发效能"),
            Map.entry("go-dev", "研发效能"),
            Map.entry("rust-dev", "研发效能"),
            Map.entry("spring-boot-dev", "研发效能"),
            Map.entry("graphql-api", "研发效能"),
            Map.entry("websocket-dev", "研发效能"),
            Map.entry("microservices", "研发效能"),
            Map.entry("message-queue", "研发效能"),
            Map.entry("algorithm-guide", "研发效能"),
            Map.entry("ci-cd", "研发效能"),
            // 数据库 / 数据分析
            Map.entry("sql-optimization", "数据库"),
            Map.entry("redis-dev", "数据库"),
            Map.entry("pandas-guide", "数据分析"),
            Map.entry("sql-cookbook", "数据分析"),
            Map.entry("data-visualization", "数据分析"),
            Map.entry("statistics-basics", "数据分析"),
            Map.entry("etl-guide", "数据分析"),
            // 运维与安全
            Map.entry("performance-tuning", "运维部署"),
            Map.entry("linux-ops", "运维部署"),
            Map.entry("nginx-config", "运维部署"),
            Map.entry("k8s-ops", "运维部署"),
            Map.entry("security-hardening", "安全审计"),
            // 文档处理
            Map.entry("tech-writing", "文档处理"),
            Map.entry("api-doc-writing", "文档处理"),
            Map.entry("business-writing", "文档处理"),
            Map.entry("note-taking", "文档处理"),
            // 办公协作
            Map.entry("okr-guide", "办公协作"),
            Map.entry("project-plan", "办公协作"),
            Map.entry("time-management", "办公协作"),
            // 运营增长
            Map.entry("seo-guide", "营销运营"),
            Map.entry("content-marketing", "营销运营"),
            Map.entry("user-growth", "营销运营"),
            // 教育学习
            Map.entry("study-plan", "教育学习"),
            Map.entry("feynman-learning", "教育学习"),
            Map.entry("english-learning", "教育学习"),
            Map.entry("interview-prep", "教育学习"),
            // 生活规划
            Map.entry("cooking-guide", "生活规划"),
            Map.entry("fitness-plan", "生活规划"),
            Map.entry("finance-basics", "生活规划"),
            Map.entry("habit-building", "生活规划"));

    @Override
    public List<Map<String, Object>> listBuiltin() {
        List<Map<String, Object>> result = new ArrayList<>();
        // 项目捆绑的内置技能（技能根目录下的 builtin 子目录）
        Path builtinRoot = Paths.get(skillsRoot).resolve("builtin");
        result.addAll(listBuiltinsFrom(builtinRoot, "", "builtin"));
        // Agent 生态技能（用户主目录下的 .agents/skills），同目展示、独立来源标记
        Path agentRoot = resolveAgentRoot();
        if (agentRoot != null && Files.isDirectory(agentRoot)) {
            result.addAll(listBuiltinsFrom(agentRoot, "agent:", "agent"));
        }
        return result;
    }

    @Override
    public Map<String, Object> getBuiltinDoc(String id) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = id == null ? "" : id.trim();
        if (key.isEmpty()) {
            result.put("found", false);
            result.put("message", "技能 id 不能为空");
            return result;
        }
        // 仅允许目录名字面量，杜绝 ../ 穿越技能根目录
        if (key.contains("..") || key.contains("/") || key.contains("\\")) {
            result.put("found", false);
            result.put("message", "非法技能 id: " + key);
            return result;
        }
        String raw = key.startsWith("agent:") ? key.substring("agent:".length()) : key;
        List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get(skillsRoot).resolve("builtin").resolve(raw));
        Path agentRoot = resolveAgentRoot();
        if (agentRoot != null) {
            candidates.add(agentRoot.resolve(raw));
        }
        for (Path dir : candidates) {
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }
            Path md = dir.resolve("SKILL.md");
            if (!Files.isRegularFile(md)) {
                md = dir.resolve("skill.md");
            }
            if (!Files.isRegularFile(md)) {
                continue;
            }
            try {
                String content = Files.readString(md);
                Map<String, String> front = parseSimpleFrontmatter(content);
                result.put("found", true);
                result.put("id", key);
                result.put("name", front.getOrDefault("title", front.getOrDefault("name", raw)));
                result.put("description", front.getOrDefault("description", ""));
                result.put("content", content);
                return result;
            } catch (IOException e) {
                log.warn("[SkillCatalog] 读取内置技能文档失败 id={}: {}", key, e.getMessage());
            }
        }
        result.put("found", false);
        result.put("message", "内置技能不存在: " + key);
        return result;
    }

    /**
     * 把配置中的 Agent 技能根目录转成可扫描的路径（支持 ~ 展开与空值禁用）。
     *
     * @return 技能根目录，未配置/不存在返回 null
     */
    private Path resolveAgentRoot() {
        String root = skillsAgentRoot == null ? null : skillsAgentRoot.strip();
        if (root == null || root.isEmpty()) {
            log.info("[SkillCatalog] 未配置 Agent 技能根目录，跳过扫描");
            return null;
        }
        try {
            if (root.startsWith("~")) {
                // `~` 展开为用户主目录；别的地方约定写法为 `~/.agents/skills`，展开时要去掉主目录后面紧跟的 `/`，
                // 否则 Windows 下 Path.resolve 会把它当成根相对路径处理，得到 `C:\.agents\skills` 而不是 `C:\Users\*.agents\skills`
                String sub = root.substring(1).replaceFirst("^/{1,2}", "");
                if (sub.isEmpty()) {
                    log.warn("[SkillCatalog] Agent 技能根目录未指定子路径，跳过扫描");
                    return null;
                }
                root = Paths.get(System.getProperty("user.home", "."))
                        .resolve(sub)
                        .toString();
            }
            return Paths.get(root);
        } catch (InvalidPathException e) {
            log.warn("[SkillCatalog] 无法解析 Agent 技能根目录 {}: {}", skillsAgentRoot, e.getMessage());
            return null;
        }
    }

    /**
     * 扫描单个技能目录，产出 {id, source, name, description, labels, version} 等字段。
     *
     * @param root    技能根目录；dir 为 null 时为空结果（目录不存在时不抛异常）
     * @param idPrefix id 前缀（项目内置为空，Agent 为 agent:）
     * @param source  来源标签
     * @return 技能条目清单
     */
    private List<Map<String, Object>> listBuiltinsFrom(Path root, String idPrefix, String source) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) {
            return result;
        }
        try (Stream<Path> stream = Files.list(root)) {
            List<Path> dirs = stream.filter(Files::isDirectory).sorted().toList();
            for (Path dir : dirs) {
                Path md = dir.resolve("SKILL.md");
                if (!Files.isRegularFile(md)) {
                    md = dir.resolve("skill.md");
                }
                String id = idPrefix + dir.getFileName().toString();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", id);
                item.put("source", source);
                item.put("builtin", true);
                String idOrName = dir.getFileName().toString();
                Map<String, String> front = new LinkedHashMap<>();
                if (Files.isRegularFile(md)) {
                    try {
                        String content = Files.readString(md);
                        front = parseSimpleFrontmatter(content);
                        // 项目内置以 title 为准，Agent 技能没有 title 则取 name（两者均未声明时取目录名）
                        item.put("name", front.getOrDefault("title", front.getOrDefault("name", idOrName)));
                        item.put("description", front.getOrDefault("description", ""));
                        if (front.get("version") != null) {
                            item.put("version", front.get("version"));
                        }
                        if (front.get("icon") != null && !front.get("icon").isEmpty()) {
                            item.put("icon", front.get("icon"));
                        }
                    } catch (IOException e) {
                        item.put("name", idOrName);
                        item.put("description", "");
                    }
                } else {
                    item.put("name", idOrName);
                    item.put("description", "");
                }
                // 分类：frontmatter 显式声明优先，其次内置映射，最后「内置工具」
                String frontCategory = front.get("category");
                String category = frontCategory == null || frontCategory.isEmpty()
                        ? BUILTIN_CATEGORY.getOrDefault(idOrName, "内置工具") : frontCategory;
                List<String> labels = new ArrayList<>();
                labels.add(category);
                item.put("labels", labels);
                item.put("category", category);
                // 官方文档地址：本服务 doc 端点（完整 SKILL.md，不截断）
                item.put("detailUrl", "/v2/skills/catalog/builtin/" + id + "/doc");
                result.add(item);
            }
        } catch (IOException e) {
            log.warn("[SkillCatalog] 扫描{}技能失败: {}", source, e.getMessage());
        }
        return result;
    }

    /**
     * 极简 YAML frontmatter 解析：仅取顶层标量键值（title/description/version/name）。
     * @param content 字符串
     * @return 返回值
    */
    private Map<String, String> parseSimpleFrontmatter(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        if (content == null) {
            return result;
        }
        String text = content.stripLeading();
        if (!text.startsWith("---")) {
            return result;
        }
        int firstNl = text.indexOf('\n', 3);
        if (firstNl < 0) {
            return result;
        }
        int closing = text.indexOf("\n---", firstNl);
        String head = closing >= 0 ? text.substring(firstNl + 1, closing) : text.substring(firstNl + 1);
        for (String line : head.split("\n")) {
            String trimmed = line.trim();
            int indent = line.length() - line.stripLeading().length();
            // 跳过空行、注释、列表项与嵌套（缩进）键
            if (trimmed.isEmpty() || trimmed.startsWith("#") || indent > 0 || trimmed.startsWith("-")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!key.isBlank() && !value.isBlank()) {
                result.put(key, value);
            }
        }
        return result;
    }

    // ============================ 本地安装 ============================

    @Override
    public Map<String, Object> installLocal(String tool, String name, boolean force) {
        Map<String, Object> result = new LinkedHashMap<>();
        SkillOfflineProvider provider = null;
        for (SkillOfflineProvider p : offlineProviders().values()) {
            if (safeName(p).equalsIgnoreCase(tool)) {
                provider = p;
                break;
            }
        }
        if (provider == null) {
            return fail(result, "未找到本地来源: " + tool);
        }
        Path src;
        try {
            src = provider.resolveSkillPath(name);
        } catch (Exception e) {
            return fail(result, "无法定位技能: " + name);
        }
        if (src == null || !Files.exists(src)) {
            return fail(result, "技能源文件不存在: " + name);
        }

        Path userRoot = userRoot();
        String safeDir = sanitize(name);
        try {
            if (Files.isDirectory(src)) {
                Path dest = userRoot.resolve(safeDir);
                if (Files.exists(dest)) {
                    if (!force) {
                        return fail(result, "技能已存在，勾选强制覆盖可替换: " + name);
                    }
                    deleteTree(dest);
                }
                copyTree(src, dest);
            } else {
                Path destDir = userRoot.resolve(safeDir);
                if (Files.exists(destDir)) {
                    if (!force) {
                        return fail(result, "技能已存在，勾选强制覆盖可替换: " + name);
                    }
                    deleteTree(destDir);
                }
                Files.createDirectories(destDir);
                Files.copy(src, destDir.resolve("SKILL.md"), StandardCopyOption.REPLACE_EXISTING);
            }
            chatSkillSupport.invalidate();
            result.put("success", true);
            result.put("message", "已安装 " + name);
            return result;
        } catch (IOException e) {
            log.warn("[SkillCatalog] 复制本地技能失败 name={}: {}", name, e.getMessage());
            return fail(result, "安装失败: " + e.getMessage());
        }
    }

    // ============================ 本地快照 ============================

    /**
     * 取本地快照，过期重建。
     * @return 返回值
    */
    private Map<String, List<Map<String, Object>>> localSnapshotMap() {
        Map<String, List<Map<String, Object>>> snapshot = localSnapshot;
        if (snapshot != null && System.currentTimeMillis() - localSnapshotAt < LOCAL_CACHE_TTL_MILLIS) {
            return snapshot;
        }
        synchronized (this) {
            snapshot = localSnapshot;
            if (snapshot != null && System.currentTimeMillis() - localSnapshotAt < LOCAL_CACHE_TTL_MILLIS) {
                return snapshot;
            }
            snapshot = scanLocal();
            localSnapshot = snapshot;
            localSnapshotAt = System.currentTimeMillis();
            return snapshot;
        }
    }

    /**
     * 扫描所有已安装本地工具的技能。
     * @return 返回值
    */
    private Map<String, List<Map<String, Object>>> scanLocal() {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (SkillOfflineProvider provider : offlineProviders().values()) {
            String tool = safeName(provider);
            List<Map<String, Object>> items = new ArrayList<>();
            if (!safeInstalled(provider)) {
                grouped.put(tool, items);
                continue;
            }
            try {
                List<SkillDefinition> skills = provider.listAgentSkills();
                if (skills != null) {
                    for (SkillDefinition skill : skills) {
                        if (skill == null) {
                            continue;
                        }
                        Path path = null;
                        try {
                            path = provider.resolveSkillPath(skill.getName());
                        } catch (Exception ignored) {
                            // 路径不可解析时仅保留名称与描述
                        }
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", skill.getName());
                        item.put("description", skill.getDescription());
                        item.put("localPath", path == null ? null : path.toAbsolutePath().toString());
                        items.add(item);
                    }
                }
            } catch (Exception e) {
                log.warn("[SkillCatalog] 本地工具 {} 扫描失败: {}", tool, e.getMessage());
            }
            grouped.put(tool, items);
        }
        return grouped;
    }

    /**
     * 作废本地快照。
     */
    private void refresh() {
        localSnapshot = null;
        localSnapshotAt = 0L;
    }

    // ============================ 开关读取 ============================

    /**
     * is Local Enabled
     * @param tool 字符串
     * @return 返回值
    */
    private boolean isLocalEnabled(String tool) {
        return globalSettingService.getBoolean(GROUP, "local_" + tool, true);
    }

    /**
     * is Market Enabled
     * @return 返回值
    */
    private boolean isMarketEnabled() {
        return globalSettingService.getBoolean(GROUP, "market", true);
    }

    /**
     * is Agg Enabled
     * @param src 字符串
     * @return 返回值
    */
    private boolean isAggEnabled(String src) {
        return globalSettingService.getBoolean(GROUP, "agg_" + src, false);
    }

    // ============================ SPI 加载 ============================

    /**
     * offline Providers
     * @return 返回值
    */
    private Map<String, SkillOfflineProvider> offlineProviders() {
        try {
            Map<String, SkillOfflineProvider> map = ServiceProvider.of(SkillOfflineProvider.class).list();
            return map != null ? map : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("[SkillCatalog] 加载本地技能源失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * online Providers
     * @return 返回值
    */
    private Map<String, SkillOnlineProvider> onlineProviders() {
        try {
            Map<String, SkillOnlineProvider> map = ServiceProvider.of(SkillOnlineProvider.class).list();
            return map != null ? map : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("[SkillCatalog] 加载在线聚合源失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ============================ 工具方法 ============================

    /**
     * user Root
     * @return 返回值
    */
    private Path userRoot() {
        Path p = Paths.get(skillsRoot).resolve("user");
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            log.warn("[SkillCatalog] 创建 user 目录失败: {}", e.getMessage());
        }
        return p;
    }

    /**
     * is Installed Here
     * @param name 字符串
     * @return 返回值
    */
    private boolean isInstalledHere(String name) {
        return Files.exists(userRoot().resolve(sanitize(name)));
    }

    /**
     * safe Name
     * @param provider 对象
     * @return 返回值
    */
    private static String safeName(Object provider) {
        try {
            return String.valueOf(provider.getClass().getMethod("name").invoke(provider));
        } catch (Exception e) {
            return provider.getClass().getSimpleName();
        }
    }

    /**
     * safe Installed
     * @param provider 参数
     * @return 返回值
    */
    private static boolean safeInstalled(SkillOfflineProvider provider) {
        try {
            return provider.isInstalled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 技能目录名仅保留字母数字、下划线、短横线。
     * @param name 字符串
     * @return 返回值
    */
    private static String sanitize(String name) {
        String cleaned = name.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return cleaned.isBlank() ? "skill" : cleaned;
    }

    /**
     * copy Tree
     * @param dest 路径
     * @param src 路径
     * @return 返回值
     * @throws IOException 异常
    */
    private static void copyTree(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir,
                    BasicFileAttributes attrs) throws IOException {
                Path target = dest.resolve(src.relativize(dir).toString());
                Files.createDirectories(target);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                Path target = dest.resolve(src.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * delete Tree
     * @param root 路径
     * @return 返回值
     * @throws IOException 异常
    */
    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException("删除文件失败: " + e.getMessage(), e);
                }
            });
        }
    }

    /**
     * 标记失败
     * @param Map<String 参数
     * @param message 字符串
     * @param result 参数
     * @return 返回值
    */
    private static Map<String, Object> fail(Map<String, Object> result, String message) {
        result.put("success", false);
        result.put("message", message);
        return result;
    }
}
