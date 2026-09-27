package com.chua.starter.eduai.collect.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 入库处理器：把前置处理器输出的行（key → 值）映射到目标表的列并写入。
 *
 * <p>配置示例（来源配置 JSON 的 importProcessor 段）：</p>
 * <pre>
 * "importProcessor": {
 *   "table": "edu_idiom",
 *   "uniqueKey": "idiom_content",
 *   "onConflict": "skip",                       // skip=跳过 / overwrite=覆盖
 *   "mapping": { "idiom_content": "word", "idiom_pinyin": "pinyin",
 *                "idiom_meaning": "explanation", "idiom_source": "derivation",
 *                "idiom_example": "example" },  // 列 ← 前置输出的 key（支持 a.b）
 *   "constants": { "idiom_difficulty": 3 },     // 固定列
 *   "gradeLevel": "小学", "grade": "一年级"       // 目标表为 edu_idiom 时附带学段映射
 * }
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public final class ImportProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 目标表建议默认值：成语库 */
    public static final String DEFAULT_TABLE = "edu_idiom";

    private ImportProcessor() {
    }

    /**
     * 执行入库。
     *
     * @param configJson      importProcessor 配置（JSON 文本）
     * @param rows            前置处理器输出的行
     * @param ownerUserId     归属用户
     * @param defaultLevel    学段兜底（任务配置）
     * @param defaultGrade    年级兜底（任务配置）
     * @return 写入条数（新增+更新）
     */
    public static int save(String configJson, List<Map<String, Object>> rows, String ownerUserId,
                           String defaultLevel, String defaultGrade) {
        DbCollectSupport support = DbCollectSupport.get();
        if (support == null) {
            throw new IllegalStateException("入库支持未初始化");
        }
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        JsonNode config = parse(configJson);
        String table = text(config, "table");
        if (!StringUtils.hasText(table)) {
            throw new IllegalArgumentException("入库处理器必须配置 table（目标表）");
        }
        List<String> uniqueKeys = splitKeys(text(config, "uniqueKey"));
        boolean overwrite = "overwrite".equalsIgnoreCase(text(config, "onConflict") == null ? "skip" : text(config, "onConflict"));
        Map<String, String> mapping = parseMapping(config.get("mapping"));
        Map<String, Object> constants = parseConstants(config.get("constants"));
        String gradeLevel = firstText(text(config, "gradeLevel"), defaultLevel);
        String grade = firstText(text(config, "grade"), defaultGrade);
        // 学段归一化为中文名（与成语库/消消乐取数一致），否则会出现 'PRIMARY' 之类的码值行查不到
        gradeLevel = normalizeLevel(gradeLevel);

        List<Map<String, Object>> tableRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> data = new LinkedHashMap<>(constants);
            if (mapping.isEmpty()) {
                data.putAll(row);
            } else {
                mapping.forEach((column, key) -> {
                    Object value = valueOf(row, key);
                    if (value != null) {
                        data.put(column, value);
                    }
                });
            }
            if (!data.isEmpty()) {
                tableRows.add(data);
            }
        }
        Map<String, Integer> counter = support.upsert(table, uniqueKeys, overwrite, tableRows);
        int changed = counter.getOrDefault("inserted", 0) + counter.getOrDefault("updated", 0);
        // 目标表为成语库/古诗库时补学段映射（其余表的学段由各自业务侧维护，不在此处理）
        if (StringUtils.hasText(gradeLevel)) {
            String target = table.trim();
            if ("edu_idiom".equalsIgnoreCase(target)) {
                for (Map<String, Object> data : tableRows) {
                    bindIdiomGrade(support, data, gradeLevel, grade, ownerUserId);
                }
            } else if ("edu_poem".equalsIgnoreCase(target)) {
                for (Map<String, Object> data : tableRows) {
                    bindPoemGrade(support, data, gradeLevel, grade, ownerUserId);
                }
            }
        }
        log.info("[collect] 入库完成: table={} 行={} 新增={} 更新={} 跳过={} 冲突策略={}",
                table, tableRows.size(), counter.get("inserted"), counter.get("updated"),
                counter.get("skipped"), overwrite ? "overwrite" : "skip");
        return changed;
    }

    private static void bindIdiomGrade(DbCollectSupport support, Map<String, Object> data,
                                       String gradeLevel, String grade, String ownerUserId) {
        Object content = data.get("idiom_content");
        if (content == null) {
            return;
        }
        List<Long> ids = support.jdbc().queryForList(
                "SELECT idiom_id FROM edu_idiom WHERE idiom_content = ? LIMIT 1", Long.class, String.valueOf(content));
        if (ids.isEmpty()) {
            return;
        }
        Long idiomId = ids.get(0);
        Integer exists = support.jdbc().queryForObject(
                "SELECT COUNT(*) FROM edu_idiom_grade WHERE idiom_id = ? AND grade_level = ?",
                Integer.class, idiomId, gradeLevel);
        if (exists != null && exists > 0) {
            return;
        }
        support.jdbc().update(
                "INSERT INTO edu_idiom_grade (idiom_id, grade_level, grade, idiom_grade_owner_user_id) VALUES (?, ?, ?, ?)",
                idiomId, gradeLevel, StringUtils.hasText(grade) ? grade : null, ownerUserId);
    }

    /**
     * 补一条古诗的学段映射。与 {@link #bindIdiomGrade} 同构，定位键与采集唯一键保持一致
     * （标题 + 作者 视为同一首），作者为空时只按标题定位。
     *
     * @param support     数据库操作入口
     * @param data        本行的入库数据（含 poem_title / poem_author）
     * @param gradeLevel  学段
     * @param grade       年级（可为空）
     * @param ownerUserId 归属用户 ID
     */
    private static void bindPoemGrade(DbCollectSupport support, Map<String, Object> data,
                                      String gradeLevel, String grade, String ownerUserId) {
        Object title = data.get("poem_title");
        if (title == null) {
            return;
        }
        Object author = data.get("poem_author");
        boolean hasAuthor = author != null && StringUtils.hasText(String.valueOf(author));
        String sql = hasAuthor
                ? "SELECT poem_id FROM edu_poem WHERE poem_title = ? AND poem_author = ? ORDER BY poem_id LIMIT 1"
                : "SELECT poem_id FROM edu_poem WHERE poem_title = ? ORDER BY poem_id LIMIT 1";
        List<Long> ids = hasAuthor
                ? support.jdbc().queryForList(sql, Long.class, String.valueOf(title), String.valueOf(author))
                : support.jdbc().queryForList(sql, Long.class, String.valueOf(title));
        if (ids.isEmpty()) {
            return;
        }
        Long poemId = ids.get(0);
        Integer exists = support.jdbc().queryForObject(
                "SELECT COUNT(*) FROM edu_poem_grade WHERE poem_id = ? AND grade_level = ?",
                Integer.class, poemId, gradeLevel);
        if (exists != null && exists > 0) {
            return;
        }
        support.jdbc().update(
                "INSERT INTO edu_poem_grade (poem_id, grade_level, grade, poem_grade_owner_user_id) VALUES (?, ?, ?, ?)",
                poemId, gradeLevel, StringUtils.hasText(grade) ? grade : null, ownerUserId);
    }

    private static Map<String, String> parseMapping(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        node = asObject(node);
        if (node == null) {
            return map;
        }
        node.fields().forEachRemaining(e -> {
            if (e.getValue() != null && !e.getValue().isNull()) {
                map.put(e.getKey(), e.getValue().asText());
            }
        });
        return map;
    }

    private static Map<String, Object> parseConstants(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        node = asObject(node);
        if (node == null) {
            return map;
        }
        node.fields().forEachRemaining(e -> {
            JsonNode value = e.getValue();
            if (value == null || value.isNull()) {
                return;
            }
            if (value.isNumber()) {
                map.put(e.getKey(), value.numberValue());
            } else if (value.isBoolean()) {
                map.put(e.getKey(), value.asBoolean());
            } else {
                map.put(e.getKey(), value.asText());
            }
        });
        return map;
    }

    /**
     * 兼容对象节点与「JSON 文本」节点（前端可能把 mapping/constants 作为字符串提交）。
     *
     * @param node 节点
     * @return 对象节点，无法解析返回 null
     */
    private static JsonNode asObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            try {
                JsonNode parsed = MAPPER.readTree(node.asText());
                return parsed.isObject() ? parsed : null;
            } catch (Exception e) {
                return null;
            }
        }
        return node.isObject() ? node : null;
    }

    private static Object valueOf(Map<String, Object> row, String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String[] segments = key.trim().split("\\.");
        Object current = row;
        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<?, ?>) map).get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static JsonNode parse(String json) {
        if (!StringUtils.hasText(json)) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("入库处理器配置不是合法 JSON: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static List<String> splitKeys(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (String part : csv.split(",")) {
            String key = part.trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * 学段值归一化：PRIMARY/JUNIOR/SENIOR/UNIVERSITY → 中文名。
     *
     * @param level 原始学段
     * @return 归一化结果
     */
    private static String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return level;
        }
        return switch (level.trim().toUpperCase(Locale.ROOT)) {
            case "PRIMARY" -> "小学";
            case "JUNIOR", "MIDDLE" -> "初中";
            case "SENIOR", "HIGH" -> "高中";
            case "UNIVERSITY" -> "大学";
            default -> level;
        };
    }
}
