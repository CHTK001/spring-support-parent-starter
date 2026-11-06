package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.collect.support.CollectMiddlewareSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据录入节点：手工录行，作为流程的行数据源头。
 *
 * <p>录入文本按 {@code format} 参数解析（CSV / JSONL / HTTP JSON / 纯文本），
 * 解析口径写死在 {@link #parseRows}。CSV 按首行表头、其后每行一条；JSONL 每行一条
 * JSON 对象；HTTP JSON 是 JSON 数组或对象；纯文本每行收进 {@code value} 列。</p>
 *
 * <p>文本先过 {@link CollectExpression#evalText}，所以和开始节点一样支持 {@code ${}} 拼接。
 * 出参是 {@code List<Map>}（默认变量名 {@code rows}），下游数据库 / 输出节点按集合整批消费 ——
 * 与抽取节点 list 模式同一套语义，不需要引擎做任何改动。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class CollectDataEntryExecutor implements CollectShapeExecutor {

    /**
     * 录入数据参数名。
     */
    public static final String ROWS = "rows";

    /**
     * 出参集合变量名参数名。
     */
    public static final String OUTPUT = "outputVariable";

    /**
     * 数据格式参数名。
     */
    public static final String FORMAT = "format";

    /**
     * 出参集合默认名。
     */
    public static final String DEFAULT_OUTPUT = "rows";

    /**
     * CSV：首行表头 + 其后每行一条。
     */
    public static final String FORMAT_CSV = "csv";

    /**
     * JSONL：每行一条 JSON 对象。
     */
    public static final String FORMAT_JSONL = "jsonl";

    /**
     * HTTP JSON：JSON 数组或对象（接口响应的常见形态）。
     */
    public static final String FORMAT_HTTP_JSON = "http-json";

    /**
     * 纯文本：每行收进 {@code value} 列。
     */
    public static final String FORMAT_TEXT = "text";

    @Override
    public String supportShape() {
        return "entry";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "数据录入", "来源", "edit-pen",
                "手工录入数据行作为流程的行数据源头，下游接数据库 / 输出等节点；按选择的数据格式解析");
        meta.setOutputVariable("采集结果写入的变量名，默认 data");
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.required(ROWS, "录入数据", "textarea",
                        "CSV：首行表头、其后每行一条；JSONL：每行一条 JSON；HTTP JSON：JSON 数组或对象；纯文本：每行一条，收进 value 列；支持 ${} 表达式"),
                CollectShapeMetas.option(FORMAT, "数据格式", FORMAT_CSV,
                        "CSV 按首行表头解析；JSONL 每行一条 JSON；HTTP JSON 提供 JSON 数组或对象；纯文本每行一条、收进 value 列",
                        FORMAT_CSV, FORMAT_JSONL, FORMAT_HTTP_JSON, FORMAT_TEXT),
                CollectShapeMetas.outputVariable("录入结果写入的变量名，默认 data"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) {
        String raw = CollectExpression.evalText(node.param(ROWS), variables);
        String format = node.param(FORMAT);
        List<Map<String, Object>> rows = parseRows(raw, format);
        variables.put(outputOf(node), rows);
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        Object value = variables.get(outputOf(node));
        int size = value instanceof List ? ((List<?>) value).size() : 0;
        return "录入 " + size + " 行";
    }

    @Override
    public String defaultOutput(CollectNode node) {
        return DEFAULT_OUTPUT;
    }

    /**
     * 解析出参变量名，空值回落默认名。
     *
     * @param node 节点
     * @return 出参变量名
     */
    private String outputOf(CollectNode node) {
        String output = node.param(OUTPUT).trim();
        return output.isEmpty() ? DEFAULT_OUTPUT : output;
    }

    /**
     * 解析录入文本。
     *
     * <p>格式由参数决定，四选一：CSV（首行表头）、JSONL（每行一条 JSON）、
     * HTTP JSON（JSON 数组或对象）、纯文本（每行收进 value 列）。</p>
     *
     * @param raw    录入文本
     * @param format 数据格式（空按 csv 兜底）
     * @return 行列表
     * @throws IllegalArgumentException 结构不合法（非数组 JSON、缺表头、列数超出表头等）
     */
    private List<Map<String, Object>> parseRows(String raw, String format) {
        String text = raw == null ? "" : raw.trim();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (text.isEmpty()) {
            return rows;
        }
        String code = StringUtils.hasText(format) ? format.trim() : FORMAT_CSV;
        return switch (code) {
            case FORMAT_JSONL -> parseJsonLines(text);
            case FORMAT_HTTP_JSON -> parseHttpJson(text);
            case FORMAT_TEXT -> parseTextLines(text);
            default -> parseCsv(text);
        };
    }

    /**
     * CSV / TSV 解析：首行表头、其后每行一条。
     *
     * @param text 录入文本
     * @return 行列表
     */
    private List<Map<String, Object>> parseCsv(String text) {
        // 分隔符看首行：有制表符按 TSV，否则按 CSV
        String firstLine = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        char delimiter = firstLine.indexOf('\t') >= 0 ? '\t' : ',';
        List<List<String>> table = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (!line.isBlank()) {
                table.add(parseCsvLine(line, delimiter));
            }
        }
        if (table.size() <= 1) {
            throw new IllegalArgumentException("CSV 录入至少需要表头与一行数据");
        }
        List<String> headers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < table.get(0).size(); i++) {
            String header = table.get(0).get(i).trim();
            if (header.isEmpty()) {
                throw new IllegalArgumentException("录入数据第 1 行（表头）第 " + (i + 1) + " 列列名为空");
            }
            if (!seen.add(header)) {
                throw new IllegalArgumentException("录入数据表头列名重复: " + header);
            }
            headers.add(header);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int r = 1; r < table.size(); r++) {
            List<String> cells = table.get(r);
            if (cells.size() > headers.size()) {
                throw new IllegalArgumentException("录入数据第 " + (r + 1) + " 行有 " + cells.size()
                        + " 列，表头只有 " + headers.size() + " 列（检查分隔符或多余逗号）");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                row.put(headers.get(c), c < cells.size() ? coerce(cells.get(c)) : null);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * JSONL 解析：每行一条 JSON 对象。
     *
     * @param text 录入文本
     * @return 行列表
     */
    private List<Map<String, Object>> parseJsonLines(String text) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            Object parsed = CollectJson.parseAny(line.trim());
            Map<String, Object> row = new LinkedHashMap<>();
            if (parsed instanceof Map<?, ?> map) {
                map.forEach((key, value) -> row.put(String.valueOf(key), value));
            } else {
                row.put(CollectMiddlewareSupport.VALUE_COLUMN, parsed);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * HTTP JSON 解析：JSON 数组或对象。
     *
     * @param text 录入文本
     * @return 行列表
     */
    private List<Map<String, Object>> parseHttpJson(String text) {
        Object parsed = CollectJson.parseAny(text);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (parsed instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                if (item instanceof Map<?, ?> map) {
                    map.forEach((key, value) -> row.put(String.valueOf(key), value));
                } else {
                    row.put(CollectMiddlewareSupport.VALUE_COLUMN, item);
                }
                rows.add(row);
            }
            return rows;
        }
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> row = new LinkedHashMap<>();
            map.forEach((key, value) -> row.put(String.valueOf(key), value));
            rows.add(row);
            return rows;
        }
        throw new IllegalArgumentException("HTTP JSON 格式要求 JSON 数组或对象，当前不是");
    }

    /**
     * 纯文本解析：每行一条收进 value 列。
     *
     * @param text 录入文本
     * @return 行列表
     */
    private List<Map<String, Object>> parseTextLines(String text) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (!line.isBlank()) {
                rows.add(Map.of(CollectMiddlewareSupport.VALUE_COLUMN, line.trim()));
            }
        }
        return rows;
    }

    /**
     * 解析一行 CSV / TSV，支持双引号包裹与双引号转义。
     *
     * @param line      行文本
     * @param delimiter 分隔符
     * @return 单元格列表
     */
    private List<String> parseCsvLine(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == delimiter) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    /**
     * 单元格按值域收型：空 → null、布尔、整数、小数，其余保留文本。
     *
     * <p>前导零的数字（如 {@code 007}）刻意不收成数字 —— 那是编号语义，收了会丢位。</p>
     *
     * @param cell 单元格原文
     * @return 收型后的值
     */
    private Object coerce(String cell) {
        String value = cell == null ? "" : cell.trim();
        if (value.isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value.toLowerCase(java.util.Locale.ROOT));
        }
        if (value.matches("-?(0|[1-9]\\d*)")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException overflow) {
                return value;
            }
        }
        if (value.matches("-?(0|[1-9]\\d*)\\.\\d+")) {
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException overflow) {
                return value;
            }
        }
        return value;
    }
}