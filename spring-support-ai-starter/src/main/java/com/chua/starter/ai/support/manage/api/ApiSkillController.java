package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.common.support.ai.skill.SkillDefinition;
import com.chua.common.support.datasearch.skill.spi.SkillOfflineProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.manage.api.market.ClawHubPluginsSkillMarketSourceProvider;
import com.chua.starter.ai.support.manage.api.market.ClawHubSkillMarketSourceProvider;
import com.chua.starter.ai.support.manage.api.market.LoopHubSkillMarketSourceProvider;
import com.chua.starter.ai.support.manage.api.market.McpWorldSkillMarketSourceProvider;
import com.chua.starter.ai.support.manage.api.market.SkillHubMcpSkillMarketSourceProvider;
import com.chua.starter.ai.support.manage.api.market.SkillHubPackagesSkillMarketSourceProvider;
import com.chua.starter.ai.support.manage.api.market.SkillHubSkillMarketSourceProvider;
import com.chua.starter.ai.support.manage.api.market.SkillMarketSourceProvider;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 前端技能控制器（对接 nomicore {@code skills.*} 契约）。
 *
 * <p>替代原 Rust 后端的技能管理能力，基于本地文件系统扫描实现。内置技能目录由
 * {@code nomifun.skills-root} 配置（默认 {@code ./skills}），其下 {@code builtin} 子目录存放内置技能，
 * {@code user} 子目录存放用户自定义技能。</p>
 *
 * <p>技能识别规则：目录下存在 {@code SKILL.md}（或 {@code skill.md}）即视为一个技能，其首行非空文本
 * 作为技能描述。市场相关接口支持从 GitHub 仓库真实下载并安装技能包。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@Slf4j
public class ApiSkillController {

    /**
     * 技能根目录
     */
    @Value("${nomifun.skills-root:./skills}")
    private String skillsRoot;

    /**
     * Skill 市场 API 基础地址，可通过环境变量或配置覆盖。
     * 示例：https://skillsmp.com/api/skills
     *       https://api.example.com/skills
     */
    @Value("${ai.skills-market-url:https://skillsmp.com/api/skills}")
    private String skillsMarketUrl;

    /**
     * 用户自定义外部技能搜索路径（内存维护）
     */
    private final List<Map<String, String>> externalPaths = new CopyOnWriteArrayList<>();

    /**
     * 全局设置服务（持久化市场启用状态）
     */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * 市场启用状态分组
     */
    private static final String MARKET_SETTING_GROUP = "skills";

    /**
     * 市场启用状态字段名
     */
    private static final String MARKET_ENABLED_FIELD = "market_enabled";

    /**
     * 按市场源的启用状态字段名前缀（skills.market_clawhub_enabled / market_skillhub_mcp_enabled 等）。
     * 未显式配置时默认启用。
     */
    private static final String MARKET_SOURCE_FIELD_PREFIX = "market_";
    private static final String MARKET_SOURCE_FIELD_SUFFIX = "_enabled";

    /**
     * 专家包“添加”时单次安装的上限技能数（避免一次下载整仓全部技能）。
     */
    private static final int MAX_AUTHOR_PACKAGE_SKILLS = 12;

