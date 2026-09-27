package com.chua.starter.eduai.collect.provider;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.chua.starter.eduai.collect.spi.QuestionSourceContext;
import com.chua.starter.eduai.collect.spi.QuestionSourceProvider;
import com.chua.starter.eduai.collect.support.DbCollectSupport;
import com.chua.starter.eduai.collect.support.HttpFetchSupport;
import com.chua.starter.eduai.collect.support.SourceConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据表入库采集源（自落库型）：三方接口/文件 JSON → 字段映射（可选 Groovy 处理脚本）→ 指定数据表。
 *
 * <p>配置示例：
 * <pre>
 * {
 *   "url": "https://cdn.jsdelivr.net/gh/pwxcoo/chinese-xinhua@master/data/idiom.json",
 *   "dataPath": "",                        // 数组路径（点号），空则取根数组或 data
 *   "offset": 0, "limit": 100,             // 从偏移处连续抽取，直到新增达到 limit
 *   "table": "edu_idiom",                  // 目标表（白名单）
 *   "uniqueKey": "idiom_content",          // 唯一键（多列逗号分隔，空=纯插入）
 *   "onConflict": "skip",                  // skip=跳过已存在 / overwrite=覆盖更新
 *   "fields": {                            // 列 → 源字段路径（支持 a.b 嵌套）
 *     "idiom_content": "word",
 *     "idiom_pinyin": "pinyin",
 *     "idiom_meaning": "explanation",
 *     "idiom_source": "derivation",
 *     "idiom_example": "example"
 *   },
 *   "constants": { "idiom_difficulty": 3, "idiom_tags": "" },   // 常量列
 *   "script": "item.my = item.word?.size()",                    // 可选：Groovy 处理脚本（可用 item/log，返回值可改 item）
 *   "gradeLevel": "小学", "grade": "一年级"                       // 写 edu_idiom 时附带学段映射
 * }
 * </pre></p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Spi(value = "DB", order = 80)
