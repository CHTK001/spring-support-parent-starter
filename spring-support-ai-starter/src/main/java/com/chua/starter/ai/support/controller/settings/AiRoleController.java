package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.service.SysAiRoleService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 角色（人设）控制器，提供角色的增删改查。
 *
 * <p>角色是一段预设的系统提示词人物设定，让 AI 以特定身份（如 Java 开发、
 * UI 设计师）应答。写接口受 {@code @Permission("admin")} 保护。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Tag(name = "AI 设置 - 角色")
@RestController
@RequestMapping("/v2/roles")
@RequiredArgsConstructor
public class AiRoleController {

    /**
     * 角色服务
     */
    private final SysAiRoleService roleService;

    /**
     * 文件导入的请求体：前端把文件读成文本直发（见 CharacterListTab.handleFileSelect），
     * 后端按 JSON（单角色对象 / 角色数组 / {characters:[...]}）解析落库。
     */
    public static class ImportBody {
        private List<ImportFile> files;

        /**
         * get Files
         * @return 返回值
        */
        public List<ImportFile> getFiles() {
            return files;
        }

        /**
         * set Files
         * @param files 参数
         * @return 返回值
        */
        public void setFiles(List<ImportFile> files) {
            this.files = files;
        }
    }

    /**
     * 单个待导入文件。
     */
    public static class ImportFile {
        private String content;
        private String filename;

        /**
         * get Content
         * @return 返回值
        */
        public String getContent() {
            return content;
        }

        /**
         * set Content
         * @param content 字符串
         * @return 返回值
        */
        public void setContent(String content) {
            this.content = content;
        }

        /**
         * get Filename
         * @return 返回值
        */
        public String getFilename() {
            return filename;
        }

        /**
         * set Filename
         * @param filename 字符串
         * @return 返回值
        */
        public void setFilename(String filename) {
            this.filename = filename;
        }
    }

    /**
     * 查询所有角色。
     *
     * @return 角色列表
     */
    @Operation(summary = "查询所有角色")
    @GetMapping
    public List<SysAiRole> list() {
        return roleService.list();
    }

    /**
     * 查询所有启用角色（供前端下拉选择）。
     *
     * @return 启用角色列表
     */
    @Operation(summary = "查询启用角色")
    @GetMapping("/enabled")
    public List<SysAiRole> listEnabled() {
        return roleService.listEnabled();
    }

    /**
     * 按 标识 查询角色。
     *
     * @param id 角色主键
     * @return 角色实体
     * @param "角色主键" "角色主键"
     * @param "角色主键" "角色主键"
     * @param "角色主键" "角色主键"
     */
    @Operation(summary = "查询单个角色")
    @GetMapping("/{id}")
    public SysAiRole getById(@Parameter(description = "角色主键") @PathVariable Integer id) {
        return roleService.getById(id);
    }

    /**
     * 新增角色。
     *
     * @param role 角色实体
     * @return 保存后的角色
     */
    @Permission("admin")
    @Operation(summary = "新增角色")
    @PostMapping
    public SysAiRole create(@RequestBody SysAiRole role) {
        return roleService.save(role);
    }

