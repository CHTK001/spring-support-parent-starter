package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import io.swagger.v3.oas.annotations.Operation;
import com.chua.starter.ai.support.chat.ChatSkillSupport;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Skills 管理控制器（/v2/skills）。
 *
 * <p>对齐插件页 Skills.vue 的接口契约：列出已安装技能、查看 SKILL.md 文档、扫描/重载、
 * 启用停用（含批量）、ZIP 上传安装、从 URL 安装、卸载。技能是宿主级共享资源，
 * 扫描与启停复用 {@link ChatSkillSupport}，安装目录为 {@code skillsRoot/user}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/skills")
@RequiredArgsConstructor
public class V2SkillController {

    private final ChatSkillSupport chatSkillSupport;

    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    @Value("${nomifun.skills-root:./skills}")
    private String skillsRoot;

    private static final String SETTING_GROUP = "skills";

    private static final String AUTO_SCAN_FIELD = "auto_scan";

    // ─────────────────────────── 列表 / 详情 / 文档 ───────────────────────────

    /**
     * 列出已安装技能（分页结构）。
     *
     * @return {items,total}
     */
    @GetMapping
    public ReturnResult<Map<String, Object>> list() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ChatSkillSupport.InstalledSkill skill : chatSkillSupport.skills()) {
            items.add(toItem(skill));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", items.size());
        return ReturnResult.ok(result);
    }

    /**
     * 获取单个技能详情。
     *
     * @param id 技能名
     * @return 技能对象
     */
    @GetMapping("/{id}")
    public ReturnResult<Map<String, Object>> detail(@PathVariable("id") String id) {
        return ReturnResult.ok(findItem(id));
    }

    /**
     * 获取技能 SKILL.md 原始文档。
     *
     * @param id 技能名
     * @return {content}
     */
    @GetMapping("/{id}/documentation")
    public ReturnResult<Map<String, Object>> documentation(@PathVariable("id") String id) {
        ChatSkillSupport.InstalledSkill skill = find(id);
        Map<String, Object> result = new HashMap<>();
        result.put("content", skill.content());
        return ReturnResult.ok(result);
    }

    /**
     * 启用状态清单。
     *
     * @return [{id,enabled}]
     */
    @GetMapping("/enabled-status")
    public ReturnResult<List<Map<String, Object>>> enabledStatus() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatSkillSupport.InstalledSkill skill : chatSkillSupport.skills()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", skill.name());
            item.put("enabled", skill.enabled());
            result.add(item);
        }
        return ReturnResult.ok(result);
    }

    // ─────────────────────────── 扫描 / 重载 ───────────────────────────

    /**
     * 扫描本地技能目录。
     *
     * @return {success,count}
     */
    @PostMapping("/scan")
    public ReturnResult<Map<String, Object>> scan() {
        chatSkillSupport.invalidate();
        int count = chatSkillSupport.skills().size();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", count);
        return ReturnResult.ok(result);
    }

    /**
     * 重载单个技能（清缓存后重新扫描）。
     *
     * @param id 技能名
     * @return {success}
     */
    @PostMapping("/{id}/reload")
    public ReturnResult<Map<String, Object>> reload(@PathVariable("id") String id) {
        find(id);
        chatSkillSupport.invalidate();
        chatSkillSupport.skills();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

    // ─────────────────────────── 启用 / 停用 ───────────────────────────

    /**
     * 启用技能。
     *
     * @param id 技能名
     * @return {success}
     */
    @PostMapping("/{id}/enable")
    public ReturnResult<Map<String, Object>> enable(@PathVariable("id") String id) {
        find(id);
        chatSkillSupport.toggle(id, true);
        return successResult();
    }

    /**
     * 停用技能。
     *
     * @param id 技能名
     * @return {success}
     */
    @PostMapping("/{id}/disable")
    public ReturnResult<Map<String, Object>> disable(@PathVariable("id") String id) {
        find(id);
        chatSkillSupport.toggle(id, false);
        return successResult();
    }

    /**
     * 批量切换技能状态。
     *
     * @param body {ids:[],enabled:bool}
     * @return {success,count}
     */
    @PostMapping("/batch-toggle")
    public ReturnResult<Map<String, Object>> batchToggle(@RequestBody Map<String, Object> body) {
        Object idsObj = body.get("ids");
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        if (!(idsObj instanceof List<?> ids)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ids 必须为数组");
        }
        int count = 0;
        for (Object idObj : ids) {
            if (idObj == null) {
                continue;
            }
            String id = idObj.toString();
            chatSkillSupport.toggle(id, enabled);
            count++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", count);
        return ReturnResult.ok(result);
    }

    // ─────────────────────────── 安装 / 卸载 ───────────────────────────

    /**
     * 通过 ZIP 上传安装技能。
     *
     * @param file  ZIP 文件
     * @param force 是否强制覆盖
     * @return {success,skillId,message}
     */
    @PostMapping("/install")
    public ReturnResult<Map<String, Object>> install(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "force", required = false) String force) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "上传文件为空");
        }
        try {
            String skillId = extractZip(file.getBytes(), parseBool(force));
            chatSkillSupport.invalidate();
            return installResult(skillId);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Skill] ZIP 安装失败", e);
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ZIP 安装失败: " + e.getMessage());
        }
    }

    /**
     * 从 URL 下载 ZIP 并安装技能。
     *
     * @param body {url,force}
     * @return {success,skillId,message}
     */
    @PostMapping("/install-from-url")
    public ReturnResult<Map<String, Object>> installFromUrl(@RequestBody Map<String, Object> body) {
        String url = body.get("url") == null ? null : body.get("url").toString();
        boolean force = parseBool(body.get("force") == null ? null : body.get("force").toString());
        if (url == null || url.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "url 不能为空");
        }
        try {
            byte[] bytes = download(url);
            String skillId = extractZip(bytes, force);
            chatSkillSupport.invalidate();
            return installResult(skillId);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Skill] 从 URL 安装失败: {}", url, e);
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "下载或安装失败: " + e.getMessage());
        }
    }

    /**
     * 预览市场技能：下载 ZIP 提取 SKILL.md（不落盘），返回名称/描述/正文，供详情页在安装前展示。
     *
     * @param body {url}
     * @return {name, description, version, content}
     */
    @Operation(summary = "预览市场技能（提取 SKILL.md，不落盘）")
    @PostMapping("/market/preview")
    public ReturnResult<Map<String, Object>> previewMarket(@RequestBody Map<String, Object> body) {
        String url = body.get("url") == null ? null : body.get("url").toString();
        if (url == null || url.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "url 不能为空");
        }
        try {
            byte[] bytes = download(url);
            String skillMd = readSkillMdFromZip(bytes);
            Map<String, Object> meta = parseFrontmatter(skillMd);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", meta.get("name"));
            result.put("description", meta.get("description"));
            result.put("version", meta.get("version"));
            result.put("icon", meta.get("icon"));
            result.put("content", stripFrontmatter(skillMd));
            return ReturnResult.ok(result);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Skill] 市场技能预览失败 url={}: {}", url, e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "预览失败: " + e.getMessage());
        }
    }

    /**
     * 卸载用户安装的技能（删除目录）。
     *
     * @param id 技能名
     * @return {success,message}
     */
    @PostMapping("/{id}/uninstall")
    public ReturnResult<Map<String, Object>> uninstall(@PathVariable("id") String id) {
        ChatSkillSupport.InstalledSkill skill = find(id);
        if (!"user".equals(skill.source())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "内置技能不可卸载");
        }
        Path target = Paths.get(skillsRoot).resolve("user").resolve(id);
        boolean deleted = !Files.exists(target);
        for (int attempt = 1; attempt <= 3 && !deleted; attempt++) {
            deleted = deleteRecursively(target);
            if (!deleted) {
                // Windows 下文件可能被杀毒/索引短暂占用，退避后重试
                try {
                    Thread.sleep(600L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (!deleted) {
            log.error("[Skill] 卸载未完成，目录仍存在: {}", target);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR,
                    "技能文件被占用，无法完全卸载，请稍后重试: " + id);
        }
        chatSkillSupport.invalidate();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已卸载 " + id);
        return ReturnResult.ok(result);
    }

    // ─────────────────────────── 自动扫描开关（持久化标记） ───────────────────────────

    /**
     * 查询自动扫描开关状态。
     *
     * @return {enabled}
     */
    @GetMapping("/watcher/status")
    public ReturnResult<Map<String, Object>> watcherStatus() {
        String raw = sysAiGlobalSettingService.getValue(SETTING_GROUP, AUTO_SCAN_FIELD).orElse("false");
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", parseBool(raw));
        return ReturnResult.ok(result);
    }

    /**
     * 切换自动扫描开关。
     *
     * @param body {enabled}
     * @return {enabled}
     */
    @PostMapping("/watcher/toggle")
    public ReturnResult<Map<String, Object>> watcherToggle(@RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        sysAiGlobalSettingService.upsertValue(SETTING_GROUP, AUTO_SCAN_FIELD,
                Boolean.toString(enabled), "是否自动扫描技能目录");
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", enabled);
        return ReturnResult.ok(result);
    }

    // ─────────────────────────── 内部实现 ───────────────────────────

    /**
     * success Result
     * @return 返回值
    */
    private ReturnResult<Map<String, Object>> successResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

    /**
     * install Result
     * @param skillId 字符串
     * @return 返回值
    */
    private ReturnResult<Map<String, Object>> installResult(String skillId) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("skillId", skillId);
        result.put("message", "已安装 " + skillId);
        return ReturnResult.ok(result);
    }

    /**
     * 查找
     * @param id 字符串
     * @return 返回值
    */
    private ChatSkillSupport.InstalledSkill find(String id) {
        for (ChatSkillSupport.InstalledSkill skill : chatSkillSupport.skills()) {
            if (skill.name().equals(id)) {
                return skill;
            }
        }
        throw new ApiException(ApiErrorCode.NOT_FOUND, "技能不存在: " + id);
    }

    /**
     * find Item
     * @param id 字符串
     * @return 返回值
    */
    private Map<String, Object> findItem(String id) {
        return toItem(find(id));
    }

    /**
     * 把内部技能对象转成前端期望的结构：{id,basePath,manifest,contentHash,source,enabled}。
     * @param skill 参数
     * @return 返回值
    */
    private Map<String, Object> toItem(ChatSkillSupport.InstalledSkill skill) {
        Map<String, Object> front = parseFrontmatter(skill.content());
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", str(front.get("name"), skill.name()));
        manifest.put("description", resolveDescription(front.get("description"), skill.description()));
        if (front.get("version") != null) {
            manifest.put("version", front.get("version"));
        }
        if (front.get("author") != null) {
            manifest.put("author", front.get("author"));
        }
        if (front.get("icon") != null) {
            manifest.put("icon", front.get("icon"));
        }
        manifest.put("tags", front.getOrDefault("tags", List.of()));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", skill.name());
        item.put("basePath", skill.location());
        item.put("manifest", manifest);
        item.put("contentHash", sha1(skill.content()));
        // 内置/Agent→system；用户安装→global（可卸载）
        item.put("source", "user".equals(skill.source()) ? "global" : "system");
        item.put("enabled", skill.enabled());
        return item;
    }

    /**
     * 解析 SKILL.md 顶部 YAML frontmatter（轻量实现，仅取常用标量与 tags）。
     * @param content 字符串
     * @return 返回值
    */
    private Map<String, Object> parseFrontmatter(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (content == null) {
            return result;
        }
        String text = content.stripLeading();
        if (!text.startsWith("---")) {
            return result;
        }
        int firstEnd = text.indexOf('\n', 3);
        if (firstEnd < 0) {
            return result;
        }
        int closing = text.indexOf("\n---", firstEnd);
        if (closing < 0) {
            return result;
        }
        String yaml = text.substring(firstEnd + 1, closing);
        String pendingKey = null;
        List<String> pendingList = new ArrayList<>();
        // 块标量状态（description: > / |）
        String blockKey = null;
        char blockFold = ' ';
        List<String> blockLines = new ArrayList<>();
        for (String rawLine : yaml.split("\n", -1)) {
            String line = rawLine.replaceAll("\r$", "");
            String trimmed = line.trim();

            // 收集块标量正文
            if (blockKey != null) {
                boolean indented = !line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t');
                boolean newTopKey = !indented && trimmed.matches("^[A-Za-z0-9_.-]+\\s*:.*");
                if (trimmed.isEmpty()) {
                    blockLines.add("");
                    continue;
                }
                if (newTopKey) {
                    result.put(blockKey, joinBlock(blockFold, blockLines));
                    blockKey = null;
                    blockLines = new ArrayList<>();
                    // 落到下方常规逻辑处理本行
                } else {
                    blockLines.add(trimmed);
                    continue;
                }
            }

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (line.matches("^\\s+-\\s+.*") && pendingKey != null) {
                pendingList.add(unquote(trimmed.replaceFirst("^-\\s+", "").trim()));
                continue;
            }
            if (pendingKey != null) {
                result.put(pendingKey, pendingList);
                pendingKey = null;
                pendingList = new ArrayList<>();
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            if (value.matches("^[>|]([-+]?\\d*)?$")) {
                // YAML 折叠(>) / 字面(|) 块标量
                blockKey = key;
                blockFold = value.charAt(0);
                blockLines = new ArrayList<>();
            } else if (value.isEmpty()) {
                pendingKey = key;
                pendingList = new ArrayList<>();
            } else if (key.equals("tags") && value.startsWith("[")) {
                List<String> tags = new ArrayList<>();
                for (String part : value.replaceAll("[\\[\\]]", "").split(",")) {
                    String t = unquote(part.trim());
                    if (!t.isEmpty()) {
                        tags.add(t);
                    }
                }
                result.put(key, tags);
            } else {
                result.put(key, unquote(value));
            }
        }
        if (blockKey != null) {
            result.put(blockKey, joinBlock(blockFold, blockLines));
        }
        if (pendingKey != null) {
            result.put(pendingKey, pendingList);
        }
        return result;
    }

    /**
     * 把块标量正文拼成文本：折叠标量(>)按语义合并为单行，字面标量(|)保留换行。
     * @param fold 参数
     * @param lines 参数
     * @return 返回值
    */
    private static String joinBlock(char fold, List<String> lines) {
        List<String> trimmed = new ArrayList<>(lines);
        // 去掉首尾空行
        while (!trimmed.isEmpty() && trimmed.get(0).isEmpty()) {
            trimmed.remove(0);
        }
        while (!trimmed.isEmpty() && trimmed.get(trimmed.size() - 1).isEmpty()) {
            trimmed.remove(trimmed.size() - 1);
        }
        if (fold == '|') {
            return String.join("\n", trimmed);
        }
        // 折叠：连续非空行用空格连接，空行作为句子边界
        StringBuilder sb = new StringBuilder();
        for (String l : trimmed) {
            if (l.isEmpty()) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
            } else {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
                sb.append(l);
            }
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    /**
     * 解析描述：块标量解析失败（值为 > / | 或空）时回退到目录正文提炼的描述。
     * @param fallback 字符串
     * @param parsed 对象
     * @return 返回值
    */
    private static String resolveDescription(Object parsed, String fallback) {
        if (parsed != null) {
            String s = parsed.toString().trim();
            if (!s.isEmpty() && !s.equals(">") && !s.equals("|")) {
                return s;
            }
        }
        return fallback == null ? "" : fallback;
    }

    /**
     * 解压 ZIP 到 user 目录并返回技能名。
     * @param force 布尔值
     * @param zipBytes 参数
     * @return 返回值
     * @throws IOException 异常
    */
    private String extractZip(byte[] zipBytes, boolean force) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                zis.transferTo(bos);
                entries.put(entry.getName().replace('\\', '/'), bos.toByteArray());
            }
        }
        if (entries.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ZIP 内没有文件");
        }

        // 定位 SKILL.md
        String skillMdName = null;
        for (String name : entries.keySet()) {
            if (name.matches("(?i)(^|.*/)skill\\.md$")) {
                if (skillMdName == null || depth(name) < depth(skillMdName)) {
                    skillMdName = name;
                }
            }
        }
        if (skillMdName == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ZIP 中未找到 SKILL.md");
        }

        String prefix = skillMdName.contains("/")
                ? skillMdName.substring(0, skillMdName.lastIndexOf('/') + 1)
                : "";
        String skillName;
        if (!prefix.isEmpty()) {
            skillName = prefix.substring(0, prefix.indexOf('/'));
        } else {
            Map<String, Object> meta = parseFrontmatter(new String(entries.get(skillMdName), StandardCharsets.UTF_8));
            String declared = meta.get("name") == null ? null : meta.get("name").toString();
            skillName = sanitize(declared);
        }

        Path target = Paths.get(skillsRoot).resolve("user").resolve(skillName).normalize();
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) {
            if (!force) {
                throw new ApiException(ApiErrorCode.CONFLICT, "技能已存在，勾选强制覆盖可替换: " + skillName);
            }
            deleteRecursively(target);
        }
        Files.createDirectories(target);

        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            String name = e.getKey();
            if (!prefix.isEmpty() && !name.startsWith(prefix)) {
                continue;
            }
            String relative = prefix.isEmpty() ? name : name.substring(prefix.length());
            if (relative.isEmpty() || relative.endsWith("/")) {
                continue;
            }
            Path out = target.resolve(relative).normalize();
            if (!out.startsWith(target)) {
                // 防 zip-slip
                continue;
            }
            Files.createDirectories(out.getParent());
            Files.write(out, e.getValue());
        }
        if (!Files.isRegularFile(target.resolve(prefix.isEmpty() ? skillMdName : skillMdName.substring(prefix.length())))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "解压后缺少 SKILL.md");
        }
        return skillName;
    }

    /**
     * 从 ZIP 字节中定位最浅层的 SKILL.md 并返回其完整文本（不落盘）。
     * @param zipBytes 参数
     * @return 返回值
     * @throws IOException 异常
    */
    private String readSkillMdFromZip(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                zis.transferTo(bos);
                entries.put(entry.getName().replace('\\', '/'), bos.toByteArray());
            }
        }
        if (entries.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ZIP 内没有文件");
        }
        String skillMdName = null;
        for (String name : entries.keySet()) {
            if (name.matches("(?i)(^|.*/)skill\\.md$")) {
                if (skillMdName == null || depth(name) < depth(skillMdName)) {
                    skillMdName = name;
                }
            }
        }
        if (skillMdName == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ZIP 中未找到 SKILL.md");
        }
        return new String(entries.get(skillMdName), StandardCharsets.UTF_8);
    }

    /**
     * 去掉 SKILL.md 顶部 YAML frontmatter，返回正文；无 frontmatter 时原样返回。
     * @param content 字符串
     * @return 返回值
    */
    private String stripFrontmatter(String content) {
        if (content == null) {
            return "";
        }
        String text = content.stripLeading();
        if (!text.startsWith("---")) {
            return text;
        }
        int firstEnd = text.indexOf('\n', 3);
        if (firstEnd < 0) {
            return text;
        }
        int closing = text.indexOf("\n---", firstEnd);
        if (closing < 0) {
            return text;
        }
        int bodyStart = text.indexOf('\n', closing + 1);
        return bodyStart < 0 ? "" : text.substring(bodyStart + 1).stripLeading();
    }

    /**
     * 下载
     * @param urlStr 字符串
     * @return 返回值
     * @throws IOException 异常
     * @throws InterruptedException 异常
    */
    private byte[] download(String urlStr) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "AiControl SkillInstaller/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + urlStr);
        }
        return response.body();
    }

    /**
     * delete Recursively
     * @param path 路径
     * @return 返回值
    */
    private static boolean deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return true;
        }
        boolean allDeleted = true;
        try (var walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    allDeleted = false;
                }
            }
        } catch (IOException e) {
            allDeleted = false;
        }
        return allDeleted && !Files.exists(path);
    }

    /**
     * depth
     * @param name 字符串
     * @return 返回值
    */
    private static int depth(String name) {
        return name.split("/").length;
    }

    /**
     * sanitize
     * @param raw 字符串
     * @return 返回值
    */
    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "skill-" + Integer.toHexString(System.identityHashCode(new Object()));
        }
        String cleaned = raw.trim().replaceAll("[\\\\/:*?\"<>|]+", "-")
                .replaceAll("\\s+", "-").replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        if (cleaned.isEmpty()) {
            return "skill";
        }
        return cleaned;
    }

    /**
     * unquote
     * @param value 字符串
     * @return 返回值
    */
    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * str
     * @param fallback 字符串
     * @param value 对象
     * @return 返回值
    */
    private static String str(Object value, String fallback) {
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        String s = value.toString();
        return s.isEmpty() && fallback != null ? fallback : s;
    }

    /**
     * parse Bool
     * @param raw 字符串
     * @return 返回值
    */
    private static boolean parseBool(String raw) {
        if (raw == null) {
            return false;
        }
        String v = raw.trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("on");
    }

    /**
     * sha1
     * @param input 字符串
     * @return 返回值
    */
    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input == null ? 0 : input.hashCode());
        }
    }
}
