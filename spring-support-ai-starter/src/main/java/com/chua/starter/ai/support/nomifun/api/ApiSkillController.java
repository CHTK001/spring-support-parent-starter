package com.chua.starter.ai.support.nomifun.api;

import com.chua.starter.ai.support.nomifun.common.ApiErrorCode;
import com.chua.starter.ai.support.nomifun.common.ApiException;
import com.chua.starter.ai.support.nomifun.common.ApiResult;
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
     * 导入技能（复制技能目录到用户目录）。
     *
     * @param body 请求体 {@code { skill_path: string }}
     * @return 导入结果
     */
    @PostMapping("/import")
    public ApiResult<Map<String, String>> importSkill(@RequestBody Map<String, Object> body) {
        Path skillPath = resolveSkillPath(body);
        if (!Files.exists(skillPath) || !Files.isDirectory(skillPath)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "技能不存在: " + skillPath);
        }
        String name = skillPath.getFileName().toString();
        Path userDir = Paths.get(skillsRoot).resolve("user");
        try {
            Files.createDirectories(userDir);
            copyDirectory(skillPath, userDir.resolve(name));
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导入技能失败: " + e.getMessage());
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("skill_name", name);
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
     * 通过符号链接导入技能。
     *
     * @param body 请求体 {@code { skill_path: string }}
     * @return 导入结果
     */
    @PostMapping("/import-symlink")
    public ApiResult<Map<String, Object>> importSymlink(@RequestBody Map<String, Object> body) {
        Path skillPath = resolveSkillPath(body);
        if (!Files.exists(skillPath) || !Files.isDirectory(skillPath)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "技能不存在: " + skillPath);
        }
        String name = skillPath.getFileName().toString();
        Path userDir = Paths.get(skillsRoot).resolve("user");
        try {
            Files.createDirectories(userDir);
            Path link = userDir.resolve(name);
            if (!Files.exists(link)) {
                try {
                    Files.createSymbolicLink(link, skillPath);
                } catch (IOException | UnsupportedOperationException e) {
                    copyDirectory(skillPath, link);
                }
            }
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "导入符号链接技能失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skill_name", name);
        List<String> names = new ArrayList<>();
        names.add(name);
        result.put("skill_names", names);
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
     * 同步技能市场排行。
     *
     * @param body 请求体 {@code { sources?: string[] }}
     * @return 同步结果
     */
    @PostMapping("/market/rankings/sync")
    public ApiResult<Map<String, Object>> marketRankingsSync(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fetched_at", System.currentTimeMillis());
        result.put("items", new ArrayList<>());
        result.put("errors", new ArrayList<>());
        return ApiResult.ok(result);
    }

    /**
     * 解析技能市场 MCP 配置。
     *
     * @param body 请求体 {@code { source, id, url }}
     * @return MCP 配置
     */
    @PostMapping("/market/mcp/config")
    public ApiResult<Map<String, Object>> marketMcpConfig(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("config_json", new LinkedHashMap<>());
        return ApiResult.ok(result);
    }

    /**
     * 安装技能市场包。
     *
     * @param body 请求体 {@code { source, id, url }}
     * @return 安装结果
     */
    @PostMapping("/market/package/install")
    public ApiResult<Map<String, Object>> marketPackageInstall(@RequestBody Map<String, Object> body) {
        String id = asText(body.get("id"));
        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("name", id == null ? "" : id);
        pkg.put("description", "");
        pkg.put("instructions", "");
        pkg.put("skill_slugs", new ArrayList<>());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("package", pkg);
        result.put("installed_skill_names", new ArrayList<>());
        result.put("errors", new ArrayList<>());
        return ApiResult.ok(result);
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
}
