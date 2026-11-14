package com.chua.starter.server.support.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Actuator {@code /actuator/loggers} 响应压缩器。
 *
 * <p>为什么要单独一层：真实应用的 loggers 响应动辄几百 KB（几千个 logger），
 * 而远端执行通道会在 64KB 处截断输出。原样交给前端，{@code JSON.parse} 必然失败，
 * 页面只能显示「没有读到日志列表」——看起来像端点没暴露，其实是数据太大。</p>
 *
 * <p>策略：能整段解析就整段解析（拿 levels 与配置级别）；解析不了说明被截断，
 * 就从前缀里按 {@code "名字":{...}} 抠条目，并如实把 {@code truncated} 置为 true，
 * 让页面提示「可用完整名字精确查询」。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class ActuatorLoggers {

    /**
     * 默认返回给前端的最大条数
     */
    public static final int DEFAULT_MAX = 2000;

    /**
     * Actuator 支持的级别（响应里没带 levels 时用）
     */
    private static final List<String> DEFAULT_LEVELS =
            List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF");

    /**
     * 从被截断的 JSON 前缀里抠条目：`"名字":{"configuredLevel":...,"effectiveLevel":...}`
     */
    private static final Pattern BLOCK =
            Pattern.compile("\"((?:[^\"\\\\]|\\\\.)+)\"\\s*:\\s*\\{([^{}]*)\\}");

    /**
     * JSON 解析器：只用于本类的两段解析，不共享全局配置
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private ActuatorLoggers() {
    }

    /**
     * 压缩 loggers 响应。
     *
     * @param body 原始响应体（可能被截断）
     * @return levels / loggers（name + configuredLevel + effectiveLevel）/ truncated
     */
    public static Map<String, Object> compact(String body) {
        return compact(body, DEFAULT_MAX);
    }

    /**
     * 压缩 loggers 响应，并限制返回条数。
     *
     * @param body 原始响应体（可能被截断）
     * @param max  最大条数
     * @return levels / loggers / truncated
     */
    public static Map<String, Object> compact(String body, int max) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, String>> rows = new ArrayList<>();
        out.put("levels", DEFAULT_LEVELS);
        out.put("loggers", rows);
        out.put("truncated", false);
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            return out;
        }
        boolean truncated = text.contains("[已截断]") || !text.endsWith("}");
        try {
            Map<String, Object> parsed = JSON.readValue(text,
                    new TypeReference<Map<String, Object>>() { });
            Object levels = parsed.get("levels");
            if (levels instanceof List<?> list && !list.isEmpty()) {
                out.put("levels", list);
            }
            Object loggers = parsed.get("loggers");
            if (loggers instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (rows.size() >= max) {
                        truncated = true;
                        break;
                    }
                    rows.add(row(entry.getKey(), entry.getValue()));
                }
            }
            out.put("truncated", truncated);
            return out;
        } catch (Exception ignored) {
            // 截断的 JSON 解析不了：走前缀抠取
        }
        Matcher blocks = BLOCK.matcher(text);
        while (blocks.find() && rows.size() < max) {
            String inner = blocks.group(2);
            // 只收 logger 节点：它们一定带 effectiveLevel（levels 数组等不含）
            if (!inner.contains("effectiveLevel")) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", blocks.group(1));
            row.put("configuredLevel", level(inner, "configuredLevel"));
            row.put("effectiveLevel", level(inner, "effectiveLevel"));
            rows.add(row);
        }
        out.put("truncated", true);
        return out;
    }

    /**
     * 把一个 logger 节点转成页面要的三列。
     *
     * @param name  日志名
     * @param value 节点值
     * @return 行
     */
    private static Map<String, String> row(Object name, Object value) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("name", String.valueOf(name));
        if (value instanceof Map<?, ?> map) {
            row.put("configuredLevel", map.get("configuredLevel") == null
                    ? "" : String.valueOf(map.get("configuredLevel")));
            row.put("effectiveLevel", map.get("effectiveLevel") == null
                    ? "" : String.valueOf(map.get("effectiveLevel")));
        }
        return row;
    }

    /**
     * 从节点片段里取某个级别字段。
     *
     * @param inner 节点内部文本
     * @param key   configuredLevel / effectiveLevel
     * @return 级别；null 或缺失时为空串
     */
    private static String level(String inner, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(?:null|\"([^\"]*)\")").matcher(inner);
        return m.find() && m.group(1) != null ? m.group(1) : "";
    }
}
