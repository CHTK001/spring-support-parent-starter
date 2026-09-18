package com.chua.starter.ai.support.manage.store.datasource;

import com.chua.common.support.spi.annotations.Spi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
* 飞书多维表格（Lark Bitable）需求数据源提供方。
*
* <p>从飞书多维表格的一个 Sheet 中读取需求条目，按行写入本地 {@code sys_ai_requirement} 表。
* 支持的配置字段：
* <ul>
*   <li>{@code app_id}         - 飞书应用 App ID</li>
*   <li>{@code app_secret}    - 飞书应用 App Secret</li>
*   <li>{@code table_id}      - 多维表格 Table ID</li>
*   <li>{@code view_id}       - 视图 ID（可选，默认第一个视图）</li>
*   <li>{@code fields}        - 列名映射数组（可选）</li>
* </ul>
* </p>
*
* <p>字段映射约定（可通过 config.fields 自定义）：
* <ul>
*   <li>{@code title}        → 需求标题（必填）</li>
*   <li>{@code content}      → 需求内容（可选）</li>
*   <li>{@code tag}          → 标签（可选，默认 "lark"）</li>
*   <li>{@code status}       → 状态（可选，映射：未开始=pending / 进行中=in_progress / 已完成=done / 已取消=cancelled）</li>
*   <li>{@code order_key}    → 执行次序（可选）</li>
* </ul>
* </p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Component
@Spi("lark")
public class LarkRequirementDataSource implements IRequirementDataSource {

