// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.chat.BuiltinRolePromptSeeder;
import com.chua.starter.ai.support.service.SysAiRoleService;
import com.chua.starter.ai.support.service.SkillCatalogService;
import com.chua.starter.ai.support.util.ClasspathDirSupport;
import com.chua.starter.common.support.annotations.Permission;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 角色模板目录控制器。
 *
 * <p>提供内置角色模板列表和添加到助手功能。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "AI 设置 - 角色模板目录")
@RestController
@RequestMapping("/v2/roles/catalog")
@RequiredArgsConstructor
public class CharacterCatalogController {

    private final SysAiRoleService roleService;
    private final SkillCatalogService skillCatalogService;
    private final BuiltinRolePromptSeeder builtinRolePromptSeeder;

    @Value("${nomifun.skills-root:./skills}")
    private String skillsRoot;

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("(?s)^---\\s*\\n(.*?)\\n---\\s*\\n(.*)");
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^(\\w+):\\s*(.+)$");

    /**
     * classpath 内置角色模板目录
     */
    private static final String BUILTIN_CHARACTER_DIR = "characters-builtin";

    /**
     * 单个角色模板的文件名
     */
    private static final String CHARACTER_FILE = "CHARACTER.md";

    /**
     * 用于把角色 settings 序列化为 JSON 字符串落库。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 列出内置角色模板。
     *
     * @return 内置角色模板及其是否已添加状态
     */
    @Operation(summary = "列出内置角色模板")
    @GetMapping("/builtin")
    public List<Map<String, Object>> listBuiltin() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<SysAiRole> existing = roleService.list();
            java.util.Set<String> existingCodes = existing.stream()
                    .map(SysAiRole::getCode)
                    .collect(Collectors.toSet());

            var templates = ClasspathDirSupport.readDirectory(
                    BUILTIN_CHARACTER_DIR, name -> name.endsWith(CHARACTER_FILE));

