package com.chua.starter.account.spi.impl;

import com.chua.starter.account.spi.ChannelConfigImportParser;
import com.chua.starter.account.spi.ChannelImportEntry;
import com.chua.starter.account.spi.ChannelImportFile;
import com.chua.starter.account.spi.ChannelImportModelDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 配置导入解析器的公共工具基类。
 *
 * <p>收录各来源共用的判定逻辑：JSONC 注释/尾逗号剥离、.env 行解析、
 * 极简 TOML 解析、SQLite 临时库读取、模型任务类型推断、同批重名去重、
 * URL 取名等。移植自前端 {@code configImport.ts} 的同名口径。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public abstract class AbstractChannelConfigImportParser implements ChannelConfigImportParser {

    /** 解析专用 ObjectMapper（只读树，不复用全局配置） */
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    /** .env 键名合法性 */
    private static final Pattern ENV_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    // ==================== 输入读取 ====================

    /**
     * 取第 index 个文件的 UTF-8 文本。
     */
    protected static String fileText(List<ChannelImportFile> files, int index) {
        byte[] bytes = fileAt(files, index).getBytes();
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 取第一个文件的 UTF-8 文本。
     */
    protected static String firstText(List<ChannelImportFile> files) {
        return fileText(files, 0);
    }

    /**
     * 取第一个文件的字节。
     */
    protected static byte[] firstBytes(List<ChannelImportFile> files) {
        return fileAt(files, 0).getBytes();
    }

    /**
     * 取第 index 个文件（缺失即抛业务可读异常）。
     */
    protected static ChannelImportFile fileAt(List<ChannelImportFile> files, int index) {
        if (files == null || index < 0 || index >= files.size() || files.get(index) == null) {
            throw new IllegalArgumentException("缺少配置文件内容");
        }
        return files.get(index);
    }

    /**
     * 按文件名后缀查找文件（如 {@code ".toml"} / {@code ".json"}），找不到返回 null。
     */
    protected static ChannelImportFile findBySuffix(List<ChannelImportFile> files, String suffix) {
        if (files == null) {
            return null;
        }
        for (ChannelImportFile file : files) {
            if (file != null && file.getName() != null
                    && file.getName().toLowerCase().endsWith(suffix)) {
                return file;
            }
        }
        return null;
    }

    // ==================== JSONC ====================

    /**
     * 剥离 JSONC 注释（// 与 /* *\/），字符串字面量内的注释符不受影响
     * （避免误伤 "https://…" 这类 URL）。
     */
    protected static String stripJsonComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        boolean inStr = false;
        boolean escape = false;
        while (i < n) {
            char c = text.charAt(i);
            if (inStr) {
                out.append(c);
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inStr = false;
                }
                i++;
                continue;
            }
            if (c == '"') {
                inStr = true;
                out.append(c);
                i++;
                continue;
            }
            if (c == '/' && i + 1 < n) {
                char d = text.charAt(i + 1);
                if (d == '/') {
                    // 行注释：吞到换行符为止（换行符保留，保住行号语义）
                    while (i < n && text.charAt(i) != '\n') {
                        i++;
                    }
                    continue;
                }
                if (d == '*') {
                    // 块注释：吞到 */
                    i += 2;
                    while (i + 1 < n && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) {
                        i++;
                    }
                    i += 2;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * 去掉对象/数组收尾前的逗号（JSONC 允许尾逗号，JSON.parse 不允许）。
     */
    protected static String removeTrailingCommas(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inStr = false;
        boolean escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inStr) {
                out.append(c);
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                out.append(c);
                continue;
            }
            if (c == ',') {
                int j = i + 1;
                while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
                    j++;
                }
                if (j < text.length() && (text.charAt(j) == '}' || text.charAt(j) == ']')) {
                    continue; // 丢弃尾逗号
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * JSONC 文本 → JSON 树（注释与尾逗号都容忍）；非法 JSON 抛业务可读异常。
     */
    protected static JsonNode parseJsonc(String text) {
        try {
            return MAPPER.readTree(removeTrailingCommas(stripJsonComments(text)));
        } catch (Exception e) {
            throw new IllegalArgumentException("配置不是有效的 JSON/JSONC: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /**
     * 安全解析 JSON 对象（cc-switch settings_config 可能是字符串 / 已是对象 / 空）。
     */
    protected static JsonNode safeParseObject(String text) {
        if (text == null || text.trim().isEmpty()) {
            return MAPPER.createObjectNode();
        }
        try {
            JsonNode node = MAPPER.readTree(text);
            if (node != null && node.isObject()) {
                return node;
            }
        } catch (Exception ignored) {
            // 非法 JSON 按空对象处理
        }
        return MAPPER.createObjectNode();
    }

    /**
     * 非对象节点统一退化为空对象，简化取字段。
     */
    protected static JsonNode obj(JsonNode node) {
        return node != null && node.isObject() ? node : MAPPER.createObjectNode();
    }

    /**
     * 节点 → 去空格文本（null/缺失返回空串）。
     */
    protected static String str(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        String value = node.asText();
        return value == null ? "" : value.trim();
    }

    /**
     * 节点 → Long（空/非数字返回 null；语义对齐前端 numOrNull）。
     */
    protected static Long numOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        String text = node.asText();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? (long) value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 第一个非空字符串（全空返回空串）。
     */
    protected static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * 是否为 http(s) URL。
     */
    protected static boolean isHttpUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    /**
     * 正则取组（默认第 1 组），无命中返回空串。
     */
    protected static String match1(String text, String regex, int group) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        java.util.regex.Matcher matcher = Pattern.compile(regex).matcher(text);
        if (matcher.find() && matcher.groupCount() >= group) {
            String value = matcher.group(group);
            return value == null ? "" : value.trim();
        }
        return "";
    }

    /**
     * 正则取第 1 组。
     */
    protected static String match1(String text, String regex) {
        return match1(text, regex, 1);
    }

    // ==================== 模型 / 条目工具 ====================

    /**
     * 模型草稿工厂。
     */
    protected static ChannelImportModelDraft draft(String id, String name, Long contextWindow,
            Long maxOutputTokens, String task) {
        ChannelImportModelDraft model = new ChannelImportModelDraft();
        model.setId(id);
        model.setName(name == null || name.isEmpty() ? id : name);
        model.setContextWindow(contextWindow);
        model.setMaxOutputTokens(maxOutputTokens);
        model.setTask(task);
        return model;
    }

    /**
     * 按模型 id / 名称 / 输入模态推断任务类型。
     */
    protected static String inferTask(String id, String name, JsonNode input) {
        String hay = (id + " " + name).toLowerCase();
        if (hay.contains("video")) {
            return "video_generation";
        }
        if (hay.contains("image")) {
            return "image_generation";
        }
        if (input != null && input.isArray()) {
            for (JsonNode item : input) {
                if (item != null && "image".equalsIgnoreCase(item.asText())) {
                    return "image_generation";
                }
            }
        }
        return "chat";
    }

    /**
     * 按模型 id / 名称推断任务类型。
     */
    protected static String inferTask(String id, String name) {
        return inferTask(id, name, null);
    }

    /**
     * 同批重名去重：第 2 次及以后出现的名字追加序号（deepseek-v4-flash → …(2)）。
     */
    protected static void dedupeEntryNames(List<ChannelImportEntry> entries) {
        Map<String, Integer> seen = new HashMap<>();
        for (ChannelImportEntry entry : entries) {
            int count = seen.merge(entry.getName(), 1, Integer::sum);
            if (count > 1) {
                entry.setName(entry.getName() + " (" + count + ")");
            }
        }
    }

    /**
     * 从 URL 提取可读名：https://api.deepseek.com/v1 → Deepseek；解析不出返回空串。
     */
    protected static String nameFromUrl(String url) {
        String value = url == null ? "" : url.trim();
        if (value.isEmpty()) {
            return "";
        }
        try {
            String host = URI.create(value).getHost();
            if (host == null) {
                return "";
            }
            String[] parts = host.toLowerCase().split("\\.");
            for (String part : parts) {
                if (!part.isEmpty() && !"www".equals(part) && !"api".equals(part)) {
                    return Character.toUpperCase(part.charAt(0)) + part.substring(1);
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== .env ====================

    /**
     * .env 文本 → 键值对。容忍 {@code export KEY=VALUE}、成对引号、行内
     * {@code #} 注释（仅未加引号时）、空行与 {@code #} 注释行。
     */
    protected static Map<String, String> parseEnvText(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null) {
            return map;
        }
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim().replaceFirst("(?i)^export\\s+", "");
            if (!ENV_KEY.matcher(key).matches()) {
                continue;
            }
            String value = line.substring(eq + 1).trim();
            boolean quoted = value.length() >= 2
                    && ((value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"')
                        || (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\''));
            if (quoted) {
                value = value.substring(1, value.length() - 1);
            } else {
                int hash = value.indexOf(" #");
                if (hash >= 0) {
                    value = value.substring(0, hash).trim();
                }
            }
            map.put(key, value);
        }
        return map;
    }

    // ==================== 极简 TOML ====================

    /**
     * 极简 TOML 解析：只取「节 + key = value」，value 支持 "…" / '…' / 裸量。
     * 足够读 codex 的 {@code model} / {@code [model_providers.*] base_url}。
     *
     * @return 节名（根节为空串） → 键值对；同节同键后者覆盖
     */
    protected static Map<String, Map<String, String>> parseToml(String text) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        Map<String, String> current = out.computeIfAbsent("", key -> new LinkedHashMap<>());
        if (text == null) {
            return out;
        }
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[[")) {
                int end = line.lastIndexOf("]]");
                if (end > 2) {
                    current = out.computeIfAbsent(line.substring(2, end).trim(),
                            key -> new LinkedHashMap<>());
                }
                continue;
            }
            if (line.startsWith("[")) {
                int end = line.lastIndexOf(']');
                if (end > 1) {
                    current = out.computeIfAbsent(line.substring(1, end).trim(),
                            key -> new LinkedHashMap<>());
                }
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.startsWith("\"")) {
                int end = value.indexOf('"', 1);
                if (end > 0) {
                    value = value.substring(1, end);
                }
            } else if (value.startsWith("'")) {
                int end = value.indexOf('\'', 1);
                if (end > 0) {
                    value = value.substring(1, end);
                }
            } else {
                int hash = value.indexOf(" #");
                if (hash >= 0) {
                    value = value.substring(0, hash).trim();
                }
            }
            current.put(key, value);
        }
        return out;
    }

    // ==================== SQLite ====================

    /**
     * 打开内存临时 SQLite 库执行读取，用完即删临时文件。
     *
     * @param bytes SQLite 库字节（校验魔数）
     * @param fn    读取逻辑
     * @param <T>   返回类型
     * @return 读取结果
     */
    protected static <T> T withSqlite(byte[] bytes, SqlReader<T> fn) {
        if (bytes == null || bytes.length < 16
                || !"SQLite format 3".equals(new String(bytes, 0, 15, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("不是有效的 SQLite 数据库文件");
        }
        Path tmp = null;
        try {
            tmp = Files.createTempFile("channel-import", ".db");
            Files.write(tmp, bytes);
            try (Connection conn = java.sql.DriverManager
                    .getConnection("jdbc:sqlite:" + tmp.toAbsolutePath())) {
                return fn.read(conn);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取 SQLite 失败: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                    // 临时文件删除失败不影响结果
                }
            }
        }
    }

    /**
     * 判断库中是否存在某张表。
     */
    protected static boolean tableExists(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString(1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 读字符串列；BLOB 按字节转 UTF-8 文本（sqlite 列类型宽松）。
     */
    protected static String columnText(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    /** SQLite 读取函数（允许抛出底层异常，统一转业务可读消息） */
    protected interface SqlReader<T> {
        T read(Connection conn) throws Exception;
    }
}
