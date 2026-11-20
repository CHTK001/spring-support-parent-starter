package com.chua.starter.account.mail.temp;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
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
 * GuerrillaMail 临时邮箱客户端。
 *
 * <p>只做 HTTP 协议本身：建匿名会话、把会话绑定到自定义地址前缀、取信。
 * 不读写账号表，也不持久化任何凭据。</p>
 *
 * <p><b>会话模型</b>：GuerrillaMail 的邮箱没有密码，地址前缀即命名空间；
 * {@code sid_token} 过期后，用一个新会话对同一前缀调用 {@code set_email_user}
 * 即可恢复同一邮箱，因此落库侧无需保存会话令牌。注意：服务端邮件仅保留约
 * 1 小时，域名随会话随机分配，接口不支持指定域名。</p>
 *
 * @author CH
 * @since 4.0.0.63
 */
public class GuerrillaMailClient {

    /**
     * GuerrillaMail AJAX 接口地址
     */
    public static final String BASE_URL = "https://api.guerrillamail.com/ajax.php";

    /**
     * 浏览器 UA（服务端对无 UA 的请求偶发拒绝）
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
     * 建一个匿名会话。
     *
     * @return 会话（sid_token + 当前地址）
     */
    public Session createSession() {
        JsonNode root = call(Map.of("f", "get_email_address"));
        String sidToken = root.path("sid_token").asText(null);
        if (StringUtils.isBlank(sidToken)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "guerrillamail 未返回会话标识");
        }
        return new Session(sidToken, root.path("email_addr").asText(null));
    }

    /**
     * 把会话绑定到自定义地址前缀（同一前缀可在新会话上恢复同一邮箱）。
     *
     * @param sidToken 会话标识
     * @param user     地址前缀（不含 @domain）
     * @return 绑定后的完整邮箱地址
     */
    public String bindUser(String sidToken, String user) {
        Map<String, String> params = new LinkedHashMap<>(4);
        params.put("f", "set_email_user");
        params.put("email_user", user);
        params.put("sid_token", sidToken);
        JsonNode root = call(params);
        String address = root.path("email_addr").asText(null);
        if (StringUtils.isBlank(address)) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "guerrillamail 绑定地址失败");
        }
        return address;
    }

    /**
     * 取当前会话邮箱的邮件列表（列表即含正文，服务端仅保留约 1 小时）。
     *
     * @param sidToken 会话标识
     * @return 邮件节点列表
     */
    public List<JsonNode> checkEmail(String sidToken) {
        Map<String, String> params = new LinkedHashMap<>(4);
        params.put("f", "check_email");
        params.put("sid_token", sidToken);
        params.put("seq", "0");
        JsonNode root = call(params);
        List<JsonNode> items = new ArrayList<>();
        for (JsonNode item : root.path("list")) {
            items.add(item);
        }
        return items;
    }

    /**
     * 执行 GET 请求并解析 JSON。
     *
     * @param params 查询参数
     * @return JSON 根节点
     */
    private JsonNode call(Map<String, String> params) {
        StringBuilder url = new StringBuilder(BASE_URL);
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            url.append(first ? '?' : '&').append(entry.getKey()).append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    // [P3C A20 豁免]
                    .timeout(Duration.ofSeconds(20))
                    .header("accept", "application/json")
                    .header("user-agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 404) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, "guerrillamail 资源不存在");
            }
            if (status >= 400) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                        "guerrillamail 返回 HTTP " + status + "：" + abbreviate(response.body()));
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
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "调用 guerrillamail 被中断", e);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "调用 guerrillamail 失败：" + e.getMessage(), e);
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
     * GuerrillaMail 会话。
     */
    public static class Session {

        /**
         * 会话标识
         */
        private final String sidToken;

        /**
         * 当前邮箱地址
         */
        private final String emailAddr;

        /**
         * 构造会话。
         *
         * @param sidToken  会话标识
         * @param emailAddr 当前邮箱地址
         */
        public Session(String sidToken, String emailAddr) {
            this.sidToken = sidToken;
            this.emailAddr = emailAddr;
        }

        /**
         * 会话标识。
         *
         * @return sid_token
         */
        public String getSidToken() {
            return sidToken;
        }

        /**
         * 当前邮箱地址。
         *
         * @return 邮箱地址
         */
        public String getEmailAddr() {
            return emailAddr;
        }
    }
}