            for (var template : templates.entrySet()) {
                String relative = template.getKey();
                try {
                    String content = new String(template.getValue(), StandardCharsets.UTF_8);
                    Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
                    if (matcher.find()) {
                        String frontmatter = matcher.group(1);
                        String body = matcher.group(2);
                        Map<String, String> meta = parseFrontmatter(frontmatter);
                        String id = extractId(relative);
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", id);
                        item.put("name", meta.getOrDefault("name", id));
                        item.put("title", meta.getOrDefault("title", meta.getOrDefault("name", id)));
                        item.put("description", meta.getOrDefault("description", ""));
                        item.put("category", meta.getOrDefault("category", "通用助手"));
                        item.put("avatar", meta.getOrDefault("avatar", ""));
                        item.put("added", existingCodes.contains(id));
                        result.add(item);
                    }
                } catch (Exception e) {
                    log.warn("[CharacterCatalog] 解析失败: {}, err={}", relative, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[CharacterCatalog] 列出内置模板失败: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * 将内置模板添加到助手，重复添加时保持幂等。
     *
     * @param id 内置角色模板标识
     * @return 添加结果和已有角色标识
     */
    @Permission("admin")
    @Operation(summary = "添加内置模板到助手")
    @PostMapping("/builtin/{id}/add")
    public Map<String, Object> addBuiltin(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 检查是否已存在
            List<SysAiRole> existing = roleService.list();
            boolean alreadyExists = existing.stream().anyMatch(r -> id.equals(r.getCode()));
            if (alreadyExists) {
                SysAiRole found = existing.stream()
                        .filter(r -> id.equals(r.getCode())).findFirst().orElse(null);
                if (found != null) {
                    builtinRolePromptSeeder.seedForRole(found);
                    result.put("roleId", found.getId());
                }
                result.put("success", true);
                result.put("message", "已在助手中");
                result.put("alreadyAdded", true);
                return result;
            }

            // 读取模板
            var templates = ClasspathDirSupport.readDirectory(
                    BUILTIN_CHARACTER_DIR, name -> name.equals(id + "/" + CHARACTER_FILE));
            for (var template : templates.entrySet()) {
                String content = new String(template.getValue(), StandardCharsets.UTF_8);
                Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
                if (matcher.find()) {
                    String frontmatter = matcher.group(1);
                    String body = matcher.group(2);
                    Map<String, String> meta = parseFrontmatter(frontmatter);

                    SysAiRole role = new SysAiRole();
                    role.setCode(id);
                    // 展示名优先用友好标题，code 已保留内置身份
                    role.setName(meta.getOrDefault("title", meta.getOrDefault("name", id)));
                    role.setDescription(meta.getOrDefault("description", ""));
                    role.setSystemPrompt(body.trim());
                    // 同步写入 settings：提示词 + 模板声明的默认技能，保证角色配置完整、默认技能生效
                    List<String> defaultSkills = splitInlineList(meta.get("skills"));
                    Map<String, Object> roleSettingsMap = new LinkedHashMap<>();
                    roleSettingsMap.put("systemPrompt", body.trim());
                    if (!defaultSkills.isEmpty()) {
                        roleSettingsMap.put("defaultSkillNames", defaultSkills);
                    }
                    role.setSettings(OBJECT_MAPPER.writeValueAsString(roleSettingsMap));
                    role.setStatus(1);
                    role.setDeleted(0);
                    roleService.save(role);
                    builtinRolePromptSeeder.seedForRole(role);

                    result.put("success", true);
                    result.put("message", "添加成功");
                    result.put("alreadyAdded", false);
                    result.put("roleId", role.getId());
                    return result;
                }
            }
            result.put("success", false);
            result.put("message", "模板未找到: " + id);
        } catch (Exception e) {
            log.error("[CharacterCatalog] 添加失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "添加失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 查询内置角色模板详情，包含擅长领域、推荐问题和内置技能。
     *
     * @param id 内置角色模板标识
     * @return 内置角色模板详情
     */
    @Operation(summary = "内置角色模板详情")
    @GetMapping("/builtin/{id}")
    public Map<String, Object> builtinDetail(@PathVariable String id) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<SysAiRole> existing = roleService.list();
            boolean added = existing.stream().anyMatch(r -> id.equals(r.getCode()));

            var templates = ClasspathDirSupport.readDirectory(
                    BUILTIN_CHARACTER_DIR, name -> name.equals(id + "/" + CHARACTER_FILE));
            for (var template : templates.entrySet()) {
                String content = new String(template.getValue(), StandardCharsets.UTF_8);
                Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
                if (!matcher.find()) {
                    continue;
                }
                Map<String, String> meta = parseFrontmatter(matcher.group(1));

                String title = meta.getOrDefault("title", meta.getOrDefault("name", id));
                String category = meta.getOrDefault("category", "通用助手");

                result.put("id", id);
                result.put("name", meta.getOrDefault("name", id));
                result.put("title", title);
                result.put("description", meta.getOrDefault("description", ""));
                result.put("category", category);
                result.put("avatar", meta.getOrDefault("avatar", ""));
                result.put("modelType", meta.getOrDefault("modelType", "text"));
                result.put("added", added);

                // 擅长领域：缺省回退为分类
                List<String> tags = splitInlineList(meta.get("tags"));
                if (tags.isEmpty()) {
                    tags = new ArrayList<>();
                    tags.add(category);
                }
                result.put("tags", tags);

                // 试试这样问我：缺省给两条通用建议
                List<String> prompts = splitInlineList(meta.get("prompts"));
                if (prompts.isEmpty()) {
                    prompts = new ArrayList<>();
                    prompts.add("请介绍一下你自己，以及你能帮我做什么？");
                    prompts.add("我想让你帮我处理「" + category + "」相关的事情，该从哪里开始？");
                }
                result.put("prompts", prompts);

                // 内置技能：解析技能 id → 友好名称
                List<String> skillIds = splitInlineList(meta.get("skills"));
                Map<String, String> skillNames = builtinSkillNameMap();
                List<Map<String, Object>> skills = new ArrayList<>();
                for (String sid : skillIds) {
                    Map<String, Object> sk = new LinkedHashMap<>();
                    sk.put("id", sid);
                    sk.put("name", skillNames.getOrDefault(sid, sid));
                    skills.add(sk);
                }
                result.put("skills", skills);
                return result;
            }
            result.put("message", "模板未找到: " + id);
        } catch (Exception e) {
            log.error("[CharacterCatalog] 读取详情失败: id={}, err={}", id, e.getMessage(), e);
            result.put("message", "读取失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 解析 frontmatter 中的内联列表（支持 [a, b] 与带引号元素）。
     * @param raw 字符串
     * @return 返回值
    */
    private List<String> splitInlineList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String value = raw.trim();
        if (value.startsWith("[")) {
            value = value.substring(1);
        }
        if (value.endsWith("]")) {
            value = value.substring(0, value.length() - 1);
        }
        // 引号项优先整体匹配；非引号项不得吞入引号/方括号，避免引号残留
        Matcher itemMatcher = Pattern.compile("\"([^\"]*)\"|'([^']*)'|([^,\"'\\[\\]]+)").matcher(value);
        while (itemMatcher.find()) {
            String part = itemMatcher.group(1) != null ? itemMatcher.group(1)
                    : itemMatcher.group(2) != null ? itemMatcher.group(2) : itemMatcher.group(3);
            if (part != null && !part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    /**
     * 内置技能 id → 友好名称映射。
     * @return 返回值
    */
    private Map<String, String> builtinSkillNameMap() {
        Map<String, String> map = new HashMap<>();
        try {
            for (Map<String, Object> item : skillCatalogService.listBuiltin()) {
                Object id = item.get("id");
                Object name = item.get("name");
                if (id != null && name != null) {
                    map.put(String.valueOf(id), String.valueOf(name));
                }
            }
        } catch (Exception e) {
            log.warn("[CharacterCatalog] 内置技能名称解析失败: {}", e.getMessage());
        }
        return map;
    }

    /**
     * parse Frontmatter
     * @param frontmatter 字符串
     * @return 返回值
    */
    private Map<String, String> parseFrontmatter(String frontmatter) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : frontmatter.split("\n")) {
            Matcher m = KEY_VALUE_PATTERN.matcher(line.trim());
            if (m.find()) {
                map.put(m.group(1), m.group(2).trim());
            }
        }
        return map;
    }

    /**
     * 从模板相对路径中取出角色标识（首个目录名）。
     *
     * @param relativePath 相对 {@code characters-builtin/} 的路径，形如 {@code algorithms-engineer/CHARACTER.md}
     * @return 角色标识；无法解析时返回 {@code unknown}
     */
    private String extractId(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "unknown";
        }
        int slash = relativePath.indexOf('/');
        return slash < 0 ? relativePath : relativePath.substring(0, slash);
    }
}
