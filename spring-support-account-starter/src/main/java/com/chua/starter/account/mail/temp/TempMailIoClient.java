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
import java.util.List;

/**
 * temp-mail.io 临时邮箱客户端（网页端 v3 内部接口，无需 API Key）。
 *
 * <p>官方 v1 API（{@code api.temp-mail.io}）需要付费 API Key；这里对接其网页
 * 使用的 v3 内部接口（{@code api.internal.temp-mail.io/api/v3}）：
 * 建号必须用表单提交（JSON 会被 415 拒绝），取信免鉴权，删号需带建号返回的
 * Bearer token。</p>
 *
 * <p>只做 HTTP 协议本身：领号、取信、删号。不读写账号表，也不持久化任何凭据 ——
 * 建号返回的 token 由编排层加密落库后按需传入。</p>
 *
 * @author CH
 * @since 4.0.0.63
 */
public class TempMailIoClient {

    /**
     * temp-mail.io v3 内部接口前缀
     */
    public static final String BASE_URL = "https://api.internal.temp-mail.io/api/v3";

    /**
     * 建号用表单类型（该接口不接受 JSON，JSON 会返回 415）
     */
    private static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";

    /**
     * 浏览器 UA（服务端按 UA 区分网页端流量）
     */
    private static final String USER_AGENT = "Mozilla/5.0";

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
     * 领一个临时邮箱（地址与 token 由服务端生成）。
     *
     * @return 邮箱地址 + token
     */
    public NewEmail newEmail() {
        // [P3C A20 豁免]
        String form = "min_name_length=10&max_name_length=10";
        JsonNode root = request("POST", "/email/new", form, null);
        String email = root.path("email").asText(null);
        if (StringUtils.isBlank(email)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "temp-mail.io 建号未返回邮箱地址");
        }
        return new NewEmail(email, root.path("token").asText(null));
    }

    /**
     * 取邮箱的全部邮件（新的在前；列表即含 text/html 正文）。
     *
     * @param email 邮箱地址
     * @return 邮件节点列表
     */
    public List<JsonNode> messages(String email) {
        JsonNode root = request("GET", "/email/" + email + "/messages", null, null);
        List<JsonNode> items = new ArrayList<>();
        if (root != null && root.isArray()) {
            for (JsonNode item : root) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * 删除邮箱及其全部邮件（带建号时返回的 token）。
     *
     * @param email 邮箱地址
     * @param token 建号返回的访问令牌
     */
    public void deleteEmail(String email, String token) {
        request("DELETE", "/email/" + email, null, token);
    }

    /**
     * 执行请求并解析 JSON。
     *
     * @param method 请求方法
     * @param path   路径（含查询串）
     * @param form   表单体（可空；非空时按表单类型提交）
     * @param token  访问令牌（可空）
     * @return JSON 根节点；空响应返回缺失节点
     */
    private JsonNode request(String method, String path, String form, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    // [P3C A20 豁免]
                    .timeout(Duration.ofSeconds(20))
                    .header("accept", "application/json")
                    .header("user-agent", USER_AGENT);
            if (StringUtils.isNotBlank(token)) {
                builder.header("authorization", "Bearer " + token);
            }
            if (form == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("content-type", CONTENT_TYPE_FORM);
                builder.method(method, HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8));
            }
            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 404) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, "temp-mail.io 资源不存在");
            }
            if (status >= 400) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                        "temp-mail.io 返回 HTTP " + status + "：" + abbreviate(response.body()));
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
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "调用 temp-mail.io 被中断", e);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "调用 temp-mail.io 失败：" + e.getMessage(), e);
        }
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
     * 新领的临时邮箱。
     */
    public static class NewEmail {

        /**
         * 邮箱地址
         */
        private final String email;

        /**
         * 访问令牌（删号时使用）
         */
        private final String token;

        /**
         * 构造新邮箱。
         *
         * @param email 邮箱地址
         * @param token 访问令牌
         */
        public NewEmail(String email, String token) {
            this.email = email;
            this.token = token;
        }

        /**
         * 邮箱地址。
         *
         * @return 地址
         */
        public String getEmail() {
            return email;
        }

        /**
         * 访问令牌。
         *
         * @return token
         */
        public String getToken() {
            return token;
        }
    }
}
