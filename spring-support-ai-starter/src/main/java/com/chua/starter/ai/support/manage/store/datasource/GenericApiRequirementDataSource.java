package com.chua.starter.ai.support.manage.store.datasource;

import com.chua.common.support.spi.annotations.Spi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
* 通用 REST API 需求数据源提供方。
*
* <p>支持从任意 REST API 拉取需求数据（Jira、Notion、GitHub Issues、TAPD 等）。
* 通过配置字段指定 API 端点、认证方式、字段映射规则。</p>
*
* <h3>配置字段（JSON 存入 sys_ai_data_source_config）：</h3>
* <pre>
* {
*   "api_url":          "https://jira.example.com/rest/api/2/search",
*   "api_method":       "GET",
*   "api_auth_type":    "basic|bearer|token|none",
*   "api_auth_value":   "user:password 或 token 值",
*   "api_query_params": {"jql": "project=DEMO AND status != Done"},
*   "api_headers":      {"X-Custom-Header": "value"},
*   "field_mapping": {
*     "title":       "summary",
*     "content":     "description",
*     "tag":         "labels",
*     "status":      "status.name",
*     "remote_id":   "key"
*   },
*   "status_map": {
*     "To Do": "pending",
*     "In Progress": "in_progress",
*     "Done": "done"
*   }
* }
* </pre>
*
* @since 4.0.0.42
* @author CH
* @param config 配置
* @param lastSyncAt 最后一个同步at
* @return 同步的结果
 */
@Slf4j
@Component
@Spi("generic")
public class GenericApiRequirementDataSource implements IRequirementDataSource {

