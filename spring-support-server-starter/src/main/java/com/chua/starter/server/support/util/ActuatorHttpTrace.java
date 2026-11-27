package com.chua.starter.server.support.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.ObjectUtils;

/**
 * Actuator {@code /actuator/httptrace}（Spring Boot 2.x）与
 * {@code /actuator/httpexchanges}（Spring Boot 3.x）响应压缩器。
 *
 * <p>为什么要单独一层：请求记录默认 100 条，每条带完整的请求/响应 headers，
 * 真实应用的响应动辄几百 KB，而远端执行通道会在 64KB 处截断输出。原样交给前端，
 * {@code JSON.parse} 必然失败——和 loggers 是同一个坑。</p>
 *
 * <p>策略：流式解析，只收数组元素（每条记录保留 method / uri / status / timeTaken /
 * timestamp，另从请求/响应头抽取 traceparent / B3 / uber-trace-id 等关联键，
 * 抽成 traceId / spanId / parentSpanId 三列，供前端按 trace 聚类成调用树；
 * 丢弃占体积大头的 headers）。解析到截断处抛 {@link com.fasterxml.jackson.core.JsonParseException}
 * 时停下来，把已读到的完整记录交出去，并如实把 {@code truncated} 置为 true，
 * 让页面提示「响应过大已截断」。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class ActuatorHttpTrace {

    /**
     * 默认返回给前端的最大条数
     */
    public static final int DEFAULT_MAX = 100;

    private static final JsonFactory FACTORY = new JsonFactory();
    private static final ObjectMapper JSON = new ObjectMapper(FACTORY);

    private ActuatorHttpTrace() {
    }

    /**
     * 压缩 httptrace / httpexchanges 响应。
     *
     * @param body 原始响应体（可能被截断）
     * @return records（每条 method/uri/status/timeTaken/timestamp）/ truncated
     */
    public static Map<String, Object> compact(String body) {
        return compact(body, DEFAULT_MAX);
    }

    /**
     * 压缩 httptrace / httpexchanges 响应，并限制返回条数。
     *
     * @param body 原始响应体（可能被截断）
     * @param max  最大条数
     * @return records / truncated
     */
    public static Map<String, Object> compact(String body, int max) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> records = new ArrayList<>();
        out.put("records", records);
        out.put("truncated", false);
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            return out;
        }
        boolean truncated = text.contains("[已截断]") || !text.endsWith("}");
        boolean fullyParsed = true;
        try (JsonParser parser = FACTORY.createParser(text)) {
            // 先定位到 traces / exchanges 数组字段（两种端点结构不同，两个都认）
            boolean found = false;
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.FIELD_NAME) {
                    String name = parser.currentName();
                    if ("traces".equals(name) || "exchanges".equals(name)) {
                        found = true;
                        break;
                    }
                    parser.nextToken();
                    parser.skipChildren();
                }
            }
            if (!found || parser.nextToken() != JsonToken.START_ARRAY) {
                out.put("truncated", truncated);
                return out;
            }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (records.size() >= max) {
                    truncated = true;
                    break;
                }
                JsonNode e = JSON.readTree(parser);
                if (e != null && !e.isNull()) {
                    records.add(record(e));
                }
            }
        } catch (Exception ignored) {
            // 截断的 JSON：已读到的完整记录照常交出，剩下的如实标注截断
            fullyParsed = false;
        }
        out.put("truncated", truncated || !fullyParsed);
        return out;
    }

    /**
     * 解析耗时字段：数字原样返回；Spring Boot 3.x+ 的
     * httpexchanges 里 timeTaken 是 ISO-8601 时长串（如
     * {@code "PT0.811985715S"} / {@code "PT2.5S"}），统一转毫秒。
     *
     * @param node 耗时节点（数字或文本）
     * @return 毫秒数；无法解析时为 -1
     */
    private static long parseTimeTaken(JsonNode node) {
        if (node.isNumber()) {
            return node.asLong();
        }
        if (!node.isTextual()) {
            return -1;
        }
        String text = node.asText().trim();
        if (text.isEmpty()) {
            return -1;
        }
        if (text.regionMatches(true, 0, "PT", 0, 2)) {
            double seconds = 0;
            boolean any = false;
            Matcher matcher = DURATION_PATTERN.matcher(text.substring(2));
            while (matcher.find()) {
                String amount = matcher.group(1);
                String unit = matcher.group(2);
                if (amount == null || unit == null) {
                    continue;
                }
                try {
                    double value = Double.parseDouble(amount);
                    switch (unit.charAt(0)) {
                        case 'H':
                            seconds += value * 3600;
                            break;
                        case 'M':
                            seconds += value * 60;
                            break;
                        case 'S':
                            seconds += value;
                            break;
                        default:
                            continue;
                    }
                    any = true;
                } catch (NumberFormatException ignored) {
                    // 非法数字段跳过
                }
            }
            if (any) {
                return Math.round(seconds * 1000);
            }
        }
        return -1;
    }

    /** ISO-8601 时长段：数量 + 单位（H/M/S），数量可为小数 */
    private static final java.util.regex.Pattern DURATION_PATTERN =
            java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)([HMS])");

    /**
     * 把一条请求记录压成页面要展示的字段。
     *
     * @param e 记录节点
     * @return 行（含 traceId / spanId / parentSpanId 三个链路关联键，取不到时为 null）
     */
    private static Map<String, Object> record(JsonNode e) {
        Map<String, Object> row = new LinkedHashMap<>();
        JsonNode req = e.get("request");
        JsonNode res = e.get("response");
        row.put("method", str(req, "method", "GET"));
        row.put("uri", str(req, "uri", ""));
        if (res != null && res.get("status") != null && res.get("status").isNumber()) {
            row.put("status", res.get("status").asInt());
        }
        if (e.get("timeTaken") != null) {
            long taken = parseTimeTaken(e.get("timeTaken"));
            if (taken >= 0) {
                row.put("timeTaken", taken);
            }
        }
        if (e.get("timestamp") != null && !e.get("timestamp").isNull()) {
            row.put("timestamp", e.get("timestamp").asText());
        }
        Map<String, String> trace = traceFields(req, res);
        if (trace != null) {
            row.put("traceId", trace.get("traceId"));
            row.put("spanId", trace.get("spanId"));
            row.put("parentSpanId", trace.get("parentSpanId"));
        }
        return row;
    }

    /**
     * 从请求/响应头里挖链路关联键（不区分大小写，兼容各种探针）。
     *
     * <p>为何只认头不认其它：一次 Actuator 请求记录里，两条链路信息都会出现 ——
     * 请求头是「上游传给应用的」（本记录关联的 traceId + 父 span），
     * 响应头是「应用自己生成的」（本记录自身的 span）。只要应用把链路头透传出边界
     * （网关/框架默认都透传），这些记录就能按 traceId 聚成一条链、
     * 按 spanId/parentSpanId 排成树，即使没有接 APM 引擎。</p>
     *
     * <p>取不到任何关联键时返回 null（普通流量没有链路头，不必生成占位）。</p>
     *
     * @param req 请求节点（可能为 null）
     * @param res 响应节点（可能为 null）
     * @return traceId / spanId / parentSpanId 三元组；没有链路头时返回 null
     */
    private static Map<String, String> traceFields(JsonNode req, JsonNode res) {
        Map<String, List<String>> reqHeaders = collectHeaders(req);
        Map<String, List<String>> resHeaders = collectHeaders(res);
        if (reqHeaders.isEmpty() && resHeaders.isEmpty()) {
            return null;
        }
        // 请求头：traceId + 父 span（上游）；响应头：本记录 span（应用自己生成）
        Traced reqTrace = parseTraceHeaders(reqHeaders);
        String traceId = reqTrace.traceId;
        String parentSpanId = reqTrace.spanId;
        Traced resTrace = parseTraceHeaders(resHeaders);
        String spanId = resTrace.spanId;
        if (traceId == null) {
            traceId = resTrace.traceId;
        }
        // 应用没有写响应头（span id 原样透传）：本记录 span 不明，用上游 span 充当；
        // 此时不能再声称它有父，避免 spanId == parentSpanId 自指成环。
        if (spanId == null) {
            spanId = parentSpanId;
            parentSpanId = null;
        }
        // 应用原样透传请求的 traceparent（网关/代理常见）：响应 span 与请求 span 相同，
        // 同样构成自指，必须清掉父引用——否则树状视图里会出现一条指向自己的边。
        if (ObjectUtils.nullSafeEquals(spanId, parentSpanId)) {
            parentSpanId = null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        out.put("traceId", traceId);
        out.put("spanId", spanId);
        out.put("parentSpanId", parentSpanId);
        return traceId == null && spanId == null && parentSpanId == null ? null : out;
    }

    /**
     * 从一组头里解析本视角的 trace 三元组。
     *
     * <p>对请求头调用返回「traceId + 请求 span（即本记录的父）」；
     * 对响应头调用返回「traceId + 响应 span（即本记录自身的 span）」。
     * 支持 W3C traceparent / B3 单头 / Uber(jaeger) / X-B3 多头 / X-Trace-Id。</p>
     *
     * @param headers 小写头名 → 值列表（可能为空）
     * @return 解析出的 trace
     */
    private static Traced parseTraceHeaders(Map<String, List<String>> headers) {
        Traced t = new Traced();
        if (headers.isEmpty()) {
            return t;
        }
        // W3C traceparent: version-traceid-spanid-flags（如 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01）
        String traceparent = first(headers.get("traceparent"));
        if (!isBlank(traceparent)) {
            String[] parts = traceparent.trim().split("-");
            if (parts.length >= 3 && !isBlank(parts[1]) && !isBlank(parts[2])) {
                t.traceId = parts[1];
                t.spanId = normalizeId(parts[2]);
            }
        }
        // B3 单头: {traceId}-{spanId}-{sampling}（可选第 4 段是父的父，建树用不上）
        String b3 = first(headers.get("b3"));
        if (!isBlank(b3)) {
            String[] parts = b3.trim().split("-");
            if (parts.length >= 1 && !isBlank(parts[0])) {
                if (t.traceId == null) {
                    t.traceId = parts[0];
                }
                if (t.spanId == null && parts.length >= 2 && !isBlank(parts[1])) {
                    t.spanId = normalizeId(parts[1]);
                }
            }
        }
        if (t.traceId == null) {
            t.traceId = first(headers.get("x-b3-traceid"));
        }
        if (t.spanId == null) {
            t.spanId = normalizeId(first(headers.get("x-b3-spanid")));
        }
        // Jaeger: {traceId}:{spanId}:{parentSpanId}:{flags}
        String uber = first(headers.get("uber-trace-id"));
        if (!isBlank(uber)) {
            String[] parts = uber.trim().split(":");
            if (parts.length >= 2) {
                if (t.traceId == null) {
                    t.traceId = parts[0];
                }
                if (t.spanId == null) {
                    t.spanId = normalizeId(parts[1]);
                }
            }
        }
        if (t.traceId == null) {
            t.traceId = first(headers.get("x-trace-id"));
        }
        return t;
    }

    /** 一次解析的中间结果：traceId + spanId（本视角的 span，8/16 位已规范化为 16 位） */
    private static final class Traced {
        private String traceId;
        private String spanId;
    }

    /**
     * 把请求头、响应头分别收集成小写名字 → 值列表。
     *
     * <p>Spring Boot 2.x 的 httptrace 里 headers 是「名 → 单值字符串」字典；
     * Spring Boot 3.x 的 httpexchanges 里是「名 → 值数组」。两个都认。
     * 返回的 Map 可能是空（没有 headers 字段），调用方自行判空。</p>
     *
     * @param node 请求或响应节点（可能为 null）
     * @return 小写头名 → 值列表
     */
    private static Map<String, List<String>> collectHeaders(JsonNode node) {
        Map<String, List<String>> headers = new HashMap<>();
        if (node == null) {
            return headers;
        }
        JsonNode raw = node.get("headers");
        if (raw == null || !raw.isObject()) {
            return headers;
        }
        Iterator<Map.Entry<String, JsonNode>> it = raw.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> field = it.next();
            String name = field.getKey().toLowerCase(Locale.ROOT);
            List<String> values = new ArrayList<>();
            if (field.getValue().isArray()) {
                for (JsonNode v : field.getValue()) {
                    values.add(v.isValueNode() ? v.asText() : v.toString());
                }
            } else if (field.getValue().isValueNode()) {
                values.add(field.getValue().asText());
            } else {
                continue;
            }
            List<String> merged = headers.computeIfAbsent(name, k -> new ArrayList<>());
            merged.addAll(values);
        }
        return headers;
    }

    /**
     * 返回值列表的第一个值。
     *
     * @param values 值列表（可能为 null）
     * @return 第一个值；取不到时返回 null
     */
    private static String first(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }
        String v = values.get(0);
        return isBlank(v) ? null : v.trim();
    }

    /**
     * 规范化 span id：兼容 64 位（8 个字符）与 128 位（16 个字符）两种写法，
     * 统一补零/原样成 16 位小写 —— 前端按 spanId 聚类时，两种写法都可能是某条 span。
     *
     * @param raw 原始 span id，可能为 null
     * @return 规范化后的 span id；入参为空时返回 null
     */
    private static String normalizeId(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.length() == 8) {
            // 64 位 span id 用 long 解析再补零成 16 位：8 位 hex 最大值 0xffffffff
            // 超出 int 范围，Integer.parseInt 会抛 NumberFormatException
            long value = Long.parseLong(text, 16);
            return String.format("%016x", value);
        }
        return text.length() == 16 ? text : raw.trim();
    }

    /**
     * 判断文本是否空白。
     *
     * @param text 待判断文本
     * @return 为 {@code null}、空串或仅空白字符时返回 {@code true}
     */
    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /**
     * 取子节点文本。
     *
     * @param node     父节点
     * @param key      字段名
     * @param fallback 缺失时的默认值
     * @return 文本
     */
    private static String str(JsonNode node, String key, String fallback) {
        JsonNode v = node == null ? null : node.get(key);
        return v == null || v.isNull() ? fallback : v.asText();
    }
}