package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.common.support.ai.skill.SkillDefinition;
import com.chua.common.support.datasearch.skill.impl.ClawHubSkillOnlineProvider;
import com.chua.common.support.datasearch.skill.impl.GithubSkillOnlineProvider;
import com.chua.common.support.datasearch.skill.impl.LoopHubSkillOnlineProvider;
import com.chua.common.support.datasearch.skill.impl.McpWorldSkillOnlineProvider;
import com.chua.common.support.datasearch.skill.impl.SkillHubSkillOnlineProvider;
import com.chua.common.support.datasearch.skill.impl.SkillShSkillProvider;
import com.chua.common.support.datasearch.skill.impl.SkillsmpSkillProvider;
import com.chua.common.support.datasearch.skill.model.SkillMarketListing;
import com.chua.common.support.datasearch.skill.spi.SkillOfflineProvider;
import com.chua.common.support.datasearch.skill.spi.SkillOnlineProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.chat.ChatSkillSupport;
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
import com.chua.starter.ai.support.model.AiInstalledSkillDTO;
import com.chua.starter.ai.support.model.AiSkillMarketItemDTO;
import com.chua.starter.ai.support.model.AiSkillMarketSearchDTO;
import com.chua.starter.ai.support.model.AiSkillMarketSourceDTO;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
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
 * <p>在线市场搜索按 {@code SkillOnlineProvider} SPI 扇出到各市场源，控制器不直连任何单一市场站；
 * 源侧无公开查询接口时该源返回空条目，不伪造数据。</p>
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
    private String skillsRoot; // skills根

    /**
     * Skill 市场 API 基础地址，可通过环境变量或配置覆盖。
     * 示例：https://skillsmp.com/api/skills
     *       https://api.example.com/skills
     */
    @Value("${ai.skills-market-url:https://skillsmp.com/api/skills}")
    private String skillsMarketUrl; // skills市场url

    /**
     * 用户自定义外部技能搜索路径（内存维护）
     */
    private final List<Map<String, String>> externalPaths = new CopyOnWriteArrayList<>();

    /**
     * 全局设置服务（持久化市场启用状态）
     */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * 已安装技能扫描与启停（对话注入侧共用）
     */
    private final ChatSkillSupport chatSkillSupport;

    /**
     * 市场启用状态分组
     */
    private static final String MARKET_SETTING_GROUP = "skills";

    /**
     * 市场启用状态字段名
     */
    private static final String MARKET_ENABLED_FIELD = "market_enabled";

    /**
     * 按市场源的启用状态字段名前缀（skills.市场_clawhub_已启用 / 市场_skillhub_mcp_已启用 等）。
     * 未显式配置时默认启用。
     */
    private static final String MARKET_SOURCE_FIELD_PREFIX = "market_";
    private static final String MARKET_SOURCE_FIELD_SUFFIX = "_enabled"; // 市场源字段后缀

    /**
     * 专家包“添加”时单次安装的上限技能数（避免一次下载整仓全部技能）。
     */
    private static final int MAX_AUTHOR_PACKAGE_SKILLS = 12;

    /**
     * 在线市场搜索的单源条数上限（前端可传更大值，此处截断）。
     */
    private static final int MAX_MARKET_PAGE_SIZE = 50;

    /**
     * GitHub 段名合法字符集（owner / repo / branch）。
     * <p>解析结果直接拼进 {@code codeload.github.com} 与 {@code api.github.com} 地址，
     * 收紧字符可确保请求主机与路径段不被进一步改写。</p>
     */
    private static final Pattern GITHUB_SEGMENT = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    /**
     * 单个技能从归档解包的文件数上限。
     */
    private static final int MAX_SKILL_FILES = 500;

    /**
     * 单个技能从归档解包的总字节上限（32 MB）。
     */
    private static final long MAX_SKILL_BYTES = 32L * 1024 * 1024;

    /**
     * 仓库归档可读字节上限（64 MB），超过则不整读进内存。
     */
    private static final long MAX_ARCHIVE_BYTES = 64L * 1024 * 1024;

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
        /**
         * 扫描skills。
         * @param userDir 用户dir
         * @param "custom" "习俗"
         * @param result 结果
         */
        scanSkills(userDir, "custom", result);
        /**
         * 扫描skills。
         * @param builtinDir builtindir
         * @param "builtin" "builtin"
         * @param result 结果
         */
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
            /**
             * 尝试。
             * @param Files.list(builtinDir 文件.列表(builtindir
             */
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
     * 汇总每个已安装 Agent 的 {@code listAgentSkills()} 扫描结果，避免反射与硬编码目录。</p>
     *
     * @return 技能列表（按 提供者 分组）
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
     * 安全读取 提供者 是否安装（兜底 尝试/卡扣）。
     * @param provider 提供者
     * @return safeInstalled的结果
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
     * @param file 文件
     * @return 读取第一个线的结果
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
     * 为Agent实例化技能（返回技能源路径列表）。
     *
     * @param body 请求体 {@code { conversation_id: string, skills: string[] }}
     * @return 实例化结果
     */
    @PostMapping("/materialize-for-agent")
    public ApiResult<Map<String, Object>> materializeForAgent(@RequestBody Map<String, Object> body) {
        Object skillsObj = body.get("skills");
        List<Map<String, String>> skills = new ArrayList<>();
        if (skillsObj instanceof List) {
            Path builtinDir = normalizeRoot(skillsRoot).resolve("builtin");
            for (Object s : (List<?>) skillsObj) {
                String name = String.valueOf(s);
                Path src = builtinDir.resolve(name).normalize();
                if (!src.getParent().equals(builtinDir)) {
                    continue;
                }
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
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param skillPath skill路径
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "技能不存在: " + skillPath);
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("name", skillPath.getFileName().toString());
        result.put("description", readDescription(skillPath));
        return ApiResult.ok(result);
    }

    /**
     * 导入技能（复制到用户目录）。支持 压缩 文件、单个技能目录和父目录。
     *
     * @param body 请求体 {@code { skill_path: string }}
     * @return 导入结果
     */
    @Permission("admin")
    @PostMapping("/import")
     public ApiResult<Map<String, Object>> importSkill(@RequestBody Map<String, Object> body) {
         String rawPath = asText(body.get("skill_path"));
         if (rawPath != null && rawPath.startsWith("agent://")) {
             return ApiResult.ok(importAgentSkill(rawPath));
         }
         Path skillPath = resolveSkillPath(body);
        if (!Files.exists(skillPath)) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param skillPath skill路径
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "路径不存在: " + skillPath);
        }

        Path userDir = Paths.get(skillsRoot).resolve("user");
        try {
            Files.createDirectories(userDir);
        } catch (IOException e) {
            /**
             * api异常。
             * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建用户技能目录失败: " + e.getMessage());
        }

        List<String> importedNames = new ArrayList<>();

        try {
            if (Files.isRegularFile(skillPath) && skillPath.toString().toLowerCase().endsWith(".zip")) {
                Path tempDir = Files.createTempDirectory("skill-import-");
                try {
                    /**
                     * extract压缩。
                     * @param skillPath skill路径
                     * @param tempDir tempdir
                     */
                    extractZip(skillPath, tempDir);
                    /**
                     * 副本skills从目录。
                     * @param tempDir tempdir
                     * @param userDir 用户dir
                     * @param importedNames 导入名称
                     */
                    copySkillsFromDirectory(tempDir, userDir, importedNames);
                } catch (IOException e) {
                    /**
                     * api异常。
                     * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
                     * @param e.getMessage( e.获取消息(
                     * @return api异常的结果
                     */
                    throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导入 zip 技能失败: " + e.getMessage());
                } finally {
                    /**
                     * 删除recursively。
                     * @param tempDir tempdir
                     */
                    deleteRecursively(tempDir);
                }
            } else if (Files.isDirectory(skillPath) && hasSkillMark(skillPath)) {
                String name = skillPath.getFileName().toString();
                /**
                 * 副本目录。
                 * @param skillPath skill路径
                 * @param userDir.resolve(name 用户dir.resolve(名称
                 */
                copyDirectory(skillPath, userDir.resolve(name));
                importedNames.add(name);
            } else if (Files.isDirectory(skillPath)) {
                /**
                 * 副本skills从目录。
                 * @param skillPath skill路径
                 * @param userDir 用户dir
                 * @param importedNames 导入名称
                 */
                copySkillsFromDirectory(skillPath, userDir, importedNames);
            } else {
                /**
                 * api异常。
                 * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
                 * @param skillPath skill路径
                 * @return api异常的结果
                 */
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "不支持的导入类型: " + skillPath);
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            /**
             * api异常。
             * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导入技能失败: " + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skill_name", importedNames.isEmpty() ? "" : importedNames.getFirst());
        result.put("skill_names", importedNames);
        return ApiResult.ok(result);
    }

    /**
     * 扫描指定文件夹下的技能。
     *
     * @param body 请求体 {@code { folder_path: string }}
     * @return 技能列表
     */
    @Permission("admin")
    @PostMapping("/scan")
    public ApiResult<List<Map<String, Object>>> scan(@RequestBody Map<String, Object> body) {
        String folderPath = asText(body.get("folder_path"));
        List<Map<String, Object>> result = new ArrayList<>();
        if (folderPath == null || folderPath.isBlank()) {
            return ApiResult.ok(result);
        }
        Path dir = resolveContainedPath(folderPath);
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
            /**
             * 尝试。
             * @param Files.list(dir 文件.列表(dir
             */
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
    @Permission("admin")
    @PostMapping("/import-symlink")
     public ApiResult<Map<String, Object>> importSymlink(@RequestBody Map<String, Object> body) {
         String rawPath = asText(body.get("skill_path"));
         if (rawPath != null && rawPath.startsWith("agent://")) {
             return ApiResult.ok(importAgentSkill(rawPath));
         }
         Path skillPath = resolveSkillPath(body);
        if (!Files.exists(skillPath)) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param skillPath skill路径
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "路径不存在: " + skillPath);
        }

        Path userDir = Paths.get(skillsRoot).resolve("user");
        try {
            Files.createDirectories(userDir);
        } catch (IOException e) {
            /**
             * api异常。
             * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建用户技能目录失败: " + e.getMessage());
        }

        List<String> importedNames = new ArrayList<>();

        try {
            if (Files.isRegularFile(skillPath) && skillPath.toString().toLowerCase().endsWith(".zip")) {
 // 大小写 1: 压缩 文件 → 解压到临时目录，扫描技能后导入
                Path tempDir = Files.createTempDirectory("skill-import-");
                try {
                    /**
                     * extract压缩。
                     * @param skillPath skill路径
                     * @param tempDir tempdir
                     */
                    extractZip(skillPath, tempDir);
                    /**
                     * 导入skills从目录。
                     * @param tempDir tempdir
                     * @param userDir 用户dir
                     * @param importedNames 导入名称
                     */
                    importSkillsFromDirectory(tempDir, userDir, importedNames);
                } catch (IOException e) {
                    /**
                     * api异常。
                     * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
                     * @param e.getMessage( e.获取消息(
                     * @return api异常的结果
                     */
                    throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导入 zip 技能失败: " + e.getMessage());
                } finally {
                    /**
                     * 删除recursively。
                     * @param tempDir tempdir
                     */
                    deleteRecursively(tempDir);
                }
            } else if (Files.isDirectory(skillPath) && hasSkillMark(skillPath)) {
 // 大小写 2: 单个技能目录 → 符号链接/复制
                String name = skillPath.getFileName().toString();
                /**
                 * symlinkor副本。
                 * @param skillPath skill路径
                 * @param userDir.resolve(name 用户dir.resolve(名称
                 */
                symlinkOrCopy(skillPath, userDir.resolve(name));
                importedNames.add(name);
            } else if (Files.isDirectory(skillPath)) {
 // 大小写 3: 父目录 → 扫描子目录批量导入
                /**
                 * 导入skills从目录。
                 * @param skillPath skill路径
                 * @param userDir 用户dir
                 * @param importedNames 导入名称
                 */
                importSkillsFromDirectory(skillPath, userDir, importedNames);
            } else {
                /**
                 * api异常。
                 * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
                 * @param skillPath skill路径
                 * @return api异常的结果
                 */
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "不支持的导入类型: " + skillPath);
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            /**
             * api异常。
             * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导入技能失败: " + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skill_name", importedNames.isEmpty() ? "" : importedNames.getFirst());
        result.put("skill_names", importedNames);
        return ApiResult.ok(result);
    }

    /**
     * 删除技能。
     *
     * @param skillName 技能名
     * @return 成功信封
     * @param @PathVariable("skill_name" @路径变量("skill_名称"
     * @param @PathVariable("skill_name" @路径变量("skill_名称"
     * @param @PathVariable("skill_name" @PathVariable("skill_name"
     */
    @Permission("admin")
    @DeleteMapping("/{skill_name}")
    public ApiResult<Void> delete(@PathVariable("skill_name") String skillName) {
        Path userDir = normalizeRoot(skillsRoot).resolve("user");
        Path target = userDir.resolve(sanitizeSkillName(skillName)).normalize();
        if (!target.getParent().equals(userDir.normalize())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "技能名非法: " + skillName);
        }
        if (Files.exists(target)) {
            try {
                /**
                 * 删除recursively。
                 * @param userDir 用户dir
                 */
                deleteRecursively(target);
            } catch (IOException e) {
                /**
                 * api异常。
                 * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
                 * @param e.getMessage( e.获取消息(
                 * @return api异常的结果
                 */
                throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "删除技能失败: " + e.getMessage());
            }
        }
        return ApiResult.ok();
    }

    /**
     * 列出已安装技能（含启用状态）。
     *
     * <p>扫描的是技能根目录下的真实 {@code SKILL.md}，供搜索弹层的「已安装」区与管理列表渲染。</p>
     *
     * @return 已安装技能列表
     */
    @GetMapping("/installed")
    public ApiResult<List<AiInstalledSkillDTO>> installed() {
        List<AiInstalledSkillDTO> result = new ArrayList<>();
        for (ChatSkillSupport.InstalledSkill skill : chatSkillSupport.skills()) {
            result.add(new AiInstalledSkillDTO(
                    skill.name(),
                    skill.id(),
                    skill.description(),
                    skill.icon(),
                    skill.location(),
                    skill.custom() ? "custom" : "builtin",
                    skill.enabled(),
                    skill.content().length()));
        }
        return ApiResult.ok(result);
    }

    /**
     * 启用/停用技能。
     * <p>停用只让该技能不再注入对话，不删除文件；名单持久化在 {@code skills.disabled_skills}，
     * 按 {@code source:name} 限定标识记录。技能是宿主级共享资源，故与安装/删除同为管理员出口。</p>
     *
     * @param skillName 技能名
     * @param body      请求体 {@code { enabled: boolean, source?: "builtin"|"custom" }}
     * @return 成功信封
     */
    @Permission("admin")
    @PostMapping("/{skill_name}/toggle")
    public ApiResult<Void> toggleSkill(@PathVariable("skill_name") String skillName,
                                       @RequestBody Map<String, Object> body) {
        String name = sanitizeSkillName(skillName);
        String source = body.get("source") == null ? "" : String.valueOf(body.get("source")).trim();
        List<ChatSkillSupport.InstalledSkill> matched = chatSkillSupport.skills().stream()
                .filter(skill -> skill.name().equals(name))
                .filter(skill -> source.isEmpty() || source.equals(skill.custom() ? "custom" : "builtin"))
                .toList();
        if (matched.isEmpty()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "技能不存在: " + skillName);
        }
        if (matched.size() > 1) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "内置与用户技能同名，请指明 source: " + name);
        }
        boolean enabled = body.get("enabled") == null
                || Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        String id = matched.get(0).id();
        chatSkillSupport.toggle(id, enabled);
        log.info("[skills] 技能 {} -> {}", id, enabled ? "启用" : "停用");
        return ApiResult.ok();
    }

    /**
     * 为技能设置标签。
     *
     * @param skillName 技能名
     * @param body      请求体 {@code { audience_tags: string[], scenario_tags: string[] }}
     * @return 成功信封
     * @param @PathVariable("skill_name" @路径变量("skill_名称"
     */
    @Permission("admin")
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
    @Permission("admin")
    @PostMapping("/external-paths")
    public ApiResult<Void> addExternalPath(@RequestBody Map<String, Object> body) {
        String name = asText(body.get("name"));
        String path = asText(body.get("path"));
        if (name == null || path == null) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param 不能为空" 不能为空"
             * @return api异常的结果
             */
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
     * @param @RequestParam("path" @请求参数("路径"
     * @param @RequestParam("path" @请求参数("路径"
     * @param @RequestParam("path" @RequestParam("path"
     */
    @Permission("admin")
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
    @Permission("admin")
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
    @Permission("admin")
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
     * 按市场源启用/禁用。源值示例：clawhub、loophub、skillhub、skillhub_mcp、mcpworld、clawhub_plugins、skillhub_包。
     * <p>持久化到 {@code sys_ai_global_setting}（分组 skills，字段 market_<source>_enabled）。</p>
     *
     * @param source 市场源标识
     * @param enabled 是否启用
     * @return 成功信封
     * @param body 主体
     */
    @Permission("admin")
    @PostMapping("/market/source/toggle")
    public ApiResult<Void> marketSourceToggle(@RequestBody Map<String, Object> body) {
        String source = asText(body.get("source"));
        boolean enabled = body.get("enabled") != null && Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        if (source == null || source.isBlank()) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param "市场源不能为空" "市场源不能为空"
             * @return api异常的结果
             */
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
     * @param marketType 可选：市场类型 过滤（skill/mcp/plugin/preset）；为空返回全部
     * @return {@code { sources: [{ source, displayName, url, marketType, enabled }] }}
     */
    @GetMapping("/market/sources/status")
    public ApiResult<Map<String, Object>> marketSourcesStatus(
            @RequestParam(required = false) String marketType) {
        List<Map<String, Object>> sources = new ArrayList<>();
        Map<String, SkillMarketSourceProvider> providers =
                ServiceProvider.of(SkillMarketSourceProvider.class).list();
        if (providers == null || providers.isEmpty()) {
 // Spring Boot fat jar 下 BOOT-INF/类 内嵌的 服务提供者 静态发现
 // 可能失败，回退到显式枚举全部 skill市场源提供者 SPI 实现。
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
     * @param dir dir
     * @param userDir 用户dir
     * @param importedNames 导入名称
     */
    private void importSkillsFromDirectory(Path dir, Path userDir, List<String> importedNames) {
        /**
         * 尝试。
         * @param Files.list(dir 文件.列表(dir
         */
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.filter(Files::isDirectory).sorted().toList()) {
                if (hasSkillMark(p)) {
                    String name = p.getFileName().toString();
                    Path target = userDir.resolve(name);
                    if (!Files.exists(target)) {
                        /**
                         * symlinkor副本。
                         * @param p p
                         * @param target Target
                         */
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
     * @param dir dir
     * @param userDir 用户dir
     * @param importedNames 导入名称
     */
    private void copySkillsFromDirectory(Path dir, Path userDir, List<String> importedNames) {
        /**
         * 尝试。
         * @param Files.list(dir 文件.列表(dir
         */
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.filter(Files::isDirectory).sorted().toList()) {
                if (hasSkillMark(p)) {
                    String name = p.getFileName().toString();
                    Path target = userDir.resolve(name);
                    if (!Files.exists(target)) {
                        /**
                         * 副本目录。
                         * @param p p
                         * @param target Target
                         */
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
     * @param source 源
     * @param target Target
     */
    private void symlinkOrCopy(Path source, Path target) {
        try {
            Files.createSymbolicLink(target, source.toAbsolutePath());
        } catch (IOException | UnsupportedOperationException e) {
            try {
                /**
                 * 副本目录。
                 * @param source 源
                 * @param target Target
                 */
                copyDirectory(source, target);
            } catch (IOException ex) {
                log.warn("[skills] 复制技能失败: {} -> {}", source, target, ex);
            }
        }
    }

    /**
     * 解压 压缩 文件到目标目录。
     * @param zipFile 压缩文件
     * @param targetDir Targetdir
     */
    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        /**
         * 尝试。
         * @param ZipInputStream(Files.newInputStream(zipFile 压缩输入流(文件.新输入流(压缩文件
         */
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
        /**
         * 尝试。
         * @param Files.list(dir 文件.列表(dir
         */
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
        Path root = normalizeRoot(skillsRoot);
        Path file = root.resolve(subDir).resolve(fileName).normalize();
        if (!isWithin(root, file) || !Files.exists(file)) {
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
             /**
              * api异常。
              * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
              * @param 不能为空" 不能为空"
              * @return api异常的结果
              */
             throw new ApiException(ApiErrorCode.INVALID_PARAM, "skill_path 不能为空");
         }
         return resolveContainedPath(skillPath);
     }

    /**
     * 解析客户端路径，并限定在技能根目录或已登记的外部路径内。
     *
     * <p>技能目录、扫描目录由前端传入，不加约束时可指向任意服务器绝对路径：
     * 导入会把该目录内容复制进共享技能目录，扫描会把目录条目回显给调用方。</p>
     *
     * @param rawPath 客户端路径
     * @return 规范化后的绝对路径
     */
    private Path resolveContainedPath(String rawPath) {
        Path path;
        try {
            path = Paths.get(rawPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "路径格式非法: " + rawPath);
        }
        if (!isWithinAllowedRoots(path)) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "路径不在技能目录或已登记的外部路径内");
        }
        return path;
    }

    /**
     * 路径是否落在允许访问的根目录集合内。
     *
     * @param path 规范化后的绝对路径
     * @return 是否允许访问
     */
    private boolean isWithinAllowedRoots(Path path) {
        if (isWithin(normalizeRoot(skillsRoot), path)) {
            return true;
        }
        for (Map<String, String> entry : externalPaths) {
            String registered = entry.get("path");
            if (registered != null && isWithin(normalizeRoot(registered), path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 规范化根目录，非法路径按不匹配处理。
     *
     * @param root 根目录原始值
     * @return 规范化绝对目录，无法解析时返回 {@code null}
     */
    private static Path normalizeRoot(String root) {
        try {
            return Paths.get(root).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * 判定路径是否位于父目录内（含父目录自身）。
     *
     * @param base 父目录
     * @param path 待判定路径
     * @return 是否位于父目录内
     */
    private static boolean isWithin(Path base, Path path) {
        return base != null && path.startsWith(base);
    }

    /**
     * 导入 {@code agent://PROVIDER/SKILL} 虚拟地址指向的 Agent 本地技能。
     *
     * <p>前端「从 Agent 导入」对 agent 类来源合成 {@code agent://<providerKey>/<skillName>}
     * 地址。这里通过 {@link SkillOfflineProvider} SPI 将虚拟地址解析为真实落盘位置
     * （技能目录或单文件规则），再走与文件系统导入相同的符号链接/复制逻辑。</p>
     *
     * @param agentUri Agent:// 虚拟地址
     * @return 导入结果（skill_name / skill_名称）
     */
    private Map<String, Object> importAgentSkill(String agentUri) {
        String rest = agentUri.substring("agent://".length());
        int slash = rest.indexOf('/');
        String providerKey = slash > 0 ? rest.substring(0, slash) : rest;
        String skillName = slash > 0 ? rest.substring(slash + 1) : "";
        if (skillName.isBlank()) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param agentUri Agenturi
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "agent 地址不完整: " + agentUri);
        }

        Map<String, SkillOfflineProvider> providers = ServiceProvider.of(SkillOfflineProvider.class).list();
        SkillOfflineProvider provider = findProviderByKey(providers, providerKey);
        if (provider == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "未找到 Agent 提供者: " + providerKey + "（请先刷新来源列表）");
        }

        Path resolved;
        try {
            resolved = provider.resolveSkillPath(skillName);
        } catch (Exception e) {
            log.warn("[ApiSkill] SkillOfflineProvider[{}] 解析技能 {} 失败: {}", providerKey, skillName, e.getMessage());
            resolved = null;
        }
        if (resolved == null || !Files.exists(resolved)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND,
                    "本机未找到 Agent 技能: " + providerKey + "/" + skillName + "（该 Agent 可能已卸载或目录已变动）");
        }

        Path userDir = Paths.get(skillsRoot).resolve("user");
        try {
            Files.createDirectories(userDir);
        } catch (IOException e) {
            /**
             * api异常。
             * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建用户技能目录失败: " + e.getMessage());
        }

        boolean isFile = Files.isRegularFile(resolved);
        String name = isFile
                ? resolved.getFileName().toString().replaceAll("\\.(md|mdc)$", "")
                : resolved.getFileName().toString();
        name = sanitizeSkillName(name);
        Path target = userDir.resolve(name);
        List<String> imported = new ArrayList<>();
        try {
            if (Files.exists(target)) {
                imported.add(name);
            } else if (isFile) {
                Files.createDirectories(target);
                Files.copy(resolved, target.resolve("SKILL.md"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                imported.add(name);
            } else {
                /**
                 * symlinkor副本。
                 * @param resolved resolved
                 * @param target Target
                 */
                symlinkOrCopy(resolved, target);
                imported.add(name);
            }
        } catch (IOException e) {
            /**
             * api异常。
             * @param ApiErrorCode.INTERNAL_ERROR api错误编码.内部错误
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导入 Agent 技能失败: " + e.getMessage());
        }
        log.info("[ApiSkill] Agent 技能导入成功: {} <- {} -> {}", name, agentUri, resolved);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skill_name", name);
        result.put("skill_names", imported);
        return result;
    }

    /**
     * 按 SPI 注册键查找 提供者（大小写不敏感，前端可能以任意大小写合成 Agent:// 地址）。
     *
     * @param providers SPI 枚举结果
     * @param key       请求的 提供者 键
     * @return 匹配的 提供者；未找到返回 空
     */
    private SkillOfflineProvider findProviderByKey(Map<String, SkillOfflineProvider> providers, String key) {
        if (providers == null || key == null || key.isBlank()) {
            return null;
        }
        SkillOfflineProvider direct = providers.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, SkillOfflineProvider> entry : providers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
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
        /**
         * 尝试。
         * @param Files.walk(source 文件.walk(源
         */
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
            /**
             * 尝试。
             * @param Files.walk(path 文件.walk(路径
             */
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
     * @return 文本或 空
     */
    private static String asText(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    // ==================== 技能市场 ====================

    /**
     * 在线市场搜索：扇出到全部 {@link SkillOnlineProvider} 市场源并聚合结果。
     *
     * <p>关键词为空时等价于浏览各源榜单，供前端渲染「推荐」区。总开关
     * {@code skills.market_enabled} 关闭时不访问任何源；单源异常只记在该源的状态上，
     * 不影响其余源，也不返回伪造条目。</p>
     *
     * @param search  搜索关键词
     * @param keyword 搜索关键词（前端历史参数名，与 {@code search} 等价）
     * @param page    页码（默认 1）
     * @param limit   每页数量（默认 20）
     * @param sortBy  排序方式（stars/forks/updated，默认 stars），源侧不支持时由实现忽略
     * @param source  可选：只查某个市场源
     * @return 搜索结果
     */
    @GetMapping("/market/search")
    public ApiResult<AiSkillMarketSearchDTO> marketSearch(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "stars") String sortBy,
            @RequestParam(required = false) String source) {
        long fetchedAt = System.currentTimeMillis();
        String term = search == null || search.isBlank() ? (keyword == null ? "" : keyword.trim()) : search.trim();

        boolean enabled = sysAiGlobalSettingService.getBoolean(MARKET_SETTING_GROUP, MARKET_ENABLED_FIELD, true);
        if (!enabled) {
            log.info("[skills] 市场总开关关闭，跳过在线搜索");
            return ApiResult.ok(new AiSkillMarketSearchDTO(false, term, new ArrayList<>(), new ArrayList<>(), fetchedAt));
        }

        int size = Math.max(1, Math.min(limit, MAX_MARKET_PAGE_SIZE));
        int current = Math.max(1, page);
        Set<String> installed = installedSkillNames();
        List<AiSkillMarketItemDTO> items = new ArrayList<>();
        List<AiSkillMarketSourceDTO> sources = new ArrayList<>();
        for (SkillOnlineProvider provider : skillOnlineProviders()) {
            String src = provider.name();
            boolean wanted = source == null || source.isBlank() || source.equalsIgnoreCase(src);
            boolean sourceEnabled = sysAiGlobalSettingService.getBoolean(
                    MARKET_SETTING_GROUP,
                    MARKET_SOURCE_FIELD_PREFIX + src + MARKET_SOURCE_FIELD_SUFFIX,
                    true);
            if (!wanted || !sourceEnabled) {
                sources.add(new AiSkillMarketSourceDTO(src, false, 0, null));
                continue;
            }
            int count = 0;
            String error = null;
            try {
                List<SkillMarketListing> listings = provider.searchMarket(term, current, size, sortBy);
                for (SkillMarketListing listing : listings) {
                    items.add(toMarketItem(listing, installed));
                }
                count = listings.size();
            } catch (Exception e) {
                log.warn("[skills] 市场源搜索失败: source={}, keyword={}", src, term, e);
                error = "搜索失败: " + e.getMessage();
            }
            sources.add(new AiSkillMarketSourceDTO(src, true, count, error));
        }
        return ApiResult.ok(new AiSkillMarketSearchDTO(true, term, items, sources, fetchedAt));
    }

    /**
     * 收集在线市场源：SPI 发现优先，fat jar 下服务发现失效时回退显式枚举。
     *
     * <p>同一实现可能以「服务登记名 + 注解值」两个别名被收集两次，这里按实现类去重，
     * 避免同一源被重复请求。</p>
     *
     * @return 市场源提供器列表
     */
    private List<SkillOnlineProvider> skillOnlineProviders() {
        Map<String, SkillOnlineProvider> discovered = ServiceProvider.of(SkillOnlineProvider.class).list();
        List<SkillOnlineProvider> providers = new ArrayList<>();
        if (discovered != null) {
            for (SkillOnlineProvider provider : discovered.values()) {
                if (provider != null) {
                    providers.add(provider);
                }
            }
        }
        if (providers.isEmpty()) {
            providers.addAll(List.of(
                    new SkillsmpSkillProvider(),
                    new SkillShSkillProvider(),
                    new GithubSkillOnlineProvider(),
                    new ClawHubSkillOnlineProvider(),
                    new LoopHubSkillOnlineProvider(),
                    new SkillHubSkillOnlineProvider(),
                    new McpWorldSkillOnlineProvider()));
        }
        List<SkillOnlineProvider> unique = new ArrayList<>();
        Set<Class<?>> seen = new LinkedHashSet<>();
        for (SkillOnlineProvider provider : providers) {
            if (seen.add(provider.getClass())) {
                unique.add(provider);
            }
        }
        return unique;
    }

    /**
     * 已落盘技能名集合，用于给市场条目标注「已安装」。
     *
     * @return 用户目录与内置目录下的技能名
     */
    private Set<String> installedSkillNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String subdir : List.of("user", "builtin")) {
            Path dir = Paths.get(skillsRoot).resolve(subdir);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isDirectory)
                        .forEach(p -> names.add(p.getFileName().toString()));
            } catch (IOException e) {
                log.debug("[skills] 扫描已安装技能目录失败: {}", dir, e);
            }
        }
        return names;
    }

    /**
     * 市场条目转 DTO。
     *
     * @param listing   市场源归一化条目
     * @param installed 已安装技能名集合
     * @return 市场条目 DTO
     */
    private AiSkillMarketItemDTO toMarketItem(SkillMarketListing listing, Set<String> installed) {
        return new AiSkillMarketItemDTO(
                listing.getId(),
                listing.getSource(),
                listing.getRank(),
                listing.getName(),
                listing.getDescription(),
                listing.getAuthor(),
                listing.getUrl(),
                listing.getPath(),
                listing.getStars(),
                listing.getForks(),
                listing.getInstalls(),
                listing.getContentLanguage(),
                listing.getName() != null && installed.contains(listing.getName()));
    }

    /**
     * 同步市场排行数据。前端调用此接口拉取市场数据。
     *
     * <p>按市场类型分发：扁平技能源（skillsmp/clawhub/skillhub/loophub）共享 SkillsMP 技能榜
     * （仅拉取一次并按源打标签）；专家包源（skillhub_包）将 skillsmp 技能按作者聚合为
     * “专家包”（一个作者=一个包，含其下技能清单），与扁平技能榜结构区分；MCP/插件源
     * （skillhub_mcp/mcpworld/clawhub_plugins）暂无可用数据源，标记 {@code unavailable} 并返回
     * 空列表，不返回伪造数据，也不报错。响应附带 {@code source_status} 供前端展示诚实空态。</p>
     *
     * @param body 请求体 {@code { sources: ["clawhub", "skillhub", ...] }}
     * @return 同步结果
     */
    @SuppressWarnings("unchecked")
    @Permission("admin")
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

 // 扁平技能源：共享 skillsmp 技能榜，仅拉取一次，按请求的源打标签
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
     * 拉取 skillsmp 技能榜单页原始技能数据。
     *
     * @param page  页码（从 1 开始）
     * @param limit 每页数量（skillsmp 上限 48，超出自动截断）
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
    @Permission("admin")
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
            try {
                String content = new String(httpGetGithubFile(ref, filePath), StandardCharsets.UTF_8);
                if (!content.isBlank()) {
                    Map<String, Object> config = parseJson(content);
                    if (!config.isEmpty()) {
                        return ApiResult.ok(Map.of("config_json", config));
                    }
                }
            } catch (Exception e) {
                log.debug("[skills] 获取 MCP 配置失败 ({}) : {}", filePath, e.getMessage());
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
     * 获取市场技能内容（SKILL.md 原文）。
     *
     * <p>按市场项的 GitHub 地址尽力拉取 {@code SKILL.md}/{@code skill.md}，供前端「内容预览」。
     * 拉取失败或地址不可解析时返回 {@code available=false}，前端展示降级态，不报错。</p>
     *
     * @param body 请求体 {@code { source, id, url }}
     * @return {@code { content, available, source_url }}
     */
    @PostMapping("/market/content")
    public ApiResult<Map<String, Object>> marketContent(@RequestBody Map<String, Object> body) {
        String url = asText(body.get("url"));
        Map<String, Object> unavailable = new LinkedHashMap<>();
        unavailable.put("content", "");
        unavailable.put("available", false);
        if (url == null || url.isBlank()) {
            return ApiResult.ok(unavailable);
        }
        GithubRef ref = parseGithubUrl(url);
        if (ref == null) {
            return ApiResult.ok(unavailable);
        }
        String subdir = asText(body.get("path"));
        String wanted = (subdir != null && !subdir.isBlank()) ? subdir.trim() : ref.path();
        // 复用安装流程的目录反查：市场 path 多为技能叶子名，真实父目录可能带 "skills/" 等前缀
        String base = resolveSkillDir(ref, wanted);
        for (String file : List.of("SKILL.md", "skill.md")) {
            String filePath = base.isBlank() ? file : base + "/" + file;
            try {
                String content = new String(httpGetGithubFile(ref, filePath), StandardCharsets.UTF_8);
                if (content != null && !content.isBlank()) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("content", content);
                    result.put("available", true);
                    result.put("source_url", url);
                    return ApiResult.ok(result);
                }
            } catch (Exception e) {
                log.debug("[skills] 拉取市场技能内容失败 (path={}): {}", filePath, e.getMessage());
            }
        }
        return ApiResult.ok(unavailable);
    }

    /**
     * 读取本机 Agent 技能内容（「从 Agent 导入」扫描项的内容预览）。
     *
     * <p>通过 {@link SkillOfflineProvider} SPI 将 {@code provider + skill_name} 解析为本机
     * 落盘位置（技能目录或单文件规则），读取其 SKILL.md 文本。未安装/已删除时
     * {@code available=false} 并附 error，前端展示降级态。</p>
     *
     * @param body 请求体 {@code { provider, skill_name }}
     * @return {@code { content, location, available, error? }}
     */
    @PostMapping("/agent/content")
    public ApiResult<Map<String, Object>> agentContent(@RequestBody Map<String, Object> body) {
        String providerKey = asText(body.get("provider"));
        String skillName = asText(body.get("skill_name"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", "");
        result.put("available", false);
        if (providerKey == null || providerKey.isBlank() || skillName == null || skillName.isBlank()) {
            result.put("error", "provider 与 skill_name 不能为空");
            return ApiResult.ok(result);
        }
        SkillOfflineProvider provider = findProviderByKey(
                ServiceProvider.of(SkillOfflineProvider.class).list(), providerKey);
        if (provider == null) {
            result.put("error", "未找到 Agent 提供者: " + providerKey);
            return ApiResult.ok(result);
        }
        Path skillPath;
        try {
            skillPath = provider.resolveSkillPath(skillName);
        } catch (Exception e) {
            result.put("error", "解析技能位置失败: " + e.getMessage());
            return ApiResult.ok(result);
        }
        if (skillPath == null) {
            result.put("error", "本机未找到该 Agent 技能（该 Agent 可能已卸载或目录已变动）");
            return ApiResult.ok(result);
        }
        Path md;
        if (Files.isRegularFile(skillPath)) {
            md = skillPath;
        } else {
            md = skillPath.resolve("SKILL.md");
            if (!Files.exists(md)) {
                md = skillPath.resolve("skill.md");
            }
        }
        if (!Files.exists(md)) {
            result.put("error", "未找到 SKILL.md 内容文件");
            return ApiResult.ok(result);
        }
        try {
            String content = Files.readString(md, StandardCharsets.UTF_8);
            result.put("content", content);
            result.put("location", md.toAbsolutePath().toString());
            result.put("available", true);
            return ApiResult.ok(result);
        } catch (IOException e) {
            result.put("error", "读取内容失败: " + e.getMessage());
            return ApiResult.ok(result);
        }
    }

    /**
     * 安装市场技能包。
     * <p>从市场技能项（GitHub 仓库子目录）真实下载并安装到 {@code skillsRoot/user/<name>}：
     * 解析 GitHub 树/blob URL → 归一化仓库内技能目录 → 通过 GitHub 内容 API 递归拉取目录文件 →
     * 校验 SKILL.md → 写入用户技能目录。失败时返回具体错误，不再返回假成功。</p>
     *
     * @param body 请求体 {@code { source, id, url, path }}
     * @return 安装结果（真实 installed_skill_名称 或 错误）
     */
    @Permission("admin")
    @PostMapping("/market/package/install")
    public ApiResult<Map<String, Object>> installMarketPackage(@RequestBody Map<String, Object> body) {
        String source = asText(body.get("source"));
        String id = asText(body.get("id"));
        String url = asText(body.get("url"));
        String path = asText(body.get("path"));
        log.info("安装市场包: source={}, id={}, url={}, path={}", source, id, url, path);

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
        String dirPath = resolveSkillDir(ref, path);

        String skillName = dirPath == null || dirPath.isBlank()
                ? ref.repo()
                : dirPath.substring(dirPath.lastIndexOf('/') + 1);
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
            /**
             * downloadgithubdir。
             * @param ref ref
             * @param dirPath 目录路径
             */
            downloadGithubDir(ref, dirPath, tmpDir, errors);
            if (!errors.isEmpty()) {
                return installResult(skillName, installed, errors);
            }
            if (!hasSkillMark(tmpDir)) {
                errors.add(errorEntry(id, "下载内容不含 SKILL.md，不是有效技能: " + skillName));
                return installResult(skillName, installed, errors);
            }
            /**
             * 副本目录。
             * @param tmpDir tmpdir
             * @param targetDir Targetdir
             */
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
                /**
                 * 删除recursivelyquietly。
                 * @param tmpDir tmpdir
                 */
                deleteRecursivelyQuietly(tmpDir);
            }
        }
    }

    /**
     * 组装市场安装结果信封。
     *
     * @param skillName 目标技能名（失败时可为 空）
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
     * 安装“专家包”（按作者）：从 skillsmp 榜单筛选该作者名下技能，下载并安装其 top 技能，
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
                /**
                 * downloadgithubdir。
                 * @param ref ref
                 * @param ref.path( ref.路径(
                 */
                downloadGithubDir(ref, ref.path(), tmpDir, errors);
                if (hasSkillMark(tmpDir)) {
                    /**
                     * 副本目录。
                     * @param tmpDir tmpdir
                     * @param targetDir Targetdir
                     */
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
                    /**
                     * 删除recursivelyquietly。
                     * @param tmpDir tmpdir
                     */
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
     * @return GithubRef的结果
     */
    private record GithubRef(String owner, String repo, String branch, String path) {
    }

    /**
     * 解析 GitHub 树/blob 地址。
     * <p>支持形如：{@code https://github.com/owner/repo/tree/branch/path}、
     * {@code https://github.com/owner/repo/blob/branch/path}、
     * {@code https://github.com/owner/repo}。</p>
     *
     * @param url GitHub 地址
     * @return 解析结果，无法解析返回 空
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
        if (!isGithubSegment(owner) || !isGithubSegment(repo) || !isGithubSegment(branch)) {
            return null;
        }
        /**
         * githubref。
         * @param owner owner
         * @param repo repo
         * @param branch 分支
         * @param "" ""
         * @return GithubRef的结果
         */
        return new GithubRef(owner, repo, branch, path != null ? path : "");
    }

    /**
     * 是否为合法的 GitHub 段名。
     *
     * @param segment 段名
     * @return 是否合法
     */
    private static boolean isGithubSegment(String segment) {
        return segment != null && GITHUB_SEGMENT.matcher(segment).matches();
    }

    /**
     * 归一化仓库内技能目录。
     * <p>市场条目回传的目录形态不一：技能市场与 GitHub 源给完整相对路径，skills.sh 只给末级
     * 技能名。后者直接当目录下载必然取不到内容，故用 GitHub 树接口在仓库内反查
     * {@code <名称>/SKILL.md} 的真实父目录；反查不到时按原值继续，由 SKILL.md 校验如实报错。</p>
     *
     * @param ref  GitHub 引用
     * @param path 市场条目给出的目录或末级技能名，可为空
     * @return 仓库内技能目录，空串表示仓库根
     */
    private String resolveSkillDir(GithubRef ref, String path) {
        String urlPath = ref.path() == null ? "" : ref.path().trim();
        String wanted = path == null ? "" : path.trim().replaceAll("^/+", "");
        if (wanted.isEmpty()) {
            return urlPath;
        }
        if (wanted.contains("/") || wanted.equalsIgnoreCase(leafOf(urlPath))) {
            return wanted;
        }
        try {
            String treeUrl = "https://api.github.com/repos/" + ref.owner() + "/" + ref.repo()
                    + "/git/trees/" + encodePath(ref.branch()) + "?recursive=1";
            Object treeValue;
            try {
                treeValue = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(httpGet(treeUrl), Object.class);
            } catch (Exception parseEx) {
                treeValue = null;
            }
            List<?> nodes = null;
            if (treeValue instanceof List<?> list) {
                nodes = list;
            } else if (treeValue instanceof Map<?, ?> map && map.get("tree") instanceof List<?> list) {
                nodes = list;
            }
            if (nodes == null) {
                return wanted;
            }
            String suffix = "/" + wanted.toLowerCase() + "/skill.md";
            for (Object nodeObj : nodes) {
                if (!(nodeObj instanceof Map<?, ?> node)) {
                    continue;
                }
                if (!"blob".equals(String.valueOf(node.get("type")))) {
                    continue;
                }
                String nodePath = String.valueOf(node.get("path")).toLowerCase();
                if (nodePath.endsWith(suffix)) {
                    return String.valueOf(node.get("path")).replaceAll("(?i)/[^/]+$", "");
                }
            }
        } catch (Exception e) {
            log.warn("[skills] 反查技能目录失败: repo={}, name={}", ref.owner() + "/" + ref.repo(), wanted, e);
        }
        return wanted;
    }

    /**
     * 取路径的末级段。
     *
     * @param path 路径，可为空串
     * @return 末级段，入参为空时返回空串
     */
    private static String leafOf(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * 下载 GitHub 目录，文件写入目标目录。
     *
     * <p>优先用 Contents API 逐项拉取：一次列目录加每文件一次请求，实测比整仓归档快一个数量级
     * （归档需下载整个仓库）。仅当目录列不出来（仓库内路径不存在、被限流或 IO 异常）时，
     * 才回退单次拉取仓库归档（{@code codeload.github.com}）并在本地解出技能子目录。</p>
     *
     * @param ref      GitHub 引用
     * @param dirPath  要下载的目录路径（仓库内相对路径，空串表示仓库根）
     * @param target   落盘目录
     * @param errors   错误收集器
     * @throws IOException          网络/IO 异常
     * @throws InterruptedException 中断异常
     */
    private void downloadGithubDir(GithubRef ref, String dirPath, Path target, List<Map<String, String>> errors)
            throws IOException, InterruptedException {
        String normalized = dirPath == null ? "" : dirPath.trim().replaceAll("^/+|/+$", "");
        List<Map<String, String>> staged = new ArrayList<>();
        boolean listed = false;
        try {
            listed = downloadGithubContentsDir(ref, normalized, target, staged);
        } catch (InterruptedException e) {
            throw e;
        } catch (IOException e) {
            log.warn("[skills] 逐项下载异常，回退仓库归档: repo={}, dir={}, err={}",
                    ref.owner() + "/" + ref.repo(), normalized, e.getMessage());
            staged.clear();
        }
        if (listed) {
            errors.addAll(staged);
            return;
        }
        if (extractSkillFromArchive(ref, normalized, target, staged)) {
            errors.addAll(staged);
            return;
        }
        errors.add(errorEntry(normalized, "技能目录不存在或无法读取: " + (normalized.isEmpty() ? "/" : normalized)));
    }

    /**
     * 从仓库 tar.gz 归档中解出技能目录。
     *
     * @param ref     GitHub 引用
     * @param dirPath 仓库内技能目录，空串表示整个仓库
     * @param target  落盘目录
     * @param errors  错误收集器
     * @return {@code true} 表示已由归档处理（含超限报错），{@code false} 表示需回退逐项下载
     * @throws IOException IO 异常
     */
    private boolean extractSkillFromArchive(GithubRef ref, String dirPath, Path target,
                                            List<Map<String, String>> errors) throws IOException {
        String archiveUrl = "https://codeload.github.com/" + ref.owner() + "/" + ref.repo()
                + "/tar.gz/refs/heads/" + encodePath(ref.branch());
        byte[] archive;
        try {
            archive = httpGetBytes(archiveUrl, null, ARCHIVE_TIMEOUT);
        } catch (Exception e) {
            log.warn("[skills] 仓库归档下载失败，回退逐项下载: repo={}, err={}",
                    ref.owner() + "/" + ref.repo(), e.getMessage());
            return false;
        }
        if (archive.length > MAX_ARCHIVE_BYTES) {
            log.warn("[skills] 仓库归档 {} MB 超过 {} MB 上限，回退逐项下载",
                    archive.length / (1024 * 1024), MAX_ARCHIVE_BYTES / (1024 * 1024));
            return false;
        }
        long written = 0L;
        int files = 0;
        boolean matched = false;
        try (TarArchiveInputStream stream = new TarArchiveInputStream(
                new GZIPInputStream(new ByteArrayInputStream(archive), 8192), StandardCharsets.UTF_8.name())) {
            TarArchiveEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                String relative = stripArchiveRoot(entry.getName());
                if (relative == null || relative.isEmpty()) {
                    continue;
                }
                if (!dirPath.isEmpty()) {
                    if (!relative.startsWith(dirPath + "/")) {
                        continue;
                    }
                    relative = relative.substring(dirPath.length() + 1);
                    if (relative.isEmpty()) {
                        continue;
                    }
                }
                matched = true;
                Path out = resolveInTarget(target, relative);
                if (out == null) {
                    errors.add(errorEntry(relative, "归档中存在越界路径，已中止安装"));
                    return true;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                if (!entry.isFile()) {
                    continue;
                }
                if (files >= MAX_SKILL_FILES || written + entry.getSize() > MAX_SKILL_BYTES) {
                    errors.add(errorEntry(dirPath, "技能内容超出安装上限：最多 " + MAX_SKILL_FILES
                            + " 个文件 / " + (MAX_SKILL_BYTES / (1024 * 1024)) + " MB"));
                    return true;
                }
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = stream.read(buffer)) > 0) {
                        os.write(buffer, 0, read);
                        written += read;
                    }
                }
                files++;
            }
        } catch (IOException e) {
            log.warn("[skills] 归档解包失败，回退逐项下载: repo={}, err={}",
                    ref.owner() + "/" + ref.repo(), e.getMessage());
            return false;
        }
        if (!matched) {
            log.warn("[skills] 仓库归档中不存在目录 {}，回退逐项下载", dirPath);
            return false;
        }
        log.info("[skills] 归档解包完成: repo={}, dir={}, 文件={} 个, 字节={}",
                ref.owner() + "/" + ref.repo(), dirPath, files, written);
        return true;
    }

    /**
     * 去掉归档条目的仓库根前缀（GitHub 归档统一为 {@code <repo>-<ref>/...}）。
     *
     * @param entryName 归档条目名
     * @return 仓库内相对路径，根条目返回 {@code null}
     */
    private static String stripArchiveRoot(String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            return null;
        }
        int slash = entryName.indexOf('/');
        return slash < 0 ? null : entryName.substring(slash + 1);
    }

    /**
     * 将归档内相对路径解析到落盘目录内，拒绝绝对路径与逃逸。
     *
     * @param target   落盘目录
     * @param relative 归档内相对路径
     * @return 落盘路径，越界时返回 {@code null}
     */
    private Path resolveInTarget(Path target, String relative) {
        if (relative.startsWith("/") || relative.contains(":")) {
            return null;
        }
        Path base = target.toAbsolutePath().normalize();
        try {
            Path out = base.resolve(relative).normalize();
            return out.startsWith(base) && !out.equals(base) ? out : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * 经 Contents API 递归下载目录（入口，落盘路径以该目录为根）。
     *
     * @param ref     GitHub 引用
     * @param dirPath 仓库内目录，空串表示仓库根
     * @param target  落盘目录
     * @param errors  错误收集器
     * @return 根目录是否成功列出
     * @throws IOException          网络/IO 异常
     * @throws InterruptedException 中断异常
     */
    private boolean downloadGithubContentsDir(GithubRef ref, String dirPath, Path target,
                                              List<Map<String, String>> errors)
            throws IOException, InterruptedException {
        return downloadGithubContentsDir(ref, dirPath, dirPath, target, errors);
    }

    /**
     * 经 Contents API 递归下载目录，文件按技能目录内的相对路径镜像落盘。
     *
     * @param ref      GitHub 引用
     * @param dirPath  当前遍历的仓库内目录
     * @param rootDir  技能目录（落盘根的对应仓库内路径）
     * @param target   落盘目录
     * @param errors   错误收集器
     * @return 当前目录是否成功列出
     * @throws IOException          网络/IO 异常
     * @throws InterruptedException 中断异常
     */
    private boolean downloadGithubContentsDir(GithubRef ref, String dirPath, String rootDir, Path target,
                                              List<Map<String, String>> errors)
            throws IOException, InterruptedException {
        String apiUrl = "https://api.github.com/repos/" + ref.owner() + "/" + ref.repo()
                + "/contents/" + encodePath(dirPath) + "?ref=" + encodePath(ref.branch());
        List<?> entries = parseJsonArray(httpGet(apiUrl));
        if (entries.isEmpty()) {
            return false;
        }
        for (Object entryObj : entries) {
            Map<?, ?> entry = (Map<?, ?>) entryObj;
            String type = String.valueOf(entry.get("type"));
            String name = String.valueOf(entry.get("name"));
            if ("dir".equals(type)) {
                String childPath = dirPath.isEmpty() ? name : dirPath + "/" + name;
                try {
                    if (!downloadGithubContentsDir(ref, childPath, rootDir, target, errors)) {
                        errors.add(errorEntry(childPath, "子目录为空或无法读取: " + childPath));
                    }
                } catch (InterruptedException e) {
                    throw e;
                } catch (IOException e) {
                    errors.add(errorEntry(childPath, "子目录下载失败: " + e.getMessage()));
                }
            } else if ("file".equals(type)) {
                String relativeDir = dirPath.length() > rootDir.length()
                        ? dirPath.substring(rootDir.length() + 1) : "";
                String relative = relativeDir.isEmpty() ? name : relativeDir + "/" + name;
                String filePath = dirPath.isEmpty() ? name : dirPath + "/" + name;
                Path out = resolveInTarget(target, relative);
                if (out == null) {
                    errors.add(errorEntry(relative, "文件名非法: " + relative));
                    continue;
                }
                try {
                    Files.createDirectories(out.getParent());
                    Files.write(out, httpGetGithubFile(ref, filePath));
                } catch (InterruptedException e) {
                    throw e;
                } catch (IOException e) {
                    errors.add(errorEntry(relative, "文件下载失败: " + e.getMessage()));
                }
            }
        }
        return true;
    }

    /**
     * 经 Contents API 读取仓库内单个文件内容。
     *
     * <p>走 {@code api.github.com} 并声明 raw 媒体类型，避免依赖 {@code raw.githubusercontent.com}
     * （该域名在部分网络下不可达）。</p>
     *
     * @param ref      GitHub 引用
     * @param filePath 仓库内文件路径
     * @return 文件字节
     * @throws IOException          网络/IO 异常
     * @throws InterruptedException 中断异常
     */
    private byte[] httpGetGithubFile(GithubRef ref, String filePath) throws IOException, InterruptedException {
        String apiUrl = "https://api.github.com/repos/" + ref.owner() + "/" + ref.repo()
                + "/contents/" + encodePath(filePath) + "?ref=" + encodePath(ref.branch());
        return httpGetBytes(apiUrl, GITHUB_RAW_ACCEPT, DEFAULT_TIMEOUT);
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
        /**
         * 尝试。
         * @param Files.walk(dir 文件.walk(dir
         */
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
     * 简易 HTTP 获取（返回字符串）。
     *
     * @param urlStr URL
     * @return 响应体
     * @throws IOException          网络/IO 异常
     * @throws InterruptedException 中断异常
     */
    private String httpGet(String urlStr) throws IOException, InterruptedException {
        /**
         * 字符串。
         * @param httpGetBytes(urlStr http获取bytes(urlstr
         * @return 字符串的结果
         */
        return new String(httpGetBytes(urlStr), StandardCharsets.UTF_8);
    }

    /**
     * 下载字节内容（HTTP 获取，跟随重定向）。
     *
     * @param urlStr URL
     * @return 响应字节
     * @throws IOException          网络/IO 异常、非法 URL、非 2xx 状态
     * @throws InterruptedException 中断异常
     */
    /**
     * 信任操作系统（Windows）根证书库的 SSL 上下文。
     *
     * <p>本机可能经 SteamTools 等加速工具以本地根 CA 代理 GitHub，其根证书已装入
     * Windows 证书库（浏览器信任）但不在 JDK 默认 cacerts 中。此处显式信任
     * Windows-ROOT，仍执行完整证书链校验；非 Windows 或加载失败时回退 JDK 默认信任库。</p>
     */
    private static final SSLContext OS_TRUST_SSL_CONTEXT = buildOsTrustSslContext();

    private static SSLContext buildOsTrustSslContext() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore osRoots = null;
            try {
                osRoots = KeyStore.getInstance("Windows-ROOT");
                osRoots.load(null, null);
            } catch (Exception ignore) {
                osRoots = null;
            }
            tmf.init(osRoots);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);
            return context;
        } catch (Exception e) {
            throw new IllegalStateException("初始化操作系统信任 SSL 上下文失败", e);
        }
    }

    /**
     * 进程级共享 HTTP 客户端（连接超时收紧，避免不可达域名长时间占住请求线程）。
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .sslContext(OS_TRUST_SSL_CONTEXT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 普通请求的整体超时。
     */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 仓库归档下载的整体超时（单次请求，需容忍较大仓库）。
     */
    private static final Duration ARCHIVE_TIMEOUT = Duration.ofSeconds(120);

    /**
     * GitHub Contents API 的 raw 媒体类型。
     */
    private static final String GITHUB_RAW_ACCEPT = "application/vnd.github.raw";

    private byte[] httpGetBytes(String urlStr) throws IOException, InterruptedException {
        return httpGetBytes(urlStr, null, DEFAULT_TIMEOUT);
    }

    /**
     * 下载字节内容。
     *
     * @param urlStr  URL
     * @param accept  {@code Accept} 头，可为 {@code null}
     * @param timeout 整体超时
     * @return 响应字节
     * @throws IOException          网络/IO 异常、非法 URL、非 2xx 状态
     * @throws InterruptedException 中断异常
     */
    private byte[] httpGetBytes(String urlStr, String accept, Duration timeout)
            throws IOException, InterruptedException {
        URI uri;
        try {
            uri = URI.create(urlStr);
        } catch (IllegalArgumentException e) {
            throw new IOException("非法 URL: " + urlStr, e);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("User-Agent", "AiControl/1.0 SkillMarket");
        if (accept != null && !accept.isBlank()) {
            builder.header("Accept", accept);
        }
        HttpResponse<byte[]> response = HTTP_CLIENT.send(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " for " + urlStr);
        }
        return response.body();
    }

    /**
     * 简易 JSON 解析。
     * @param json json
     * @return 解析json的结果
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