    private static final String TYPE = "generic"; // 类型
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30); // http超时
    /**
    * 类型。
    * @return 类型的结果
    * @param config 配置
    * @param lastSyncAt 最后一个同步at
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
/**
* 类型。
* @return 类型的结果
 */

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int sync(Map<String, Object> config, long lastSyncAt) {
        try {
            String apiUrl = getString(config, "api_url");
            if (apiUrl == null || apiUrl.isEmpty()) {
                log.warn("[GenericDS] api_url 未配置");
                return 0;
            }

            String method = getString(config, "api_method", "GET");
            Map<String, Object> queryParams = (Map<String, Object>) config.get("api_query_params");
            Map<String, String> headers = (Map<String, String>) config.get("api_headers");
            String authType = getString(config, "api_auth_type", "none");
            String authValue = getString(config, "api_auth_value");

 // 构建请求 URL（拼接 查询 参数）
            String fullUrl = apiUrl;
            if (queryParams != null && !queryParams.isEmpty()) {
                StringBuilder sb = new StringBuilder(fullUrl);
                sb.append(fullUrl.contains("?") ? "&" : "?");
                queryParams.forEach((k, v) -> {
                    String encoded = URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8);
                    sb.append(k).append("=").append(encoded).append("&");
                });
                fullUrl = sb.toString().endsWith("&") ? sb.substring(0, sb.length() - 1) : sb.toString();
            }

            // 构建请求
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .timeout(HTTP_TIMEOUT);

            // 认证头
            if ("basic".equals(authType) && authValue != null) {
                String encoded = java.util.Base64.getEncoder().encodeToString(authValue.getBytes(StandardCharsets.UTF_8));
                reqBuilder.header("Authorization", "Basic " + encoded);
            } else if ("bearer".equals(authType) && authValue != null) {
                reqBuilder.header("Authorization", "Bearer " + authValue);
            } else if ("token".equals(authType) && authValue != null) {
                reqBuilder.header("X-API-Token", authValue);
            }

 // 自定义 头部
            if (headers != null) {
                headers.forEach(reqBuilder::header);
            }

            if ("POST".equalsIgnoreCase(method)) {
                reqBuilder.POST(HttpRequest.BodyPublishers.ofString("{}"));
            } else {
                reqBuilder.GET();
            }

            HttpRequest request = reqBuilder.build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[GenericDS] API 请求失败: status={}, url={}", response.statusCode(), fullUrl);
                return 0;
            }

            // 解析响应并提取 records
            Map<String, Object> resp = parseJson(response.body());
            if (resp == null) {
                log.warn("[GenericDS] 响应非 JSON: {}", response.body().substring(0, Math.min(200, response.body().length())));
                return 0;
            }

 // 兼容两种格式：直接 array 或 { 数据: [...] } 或 { 结果: [...] }
            List<Map<String, Object>> records = extractRecords(resp);
            if (records == null || records.isEmpty()) {
                log.info("[GenericDS] API 返回 0 条记录: {}", fullUrl);
                return 0;
            }

            // 字段映射
            Map<String, String> fieldMapping = (Map<String, String>) config.get("field_mapping");
            Map<String, String> statusMap = (Map<String, String>) config.get("status_map");
            String defaultTag = getString(config, "default_tag", "generic");

            int count = 0;
            for (Map<String, Object> raw : records) {
                Map<String, Object> mapped = mapFields(raw, fieldMapping);
                String title = getString(mapped, "title");
                if (title == null || title.isEmpty()) {
                    continue;
                }

                String status = getString(mapped, "status");
                if (statusMap != null && statusMap.containsKey(status)) {
                    status = statusMap.get(status);
                } else if (status == null) {
                    status = "pending";
                }

                String tag = getString(mapped, "tag", defaultTag);
                log.debug("[GenericDS] 同步需求: title={}, tag={}, status={}", title, tag, status);
                count++;
            }

            log.info("[GenericDS] 同步完成: {} 条记录, url={}", count, fullUrl);
            return count;
        } catch (Exception e) {
            log.error("[GenericDS] 同步异常: {}", e.getMessage(), e);
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
            String apiUrl = getString(config, "api_url");
            if (apiUrl == null) {
                return false;
            }
 // 发一次 HEAD 或 获取，验证连通性
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.warn("[GenericDS] 连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
    * 从 API 响应中提取记录列表，兼容多种常见格式。
    * @param resp resp
    * @return extractRecords的结果
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRecords(Map<String, Object> resp) {
        // 常见格式1: { "data": [...] }
        Object data = resp.get("data");
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        // 常见格式2: { "results": [...] }
        Object results = resp.get("results");
        if (results instanceof List) {
            return (List<Map<String, Object>>) results;
        }
        // 常见格式3: { "issues": [...] } (Jira)
        Object issues = resp.get("issues");
        if (issues instanceof List) {
            return (List<Map<String, Object>>) issues;
        }
        // 常见格式4: { "objects": [...] } (Notion)
        Object objects = resp.get("objects");
        if (objects instanceof List) {
            return (List<Map<String, Object>>) objects;
        }
        // 常见格式5: 顶层就是 array
        if (resp.get("") instanceof List) {
            return (List<Map<String, Object>>) resp.get("");
        }
        // 尝试把整个 resp 当作单条记录
        if (!resp.isEmpty()) {
            return Collections.singletonList(resp);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    /**
    * 映射字段。
    * @param raw raw
    * @param fieldMapping 字段mapping
    * @return 映射字段的结果
     */
    private Map<String, Object> mapFields(Map<String, Object> raw, Map<String, String> fieldMapping) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        if (fieldMapping == null) {
            // 无映射配置：原样传递
            return raw;
        }
        for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
            String targetKey = entry.getKey();
            String sourcePath = entry.getValue();
            Object val = getByDotPath(raw, sourcePath);
            if (val != null) {
                mapped.put(targetKey, val instanceof Map ? toJSONString(val) : String.valueOf(val));
            }
        }
        return mapped;
    }

    /**
    * 通过点分路径从嵌套 映射 中取值：如 "状态.名称" → 映射.获取("状态").获取("名称")
    *
    * @param map 映射
    * @param path 点分路径
    * @return 路径处的值（不存在返回 null）
     */
    @SuppressWarnings("unchecked")
    private Object getByDotPath(Map<String, Object> map, String path) {
        if (map == null || path == null) {
            return null;
        }
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        /**
        * 解析json。
        * @param json json
        * @return 解析json的结果
        * @param obj obj
         */
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return JSON.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[GenericDS] JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String toJSONString(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