    /**
     * 列出全部可用技能。
     *
     * @return 技能列表
     */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        Path userDir = Paths.get(skillsRoot).resolve("user");
        Path builtinDir = Paths.get(skillsRoot).resolve("builtin");
        scanSkills(userDir, "custom", result);
        scanSkills(builtinDir, "builtin", result);
        return ApiResult.ok(result);
    }

    /**
     * 列出内置自动技能。
     *
     * @return 内置自动技能列表
     */
    @GetMapping("/builtin-auto")
    public ApiResult<List<Map<String, Object>>> builtinAuto() {
        List<Map<String, Object>> result = new ArrayList<>();
        Path builtinDir = Paths.get(skillsRoot).resolve("builtin");
        if (Files.exists(builtinDir) && Files.isDirectory(builtinDir)) {
            try (Stream<Path> stream = Files.list(builtinDir)) {
                for (Path p : stream.filter(Files::isDirectory).sorted().toList()) {
                    Map<String, Object> skill = new LinkedHashMap<>();
                    skill.put("name", p.getFileName().toString());
                    skill.put("description", readDescription(p));
                    skill.put("location", p.toAbsolutePath().toString());
                    result.add(skill);
                }
            } catch (IOException e) {
                log.debug("[skills] 扫描技能目录失败: {}", builtinDir, e);
            }
        }
        return ApiResult.ok(result);
    }

    /**
     * 扫描本机已安装 AI 编辑器的 skill/rules 目录，返回可导入的技能列表。
     *
     * <p>通过 {@link ServiceProvider} SPI 收集全部离线 {@link SkillOfflineProvider} 实现，
     * 汇总每个已安装 agent 的 {@code listAgentSkills()} 扫描结果，避免反射与硬编码目录。</p>
     *
     * @return 技能列表（按 provider 分组）
     */
    @GetMapping("/agent")
    public ApiResult<List<Map<String, Object>>> fromAgent() {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, SkillOfflineProvider> providers = ServiceProvider.of(SkillOfflineProvider.class).list();
        if (providers == null || providers.isEmpty()) {
            log.warn("[ApiSkill] 未发现任何 SkillOfflineProvider 实现");
            return ApiResult.ok(result);
        }
        for (Map.Entry<String, SkillOfflineProvider> entry : providers.entrySet()) {
            SkillOfflineProvider provider = entry.getValue();
            Map<String, Object> providerItem = new LinkedHashMap<>();
            providerItem.put("provider", entry.getKey());
            providerItem.put("name", provider.name());
            List<SkillDefinition> skills = List.of();
            try {
                if (provider.isInstalled()) {
                    skills = provider.listAgentSkills();
                }
            } catch (Exception e) {
                log.warn("[ApiSkill] SkillOfflineProvider[{}] 扫描 Agent 技能失败: {}", entry.getKey(), e.getMessage());
            }
            List<Map<String, Object>> skillItems = new ArrayList<>();
            for (SkillDefinition skill : skills) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", skill.getName());
                item.put("description", skill.getDescription() != null ? skill.getDescription() : "");
                item.put("source", "agent");
                item.put("provider", entry.getKey());
                skillItems.add(item);
            }
            providerItem.put("installed", !skills.isEmpty() || safeInstalled(provider));
            providerItem.put("skill_count", skillItems.size());
            providerItem.put("skills", skillItems);
            result.add(providerItem);
            if (!skillItems.isEmpty()) {
                log.info("[ApiSkill] SkillOfflineProvider[{}] 扫描到 {} 个 Agent 技能", entry.getKey(), skillItems.size());
            }
        }
        return ApiResult.ok(result);
    }

    /**
     * 安全读取 provider 是否安装（兜底 try/catch）。
     */
    private boolean safeInstalled(SkillOfflineProvider provider) {
        try {
            return provider.isInstalled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 读取文件首行非空文本作为描述。
     */
    private String readFirstLine(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        } catch (IOException e) {
            log.debug("[skills] 读取技能描述失败: {}", file, e);
        }
        return "";
    }

    /**
     * 读取内置规则文件内容。
     *
     * @param body 请求体 {@code { file_name: string }}
     * @return 规则内容
     */
    @PostMapping("/builtin-rule")
    public ApiResult<String> builtinRule(@RequestBody Map<String, Object> body) {
        String fileName = asText(body.get("file_name"));
        return ApiResult.ok(readBuiltinFile("rules", fileName));
    }

    /**
     * 读取内置技能文件内容。
     *
     * @param body 请求体 {@code { file_name: string }}
     * @return 技能文件内容
     */
    @PostMapping("/builtin-skill")
    public ApiResult<String> builtinSkill(@RequestBody Map<String, Object> body) {
        String fileName = asText(body.get("file_name"));
        return ApiResult.ok(readBuiltinFile("skills", fileName));
    }

    /**
     * 为智能体实例化技能（返回技能源路径列表）。
     *
     * @param body 请求体 {@code { conversation_id: string, skills: string[] }}
     * @return 实例化结果
     */
    @PostMapping("/materialize-for-agent")
    public ApiResult<Map<String, Object>> materializeForAgent(@RequestBody Map<String, Object> body) {
        Object skillsObj = body.get("skills");
        List<Map<String, String>> skills = new ArrayList<>();
        if (skillsObj instanceof List) {
            Path builtinDir = Paths.get(skillsRoot).resolve("builtin");
            for (Object s : (List<?>) skillsObj) {
                String name = String.valueOf(s);
                Path src = builtinDir.resolve(name);
                Map<String, String> item = new LinkedHashMap<>();
                item.put("name", name);
                item.put("source_path", Files.exists(src) ? src.toAbsolutePath().toString() : "");
                skills.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skills", skills);
        return ApiResult.ok(result);
    }

    /**
     * 读取技能信息。
     *
     * @param body 请求体 {@code { skill_path: string }}
     * @return 技能信息
     */
    @PostMapping("/info")
    public ApiResult<Map<String, String>> info(@RequestBody Map<String, Object> body) {
        Path skillPath = resolveSkillPath(body);
        if (!Files.exists(skillPath) || !Files.isDirectory(skillPath)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "技能不存在: " + skillPath);
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("name", skillPath.getFileName().toString());
        result.put("description", readDescription(skillPath));
        return ApiResult.ok(result);
    }

    /**
     * 导入技能（复制到用户目录）。支持 zip 文件、单个技能目录和父目录。
     *
     * @param body 请求体 {@code { skill_path: string }}
     * @return 导入结果
     */
    @PostMapping("/import")
    public ApiResult<Map<String, Object>> importSkill(@RequestBody Map<String, Object> body) {
        Path skillPath = resolveSkillPath(body);
        if (!Files.exists(skillPath)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "路径不存在: " + skillPath);
        }

        Path userDir = Paths.get(skillsRoot).resolve("user");
        try {
            Files.createDirectories(userDir);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建用户技能目录失败: " + e.getMessage());
        }

        List<String> importedNames = new ArrayList<>();

        try {
            if (Files.isRegularFile(skillPath) && skillPath.toString().toLowerCase().endsWith(".zip")) {
                Path tempDir = Files.createTempDirectory("skill-import-");
                try {
                    extractZip(skillPath, tempDir);
                    copySkillsFromDirectory(tempDir, userDir, importedNames);
                } catch (IOException e) {
                    throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导入 zip 技能失败: " + e.getMessage());
                } finally {
                    deleteRecursively(tempDir);
                }
            } else if (Files.isDirectory(skillPath) && hasSkillMark(skillPath)) {
                String name = skillPath.getFileName().toString();
                copyDirectory(skillPath, userDir.resolve(name));
                importedNames.add(name);
            } else if (Files.isDirectory(skillPath)) {
                copySkillsFromDirectory(skillPath, userDir, importedNames);
            } else {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "不支持的导入类型: " + skillPath);
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导入技能失败: " + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skill_name", importedNames.isEmpty() ? "" : importedNames.get(0));
        result.put("skill_names", importedNames);
        return ApiResult.ok(result);
    }

    /**
     * 扫描指定文件夹下的技能。
     *
     * @param body 请求体 {@code { folder_path: string }}
     * @return 技能列表
     */
    @PostMapping("/scan")
    public ApiResult<List<Map<String, Object>>> scan(@RequestBody Map<String, Object> body) {
        String folderPath = asText(body.get("folder_path"));
        List<Map<String, Object>> result = new ArrayList<>();
        if (folderPath == null || folderPath.isBlank()) {
            return ApiResult.ok(result);
        }
        Path dir = Paths.get(folderPath);
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                for (Path p : stream.filter(Files::isDirectory).sorted().toList()) {
                    if (hasSkillMark(p)) {
                        Map<String, Object> skill = new LinkedHashMap<>();
                        skill.put("name", p.getFileName().toString());
                        skill.put("description", readDescription(p));
                        skill.put("path", p.toAbsolutePath().toString());
                        result.add(skill);
                    }
                }
            } catch (IOException e) {
                log.debug("[skills] 扫描技能目录失败: {}", dir, e);
            }
        }
        return ApiResult.ok(result);
    }

    /**
     * 探测常见技能路径。
     *
     * @return 路径列表
     */
    @GetMapping("/detect-paths")
    public ApiResult<List<Map<String, String>>> detectPaths() {
        List<Map<String, String>> result = new ArrayList<>();
        Path builtinDir = Paths.get(skillsRoot).resolve("builtin");
        Path userDir = Paths.get(skillsRoot).resolve("user");
        if (Files.exists(builtinDir)) {
            result.add(namedPath("builtin", builtinDir.toAbsolutePath().toString()));
        }
        if (Files.exists(userDir)) {
            result.add(namedPath("user", userDir.toAbsolutePath().toString()));
        }
        return ApiResult.ok(result);
    }

    /**
     * 探测外部技能目录。
     *
     * @return 外部技能目录列表
     */
    @GetMapping("/detect-external")
    public ApiResult<List<Map<String, Object>>> detectExternal() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, String> ext : externalPaths) {
            String path = ext.get("path");
            Path dir = Paths.get(path);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                continue;
            }
            List<Map<String, Object>> skills = new ArrayList<>();
            try (Stream<Path> stream = Files.list(dir)) {
                for (Path p : stream.filter(Files::isDirectory).sorted().toList()) {
                    if (hasSkillMark(p)) {
                        Map<String, Object> skill = new LinkedHashMap<>();
                        skill.put("name", p.getFileName().toString());
                        skill.put("description", readDescription(p));
                        skill.put("path", p.toAbsolutePath().toString());
                        skills.add(skill);
                    }
                }
            } catch (IOException e) {
                log.debug("[skills] 扫描外部技能目录失败: {}", dir, e);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", ext.get("name"));
            entry.put("path", path);
            entry.put("source", "external");
            entry.put("skill_count", skills.size());
            entry.put("skills", skills);
            result.add(entry);
        }
        return ApiResult.ok(result);
    }

    /**
     * 通过符号链接导入技能。支持三种输入：
     * <ul>
     *   <li>zip 文件 → 解压后扫描技能目录导入</li>
     *   <li>单个技能目录（含 SKILL.md） → 符号链接/复制导入</li>
     *   <li>父目录（含多个技能子目录） → 扫描并批量导入</li>
     * </ul>
     *
     * @param body 请求体 {@code { skill_path: string }}
     * @return 导入结果
     */
    @PostMapping("/import-symlink")
    public ApiResult<Map<String, Object>> importSymlink(@RequestBody Map<String, Object> body) {
        Path skillPath = resolveSkillPath(body);
        if (!Files.exists(skillPath)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "路径不存在: " + skillPath);
        }

        Path userDir = Paths.get(skillsRoot).resolve("user");
        try {
            Files.createDirectories(userDir);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建用户技能目录失败: " + e.getMessage());
        }

        List<String> importedNames = new ArrayList<>();

        try {
            if (Files.isRegularFile(skillPath) && skillPath.toString().toLowerCase().endsWith(".zip")) {
                // Case 1: zip 文件 → 解压到临时目录，扫描技能后导入
                Path tempDir = Files.createTempDirectory("skill-import-");
                try {
                    extractZip(skillPath, tempDir);
                    importSkillsFromDirectory(tempDir, userDir, importedNames);
                } catch (IOException e) {
                    throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导入 zip 技能失败: " + e.getMessage());
                } finally {
                    deleteRecursively(tempDir);
                }
            } else if (Files.isDirectory(skillPath) && hasSkillMark(skillPath)) {
                // Case 2: 单个技能目录 → 符号链接/复制
                String name = skillPath.getFileName().toString();
                symlinkOrCopy(skillPath, userDir.resolve(name));
                importedNames.add(name);
            } else if (Files.isDirectory(skillPath)) {
                // Case 3: 父目录 → 扫描子目录批量导入
                importSkillsFromDirectory(skillPath, userDir, importedNames);
            } else {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "不支持的导入类型: " + skillPath);
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导入技能失败: " + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skill_name", importedNames.isEmpty() ? "" : importedNames.get(0));
        result.put("skill_names", importedNames);
        return ApiResult.ok(result);
    }

    /**
     * 删除技能。
     *
     * @param skillName 技能名
     * @return 成功信封
     */
    @DeleteMapping("/{skill_name}")
    public ApiResult<Void> delete(@PathVariable("skill_name") String skillName) {
        Path userDir = Paths.get(skillsRoot).resolve("user").resolve(skillName);
        if (Files.exists(userDir)) {
            try {
                deleteRecursively(userDir);
            } catch (IOException e) {
                throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "删除技能失败: " + e.getMessage());
            }
        }
        return ApiResult.ok();
    }

    /**
     * 为技能设置标签。
     *
     * @param skillName 技能名
     * @param body      请求体 {@code { audience_tags: string[], scenario_tags: string[] }}
     * @return 成功信封
     */
    @PutMapping("/{skill_name}/tags")
    public ApiResult<Void> setTags(@PathVariable("skill_name") String skillName,
                                   @RequestBody Map<String, Object> body) {
        log.debug("[skills] setTags skill={}, body={}", skillName, body);
        return ApiResult.ok();
    }

    /**
     * 获取技能目录路径。
     *
     * @return 用户/内置技能目录
     */
    @GetMapping("/paths")
    public ApiResult<Map<String, String>> paths() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("user_skills_dir", Paths.get(skillsRoot).resolve("user").toAbsolutePath().toString());
        result.put("builtin_skills_dir", Paths.get(skillsRoot).resolve("builtin").toAbsolutePath().toString());
        return ApiResult.ok(result);
    }

    /**
     * 获取自定义外部路径列表。
     *
     * @return 外部路径列表
     */
    @GetMapping("/external-paths")
    public ApiResult<List<Map<String, String>>> getExternalPaths() {
        return ApiResult.ok(new ArrayList<>(externalPaths));
    }

    /**
     * 新增自定义外部路径。
     *
     * @param body 请求体 {@code { name: string, path: string }}
     * @return 成功信封
     */
    @PostMapping("/external-paths")
    public ApiResult<Void> addExternalPath(@RequestBody Map<String, Object> body) {
        String name = asText(body.get("name"));
        String path = asText(body.get("path"));
        if (name == null || path == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "name 与 path 不能为空");
        }
        externalPaths.removeIf(e -> path.equals(e.get("path")));
        externalPaths.add(namedPath(name, path));
        return ApiResult.ok();
    }

    /**
     * 删除自定义外部路径。
     *
     * @param path 路径参数
     * @return 成功信封
     */
    @DeleteMapping("/external-paths")
    public ApiResult<Void> removeExternalPath(@RequestParam("path") String path) {
        externalPaths.removeIf(e -> path.equals(e.get("path")));
        return ApiResult.ok();
    }

    /**
     * 启用技能市场。
     * <p>将市场启用状态持久化到 {@code sys_ai_global_setting}（分组 skills，字段 market_enabled）。</p>
     *
     * @return 成功信封
     */
    @PostMapping("/market/enable")
    public ApiResult<Void> marketEnable() {
        sysAiGlobalSettingService.upsertValue(MARKET_SETTING_GROUP, MARKET_ENABLED_FIELD, "true", "技能市场是否启用");
        log.info("[skills] market enabled");
        return ApiResult.ok();
    }

    /**
     * 禁用技能市场。
     * <p>将市场禁用状态持久化到 {@code sys_ai_global_setting}（分组 skills，字段 market_enabled）。</p>
     *
     * @return 成功信封
     */
    @PostMapping("/market/disable")
    public ApiResult<Void> marketDisable() {
        sysAiGlobalSettingService.upsertValue(MARKET_SETTING_GROUP, MARKET_ENABLED_FIELD, "false", "技能市场是否启用");
        log.info("[skills] market disabled");
        return ApiResult.ok();
    }

    /**
     * 查询技能市场启用状态。
     *
     * @return 市场启用状态（enabled=true 表示市场开启；默认开启）
     */
    @GetMapping("/market/status")
    public ApiResult<Map<String, Object>> marketStatus() {
        boolean enabled = sysAiGlobalSettingService.getBoolean(MARKET_SETTING_GROUP, MARKET_ENABLED_FIELD, true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        return ApiResult.ok(result);
    }

    /**
     * 按市场源启用/禁用。源值示例：clawhub、loophub、skillhub、skillhub_mcp、mcpworld、clawhub_plugins、skillhub_packages。
     * <p>持久化到 {@code sys_ai_global_setting}（分组 skills，字段 market_<source>_enabled）。</p>
     *
     * @param source 市场源标识
     * @param enabled 是否启用
     * @return 成功信封
     */
    @PostMapping("/market/source/toggle")
    public ApiResult<Void> marketSourceToggle(@RequestBody Map<String, Object> body) {
        String source = asText(body.get("source"));
        boolean enabled = body.get("enabled") != null && Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        if (source == null || source.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "市场源不能为空");
        }
        sysAiGlobalSettingService.upsertValue(
                MARKET_SETTING_GROUP,
                MARKET_SOURCE_FIELD_PREFIX + source + MARKET_SOURCE_FIELD_SUFFIX,
                String.valueOf(enabled),
                "市场源 " + source + " 是否启用");
        log.info("[skills] market source {} -> {}", source, enabled);
        return ApiResult.ok();
    }

    /**
     * 查询各市场源配置（由 {@link SkillMarketSourceProvider} SPI 枚举，含启用状态）。
     * 未显式配置的源默认启用。
     *
     * @param marketType 可选：marketType 过滤（skill/mcp/plugin/preset）；为空返回全部
     * @return {@code { sources: [{ source, displayName, url, marketType, enabled }] }}
     */
    @GetMapping("/market/sources/status")
    public ApiResult<Map<String, Object>> marketSourcesStatus(
            @RequestParam(required = false) String marketType) {
        List<Map<String, Object>> sources = new ArrayList<>();
        Map<String, SkillMarketSourceProvider> providers =
                ServiceProvider.of(SkillMarketSourceProvider.class).list();
        if (providers == null || providers.isEmpty()) {
            // Spring Boot fat jar 下 BOOT-INF/classes 内嵌的 ServiceProvider 静态发现
            // 可能失败，回退到显式枚举全部 SkillMarketSourceProvider SPI 实现。
            providers = new LinkedHashMap<>();
            for (SkillMarketSourceProvider p : List.of(
                    new ClawHubSkillMarketSourceProvider(),
                    new LoopHubSkillMarketSourceProvider(),
                    new SkillHubSkillMarketSourceProvider(),
                    new SkillHubMcpSkillMarketSourceProvider(),
                    new McpWorldSkillMarketSourceProvider(),
                    new ClawHubPluginsSkillMarketSourceProvider(),
                    new SkillHubPackagesSkillMarketSourceProvider())) {
                providers.put(p.source(), p);
            }
        }
        if (providers != null) {
            for (Map.Entry<String, SkillMarketSourceProvider> entry : providers.entrySet()) {
                SkillMarketSourceProvider provider = entry.getValue();
                String source = provider.source();
                String type = provider.marketType();
                if (marketType != null && !marketType.isBlank() && !marketType.equals(type)) {
                    continue;
                }
                boolean enabled = sysAiGlobalSettingService.getBoolean(
                        MARKET_SETTING_GROUP,
                        MARKET_SOURCE_FIELD_PREFIX + source + MARKET_SOURCE_FIELD_SUFFIX,
                        provider.defaultEnabled());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("source", source);
                item.put("displayName", provider.displayName());
                item.put("url", provider.url());
                item.put("marketType", type);
                item.put("enabled", enabled);
                sources.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sources", sources);
        return ApiResult.ok(result);
    }

    /**
     * 从目录中扫描并以符号链接方式导入所有技能子目录。
     */
    private void importSkillsFromDirectory(Path dir, Path userDir, List<String> importedNames) {
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.filter(Files::isDirectory).sorted().toList()) {
                if (hasSkillMark(p)) {
                    String name = p.getFileName().toString();
                    Path target = userDir.resolve(name);
                    if (!Files.exists(target)) {
                        symlinkOrCopy(p, target);
                    }
                    importedNames.add(name);
                }
            }
        } catch (IOException e) {
            log.warn("[skills] 扫描目录失败: {}", dir, e);
        }
    }

    /**
     * 从目录中扫描并以复制方式导入所有技能子目录。
     */
    private void copySkillsFromDirectory(Path dir, Path userDir, List<String> importedNames) {
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.filter(Files::isDirectory).sorted().toList()) {
                if (hasSkillMark(p)) {
                    String name = p.getFileName().toString();
                    Path target = userDir.resolve(name);
                    if (!Files.exists(target)) {
                        copyDirectory(p, target);
                    }
                    importedNames.add(name);
                }
            }
        } catch (IOException e) {
            log.warn("[skills] 扫描目录失败: {}", dir, e);
        }
    }

    /**
     * 创建符号链接，不支持时回退为复制。
     */
    private void symlinkOrCopy(Path source, Path target) {
        try {
            Files.createSymbolicLink(target, source.toAbsolutePath());
        } catch (IOException | UnsupportedOperationException e) {
            try {
                copyDirectory(source, target);
            } catch (IOException ex) {
                log.warn("[skills] 复制技能失败: {} -> {}", source, target, ex);
            }
        }
    }

    /**
     * 解压 zip 文件到目标目录。
     */
    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(targetDir.resolve(entry.getName()));
                    continue;
                }
                Path filePath = targetDir.resolve(entry.getName());
                Path parent = filePath.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                Files.copy(zis, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                zis.closeEntry();
            }
        }
    }

    /**
     * 扫描技能目录并加入结果列表。
     *
     * @param dir    目录
     * @param source 来源标识
     * @param result 结果收集器
     */
    private void scanSkills(Path dir, String source, List<Map<String, Object>> result) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.filter(Files::isDirectory).sorted().toList()) {
                if (!hasSkillMark(p)) {
                    continue;
                }
                Map<String, Object> skill = new LinkedHashMap<>();
                skill.put("name", p.getFileName().toString());
                skill.put("description", readDescription(p));
                skill.put("location", p.toAbsolutePath().toString());
                skill.put("relative_location", p.getFileName().toString());
                skill.put("is_custom", "custom".equals(source));
                skill.put("source", source);
                result.add(skill);
            }
        } catch (IOException e) {
            log.debug("[skills] 遍历技能目录失败: {}", dir, e);
        }
    }

    /**
     * 读取内置文件内容。
     *
     * @param subDir 子目录（rules/skills）
     * @param fileName 文件名
     * @return 文件内容或空串
     */
    private String readBuiltinFile(String subDir, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        Path file = Paths.get(skillsRoot).resolve(subDir).resolve(fileName);
        if (!Files.exists(file)) {
            return "";
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 解析技能路径参数。
     *
     * @param body 请求体
     * @return 技能目录路径
     */
    private Path resolveSkillPath(Map<String, Object> body) {
        String skillPath = asText(body.get("skill_path"));
        if (skillPath == null || skillPath.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "skill_path 不能为空");
        }
        return Paths.get(skillPath).toAbsolutePath().normalize();
    }

    /**
     * 判断目录是否含技能标记文件。
     *
     * @param dir 目录
     * @return 是否技能
     */
    private boolean hasSkillMark(Path dir) {
        return Files.exists(dir.resolve("SKILL.md")) || Files.exists(dir.resolve("skill.md"));
    }

    /**
     * 读取技能描述（SKILL.md 首行非空文本）。
     *
     * @param dir 技能目录
     * @return 描述
     */
    private String readDescription(Path dir) {
        for (String name : new String[]{"SKILL.md", "skill.md"}) {
            Path md = dir.resolve(name);
            if (Files.exists(md)) {
                try {
                    List<String> lines = Files.readAllLines(md, StandardCharsets.UTF_8);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            return trimmed;
                        }
                    }
                } catch (IOException e) {
                    log.debug("[skills] 读取技能描述失败: {}", dir, e);
                }
            }
        }
        return "";
    }

    /**
     * 构造命名路径映射。
     *
     * @param name 名称
     * @param path 路径
     * @return 映射
     */
    private Map<String, String> namedPath(String name, String path) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("path", path);
        return m;
    }

    /**
     * 递归复制目录。
     *
     * @param source 源
     * @param target 目标
     * @throws IOException IO 异常
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                Path dest = target.resolve(source.relativize(p).toString());
                Files.createDirectories(dest.getParent());
                Files.copy(p, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * 递归删除。
     *
     * @param path 待删除路径
     * @throws IOException IO 异常
     */
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.walk(path)) {
                List<Path> all = stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
                for (Path p : all) {
                    Files.deleteIfExists(p);
                }
            }
        } else {
            Files.deleteIfExists(path);
        }
    }

    /**
     * 安全取文本。
     *
     * @param obj 对象
     * @return 文本或 null
     */
    private static String asText(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    // ==================== 技能市场 ====================

    /**
     * 搜索 SkillsMP 技能市场。
     *
     * @param search 搜索关键词
     * @param page   页码（默认 1）
     * @param limit  每页数量（默认 20）
     * @param sortBy 排序方式（stars/forks/updated，默认 stars）
     * @return 搜索结果
     */
    @GetMapping("/market/search")
    public ApiResult<Map<String, Object>> marketSearch(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "stars") String sortBy) {
        try {
            StringBuilder urlBuilder = new StringBuilder(skillsMarketUrl)
                    .append("?page=").append(page).append("&limit=").append(limit)
                    .append("&sortBy=").append(sortBy);
            if (search != null && !search.isBlank()) {
                urlBuilder.append("&search=").append(java.net.URLEncoder.encode(search, StandardCharsets.UTF_8));
            }
            String url = urlBuilder.toString();
            String body = httpGet(url);
            Map<String, Object> raw = parseJson(body);
            List<?> rawSkills = (List<?>) raw.get("skills");
            Map<String, Object> pagination = (Map<String, Object>) raw.get("pagination");

            List<Map<String, Object>> items = new ArrayList<>();
            if (rawSkills != null) {
                int rank = (page - 1) * limit + 1;
                for (Object obj : rawSkills) {
                    Map<String, Object> skill = (Map<String, Object>) obj;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", "skillsmp:" + skill.getOrDefault("id", ""));
                    item.put("source", "skillsmp");
                    item.put("rank", rank++);
                    item.put("name", skill.getOrDefault("name", ""));
                    item.put("description", skill.getOrDefault("description", ""));
                    item.put("url", skill.getOrDefault("githubUrl", ""));
                    item.put("install_command", "");
                    item.put("tags", List.of());
                    item.put("stats", "★ " + skill.getOrDefault("stars", 0) + " · Fork " + skill.getOrDefault("forks", 0));
                    items.add(item);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("items", items);
            result.put("fetched_at", System.currentTimeMillis());
            result.put("errors", List.of());
            if (pagination != null) {
                result.put("pagination", pagination);
            }
            return ApiResult.ok(result);
        } catch (Exception e) {
            log.error("SkillsMP 市场搜索失败: search={}", search, e);
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("items", List.of());
            empty.put("fetched_at", System.currentTimeMillis());
            empty.put("errors", List.of("市场搜索失败: " + e.getMessage()));
            return ApiResult.ok(empty);
        }
    }

    /**
     * 同步市场排行数据。前端调用此接口拉取市场数据。
     *
     * <p>按市场类型分发：扁平技能源（skillsmp/clawhub/skillhub/loophub）共享 SkillsMP 技能榜
     * （仅拉取一次并按源打标签）；专家包源（skillhub_packages）将 SkillsMP 技能按作者聚合为
     * “专家包”（一个作者=一个包，含其下技能清单），与扁平技能榜结构区分；MCP/插件源
     * （skillhub_mcp/mcpworld/clawhub_plugins）暂无可用数据源，标记 {@code unavailable} 并返回
     * 空列表，不返回伪造数据，也不报错。响应附带 {@code source_status} 供前端展示诚实空态。</p>
     *
     * @param body 请求体 {@code { sources: ["clawhub", "skillhub", ...] }}
     * @return 同步结果
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/market/rankings/sync")
    public ApiResult<Map<String, Object>> syncMarketRankings(@RequestBody Map<String, Object> body) {
        List<String> sources = new ArrayList<>();
        Object sourcesObj = body.get("sources");
        if (sourcesObj instanceof List<?> list) {
            for (Object item : list) {
                sources.add(String.valueOf(item));
            }
        }
        if (sources.isEmpty()) {
            sources.addAll(List.of("skillsmp"));
        }

        List<Map<String, Object>> allItems = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, String> sourceStatus = new LinkedHashMap<>();

        // 扁平技能源：共享 SkillsMP 技能榜，仅拉取一次，按请求的源打标签
        Set<String> flatSources = Set.of("skillsmp", "clawhub", "skillhub", "loophub");
        List<String> requestedFlat = sources.stream().filter(flatSources::contains).toList();
        if (!requestedFlat.isEmpty()) {
            List<Map<String, Object>> skills = fetchSkillsMpPage(1, 30);
            for (String source : requestedFlat) {
                int rank = 1;
                for (Map<String, Object> skill : skills) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", source + ":" + skill.getOrDefault("id", ""));
                    item.put("source", source);
                    item.put("rank", rank++);
                    item.put("name", skill.getOrDefault("name", ""));
                    item.put("description", skill.getOrDefault("description", ""));
                    item.put("url", skill.getOrDefault("githubUrl", ""));
                    item.put("install_command", "");
                    item.put("tags", List.of());
                    item.put("stats", "★ " + skill.getOrDefault("stars", 0)
                            + " · Fork " + skill.getOrDefault("forks", 0));
                    allItems.add(item);
                }
                sourceStatus.put(source, skills.isEmpty() ? "empty" : "ok");
            }
        }

        // 专家包源（角色市场）：按作者聚合为专家包，与扁平技能榜结构区分
        if (sources.contains("skillhub_packages")) {
            List<Map<String, Object>> skills = new ArrayList<>();
            for (int page = 1; page <= 3; page++) {
                skills.addAll(fetchSkillsMpPage(page, 48));
            }
            List<Map<String, Object>> packages = aggregateAuthorPackages(skills);
            allItems.addAll(packages);
            sourceStatus.put("skillhub_packages", packages.isEmpty() ? "empty" : "ok");
        }

        // MCP/插件源：暂无可用数据源，标记不可用（不返回伪造数据，也不报错）
        Set<String> unavailableSources = Set.of("skillhub_mcp", "mcpworld", "clawhub_plugins");
        for (String source : sources) {
            if (unavailableSources.contains(source)) {
                log.info("[market sync] 市场源 {} 暂无可用数据源，标记为不可用", source);
                sourceStatus.put(source, "unavailable");
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", allItems);
        result.put("fetched_at", System.currentTimeMillis());
        result.put("errors", errors);
        result.put("source_status", sourceStatus);
        return ApiResult.ok(result);
    }

    /**
     * 拉取 SkillsMP 技能榜单页原始技能数据。
     *
     * @param page  页码（从 1 开始）
     * @param limit 每页数量（SkillsMP 上限 48，超出自动截断）
     * @return 原始技能数据列表（拉取失败返回空列表，不抛异常）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchSkillsMpPage(int page, int limit) {
        int capped = Math.max(1, Math.min(limit, 48));
        try {
            String url = skillsMarketUrl + "?page=" + page + "&limit=" + capped + "&sortBy=stars";
            Map<String, Object> raw = parseJson(httpGet(url));
            Object skillsObj = raw.get("skills");
            if (skillsObj instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        result.add((Map<String, Object>) m);
                    }
                }
                return result;
            }
            return List.of();
        } catch (Exception e) {
            log.error("拉取 SkillsMP 技能榜第 {} 页失败: {}", page, e.getMessage());
            return List.of();
        }
    }

    /**
     * 将技能按作者（owner）聚合为“专家包”市场条目。
     *
     * <p>一个作者（GitHub owner）视为一个专家包，包内含其名下技能名清单（去重）。
     * 包按所含技能数降序排列。用于角色/专家包市场，使其与扁平技能榜区分开。</p>
     *
     * @param skills 原始技能列表（多页合并）
     * @return 专家包市场条目列表
     */
    private List<Map<String, Object>> aggregateAuthorPackages(List<Map<String, Object>> skills) {
        Map<String, List<Map<String, Object>>> byAuthor = new LinkedHashMap<>();
        for (Map<String, Object> skill : skills) {
            String author = String.valueOf(skill.getOrDefault("author", ""));
            if (author.isBlank() || "null".equals(author)) {
                continue;
            }
            byAuthor.computeIfAbsent(author, k -> new ArrayList<>()).add(skill);
        }
        List<Map<String, Object>> packages = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, List<Map<String, Object>>> entry
                : byAuthor.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                        .toList()) {
            String author = entry.getKey();
            List<Map<String, Object>> authorSkills = entry.getValue();
            List<String> slugs = authorSkills.stream()
                    .map(s -> String.valueOf(s.getOrDefault("name", "")))
                    .filter(n -> !n.isBlank() && !"null".equals(n))
                    .distinct()
                    .toList();
            long stars = authorSkills.stream().mapToLong(s -> asLong(s.get("stars"))).max().orElse(0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "skillhub_packages:" + author);
            item.put("source", "skillhub_packages");
            item.put("rank", rank++);
            item.put("name", author);
            item.put("description", author + " 专家包 · 含 " + slugs.size() + " 个技能");
            item.put("url", "https://github.com/" + author);
            item.put("install_command", "");
            item.put("tags", List.of());
            item.put("stats", "含 " + slugs.size() + " 个技能 · ★ " + stars);
            item.put("skill_slugs", slugs);
            packages.add(item);
        }
        return packages;
    }

    /**
     * 宽松地将对象转为 long（用于统计展示）。
     *
     * @param v 原始值
     * @return long 值，无法解析返回 0
     */
    private long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 解析市场 MCP 配置。
     * <p>根据技能包 URL 尝试从 GitHub 仓库获取真实 MCP 配置。优先读取
     * 技能目录下的 {@code mcp.json}、{@code mcp_config.json} 或
     * {@code .mcp.json}。若无法获取，返回错误而非假配置。</p>
     *
     * @param body 请求体 {@code { source, id, url }}
     * @return MCP 配置
     */
    @PostMapping("/market/mcp/config")
    public ApiResult<Map<String, Object>> resolveMcpConfig(@RequestBody Map<String, Object> body) {
        String source = asText(body.get("source"));
        String id = asText(body.get("id"));
        String url = asText(body.get("url"));
        log.info("解析 MCP 配置: source={}, id={}, url={}", source, id, url);

        if (url == null || url.isBlank()) {
            return ApiResult.ok(Map.of("config_json", Map.of()));
        }

        GithubRef ref = parseGithubUrl(url);
        if (ref == null) {
            return ApiResult.ok(Map.of("config_json", Map.of()));
        }

        String mcpName = id != null ? id : "default";
        for (String mcpFile : List.of("mcp.json", "mcp_config.json", ".mcp.json")) {
            String filePath = (ref.path() != null ? ref.path() + "/" : "") + mcpFile;
            String rawUrl = "https://raw.githubusercontent.com/" + ref.owner() + "/" + ref.repo()
                    + "/" + encodePath(ref.branch()) + "/" + encodePath(filePath);
            try {
                String content = httpGet(rawUrl);
                if (!content.isBlank()) {
                    Map<String, Object> config = parseJson(content);
                    if (!config.isEmpty()) {
                        return ApiResult.ok(Map.of("config_json", config));
                    }
                }
            } catch (Exception e) {
                log.debug("[skills] 获取 MCP 配置失败 ({}): {}", mcpFile, e.getMessage());
            }
        }

        Map<String, Object> fallback = Map.of(
                "mcpServers", Map.of(mcpName, Map.of(
                        "command", "npx",
                        "args", List.of("-y", mcpName),
                        "env", Map.of()
                ))
        );
        return ApiResult.ok(Map.of("config_json", fallback));
    }

    /**
     * 安装市场技能包。
     * <p>从市场技能项（GitHub 仓库子目录）真实下载并安装到 {@code skillsRoot/user/<name>}：
     * 解析 GitHub tree/blob URL → 通过 GitHub Contents API 递归拉取目录文件 → 校验 SKILL.md →
     * 写入用户技能目录。失败时返回具体错误，不再返回假成功。</p>
     *
     * @param body 请求体 {@code { source, id, url }}
     * @return 安装结果（真实 installed_skill_names 或 errors）
     */
    @PostMapping("/market/package/install")
    public ApiResult<Map<String, Object>> installMarketPackage(@RequestBody Map<String, Object> body) {
        String source = asText(body.get("source"));
        String id = asText(body.get("id"));
        String url = asText(body.get("url"));
        log.info("安装市场包: source={}, id={}, url={}", source, id, url);

        List<String> installed = new ArrayList<>();
        List<Map<String, String>> errors = new ArrayList<>();

        if (id == null || id.isBlank()) {
            errors.add(errorEntry(id, "技能标识(id)不能为空"));
            return installResult(null, installed, errors);
        }
        if (url == null || url.isBlank()) {
            errors.add(errorEntry(id, "技能来源地址(url)不能为空"));
            return installResult(null, installed, errors);
        }

        // 专家包（按作者）：安装该作者名下聚合的 top 技能；url 为作者主页，非单一技能目录
        if ("skillhub_packages".equals(source)) {
            String author = id.startsWith("skillhub_packages:")
                    ? id.substring("skillhub_packages:".length())
                    : id;
            return installAuthorPackage(author, installed, errors);
        }

        GithubRef ref = parseGithubUrl(url);
        if (ref == null) {
            errors.add(errorEntry(id, "仅支持 GitHub 仓库地址: " + url));
            return installResult(null, installed, errors);
        }

        String skillName = ref.path() == null || ref.path().isBlank()
                ? ref.repo()
                : ref.path().substring(ref.path().lastIndexOf('/') + 1);
        if (skillName.isBlank()) {
            skillName = ref.repo();
        }
        skillName = sanitizeSkillName(skillName);

        Path userDir = Paths.get(skillsRoot).resolve("user");
        Path targetDir = userDir.resolve(skillName);
        if (Files.exists(targetDir)) {
            errors.add(errorEntry(id, "技能已存在: " + skillName));
            return installResult(null, installed, errors);
        }

        Path tmpDir = null;
        try {
            Files.createDirectories(userDir);
            tmpDir = Files.createTempDirectory("skill-market-" + skillName);
            downloadGithubDir(ref, ref.path(), tmpDir, errors);
            if (!errors.isEmpty()) {
                return installResult(skillName, installed, errors);
            }
            if (!hasSkillMark(tmpDir)) {
                errors.add(errorEntry(id, "下载内容不含 SKILL.md，不是有效技能: " + skillName));
                return installResult(skillName, installed, errors);
            }
            copyDirectory(tmpDir, targetDir);
            installed.add(skillName);
            log.info("[skills] 市场技能安装成功: {} <- {}", skillName, url);
            return installResult(skillName, installed, errors);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add(errorEntry(id, "安装失败: " + e.getMessage()));
            return installResult(skillName, installed, errors);
        } finally {
            if (tmpDir != null) {
                deleteRecursivelyQuietly(tmpDir);
            }
        }
    }

    /**
     * 组装市场安装结果信封。
     *
     * @param skillName 目标技能名（失败时可为 null）
     * @param installed 已安装技能名列表
     * @param errors    错误列表
     * @return 安装结果
     */
    private ApiResult<Map<String, Object>> installResult(
            String skillName, List<String> installed, List<Map<String, String>> errors) {
        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("name", skillName != null ? skillName : "unknown");
        pkg.put("description", installed.isEmpty() ? "" : "从市场安装的技能包");
        pkg.put("instructions", "");
        pkg.put("skill_slugs", installed);
        pkg.put("avatar", "");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("package", pkg);
        result.put("installed_skill_names", installed);
        result.put("errors", errors);
        return ApiResult.ok(result);
    }

    /**
     * 安装“专家包”（按作者）：从 SkillsMP 榜单筛选该作者名下技能，下载并安装其 top 技能，
     * 返回已安装技能名列表。用于角色/专家包市场“添加”动作；单次安装数量受
     * {@link #MAX_AUTHOR_PACKAGE_SKILLS} 限制，避免一次下载整仓全部技能。
     *
     * @param author   作者（GitHub owner）
     * @param installed 已安装技能名收集器
     * @param errors   错误收集器
     * @return 安装结果
     */
    private ApiResult<Map<String, Object>> installAuthorPackage(
            String author, List<String> installed, List<Map<String, String>> errors) {
        if (author == null || author.isBlank()) {
            errors.add(errorEntry("skillhub_packages", "专家包作者不能为空"));
            return installResult(null, installed, errors);
        }
        Path userDir = Paths.get(skillsRoot).resolve("user");
        try {
            Files.createDirectories(userDir);
        } catch (IOException e) {
            errors.add(errorEntry("skillhub_packages:" + author, "创建用户技能目录失败: " + e.getMessage()));
            return installResult(author, installed, errors);
        }

        List<Map<String, Object>> skills = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            skills.addAll(fetchSkillsMpPage(page, 48));
        }
        List<Map<String, Object>> authorSkills = skills.stream()
                .filter(s -> author.equals(String.valueOf(s.getOrDefault("author", ""))))
                .limit(MAX_AUTHOR_PACKAGE_SKILLS)
                .toList();
        if (authorSkills.isEmpty()) {
            errors.add(errorEntry("skillhub_packages:" + author, "未找到该专家包下可安装的技能"));
            return installResult(author, installed, errors);
        }

        for (Map<String, Object> skill : authorSkills) {
            String skillName = sanitizeSkillName(String.valueOf(skill.getOrDefault("name", "")));
            String skillUrl = String.valueOf(skill.getOrDefault("githubUrl", ""));
            Path targetDir = userDir.resolve(skillName);
            if (Files.exists(targetDir)) {
                installed.add(skillName);
                continue;
            }
            GithubRef ref = parseGithubUrl(skillUrl);
            if (ref == null) {
                errors.add(errorEntry(skillName, "不支持的技能来源地址: " + skillUrl));
                continue;
            }
            Path tmpDir = null;
            try {
                tmpDir = Files.createTempDirectory("skill-market-" + skillName + "-");
                downloadGithubDir(ref, ref.path(), tmpDir, errors);
                if (hasSkillMark(tmpDir)) {
                    copyDirectory(tmpDir, targetDir);
                    installed.add(skillName);
                } else {
                    errors.add(errorEntry(skillName, "下载内容不含 SKILL.md，不是有效技能"));
                }
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                errors.add(errorEntry(skillName, "安装失败: " + e.getMessage()));
            } finally {
                if (tmpDir != null) {
                    deleteRecursivelyQuietly(tmpDir);
                }
            }
        }

        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("name", author);
        pkg.put("description", author + " 专家包（已安装 " + installed.size() + " 个技能）");
        pkg.put("instructions", "");
        pkg.put("skill_slugs", installed);
        pkg.put("avatar", "");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("package", pkg);
        result.put("installed_skill_names", installed);
        result.put("errors", errors);
        return ApiResult.ok(result);
    }

    /**
     * 构造单条错误项。
     *
     * @param id    技能标识
     * @param error 错误描述
     * @return 错误项
     */
    private Map<String, String> errorEntry(String id, String error) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("skill_slug", id);
        entry.put("error", error);
        return entry;
    }

    /**
     * GitHub 引用信息。
     *
     * @param owner  仓库所有者
     * @param repo   仓库名
     * @param branch 分支名
     * @param path   目录路径（可为空串）
     */
    private record GithubRef(String owner, String repo, String branch, String path) {
    }

    /**
     * 解析 GitHub tree/blob 地址。
     * <p>支持形如：{@code https://github.com/owner/repo/tree/branch/path}、
     * {@code https://github.com/owner/repo/blob/branch/path}、
     * {@code https://github.com/owner/repo}。</p>
     *
     * @param url GitHub 地址
     * @return 解析结果，无法解析返回 null
     */
    private GithubRef parseGithubUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Pattern pattern = Pattern.compile(
                "github\\.com/([^/]+)/([^/]+)(?:/(tree|blob)/([^/]+)(?:/(.*))?)?");
        Matcher matcher = pattern.matcher(url);
        if (!matcher.find()) {
            return null;
        }
        String owner = matcher.group(1);
        String repo = matcher.group(2);
        String branch = matcher.group(4);
        String path = matcher.group(5);
        if (branch == null || branch.isBlank()) {
            branch = "main";
        }
        return new GithubRef(owner, repo, branch, path != null ? path : "");
    }

    /**
     * 下载 GitHub 目录（递归），文件写入目标目录。
     * <p>通过 GitHub Contents API 列出目录项，文件用 raw 地址逐一下载。</p>
     *
     * @param ref      GitHub 引用
     * @param dirPath  要下载的目录路径（仓库内相对路径）
     * @param target   落盘目录
     * @param errors   错误收集器
     * @throws IOException          网络/IO 异常
     * @throws InterruptedException 中断异常
     */
    private void downloadGithubDir(GithubRef ref, String dirPath, Path target, List<Map<String, String>> errors)
            throws IOException, InterruptedException {
        String apiUrl = "https://api.github.com/repos/" + ref.owner() + "/" + ref.repo()
                + "/contents/" + encodePath(dirPath) + "?ref=" + encodePath(ref.branch());
        String response = httpGet(apiUrl);
        List<?> entries = parseJsonArray(response);
        if (entries == null || entries.isEmpty()) {
            errors.add(errorEntry(ref.path(), "目录为空或无法读取: " + dirPath));
            return;
        }
        for (Object entryObj : entries) {
            Map<?, ?> entry = (Map<?, ?>) entryObj;
            String type = String.valueOf(entry.get("type"));
            String name = String.valueOf(entry.get("name"));
            if ("dir".equals(type)) {
                String childPath = dirPath == null || dirPath.isBlank()
                        ? name
                        : dirPath + "/" + name;
                downloadGithubDir(ref, childPath, target, errors);
            } else if ("file".equals(type)) {
                Object downloadUrlObj = entry.get("download_url");
                String downloadUrl = downloadUrlObj != null ? String.valueOf(downloadUrlObj) : "";
                if (downloadUrl.isBlank() || "null".equals(downloadUrl)) {
                    errors.add(errorEntry(name, "文件无下载地址: " + name));
                    continue;
                }
                Path filePath = target.resolve(name);
                byte[] content = httpGetBytes(downloadUrl);
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, content);
            }
        }
    }

    /**
     * 路径编码（保留 / 分隔符）。
     *
     * @param path 路径
     * @return URL 编码后的路径
     */
    private String encodePath(String path) {
        return java.net.URLEncoder.encode(path, StandardCharsets.UTF_8).replace("%2F", "/");
    }

    /**
     * 解析 JSON 数组。
     *
     * @param json JSON 文本
     * @return 列表，解析失败返回空列表
     */
    @SuppressWarnings("unchecked")
    private List<?> parseJsonArray(String json) {
        try {
            Object value = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
            return value instanceof List<?> list ? list : List.of();
        } catch (Exception e) {
            log.debug("[skills] JSON 数组解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 清洗技能名（仅保留安全字符）。
     *
     * @param name 原始名
     * @return 清洗后的名称
     */
    private String sanitizeSkillName(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9._-]", "-");
        return cleaned.isBlank() ? "skill" : cleaned;
    }

    /**
     * 静默删除目录树。
     *
     * @param dir 目录
     */
    private void deleteRecursivelyQuietly(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    log.debug("[skills] 删除临时文件失败: {}", p);
                }
            });
        } catch (IOException e) {
            log.debug("[skills] 清理临时目录失败: {}", dir);
        }
    }

    /**
     * 简易 HTTP GET（返回字符串）。
     *
     * @param urlStr URL
     * @return 响应体
     * @throws IOException          网络/IO 异常
     * @throws InterruptedException 中断异常
     */
    private String httpGet(String urlStr) throws IOException, InterruptedException {
        return new String(httpGetBytes(urlStr), StandardCharsets.UTF_8);
    }

    /**
     * 下载字节内容（HTTP GET，跟随重定向）。
     *
     * @param urlStr URL
     * @return 响应字节
     * @throws IOException          网络/IO 异常、非法 URL、非 2xx 状态
     * @throws InterruptedException 中断异常
     */
    private byte[] httpGetBytes(String urlStr) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        URI uri;
        try {
            uri = URI.create(urlStr);
        } catch (IllegalArgumentException e) {
            throw new IOException("非法 URL: " + urlStr, e);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "AiControl/1.0 SkillMarket")
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " for " + urlStr);
        }
        return response.body();
    }

    /**
     * 简易 JSON 解析。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