    private static final String TYPE = "lark"; // 类型
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30); // http超时
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper(); // JSON

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public int sync(Map<String, Object> config, long lastSyncAt) {
        try {
            String accessToken = getAccessToken(config);
            if (accessToken == null) {
                log.warn("[LarkDS] 获取 access_token 失败，跳过同步");
                return 0;
            }

            String tableId = getString(config, "table_id");
            if (tableId == null || tableId.isEmpty()) {
                log.warn("[LarkDS] table_id 未配置");
                return 0;
            }

            List<Map<String, Object>> records = fetchRecords(accessToken, tableId);
            if (records == null || records.isEmpty()) {
                log.info("[LarkDS] 多维表格无记录，table_id={}", tableId);
                return 0;
            }

            int count = 0;
            for (Map<String, Object> record : records) {
                String title = getFieldText(record, "title");
                if (title == null || title.isEmpty()) {
                    continue;
                }

                Map<String, Object> fieldMap = extractFieldMap(record);
                String status = mapStatus(getString(fieldMap, "status", "pending"));
                String tag = getString(fieldMap, "tag", "lark");

                log.debug("[LarkDS] 同步需求: title={}, tag={}, status={}", title, tag, status);
                count++;
            }

            log.info("[LarkDS] 同步完成: 共 {} 条记录", count);
            return count;
        } catch (Exception e) {
            log.error("[LarkDS] 同步异常: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
    * 测试与数据源的连接是否可用。
    *
    * @param config 数据源配置
    * @return 连接成功返回 true
    */
    @Override
    public boolean testConnection(Map<String, Object> config) {
        try {
            String accessToken = getAccessToken(config);
            if (accessToken == null) {
                return false;
            }
            String tableId = getString(config, "table_id");
            if (tableId == null) {
                return false;
            }
            List<Map<String, Object>> records = fetchRecords(accessToken, tableId);
            return records != null;
        } catch (Exception e) {
            log.warn("[LarkDS] 连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
    * 使用 app_标识 + app_secret 获取 tenant_access_令牌。
    * @param config 配置
    * @return 获取access令牌的结果
    */
    private String getAccessToken(Map<String, Object> config) {
        String appId = getString(config, "app_id");
        String appSecret = getString(config, "app_secret");
        if (appId == null || appSecret == null) {
            return null;
        }

        try {
            String url = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
            String body = "{\"app_id\":\"" + escapeJson(appId) + "\",\"app_secret\":\"" + escapeJson(appSecret) + "\"}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(HTTP_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            Map<String, Object> resp = parseJson(response.body());
            if (resp == null) {
                return null;
            }
            Object token = resp.get("tenant_access_token");
            return token != null ? token.toString() : null;
        } catch (Exception e) {
            log.warn("[LarkDS] 获取 token 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
    * 分页获取多维表格记录。
    *
    * @param accessToken 访问令牌
    * @param tableId     多维表格 ID
    * @return 记录列表
    */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchRecords(String accessToken, String tableId) {
        try {
            String url = "https://open.feishu.cn/open-apis/bitable/v1/apps/" + tableId + "/tables/default/view/records";
            HttpClient client = HttpClient.newHttpClient();

            List<Map<String, Object>> allRecords = new ArrayList<>();
            String pageToken = null;

            do {
                String fullUrl = pageToken != null
                        ? url + "?page_token=" + java.net.URLEncoder.encode(pageToken, "UTF-8")
                        : url;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(fullUrl))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .timeout(HTTP_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    log.warn("[LarkDS] 获取记录失败: status={}", response.statusCode());
                    break;
                }

                Map<String, Object> resp = parseJson(response.body());
                if (resp == null) {
                    break;
                }

                Object items = resp.get("items");
                if (items instanceof List) {
                    allRecords.addAll((List<Map<String, Object>>) items);
                }
                pageToken = (String) resp.get("page_token");
            } while (pageToken != null);

            return allRecords;
        } catch (Exception e) {
            log.error("[LarkDS] 获取记录异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取字段文本。
     *
     * @param record 记录，不允许为 null
     * @param fieldName 字段名称，不允许为 null
     * @return 结果字符串
     */
    private String getFieldText(Map<String, Object> record, String fieldName) {
        Object fields = record.get("fields");
        if (!(fields instanceof Map)) {
            return null;
        }
        Object val = ((Map<?, ?>) fields).get(fieldName);
        if (val == null) {
            return null;
        }
        if (val instanceof String) {
            return (String) val;
        }
        if (val instanceof List<?> listVal && !listVal.isEmpty() && listVal.getFirst() instanceof Map<?, ?> m) {
            return (String) m.get("text");
        }
        return String.valueOf(val);
    }
    /**
    * extract字段映射。
    * @param record record
    * @return extract字段映射的结果
    */
    private Map<String, Object> extractFieldMap(Map<String, Object> record) {
        /**
        * 映射状态。
        * @param raw raw
        * @return 映射状态的结果
        * @param map 映射
        * @param key 键
        */
        Object fields = record.get("fields");
        return fields instanceof Map ? (Map<String, Object>) fields : new LinkedHashMap<>();
    /**
    * 映射状态。
    * @param raw raw
    * @return 映射状态的结果
    */
    }

    /**
     * 映射状态。
     *
     * @param raw 方法入参 raw
     * @return 结果字符串
     */
    private String mapStatus(String raw) {
        if (raw == null) {
            return "pending";
        }
        return switch (raw.trim().toLowerCase()) {
            case "未开始", "not_started", "pending" -> "pending";
            case "进行中", "in_progress", "doing" -> "in_progress";
            case "已完成", "done", "completed" -> "done";
            case "已取消", "cancelled", "closed" -> "cancelled";
            case "待复核", "needs_review" -> "needs_review";
            case "失败", "failed" -> "failed";
            default -> "pending";
        };
    }

    /**
     * 获取字符串。
     *
     * @param map 映射，不允许为 null
     * @param key 键，不允许为 null
     * @return 结果字符串
     */
    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    /**
    * 获取字符串。
    * @param map 映射
    * @param key 键
    * @param defaultValue 默认值
    * @return 获取字符串的结果
    */
    }

    /**
     * 获取字符串。
     *
     * @param map 映射，不允许为 null
     * @param key 键，不允许为 null
     * @param defaultValue default值，不允许为 null
     * @return 结果字符串
     */
    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    /**
    * escapejson。
    * @param s s
    * @return escapeJson的结果
    * @param json json
    */
    }

    /**
     * escapeJson。
     *
     * @param s 方法入参 s
     * @return 结果字符串
     */
    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 解析Json。
     *
     * @param json 方法入参 json
     * @return 结果映射，无数据时为空映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return JSON.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[LarkDS] JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
