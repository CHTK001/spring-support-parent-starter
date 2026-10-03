package com.chua.starter.monitor.support.support;

import com.chua.common.support.lang.datasource.dialect.SqlName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据导入 / 导出的格式编解码。
 *
 * <p>负责把外部数据文件（CSV / TSV / JSON 数组）与内表行互转，并把内表行序列化为
 * 导出文件（CSV / TSV / JSON / XML / SQL）。本类只做纯文本层面的转换，不触碰连接，
 * 因此可脱离数据库单测。</p>
 *
 * <p><b>安全豁免</b>：本类不接受也不拼装任何标识符——列名一律由调用方传入且已在
 * 上层经 {@link SqlName} 白名单校验；单元格值只作为数据参与序列化，导出 SQL 时
 * 字面量统一走 {@link #sqlLiteral} 转义，不做字符串拼接进 DDL。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public final class DbTransferCodec {

    /** UTF-8 BOM，Excel 导出的 CSV 常带，解析时需剥掉，否则第一列表头会带上不可见字符 */
    private static final String UTF8_BOM = "\uFEFF";

    /** 导出时间戳格式，用于文件名与 SQL 注释头 */
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 单次解析允许的最大行数，防止超大文件把堆打满 */
    private static final int MAX_PARSE_ROWS = 500_000;

    /** 单次导出允许的最大行数 */
    public static final int MAX_EXPORT_ROWS = 200_000;

    private DbTransferCodec() {
    }

    /**
     * 当前时间戳（文件名用）。
     *
     * @return 形如 {@code 20261003_120501}
     */
    public static String timestamp() {
        return LocalDateTime.now().format(STAMP);
    }

    // ==================== 解析侧（导入） ====================

    /**
     * 解析分隔符格式（CSV / TSV）文本为「表头 + 数据行」。
     *
     * @param text      原始文本，可含 BOM
     * @param delimiter 分隔符
     * @param hasHeader 首行是否为表头
     * @return 表头列名与数据行；{@code hasHeader=false} 时表头为空列表
     */
    public static ParsedTable parseDelimited(String text, char delimiter, boolean hasHeader) {
        List<List<String>> raw = readAllRecords(stripBom(text), delimiter);
        List<String> header = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        int start = 0;
        if (hasHeader && !raw.isEmpty()) {
            List<String> first = raw.get(0);
            for (int i = 0; i < first.size(); i++) {
                header.add(first.get(i).trim());
            }
            start = 1;
        } else {
            for (int i = 0; i < (raw.isEmpty() ? 0 : raw.get(0).size()); i++) {
                header.add("col" + (i + 1));
            }
        }
        for (int r = start; r < raw.size(); r++) {
            List<String> record = raw.get(r);
            // 跳过完全空行（CSV 结尾多一个换行很常见）
            boolean blank = record.stream().allMatch(v -> v == null || v.trim().isEmpty());
            if (blank) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                row.put(header.get(c), c < record.size() ? record.get(c) : null);
            }
            rows.add(row);
            if (rows.size() >= MAX_PARSE_ROWS) {
                break;
            }
        }
        return new ParsedTable(List.copyOf(header), List.copyOf(rows));
    }

    /**
     * 按引号规则切分整段文本为记录（支持字段内换行、双引号转义 {@code ""}）。
     *
     * @param text      原始文本
     * @param delimiter 分隔符
     * @return 记录列表
     */
    private static List<List<String>> readAllRecords(String text, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                current.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                current.add(field.toString());
                field.setLength(0);
                records.add(current);
                current = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            records.add(current);
        }
        return records;
    }

    /**
     * 解析 JSON 数组为行集合。
     *
     * <p>接受两种形态：对象数组（{@code [{"a":1}]}，键即列名）与二维数组
     * （{@code [[1,2]]}，配合 {@code header} 取列名）。</p>
     *
     * @param text   JSON 文本
     * @param header 二维数组形态下的列名
     * @return 解析结果
     * @throws IOException JSON 非法
     */
    public static ParsedTable parseJson(String text, List<String> header) throws IOException {
        JsonNode root = MAPPER.readTree(stripBom(text));
        if (!root.isArray()) {
            throw new IOException("JSON 必须是数组");
        }
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!root.isEmpty() && root.get(0).isObject()) {
            for (JsonNode element : root) {
                if (!element.isObject()) {
                    throw new IOException("JSON 数组元素必须是对象");
                }
                Map<String, Object> row = new LinkedHashMap<>();
                element.fields().forEachRemaining(entry -> {
                    if (!columns.contains(entry.getKey())) {
                        columns.add(entry.getKey());
                    }
                    row.put(entry.getKey(), toPlain(entry.getValue()));
                });
                rows.add(row);
                if (rows.size() >= MAX_PARSE_ROWS) {
                    break;
                }
            }
            return new ParsedTable(List.copyOf(columns), List.copyOf(rows));
        }
        columns.addAll(header);
        for (JsonNode element : root) {
            if (!element.isArray()) {
                throw new IOException("二维 JSON 数组的元素必须是数组");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < columns.size(); c++) {
                row.put(columns.get(c), c < element.size() ? toPlain(element.get(c)) : null);
            }
            rows.add(row);
            if (rows.size() >= MAX_PARSE_ROWS) {
                break;
            }
        }
        return new ParsedTable(List.copyOf(columns), List.copyOf(rows));
    }

    /**
     * Jackson 节点转朴素 Java 值（保证可 {@code setObject} 进 PreparedStatement）。
     *
     * @param node JSON 节点
     * @return null / String / Number / Boolean
     */
    private static Object toPlain(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        return node.asText();
    }

    /**
     * 按分号切分 SQL 语句，忽略引号内与注释里的分号。
     *
     * @param sql SQL 脚本
     * @return 语句列表（已 trim 并去空）
     */
    public static List<String> splitSqlStatements(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == '\\' && i + 1 < sql.length()) {
                    current.append(sql.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                current.append(c);
            } else if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                // 整行注释：跳到行尾
                while (i < sql.length() && sql.charAt(i) != '\n') {
                    i++;
                }
                current.append('\n');
            } else if (c == ';') {
                addStatement(result, current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addStatement(result, current.toString());
        return result;
    }

    private static void addStatement(List<String> result, String statement) {
        String trimmed = statement.trim();
        if (!trimmed.isEmpty()) {
            result.add(trimmed);
        }
    }

    private static String stripBom(String text) {
        return text != null && text.startsWith(UTF8_BOM) ? text.substring(UTF8_BOM.length()) : text;
    }

    /**
     * 探测分隔符格式文本的分隔符。
     *
     * @param text 原始文本
     * @return {@code \t} 或 {@code ,}
     */
    public static char detectDelimiter(String text) {
        String sample = stripBom(text);
        int firstLine = sample.indexOf('\n');
        String line = firstLine > 0 ? sample.substring(0, firstLine) : sample;
        return line.indexOf('\t') >= 0 ? '\t' : ',';
    }

    /**
     * 把上传字节按指定编码解码；编码名非法或内容为 null 时回退 UTF-8。
     *
     * @param bytes    文件字节
     * @param encoding 编码名
     * @return 文本
     */
    public static String decode(byte[] bytes, String encoding) {
        if (bytes == null) {
            return "";
        }
        Charset charset = StandardCharsets.UTF_8;
        if (encoding != null && !encoding.isBlank()) {
            try {
                charset = Charset.forName(encoding.trim());
            } catch (RuntimeException ignored) {
                // 未知编码名，回退 UTF-8
            }
        }
        return new String(bytes, charset);
    }

    // ==================== 序列化侧（导出） ====================

    /**
     * 序列化为分隔符格式（CSV / TSV）。
     *
     * @param columns   列名
     * @param rows      行
     * @param delimiter 分隔符
     * @return 文本（含表头）
     */
    public static String writeDelimited(List<String> columns, List<Map<String, Object>> rows, char delimiter) {
        StringBuilder out = new StringBuilder();
        out.append(String.join(String.valueOf(delimiter), columns.stream().map(v -> quote(v, delimiter)).toList()));
        out.append('\n');
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>(columns.size());
            for (String column : columns) {
                values.add(quote(stringify(row.get(column)), delimiter));
            }
            out.append(String.join(String.valueOf(delimiter), values)).append('\n');
        }
        return out.toString();
    }

    /**
     * 序列化为 JSON 数组。
     *
     * @param columns 列名
     * @param rows    行
     * @return JSON 文本
     * @throws IOException 序列化失败
     */
    public static String writeJson(List<String> columns, List<Map<String, Object>> rows) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> ordered = new LinkedHashMap<>();
            for (String column : columns) {
                ordered.put(column, normalize(row.get(column)));
            }
            out.add(ordered);
        }
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out);
    }

    /**
     * 序列化为 XML（每行一个 {@code <row>}）。
     *
     * @param columns 列名
     * @param rows    行
     * @return XML 文本
     */
    public static String writeXml(List<String> columns, List<Map<String, Object>> rows) {
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rows>\n");
        for (Map<String, Object> row : rows) {
            out.append("  <row>\n");
            for (String column : columns) {
                out.append("    <").append(column).append('>')
                        .append(escapeXml(stringify(row.get(column))))
                        .append("</").append(column).append(">\n");
            }
            out.append("  </row>\n");
        }
        out.append("</rows>\n");
        return out.toString();
    }

    /**
     * 序列化为 SQL INSERT 脚本（每批多值）。
     *
     * @param tableName 目标表名（仅作为字面量输出，不做 DDL）
     * @param columns   列名
     * @param rows      行
     * @param batchSize 每条 INSERT 的值组数
     * @return SQL 文本
     */
    public static String writeSql(String tableName, List<String> columns, List<Map<String, Object>> rows, int batchSize) {
        int size = Math.max(1, batchSize);
        String safeTable = SqlName.checkSimple(tableName, "表名");
        String columnList = String.join(", ", columns.stream().map(c -> SqlName.quoteMysql(c, "列名")).toList());
        StringBuilder out = new StringBuilder();
        out.append("-- 由 Web 数据库管理器导出\n-- 表: ").append(safeTable)
                .append("  行数: ").append(rows.size()).append('\n');
        for (int i = 0; i < rows.size(); i += size) {
            List<Map<String, Object>> slice = rows.subList(i, Math.min(rows.size(), i + size));
            out.append("INSERT INTO ").append(SqlName.quoteMysql(safeTable, "表名"))
                    .append(" (").append(columnList).append(") VALUES\n");
            for (int r = 0; r < slice.size(); r++) {
                Map<String, Object> row = slice.get(r);
                List<String> values = new ArrayList<>(columns.size());
                for (String column : columns) {
                    values.add(sqlLiteral(row.get(column)));
                }
                out.append("  (").append(String.join(", ", values)).append(')');
                out.append(r == slice.size() - 1 ? ";\n" : ",\n");
            }
        }
        if (rows.isEmpty()) {
            out.append("-- （无数据）\n");
        }
        return out.toString();
    }

    /**
     * 生成带 DROP / CREATE 的建表脚本（仅结构导出）。
     *
     * @param tableName 表名
     * @param ddl       建表语句
     * @return SQL 文本
     */
    public static String writeSchemaSql(String tableName, String ddl) {
        String safeTable = SqlName.checkSimple(tableName, "表名");
        return "DROP TABLE IF EXISTS " + SqlName.quoteMysql(safeTable, "表名") + ";\n"
                + ddl.trim() + ";\n";
    }

    /**
     * 值转 SQL 字面量：null / 数字直接输出，字符串单引号转义。
     *
     * @param value 原始值
     * @return SQL 字面量文本
     */
    private static String sqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return stringify(value);
        }
        return "'" + stringify(value).replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String quote(String value, char delimiter) {
        String v = value == null ? "" : value;
        boolean needQuote = v.indexOf(delimiter) >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0 || !v.trim().equals(v);
        if (!needQuote) {
            return v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    /**
     * 值转可读字符串（导出与 XML 共用）。
     *
     * @param value 原始值
     * @return 字符串；null 返回 {@code \N}（与 mysqldump 一致，便于再导入）
     */
    public static String stringify(Object value) {
        if (value == null) {
            return "\\N";
        }
        if (value instanceof byte[] bytes) {
            StringBuilder hex = new StringBuilder("0x");
            for (byte b : bytes) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        }
        if (value instanceof java.sql.Timestamp || value instanceof java.sql.Date) {
            return value.toString();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return String.valueOf(value);
    }

    /**
     * 归一化值用于 JSON 序列化（BIGINT 超出 JS 安全整数时转字符串）。
     *
     * @param value 原始值
     * @return 可安全序列化的值
     */
    private static Object normalize(Object value) {
        if (value instanceof byte[]) {
            return stringify(value);
        }
        if (value instanceof Number number && (value instanceof java.lang.Long || value instanceof java.lang.Integer)) {
            long asLong = number.longValue();
            if (asLong > 9_007_199_254_740_991L || asLong < -9_007_199_254_740_991L) {
                return String.valueOf(asLong);
            }
        }
        return value;
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    /**
     * 解析结果。
     *
     * @param columns 列名
     * @param rows    行
     */
    public record ParsedTable(List<String> columns, List<Map<String, Object>> rows) {
        /**
         * 规范构造器：集合做防御性拷贝。
         *
         * @param columns 列名
         * @param rows    行
         */
        public ParsedTable {
            columns = List.copyOf(columns);
            rows = java.util.Collections.unmodifiableList(new ArrayList<>(rows));
        }
    }

    /**
     * 把格式名规整为小写。
     *
     * @param format 格式名
     * @return 小写形式
     */
    public static String normalizeFormat(String format) {
        return format == null ? "csv" : format.trim().toLowerCase(Locale.ROOT);
    }
}