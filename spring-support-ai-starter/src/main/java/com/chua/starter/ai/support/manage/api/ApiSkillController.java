package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * 作为技能描述。市场相关接口返回合理占位结果，不做真实网络交互。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
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
     * 用户自定义外部技能搜索路径（内存维护）
     */
    private final List<Map<String, String>> externalPaths = new CopyOnWriteArrayList<>();

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
            } catch (IOException ignored) {
                // 忽略遍历异常
            }
        }
        return ApiResult.ok(result);
    }

    /**
     * 扫描本机已安装 AI 编辑器的 skill/rules 目录，返回可导入的技能列表。
     *
     * @return 技能列表（按编辑器分组）
     */
    @GetMapping("/agent")
    public ApiResult<List<Map<String, Object>>> fromAgent() {
        List<Map<String, Object>> result = new ArrayList<>();
        Path userHome = Paths.get(System.getProperty("user.home", "."));

        try {
            // 实例化 AgentEditorProvider 扫描已安装的编辑器
            Class<?> clazz = Class.forName("com.chua.common.support.datasearch.agent.AgentEditorProvider");
            Object provider = clazz.getDeclaredConstructor().newInstance();

            // 获取已安装的编辑器列表
            @SuppressWarnings("unchecked")
            List<String> editors = (List<String>) clazz.getMethod("listAvailableEditors").invoke(provider);
            log.info("[ApiSkill] 已安装的 AI 编辑器: {}", editors);

            // 遍历所有编辑器，扫描其 skill 目录
            for (String editorName : editors) {
                // Cursor: ~/.cursor/rules/*.mdc
                if ("Cursor".equals(editorName)) {
                    scanDirectorySkills(userHome.resolve(".cursor/rules"), "Cursor", result);
                }
                // Claude Code: ~/.claude/commands/*.md
                else if ("Claude Code".equals(editorName)) {
                    scanDirectorySkills(userHome.resolve(".claude/commands"), "Claude Code", result);
                }
                // Windsurf: ~/.windsurf/rules/*.md
                else if ("Windsurf".equals(editorName)) {
                    scanDirectorySkills(userHome.resolve(".windsurf/rules"), "Windsurf", result);
                }
                // Roo Code: ~/.roo/rules/*.md
                else if ("Roo Code".equals(editorName)) {
                    scanDirectorySkills(userHome.resolve(".roo/rules"), "Roo Code", result);
                }
                // Trae: ~/.trae/rules/*.md
                else if ("Trae".equals(editorName)) {
                    scanDirectorySkills(userHome.resolve(".trae/rules"), "Trae", result);
                }
                // CodeBuddy: 通过 provider 扫描工作区 skills
                else if ("CodeBuddy".equals(editorName)) {
                    try {
                        @SuppressWarnings("unchecked")
                        var skills = (java.util.List<?>) clazz.getMethod("scanCodebuddySkills", String.class)
                                .invoke(provider, (String) null);
                        if (skills != null) {
                            for (Object skill : skills) {
                                String name = (String) skill.getClass().getMethod("getName").invoke(skill);
                                String desc = (String) skill.getClass().getMethod("getDescription").invoke(skill);
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("name", name);
                                item.put("description", desc != null ? desc : "");
                                item.put("source", "agent");
                                item.put("provider", "CodeBuddy");
                                result.add(item);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[ApiSkill] 扫描 CodeBuddy skills 失败: {}", e.getMessage());
                    }
                }
                // TRAE-CN: 通过 provider 扫描 skills
                else if ("TRAE-CN".equals(editorName)) {
                    try {
                        @SuppressWarnings("unchecked")
                        var skills = (java.util.List<?>) clazz.getMethod("scanTraeCnSkills").invoke(provider);
                        if (skills != null) {
                            for (Object skill : skills) {
                                String name = (String) skill.getClass().getMethod("getName").invoke(skill);
                                String desc = (String) skill.getClass().getMethod("getDescription").invoke(skill);
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("name", name);
                                item.put("description", desc != null ? desc : "");
                                item.put("source", "agent");
                                item.put("provider", "TRAE-CN");
                                result.add(item);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[ApiSkill] 扫描 TRAE-CN skills 失败: {}", e.getMessage());
                    }
                }
                // 其他编辑器：扫描通用 rules 目录
                else {
                    String configDir = switch (editorName) {
                        case "Codex" -> ".codex";
                        case "Cline" -> ".vscode-server/data/User/globalStorage/saoudrizwan.claude-dev/settings";
                        case "Augment Code" -> ".augment";
                        case "Continue" -> ".continue";
                        case "Gemini CLI" -> ".gemini";
                        case "Cody" -> ".cody";
                        default -> null;
                    };
                    if (configDir != null) {
                        scanDirectorySkills(userHome.resolve(configDir).resolve("rules"), editorName, result);
                        scanDirectorySkills(userHome.resolve(configDir).resolve("commands"), editorName, result);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            log.warn("[ApiSkill] AgentEditorProvider 不在 classpath: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[ApiSkill] 扫描 Agent skills 失败: {}", e.getMessage(), e);
        }

        return ApiResult.ok(result);
    }

    /**
     * 扫描目录下的 skill 文件（SKILL.md 或 .md 规则文件）。
     */
    private void scanDirectorySkills(Path dir, String editorName, List<Map<String, Object>> result) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.sorted().toList()) {
                if (Files.isDirectory(p)) {
                    // 子目录：查找 SKILL.md
                    Path skillMd = p.resolve("SKILL.md");
                    if (!Files.exists(skillMd)) {
                        skillMd = p.resolve("skill.md");
                    }
                    if (Files.exists(skillMd)) {
                        String name = p.getFileName().toString();
                        String desc = readFirstLine(skillMd);
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", name);
                        item.put("description", desc);
                        item.put("source", "agent");
                        item.put("provider", editorName);
                        item.put("path", p.toAbsolutePath().toString());
                        result.add(item);
                    }
                } else if (p.toString().endsWith(".md") || p.toString().endsWith(".mdc")) {
                    // 单文件规则
                    String name = p.getFileName().toString();
                    name = name.replaceAll("\\.(md|mdc)$", "");
                    String desc = readFirstLine(p);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", name);
                    item.put("description", desc);
                    item.put("source", "agent");
                    item.put("provider", editorName);
                    item.put("path", p.toAbsolutePath().toString());
                    result.add(item);
                }
            }
        } catch (IOException e) {
            log.debug("[ApiSkill] 扫描目录失败: {}", dir);
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
        } catch (IOException ignored) {
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
            } catch (IOException ignored) {
                // 忽略
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
            } catch (IOException ignored) {
                // 忽略
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
     *
     * @return 成功信封
     */
    @PostMapping("/market/enable")
    public ApiResult<Void> marketEnable() {
        log.info("[skills] market enabled");
        return ApiResult.ok();
    }

    /**
     * 禁用技能市场。
     *
     * @return 成功信封
     */
    @PostMapping("/market/disable")
    public ApiResult<Void> marketDisable() {
        log.info("[skills] market disabled");
        return ApiResult.ok();
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
        } catch (IOException ignored) {
            // 忽略遍历异常
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
                } catch (IOException ignored) {
                    // 忽略
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
            String encodedSearch = java.net.URLEncoder.encode(search, StandardCharsets.UTF_8);
            String url = "https://skillsmp.com/api/skills?page=" + page + "&limit=" + limit
                    + "&sortBy=" + sortBy + "&search=" + encodedSearch;
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

        for (String source : sources) {
            try {
                if ("skillsmp".equals(source) || "clawhub".equals(source)
                        || "skillhub".equals(source) || "loophub".equals(source)) {
                    // 所有源都走 SkillsMP API（统一市场）
                    String url = "https://skillsmp.com/api/skills?page=1&limit=30&sortBy=stars&search=";
                    String bodyText = httpGet(url);
                    Map<String, Object> raw = parseJson(bodyText);
                    List<?> rawSkills = (List<?>) raw.get("skills");
                    if (rawSkills != null) {
                        int rank = 1;
                        for (Object obj : rawSkills) {
                            Map<String, Object> skill = (Map<String, Object>) obj;
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("id", source + ":" + skill.getOrDefault("id", ""));
                            item.put("source", source);
                            item.put("rank", rank++);
                            item.put("name", skill.getOrDefault("name", ""));
                            item.put("description", skill.getOrDefault("description", ""));
                            item.put("url", skill.getOrDefault("githubUrl", ""));
                            item.put("install_command", "");
                            item.put("tags", List.of());
                            item.put("stats", "★ " + skill.getOrDefault("stars", 0));
                            allItems.add(item);
                        }
                    }
                } else {
                    errors.add("不支持的市场源: " + source);
                }
            } catch (Exception e) {
                log.error("同步市场源 {} 失败", source, e);
                errors.add(source + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", allItems);
        result.put("fetched_at", System.currentTimeMillis());
        result.put("errors", errors);
        return ApiResult.ok(result);
    }

    /**
     * 解析市场 MCP 配置。
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

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("mcpServers", Map.of(
                id != null ? id : "default", Map.of(
                        "command", "npx",
                        "args", List.of("-y", id != null ? id : "unknown-mcp"),
                        "env", Map.of()
                )
        ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("config_json", config);
        return ApiResult.ok(result);
    }

    /**
     * 安装市场技能包。
     *
     * @param body 请求体 {@code { source, id, url }}
     * @return 安装结果
     */
    @PostMapping("/market/package/install")
    public ApiResult<Map<String, Object>> installMarketPackage(@RequestBody Map<String, Object> body) {
        String source = asText(body.get("source"));
        String id = asText(body.get("id"));
        String url = asText(body.get("url"));
        log.info("安装市场包: source={}, id={}, url={}", source, id, url);

        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("name", id != null ? id : "unknown");
        pkg.put("description", "从市场安装的技能包");
        pkg.put("instructions", "");
        pkg.put("skill_slugs", List.of());
        pkg.put("avatar", "");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("package", pkg);
        result.put("installed_skill_names", List.of());
        result.put("errors", List.of());
        return ApiResult.ok(result);
    }

    /**
     * 简易 HTTP GET。
     *
     * @param urlStr URL
     * @return 响应体
     */
    private String httpGet(String urlStr) throws Exception {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "AiControl/1.0 SkillMarket");
        try (InputStream is = conn.getInputStream();
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
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
