package com.chua.starter.device.provider.sdk;

import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * SDK 实现的公共基类：连接信息、JSON 解析、分页列表归一、字段取值。
 *
 * <p>各平台返回的列表字段五花八门（{@code list} / {@code records} / {@code data} / 匿名数组），
 * 字段名也常见同义多种（{@code id} / {@code orgId} / {@code orgCode}）。这里统一收敛，
 * 避免每个实现各写一份容错。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public abstract class AbstractPlatformSdk implements DevicePlatformSdk {

    /** JSON 解析器 */
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    /** 连接信息（含 AK/SK、baseUrl、产品/项目等扩展参数） */
    protected final DeviceConnectInfo info;

    /**
     * 构造门面实现。
     *
     * @param info 连接信息
     */
    protected AbstractPlatformSdk(DeviceConnectInfo info) {
        this.info = info;
    }

    /**
     * 取扩展参数。
     *
     * @param key 键
     * @return 字符串值，缺失返回 null
     */
    protected String extra(String key) {
        if (info == null || info.extra() == null) {
            return null;
        }
        Object value = info.extra().get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 解析响应文本为 JSON；非法内容返回 null，由调用方给出可读错误。
     *
     * @param body 响应文本
     * @return JSON 节点，解析失败返回 null
     */
    protected JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(body);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 把平台返回的 data 节点归一成列表。
     *
     * @param data 响应 data 节点
     * @return 条目列表
     */
    protected static List<JsonNode> toList(JsonNode data) {
        if (data == null || data.isNull()) {
            return List.of();
        }
        if (data.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            // [P3C CO-循环 豁免]
            for (Iterator<JsonNode> it = data.elements(); it.hasNext(); ) {
                result.add(it.next());
            }
            return result;
        }
        if (!data.isObject()) {
            return List.of();
        }
        for (String field : List.of("list", "records", "pageList", "pageInfo", "data",
                "rows", "items", "nodes", "table", "deviceList")) {
            if (data.has(field)) {
                return toList(data.get(field));
            }
        }
        return List.of();
    }

    /**
     * 依次尝试多个字段名，返回第一个非空字符串。
     *
     * @param node   条目
     * @param fields 候选字段
     * @return 字段值，全部缺失返回 null
     */
    protected static String str(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    /**
     * 取整型字段。
     *
     * @param node   条目
     * @param fields 候选字段
     * @return 整数，缺失返回 null
     */
    protected static Integer integer(JsonNode node, String... fields) {
        String text = str(node, fields);
        if (text == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(text);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 取布尔字段（兼容 1/0、true/false、online/offline）。
     *
     * @param node   条目
     * @param fields 候选字段
     * @return 布尔值，缺失返回 null
     */
    protected static Boolean bool(JsonNode node, String... fields) {
        String text = str(node, fields);
        if (text == null) {
            return null;
        }
        String lower = text.trim().toLowerCase();
        return "1".equals(lower) || "true".equals(lower) || "online".equals(lower)
                || "on".equals(lower) || "yes".equals(lower);
    }

    /**
     * 取十进制字段。
     *
     * @param node   条目
     * @param fields 候选字段
     * @return 数值，缺失返回 null
     */
    protected static java.math.BigDecimal decimal(JsonNode node, String... fields) {
        String text = str(node, fields);
        if (text == null) {
            return null;
        }
        try {
            return new java.math.BigDecimal(text.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