public class DbCollectProvider implements QuestionSourceProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String type() {
        return "DB";
    }

    @Override
    public boolean selfPersisted() {
        return true;
    }

    @Override
    public List<CollectedQuestion> fetch(QuestionSourceContext context) {
        return List.of();
    }

    @Override
    public int persist(QuestionSourceContext context) {
        DbCollectSupport support = DbCollectSupport.get();
        if (support == null) {
            throw new IllegalStateException("入库支持未初始化");
        }
        String config = context.getSourceConfig();
        String url = SourceConfigReader.text(config, "url");
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("数据表入库来源必须配置 url（三方接口/词库地址）");
        }
        String dataPath = SourceConfigReader.text(config, "dataPath");
        String table = SourceConfigReader.text(config, "table");
        if (!StringUtils.hasText(table)) {
            throw new IllegalArgumentException("数据表入库来源必须配置 table（目标表）");
        }
        List<String> uniqueKeys = splitKeys(SourceConfigReader.text(config, "uniqueKey"));
        boolean overwrite = "overwrite".equalsIgnoreCase(firstText(SourceConfigReader.text(config, "onConflict"), "skip"));
        Map<String, String> fields = parseObject(SourceConfigReader.text(config, "fields"));
        Map<String, Object> constants = parseObjectValues(SourceConfigReader.text(config, "constants"));
        String script = SourceConfigReader.text(config, "script");
        int offset = intOf(SourceConfigReader.text(config, "offset"), 0);
        int limit = intOf(SourceConfigReader.text(config, "limit"), 100);
        String gradeLevel = firstText(SourceConfigReader.text(config, "gradeLevel"), context.getGradeLevel(), "");
        String grade = firstText(SourceConfigReader.text(config, "grade"), context.getGrade(), "");

        String json;
        try {
            json = fetchWithFallback(url.trim(), SourceConfigReader.text(config, "fallbackUrls"));
        } catch (Exception e) {
            throw new IllegalStateException("数据源拉取失败: " + e.getMessage(), e);
        }
        List<JsonNode> array = locateArray(json, dataPath);
        if (array.isEmpty()) {
            throw new IllegalStateException("数据源未返回可用数组（可配置 dataPath 指定路径）");
        }

        int cursor = Math.max(0, offset);
        int changed = 0;
        int scanned = 0;
        while (cursor < array.size() && changed < limit) {
            JsonNode node = array.get(cursor++);
            scanned++;
            Map<String, Object> row = buildRow(node, fields, constants, script);
            if (row.isEmpty()) {
                continue;
            }
            Map<String, Integer> counter = support.upsert(table, uniqueKeys, overwrite, List.of(row));
            int done = counter.getOrDefault("inserted", 0) + counter.getOrDefault("updated", 0);
            changed += done;
            // 写 edu_idiom 时补学段映射
            if (done > 0 && "edu_idiom".equalsIgnoreCase(table.trim()) && StringUtils.hasText(gradeLevel)) {
                bindIdiomGrade(support, row, gradeLevel, grade, context.getOwnerUserId());
            }
        }
        log.info("[collect] 数据表入库完成: table={} url={} 扫描={} 写入={} 冲突策略={} 唯一键={}",
                table, url, scanned, changed, overwrite ? "overwrite" : "skip", uniqueKeys);
        return changed;
    }

    /**
     * 拉取 JSON：主地址失败时依次尝试 fallbackUrls；未配置 fallback 且是公开词库时用内置镜像。
     *
     * @param url          主地址
     * @param fallbackCsv  备用地址（逗号分隔，可空）
     * @return JSON 文本
     */
    private String fetchWithFallback(String url, String fallbackCsv) {
        List<String> candidates = new ArrayList<>();
        candidates.add(url);
        if (StringUtils.hasText(fallbackCsv)) {
            for (String part : fallbackCsv.split(",")) {
                String candidate = part.trim();
                if (!candidate.isEmpty() && !candidates.contains(candidate)) {
                    candidates.add(candidate);
                }
            }
        } else if (url.contains("chinese-xinhua")) {
            for (String mirror : List.of(
                    "https://cdn.jsdelivr.net/gh/pwxcoo/chinese-xinhua@master/data/idiom.json",
                    "https://fastly.jsdelivr.net/gh/pwxcoo/chinese-xinhua@master/data/idiom.json",
                    "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/idiom.json",
                    "https://ghproxy.net/https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/idiom.json")) {
                if (!candidates.contains(mirror)) {
                    candidates.add(mirror);
                }
            }
        }
        Exception last = null;
        for (String candidate : candidates) {
            try {
                return HttpFetchSupport.get(candidate);
            } catch (IOException e) {
                last = e;
                log.warn("[collect] 数据源不可用，尝试下一个: {} 原因: {}", candidate, e.getMessage());
            }
        }
        throw new IllegalStateException(last == null ? "无可用数据源" : last.getMessage(), last);
    }

    /**
     * 构造一行数据：字段映射 + 常量 + 可选 Groovy 处理。
     *
     * @param node      原始 JSON 节点
     * @param fields    列 → 源路径
     * @param constants 常量列
     * @param script    处理脚本（可空）
     * @return 行数据
     */
    private Map<String, Object> buildRow(JsonNode node, Map<String, String> fields,
                                         Map<String, Object> constants, String script) {
        Map<String, Object> raw = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> raw.put(e.getKey(), nodeValue(e.getValue())));

        Map<String, Object> item = new LinkedHashMap<>(raw);
        if (StringUtils.hasText(script)) {
            try {
                Binding binding = new Binding();
                binding.setVariable("item", item);
                binding.setVariable("log", log);
                Object result = new GroovyShell(binding).evaluate(script);
                if (result instanceof Map<?, ?> ext) {
                    ext.forEach((k, v) -> item.put(String.valueOf(k), v));
                }
            } catch (Exception e) {
                throw new IllegalStateException("处理脚本执行失败: " + e.getMessage(), e);
            }
        }

        Map<String, Object> row = new LinkedHashMap<>(constants);
        if (fields.isEmpty()) {
            // 未配置映射：源字段名即列名
            row.putAll(item);
        } else {
            fields.forEach((column, path) -> {
                Object value = valueOf(item, path);
                if (value != null) {
                    row.put(column, value);
                }
            });
        }
        return row;
    }

    /**
     * 按点号路径取值（支持嵌套对象）。
     *
     * @param item 数据
     * @param path 路径（如 a.b）
     * @return 值，缺失返回 null
     */
    private Object valueOf(Map<String, Object> item, String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String[] segments = path.trim().split("\\.");
        Object current = item;
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

    /**
     * JSON 节点转普通值（对象/数组转字符串，便于入库）。
     *
     * @param node 节点
     * @return 值
     */
    private Object nodeValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject() || node.isArray()) {
            return node.toString();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return node.asText();
    }

    /**
     * 写 edu_idiom 后补充学段/年级映射（消消乐按此取数）。
     *
     * @param support     入库支持
     * @param row         行数据
     * @param gradeLevel  学段
     * @param grade       年级
     * @param ownerUserId 归属用户
     */
    private void bindIdiomGrade(DbCollectSupport support, Map<String, Object> row,
                                String gradeLevel, String grade, String ownerUserId) {
        Object content = row.get("idiom_content");
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
     * 定位 JSON 数组：dataPath 优先，其次根数组，再次 data 字段。
     *
     * @param json     接口返回
     * @param dataPath 点号路径
     * @return 节点列表
     */
    private List<JsonNode> locateArray(String json, String dataPath) {
        List<JsonNode> list = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode array = root;
            if (StringUtils.hasText(dataPath)) {
                for (String segment : dataPath.split("\\.")) {
                    if (array == null || segment.isBlank()) {
                        continue;
                    }
                    array = array.get(segment);
                }
            } else if (!root.isArray()) {
                array = root.path("data");
            }
            if (array != null && array.isArray()) {
                array.forEach(list::add);
            }
        } catch (Exception e) {
            throw new IllegalStateException("数据源解析失败: " + e.getMessage(), e);
        }
        return list;
    }

    private List<String> splitKeys(String csv) {
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

    private Map<String, String> parseObject(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!StringUtils.hasText(json)) {
            return map;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
        } catch (Exception e) {
            log.warn("[collect] 字段映射解析失败: {}", e.getMessage());
        }
        return map;
    }

    private Map<String, Object> parseObjectValues(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (!StringUtils.hasText(json)) {
            return map;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            node.fields().forEachRemaining(e -> map.put(e.getKey(), nodeValue(e.getValue())));
        } catch (Exception e) {
            log.warn("[collect] 常量配置解析失败: {}", e.getMessage());
        }
        return map;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static int intOf(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
