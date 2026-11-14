package com.chua.starter.server.support.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Actuator {@code /actuator/httptrace}（Spring Boot 2.x）与
 * {@code /actuator/httpexchanges}（Spring Boot 3.x）响应压缩器。
 *
 * <p>为什么要单独一层：请求记录默认 100 条，每条带完整的请求/响应 headers，
 * 真实应用的响应动辄几百 KB，而远端执行通道会在 64KB 处截断输出。原样交给前端，
 * {@code JSON.parse} 必然失败——和 loggers 是同一个坑。</p>
 *
 * <p>策略：流式解析，只收数组元素（每条只留 method / uri / status / timeTaken /
 * timestamp，丢弃占体积大头的 headers）。解析到截断处抛 {@link com.fasterxml.jackson.core.JsonParseException}
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
     * 把一条请求记录压成页面要展示的字段。
     *
     * @param e 记录节点
     * @return 行
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
        if (e.get("timeTaken") != null && e.get("timeTaken").isNumber()) {
            row.put("timeTaken", e.get("timeTaken").asLong());
        }
        if (e.get("timestamp") != null && !e.get("timestamp").isNull()) {
            row.put("timestamp", e.get("timestamp").asText());
        }
        return row;
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