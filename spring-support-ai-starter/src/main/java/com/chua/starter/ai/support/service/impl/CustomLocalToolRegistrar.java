package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiUserSettingService;
import com.chua.starter.ai.support.util.AiUserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义本地工具注册器。
 *
 * <p>允许管理员把本机一个「含插件清单的目录」注册为本地工具：注册信息以 JSON 数组形式
 * 存放在 {@code sys_ai_global_setting} 的 {@code plugins} 分组、{@code custom_tools} 字段，
 * 元素为 <code>{name, path}</code>。</p>
 *
 * <p>扫描约定（清单文件名优先 {@code plugin.json}，其次 {@code package.json}）：</p>
 * <ol>
 *   <li>若指定目录本身含清单 → 单个工具；</li>
 *   <li>否则遍历其一级子目录，每个含清单的子目录 → 一个工具。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomLocalToolRegistrar {

    /**
     * 配置分组与字段
     */
    public static final String GROUP = "plugins";
    public static final String FIELD = "custom_tools";

    /**
     * 自定义工具标识前缀，避免与磁盘扫描结果撞 id
     */
    public static final String ID_PREFIX = "custom:";

    private static final String[] MANIFEST_NAMES = {"plugin.json", "package.json"};

    private final SysAiGlobalSettingService globalSettingService;
    private final SysAiUserSettingService userSettingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 读取全部注册项（原始 name/path）。
     *
     * @return 注册项列表，未配置时为空
     */
    public List<Map<String, String>> listRegistrations() {
        String raw = readRegistrationsRaw();
        return parseRegistrations(raw);
    }

    /**
     * 扫描并返回全部已注册的自定义工具条目（已合并开关状态由调用方处理）。
     *
     * @return 工具 map 列表
     */
    public List<Map<String, Object>> resolveTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (Map<String, String> reg : listRegistrations()) {
            String path = reg.get("path");
            String fallbackName = reg.get("name");
            if (path == null || path.isBlank()) {
                continue;
            }
            tools.addAll(scanPath(Paths.get(path), fallbackName));
        }
        return tools;
    }

    /**
     * 注册一个自定义工具目录：先扫描确认能识别出工具，再持久化。
     *
     * @param name 期望的工具名（可空，缺省用清单 name）
     * @param path 目录绝对路径
     * @return 扫描出的工具列表
     * @throws IllegalArgumentException 参数不合法或目录中未发现清单
     */
    public List<Map<String, Object>> register(String name, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("工具目录不能为空");
        }
        Path root = Paths.get(path.trim());
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("目录不存在或不是文件夹: " + path);
        }
        List<Map<String, Object>> scanned = scanPath(root, name);
        if (scanned.isEmpty()) {
            throw new IllegalArgumentException(
                    "目录中未发现 plugin.json 或 package.json 清单：" + path);
        }

        List<Map<String, String>> registrations = listRegistrations();
        boolean duplicated = registrations.stream()
                .anyMatch(r -> normalizePath(r.get("path")).equals(root.toAbsolutePath().normalize().toString()));
        if (!duplicated) {
            Map<String, String> reg = new LinkedHashMap<>();
            reg.put("name", name == null || name.isBlank() ? root.getFileName().toString() : name.trim());
            reg.put("path", root.toAbsolutePath().normalize().toString());
            registrations.add(reg);
            saveRegistrations(registrations);
        }
        return scanned;
    }

    /**
     * 按工具 id 移除注册项；若一个注册目录对应多个工具，仅当移除的是该目录最后一个工具时才删除目录注册。
     *
     * @param pluginId 工具 id（custom: 前缀）
     * @return 是否移除成功
     */
    public boolean unregister(String pluginId) {
        if (pluginId == null || !pluginId.startsWith(ID_PREFIX)) {
            return false;
        }
        List<Map<String, String>> registrations = listRegistrations();
        boolean changed = false;
        // 直接按目录/工具定位：custom:<safeDir> 或 custom:<safeDir>/<safeTool>
        String body = pluginId.substring(ID_PREFIX.length());
        String dirKey = body.contains("/") ? body.substring(0, body.indexOf('/')) : body;

        var it = registrations.iterator();
        while (it.hasNext()) {
            Map<String, String> reg = it.next();
            String path = reg.get("path");
            if (path == null) {
                continue;
            }
            String safeDir = safe(Paths.get(path).getFileName() == null
                    ? path : Paths.get(path).getFileName().toString());
            if (safeDir.equals(dirKey)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            saveRegistrations(registrations);
        }
        return changed;
    }

    /**
     * 判断给定 id 是否为自定义工具。
     *
     * @param pluginId 工具 id
     * @return 是否自定义
     */
    public boolean isCustom(String pluginId) {
        return pluginId != null && pluginId.startsWith(ID_PREFIX);
    }

    /* ===================== 扫描实现 ===================== */

    /**
     * 扫描目录：目录自身含清单则单工具，否则扫描一级子目录。
     *
     * @param root         目录
     * @param fallbackName 期望名
     * @return 工具列表
     */
    private List<Map<String, Object>> scanPath(Path root, String fallbackName) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        Path directManifest = firstManifest(root);
        if (directManifest != null) {
            Map<String, Object> tool = readTool(root, directManifest, fallbackName);
            if (tool != null) {
                out.add(tool);
            }
            return out;
        }
        try (var stream = Files.list(root)) {
            for (Path sub : stream.filter(Files::isDirectory).sorted().toList()) {
                Path manifest = firstManifest(sub);
                if (manifest == null) {
                    continue;
                }
                Map<String, Object> tool = readTool(sub, manifest, null);
                if (tool != null) {
                    out.add(tool);
                }
            }
        } catch (IOException e) {
            log.warn("[CustomTool] 遍历目录失败 root={}: {}", root, e.getMessage());
        }
        return out;
    }

    /**
     * 取目录下第一个存在的清单文件。
     *
     * @param dir 目录
     * @return 清单路径，无则 null
     */
    private Path firstManifest(Path dir) {
        for (String manifestName : MANIFEST_NAMES) {
            Path candidate = dir.resolve(manifestName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 读取清单并构造工具 map。
     *
     * @param toolDir  工具目录
     * @param manifest 清单路径
     * @param override 期望名（可空）
     * @return 工具 map，解析失败返回 null
     */
    private Map<String, Object> readTool(Path toolDir, Path manifest, String override) {
        try {
            String json = Files.readString(manifest, StandardCharsets.UTF_8);
            // 兼容带 UTF-8 BOM 的清单（部分编辑器默认写入 BOM，Jackson 无法直接解析）
            if (!json.isEmpty() && json.charAt(0) == '\uFEFF') {
                json = json.substring(1);
            }
            JsonNode node = objectMapper.readTree(json);

            String manifestName = text(node, "name");
            String toolName = (override != null && !override.isBlank()) ? override.trim()
                    : (!manifestName.isBlank() ? manifestName : toolDir.getFileName().toString());
            String dirName = toolDir.getFileName().toString();
            String id = ID_PREFIX + safe(dirName)
                    + (manifestName.isBlank() ? "" : "/" + safe(manifestName));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("pluginId", id);
            item.put("name", toolName);
            item.put("description", text(node, "description"));
            item.put("version", text(node, "version"));
            item.put("author", authorText(node));
            item.put("location", toolDir.toAbsolutePath().toString());
            item.put("manifestPath", manifest.toAbsolutePath().toString());
            item.put("source", "user");
            item.put("origin", "custom");
            item.put("custom", true);
            return item;
        } catch (Exception e) {
            log.warn("[CustomTool] 解析清单失败 manifest={}: {}", manifest, e.getMessage());
            return null;
        }
    }

    /**
     * 读取作者字段：可能是字符串，也可能是 {name/email} 对象。
     *
     * @param node 清单根节点
     * @return 作者文本
     */
    private String authorText(JsonNode node) {
        JsonNode author = node.get("author");
        if (author == null || author.isNull()) {
            return "";
        }
        if (author.isTextual()) {
            return author.asText();
        }
        if (author.has("name")) {
            return author.get("name").asText("");
        }
        return "";
    }

    /**
     * 安全读取文本字段。
     *
     * @param node 节点
     * @param key  字段
     * @return 文本，缺失为空串
     */
    private String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    /* ===================== 注册项持久化 ===================== */

    /**
     * 解析注册项 JSON。
     *
     * @param raw 原始 JSON
     * @return 注册项列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseRegistrations(String raw) {
        List<Map<String, String>> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        try {
            JsonNode array = objectMapper.readTree(raw);
            if (!array.isArray()) {
                return out;
            }
            for (JsonNode element : array) {
                Map<String, String> reg = new LinkedHashMap<>();
                reg.put("name", element.path("name").asText(""));
                reg.put("path", element.path("path").asText(""));
                out.add(reg);
            }
        } catch (Exception e) {
            log.warn("[CustomTool] 解析注册项失败: {}", e.getMessage());
        }
        return out;
    }

    /**
     * 持久化注册项并刷新缓存的动作由目录服务在外部触发。
     *
     * @param registrations 注册项
     */
    private void saveRegistrations(List<Map<String, String>> registrations) {
        try {
            String json = objectMapper.writeValueAsString(registrations);
            // 写出口按身份分流：管理员写全局（= 平台默认清单），普通账号只写自己那份覆盖。
            // 与 sys_ai_model / sys_ai_app_key 的归属口径一致。
            if (isAdmin()) {
                globalSettingService.upsertValue(GROUP, FIELD, json, "自定义本地工具目录注册");
            } else {
                userSettingService.upsertValue(AiUserContext.requireUserId(), GROUP, FIELD, json,
                        "TEXT", "自定义本地工具目录注册（账号级）");
            }
        } catch (Exception e) {
            log.error("[CustomTool] 保存注册项失败: {}", e.getMessage(), e);
            throw new IllegalStateException("保存自定义工具失败: " + e.getMessage());
        }
    }

    /**
     * 读取注册表原始值：**用户覆盖优先，无覆盖则回落全局**。
     *
     * <p>无身份上下文（系统线程 / 异步线程丢上下文）时只读全局 —— 这与
     * {@code GlobalSettingReader} 的既有语义一致，不在此处「猜」一个用户。</p>
     *
     * @return 原始 JSON 字符串，无值时为 {@code null}
     */
    private String readRegistrationsRaw() {
        try {
            String userId = AiUserContext.currentUserId();
            if (userId == null || userId.isBlank()) {
                // 无身份上下文只读全局：与 GlobalSettingReader 既有语义一致，不在此处「猜」一个用户
                return globalSettingService.getValue(GROUP, FIELD).orElse(null);
            }
            // resolve() 本身就是「用户覆盖优先 → 回落全局」，不重复实现这套语义
            return userSettingService.resolve(userId, GROUP, FIELD).orElse(null);
        } catch (Exception e) {
            log.warn("[CustomTool] 读取注册表失败，回落全局: {}", e.getMessage());
            return globalSettingService.getValue(GROUP, FIELD).orElse(null);
        }
    }

    /**
     * 当前是否管理员。上下文不可用时按非管理员处理（更保守的一侧）。
     * @return 返回值
    */
    private boolean isAdmin() {
        try {
            return AiUserContext.isAdmin();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 路径归一化字符串。
     *
     * @param path 路径
     * @return 归一化结果
     */
    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return Paths.get(path).toAbsolutePath().normalize().toString();
    }

    /**
     * 生成仅含小写字母数字与短横线的安全标识。
     *
     * @param raw 原始名
     * @return 安全标识
     */
    private String safe(String raw) {
        if (raw == null) {
            return "tool";
        }
        String cleaned = raw.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return cleaned.isBlank() ? "tool" : cleaned;
    }
}
