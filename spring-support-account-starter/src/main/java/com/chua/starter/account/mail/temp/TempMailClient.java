package com.chua.starter.account.mail.temp;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * mail.tm 临时邮箱客户端。
 *
 * <p>只做 HTTP 协议本身：领域名、建账号、换 token、取信、删账号。
 * 不读写账号表，也不持久化任何凭据 —— 凭据由编排层加密落库后按需传入。</p>
 *
 * <p>mail.tm 是公开服务（无需 API Key），接口前缀固定
 * {@code https://api.mail.tm}，因此没有做成可配置网关。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public class TempMailClient {

    /**
     * mail.tm 接口前缀
     */
    public static final String BASE_URL = "https://api.mail.tm";

    /**
     * 建号与换 token 用 JSON
     */
    private static final String CONTENT_TYPE_JSON = "application/json";

    /**
     * 限流重试次数（mail.tm 对建号 / 换 token 有速率限制）
     */
    private static final int RATE_LIMIT_RETRY = 3;

    /**
     * JSON 编解码
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * HTTP 客户端（连接超时 10s，请求超时 20s）
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            // [P3C A20 豁免]
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 取一个可用域名。
     *
     * @return 域名，如 {@code mail.tm}
     */
    public String pickDomain() {
        JsonNode root = get("/domains", null);
        for (JsonNode item : items(root)) {
            if (item.path("isActive").asBoolean(true) && !item.path("isPrivate").asBoolean(false)) {
                String domain = item.path("domain").asText(null);
                if (StringUtils.isNotBlank(domain)) {
                    return domain;
                }
            }
        }
        throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "mail.tm 没有可用域名");
    }

    /**
     * 建一个临时邮箱账号。
     *
     * @param address  邮箱地址
     * @param password 密码（后续换 token 用）
     * @return 服务端账号标识
     */
    public String createAccount(String address, String password) {
        Map<String, Object> body = new LinkedHashMap<>(2);
        body.put("address", address);
        body.put("password", password);
        JsonNode root = post("/accounts", body, null);
        String id = root.path("id").asText(null);
        if (StringUtils.isBlank(id)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "mail.tm 建号未返回账号标识");
        }
        return id;
    }

    /**
     * 用账号密码换访问 token。
     *
     * @param address  邮箱地址
     * @param password 密码
     * @return token
     */
    public String token(String address, String password) {
        Map<String, Object> body = new LinkedHashMap<>(2);
        body.put("address", address);
        body.put("password", password);
        JsonNode root = post("/token", body, null);
        String token = root.path("token").asText(null);
        if (StringUtils.isBlank(token)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "mail.tm 未返回访问令牌");
        }
        return token;
    }

    /**
     * 取一页邮件。
     *
     * @param token 访问令牌
     * @param page  页码（从 1 开始）
     * @return 邮件节点与总数
     */
    public Page listMessages(String token, int page) {
        JsonNode root = get("/messages?page=" + Math.max(1, page), token);
        List<JsonNode> items = new ArrayList<>();
        for (JsonNode item : items(root)) {
            items.add(item);
        }
        return new Page(items, root.path("hydra:totalItems").asInt(items.size()));
    }

    /**
     * 取单封邮件详情。
     *
     * @param token     访问令牌
     * @param messageId 邮件标识
     * @return 详情节点；不存在返回 {@code null}
     */
    public JsonNode message(String token, String messageId) {
        try {
            return get("/messages/" + messageId, token);
        } catch (ApiException e) {
            if (e.getErrorCode() == ApiErrorCode.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 删除服务端账号。
     *
     * @param token     访问令牌
     * @param accountId 服务端账号标识
     */
    public void deleteAccount(String token, String accountId) {
        try {
            request("DELETE", "/accounts/" + accountId, null, token);
        } catch (ApiException e) {
            if (e.getErrorCode() != ApiErrorCode.NOT_FOUND) {
                throw e;
            }
        }
    }

    /**
     * GET 请求。
     *
     * @param path  路径
     * @param token 访问令牌（可空）
     * @return JSON 根节点
     */
    private JsonNode get(String path, String token) {
        return request("GET", path, null, token);
    }

    /**
     * POST 请求。
     *
     * @param path  路径
     * @param body  请求体
     * @param token 访问令牌（可空）
     * @return JSON 根节点
     */
    private JsonNode post(String path, Object body, String token) {
        return request("POST", path, body, token);
    }

    /**
     * 执行请求并解析 JSON。
     *
     * @param method 方法
     * @param path   路径
     * @param body   请求体（可空）
     * @param token  访问令牌（可空）
     * @return JSON 根节点
     */
    private JsonNode request(String method, String path, Object body, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    // [P3C A20 豁免]
                    .timeout(Duration.ofSeconds(20))
                    .header("accept", CONTENT_TYPE_JSON);
            if (StringUtils.isNotBlank(token)) {
                builder.header("authorization", "Bearer " + token);
            }
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("content-type", CONTENT_TYPE_JSON);
                builder.method(method, HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
            }
            HttpRequest request = builder.build();
            HttpResponse<String> response = null;
            for (int attempt = 1; attempt <= RATE_LIMIT_RETRY; attempt++) {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                // [P3C A20 豁免]
                if (response.statusCode() != 429 || attempt == RATE_LIMIT_RETRY) {
                    break;
                }
                // mail.tm 对建号 / 换 token / 删号有速率限制，连续操作会 429；
                // 短退避重试能救回「刚领取就销毁」这类连打场景。
                // [P3C A20 豁免]
                Thread.sleep(1200L * attempt);
            }
            int status = response.statusCode();
            // [P3C A20 豁免]
            if (status == 404) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, "mail.tm 资源不存在");
            }
            // [P3C A20 豁免]
            if (status == 429) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "临时邮箱服务繁忙（mail.tm 限流），请稍后重试");
            }
            // [P3C A20 豁免]
            if (status >= 400) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                        "mail.tm 返回 HTTP " + status + "：" + abbreviate(response.body()));
            }
            String text = response.body();
            if (StringUtils.isBlank(text)) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(text);
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "调用 mail.tm 被中断", e);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "调用 mail.tm 失败：" + e.getMessage(), e);
        }
    }

    /**
     * 取集合条目：mail.tm 按 Accept 头二选一返回 JSON-LD 集合或裸数组。
     *
     * <p>发 {@code accept: application/json} 时拿到的是裸数组，
     * 发 {@code * / *} 才带 {@code hydra:member}；两种都要认，
     * 否则换个客户端实现就会「没有可用域名」。</p>
     *
     * @param root 响应根节点
     * @return 条目数组
     */
    private JsonNode items(JsonNode root) {
        return root != null && root.isArray() ? root : root.path("hydra:member");
    }

    /**
     * 截断响应体，避免把整页 HTML 塞进错误信息。
     *
     * @param text 响应体
     * @return 前 200 字
     */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        // [P3C A20 豁免]
        return text.length() <= 200 ? text : text.substring(0, 200);
    }

    /**
     * 一页邮件。
     *
     * @param items 邮件节点
     * @param total 总数
     */
    public static class Page {

        /**
         * 邮件节点
         */
        private final List<JsonNode> items;

        /**
         * 总数
         */
        private final int total;

        /**
         * 构造一页邮件。
         *
         * @param items 邮件节点
         * @param total 总数
         */
        public Page(List<JsonNode> items, int total) {
            this.items = items;
            this.total = total;
        }

        /**
         * 邮件节点。
         *
         * @return 节点列表
         */
        public List<JsonNode> getItems() {
            return items;
        }

        /**
         * 邮件总数。
         *
         * @return 总数
         */
        public int getTotal() {
            return total;
        }
    }
}