    /**
     * 从文件内容批量导入角色。
     *
     * <p>前端把用户选中的文件读成文本后整体 POST 到这里
     * （{@code POST /v2/roles/import}，此前控制器没有这条路由，
     * 导入按钮点下去文件框能开、喂完文件就 405，整条链是断的）。
     * 每个文件独立解析、逐条落库，返回逐条结果供前端统计成功/部分失败。</p>
     *
     * <p>接受三种 JSON 形状：单角色对象、角色数组、{@code {characters:[...]}} 包裹。
     * 字段兼容两套键名：前端导出形（title/name/description/systemPrompt/model/
     * temperature/settings）与实体形（name/description/systemPrompt/model/
     * temperature/settings）。settings 同时接受对象与 JSON 字符串。</p>
     *
     * @param body 待导入文件列表
     * @return 逐条导入结果（status=ok/error，附 filename 与 roleId/message）
     */
    @Permission("admin")
    @Operation(summary = "从文件导入角色")
    @PostMapping("/import")
    public List<Map<String, Object>> importRoles(@RequestBody ImportBody body) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (body == null || body.getFiles() == null || body.getFiles().isEmpty()) {
            return results;
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (ImportFile file : body.getFiles()) {
            String filename = file == null ? null : file.getFilename();
            String content = file == null ? null : file.getContent();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("filename", filename);
            if (content == null || content.isBlank()) {
                row.put("status", "error");
                row.put("message", "文件内容为空");
                results.add(row);
                continue;
            }
            try {
                List<Map<String, Object>> items = parseImportItems(mapper, content.trim());
                if (items.isEmpty()) {
                    row.put("status", "error");
                    row.put("message", "未解析出任何角色（需要 JSON 对象 / 数组 / {characters:[...]}）");
                    results.add(row);
                    continue;
                }
                int ok = 0;
                List<String> errors = new ArrayList<>();
                List<Integer> roleIds = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    try {
                        SysAiRole saved = roleService.save(toRole(item));
                        ok++;
                        if (saved != null && saved.getId() != null) {
                            roleIds.add(saved.getId());
                        }
                    } catch (Exception e) {
                        errors.add(String.valueOf(e.getMessage()));
                    }
                }
                row.put("status", ok > 0 ? "ok" : "error");
                row.put("imported", ok);
                row.put("failed", items.size() - ok);
                if (!roleIds.isEmpty()) {
                    row.put("roleIds", roleIds);
                    row.put("roleId", roleIds.get(0));
                }
                if (!errors.isEmpty()) {
                    row.put("message", String.join("; ", errors));
                }
            } catch (Exception e) {
                row.put("status", "error");
                row.put("message", "解析失败：" + e.getMessage());
            }
            results.add(row);
        }
        return results;
    }

    /**
     * 把三种 JSON 形状归一成角色条目列表。
     */
    private static List<Map<String, Object>> parseImportItems(
            com.fasterxml.jackson.databind.ObjectMapper mapper, String content) throws Exception {
        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(content);
        List<Map<String, Object>> items = new ArrayList<>();
        if (root.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode node : root) {
                if (node.isObject()) {
                    items.add(toStringMap(mapper, node));
                }
            }
            return items;
        }
        if (!root.isObject()) {
            return items;
        }
        com.fasterxml.jackson.databind.JsonNode wrapped = root.get("characters");
        if (wrapped == null) {
            wrapped = root.get("roles");
        }
        if (wrapped != null && wrapped.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode node : wrapped) {
                if (node.isObject()) {
                    items.add(toStringMap(mapper, node));
                }
            }
            return items;
        }
        items.add(toStringMap(mapper, root));
        return items;
    }

    private static Map<String, Object> toStringMap(
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            com.fasterxml.jackson.databind.JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> {
            com.fasterxml.jackson.databind.JsonNode v = e.getValue();
            map.put(e.getKey(), v.isValueNode() ? v.asText() : v.toString());
        });
        return map;
    }

    /**
     * 导入条目转实体：两套键名兼容，code 不提供时由 save 侧生成唯一值。
     * @param Map<String 参数
     * @param item 参数
     * @return 返回值
    */
    private static SysAiRole toRole(Map<String, Object> item) {
        SysAiRole role = new SysAiRole();
        Object title = item.get("title");
        Object name = item.get("name");
        role.setName(str(title != null ? title : name));
        role.setDescription(str(item.get("description")));
        role.setSystemPrompt(str(item.get("systemPrompt")));
        role.setModel(str(item.get("model")));
        Object temperature = item.get("temperature");
        if (temperature != null && !String.valueOf(temperature).isBlank()) {
            try {
                role.setTemperature(Double.valueOf(String.valueOf(temperature)));
            } catch (NumberFormatException ignored) {
                // 非数字温度直接丢弃，不让一条坏字段挡住整条导入
            }
        }
        Object settings = item.get("settings");
        role.setSettings(settings == null ? null : String.valueOf(settings));
        Object code = item.get("code");
        if (code != null && !String.valueOf(code).isBlank()) {
            role.setCode(String.valueOf(code));
        }
        role.setStatus(1);
        role.setDeleted(0);
        return role;
    }

    /**
     * str
     * @param v 对象
     * @return 返回值
    */
    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 更新角色。
     *
     * @param id   角色主键
     * @param role 角色实体
     * @return 是否成功
     * @param "角色主键" "角色主键"
     */
    @Permission("admin")
    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    public boolean update(@Parameter(description = "角色主键") @PathVariable Integer id,
                          @RequestBody SysAiRole role) {
        role.setId(id);
        return roleService.update(role);
    }

    /**
     * 删除角色（逻辑删除）。
     *
     * <p>通用助手（id=1 / code=general-assistant）为系统内置助手，禁止删除。
     * 前端已隐藏/拦截删除按钮，这里仍由后端兜底，直接 403，防止绕过页面调用
     * {@code DELETE /v2/roles/1} 删掉内置角色。后续若数据库主键迁移，
     * code=general-assistant 仍命中同一保护。</p>
     *
     * @param id 角色主键
     * @return 是否成功
     */
    @Permission("admin")
    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public boolean delete(@Parameter(description = "角色主键") @PathVariable Integer id) {
        if (Integer.valueOf(1).equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "通用助手为系统内置，不允许删除");
        }
        SysAiRole existing = roleService.getById(id);
        if (existing != null && "general-assistant".equals(existing.getCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "通用助手为系统内置，不允许删除");
        }
        return roleService.deleteById(id);
    }
}
