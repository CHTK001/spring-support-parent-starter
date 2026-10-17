package com.chua.starter.device.support.util;

import com.chua.starter.device.spi.model.AccessRecord;
import com.chua.starter.device.spi.model.PresetInfo;
import com.chua.starter.device.spi.model.RecordFile;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 打开api 响应 JSON 到设备领域模型的解析工具
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class JsonMappers {

    /**
     * 通用日期格式
     */
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 创建 json映射器 实例
    */
    private JsonMappers() {
    }

    /**
     * 解析 JSON 数组为预置位列表。
     *
     * @param data JSON 节点
     * @return 预置位列表
     */
    public static List<PresetInfo> parsePresets(JsonNode data) {
        if (data == null || data.isNull()) {
            return Collections.emptyList();
        }
        JsonNode arr = data.isArray() ? data : data.path("list");
        if (!arr.isArray()) {
            return Collections.emptyList();
        }
        List<PresetInfo> list = new ArrayList<>();
        for (JsonNode n : arr) {
            PresetInfo p = new PresetInfo(
                    n.path("presetId").asInt(n.path("id").asInt()),
                    n.path("name").asText(n.path("presetName").asText(""))
            );
            list.add(p);
        }
        return list;
    }

    /**
     * 解析 JSON 数组为录像文件列表。
     *
     * @param data JSON 节点
     * @return 录像列表
     */
    public static List<RecordFile> parseRecordFiles(JsonNode data) {
        if (data == null || data.isNull()) {
            return Collections.emptyList();
        }
        JsonNode arr = data.isArray() ? data : data.path("list");
        if (!arr.isArray()) {
            return Collections.emptyList();
        }
        List<RecordFile> list = new ArrayList<>();
        for (JsonNode n : arr) {
            list.add(new RecordFile(
                    n.path("fileId").asText(n.path("recordId").asText(n.path("id").asText(""))),
                    n.path("channelId").asText(n.path("channel").asText("")),
                    parseDate(n.path("startTime").asText()),
                    parseDate(n.path("endTime").asText()),
                    n.path("size").asLong()));
        }
        return list;
    }

    /**
     * 解析 JSON 数组为门禁出入记录列表。
     *
     * @param data JSON 节点
     * @return 出入记录
     */
    public static List<AccessRecord> parseAccessRecords(JsonNode data) {
        if (data == null || data.isNull()) {
            return Collections.emptyList();
        }
        JsonNode arr = data.isArray() ? data : data.path("list");
        if (!arr.isArray()) {
            return Collections.emptyList();
        }
        List<AccessRecord> list = new ArrayList<>();
        for (JsonNode n : arr) {
            list.add(new AccessRecord(
                    n.path("doorId").asText(""),
                    n.path("cardNo").asText(""),
                    n.path("userName").asText(n.path("personName").asText(n.path("name").asText(""))),
                    parseDate(n.path("time").asText(n.path("occurredAt").asText(""))),
                    n.path("eventType").asText("")));
        }
        return list;
    }

    /**
     * 解析日期
     *
     * @param s s
     * @return 解析日期的结果
     */
    private static LocalDateTime parseDate(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ignore) {
            try {
                return LocalDateTime.parse(s, DTF);
            } catch (Exception ignore2) {
                return null;
            }
        }
    }
}

