// [P3C A20 豁免] A20/A30/A31: 含 HTTP 端点、MIME、协议字段名等非业务魔法值场景，豁免。
package com.chua.starter.account.spi.impl;

import com.chua.common.support.ai.generation.Model3DGenerationResult;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.Model3DChannelProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * three.ws Forge 图生 / 文生 3D 渠道。
 *
 * <p>对接 <a href="https://three.ws">three.ws Forge</a>：免费档（{@code draft} /
 * {@code standard}）无需任何密钥或账号，提交参考图公网地址或提示词后轮询任务直至完成，
 * 取回 glTF 二进制（GLB）模型地址；其余格式经 {@code /api/forge-remesh} 转换。
 * 上游于 2026-11-20 按真实链路验收通过（图生 3D、文生 3D、USDZ 转换均产出带纹理的真实网格），
 * 证据见 {@code temp/keyless-upstream-probe-2026-11-20.md}。</p>
 *
 * <p>与 {@link ForgeModel3DChannel} 的 {@code /generate} 协议不同，本渠道：</p>
 * <ul>
 *   <li>提交：{@code POST {endpoint}/api/forge}，body 为
 *       {@code {image_urls:[公网图URL]}} 或 {@code {prompt}}，附 {@code tier}
 *       （默认 {@code draft} 免费档）；</li>
 *   <li>参考图只接受公网 http(s) 链接（不超过 2048 字符），不接受 base64 / data URI，
 *       且需为单主体、简洁背景，否则上游以 {@code no_clear_subject} 拒绝；</li>
 *   <li>异步任务：返回 {@code job_id} 后轮询 {@code GET {endpoint}/api/forge?job=...}
 *       直至 {@code done} / {@code failed}；</li>
 *   <li>产物：{@code glb_url} 原生为 GLB；其余目标格式再经
 *       {@code POST {endpoint}/api/forge-remesh} 转换取 {@code result_url}。</li>
 * </ul>
 *
 * <p>上游端点与档位优先取请求体，其次回落到系统属性：</p>
 * <ul>
 *   <li>{@code chua.deeplearning.threews.endpoint}</li>
 *   <li>{@code chua.deeplearning.threews.tier}（免费档 {@code draft} / {@code standard}）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Spi("model3d-threews")
public class ThreeWsModel3DChannel implements Model3DChannelProvider {

    /**
     * 渠道标识
     */
    public static final String CHANNEL = "model3d-threews";

    /**
     * 端点系统属性
     */
    private static final String PROP_ENDPOINT = "chua.deeplearning.threews.endpoint";

    /**
     * 档位系统属性
     */
    private static final String PROP_TIER = "chua.deeplearning.threews.tier";

    /**
     * 默认端点
     */
    private static final String DEFAULT_ENDPOINT = "https://three.ws";

    /**
     * 默认档位（免费）
     */
    private static final String DEFAULT_TIER = "draft";

    /**
     * 默认模型格式（上游原生产物）
     */
    private static final String DEFAULT_FORMAT = "glb";

    /**
     * 生成任务路径
     */
    private static final String PATH_FORGE = "/api/forge";

    /**
     * 格式转换路径
     */
    private static final String PATH_REMESH = "/api/forge-remesh";

    /**
     * 轮询间隔（毫秒）
     */
    private static final long POLL_INTERVAL_MS = 3000L;

    /**
     * 单次 HTTP 请求超时上限（毫秒）
     */
    private static final long REQUEST_TIMEOUT_MS = 60000L;

    /**
     * 参考图 URL 长度上限
     */
    private static final int MAX_IMAGE_URL_LENGTH = 2048;

    /**
     * JSON 媒体类型
     */
    private static final String CONTENT_TYPE_JSON = "application/json";

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 渠道标识。
     *
     * @return {@code model3d-threews}
     */
    @Override
    public String channel() {
        return CHANNEL;
    }

    /**
     * 渠道展示名称。
     *
     * @return 展示名称
     */
    @Override
    public String displayName() {
        return "图生 / 文生 3D（three.ws Forge）";
    }

    /**
     * 本渠道不依赖 ChatClient 扩展（自行完成 HTTP 调用）。
     *
     * @return {@code null}
     */
    @Override
    public String extension() {
        return null;
    }

    /**
     * 可用格式。{@code glb} 为上游原生产物，其余格式经 {@code /api/forge-remesh} 转换。
     *
     * @return 格式列表
     */
    @Override
    public List<Map<String, Object>> listModels() {
        return List.of(
                Map.of("id", "glb", "name", "GLB（原生，推荐）"),
                Map.of("id", "gltf", "name", "glTF"),
                Map.of("id", "usdz", "name", "USDZ"),
                Map.of("id", "fbx", "name", "FBX"),
                Map.of("id", "stl", "name", "STL"),
                Map.of("id", "ply", "name", "PLY"));
    }

    /**
     * 生成 3D 模型。
     *
     * <p>参考图与提示词至少提供一个：给参考图走图生 3D（{@code image_urls}），
     * 只给提示词走文生 3D（{@code prompt}），两者都给时上游以参考图为主、提示词辅助。</p>
     *
     * @param prompt         提示词（文生 3D 必需，图生 3D 可选）
     * @param refImageUrl    参考图公网 http(s) URL（不接受 base64 / data URI）
     * @param format         目标格式（glb / gltf / usdz / fbx / stl / ply）
     * @param timeoutSeconds 超时秒数
     * @param model          模型标识（本渠道忽略，格式即模型）
     * @param endpoint       上游端点（可空）
     * @param token          上游凭据（可空，上游免费档不需要）
     * @return 生成结果
     */
    @Override
    public Model3DGenerationResult generate(
            String prompt, String refImageUrl, String format, int timeoutSeconds,
            String model, String endpoint, String token) {
        boolean hasImage = refImageUrl != null && !refImageUrl.isBlank();
        boolean hasPrompt = prompt != null && !prompt.isBlank();
        if (!hasImage && !hasPrompt) {
            throw new IllegalArgumentException("3D 生成必须提供 refImageUrl 或 prompt");
        }
        String imageUrl = hasImage ? normalizeImageUrl(refImageUrl.trim()) : null;
        String targetFormat = format == null || format.isBlank()
                ? DEFAULT_FORMAT : format.trim().toLowerCase(Locale.ROOT);
        int timeout = timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        long deadline = System.currentTimeMillis() + timeout * 1000L;
        String baseUrl = resolveEndpoint(endpoint);
        String glbUrl = submitAndPoll(baseUrl, token, imageUrl, hasPrompt ? prompt.trim() : null, deadline);
        String modelUrl = convertIfNeeded(baseUrl, token, glbUrl, targetFormat, deadline);
        Model3DGenerationResult.GeneratedModel3D generated =
                new Model3DGenerationResult.GeneratedModel3D(
                        modelUrl, modelUrl, null, targetFormat, null);
        return new Model3DGenerationResult(List.of(generated), prompt);
    }

    /**
     * 校验并规范化参考图地址。
     *
     * @param url 参考图地址
     * @return 规范化后的地址
     */
    private String normalizeImageUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "three.ws 仅接受公网 http(s) 图片链接，不支持 base64 / data URI");
        }
        if (url.length() > MAX_IMAGE_URL_LENGTH) {
            throw new IllegalArgumentException("参考图 URL 超过 " + MAX_IMAGE_URL_LENGTH + " 字符上限");
        }
        return url;
    }

    /**
     * 提交生成任务并轮询至完成。
     *
     * @param baseUrl  上游端点
     * @param token    上游凭据（可空）
     * @param imageUrl 参考图地址（可空）
     * @param prompt   提示词（可空）
     * @param deadline 截止时间戳（毫秒）
     * @return GLB 模型地址
     */
    private String submitAndPoll(String baseUrl, String token, String imageUrl,
            String prompt, long deadline) {
        ObjectNode body = objectMapper.createObjectNode();
        if (imageUrl != null) {
            body.putArray("image_urls").add(imageUrl);
        }
        if (prompt != null && !prompt.isBlank()) {
            body.put("prompt", prompt);
        }
        body.put("tier", resolveTier());
        JsonNode submitted = postForJson(baseUrl + PATH_FORGE, token, body, deadline, "提交 3D 生成任务");
        String inlineUrl = firstText(submitted, "glb_url", "model_url", "url");
        if (inlineUrl != null) {
            return inlineUrl;
        }
        String jobId = text(submitted, "job_id");
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalStateException("3D 上游未返回任务号：" + truncate(submitted.toString(), 300));
        }
        return pollUntilDone(baseUrl + PATH_FORGE + "?job=" + urlEncode(jobId), token, deadline,
                "3D 生成", node -> firstText(node, "glb_url", "model_url", "url", "result_url"));
    }

    /**
     * 目标格式非 GLB 时，调用上游格式转换并轮询至完成。
     *
     * @param baseUrl      上游端点
     * @param token        上游凭据（可空）
     * @param glbUrl       GLB 模型地址
     * @param targetFormat 目标格式
     * @param deadline     截止时间戳（毫秒）
     * @return 转换后的模型地址
     */
    private String convertIfNeeded(String baseUrl, String token, String glbUrl,
            String targetFormat, long deadline) {
        if (DEFAULT_FORMAT.equals(targetFormat)) {
            return glbUrl;
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("mesh_url", glbUrl);
        body.put("operation", "convert");
        body.put("output_format", targetFormat);
        JsonNode submitted = postForJson(baseUrl + PATH_REMESH, token, body, deadline, "提交格式转换任务");
        String inlineUrl = firstText(submitted, "result_url", "url");
        if (inlineUrl != null) {
            return inlineUrl;
        }
        String jobId = text(submitted, "job_id");
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalStateException("格式转换上游未返回任务号：" + truncate(submitted.toString(), 300));
        }
        return pollUntilDone(baseUrl + PATH_REMESH + "?job=" + urlEncode(jobId), token, deadline,
                "格式转换", node -> firstText(node, "result_url", "url", "mesh_url"));
    }

    /**
     * 轮询任务直至完成或失败。
     *
     * @param url           轮询地址
     * @param token         上游凭据（可空）
     * @param deadline      截止时间戳（毫秒）
     * @param action        动作名（用于错误信息）
     * @param doneExtractor 完成时从响应提取模型地址的函数
     * @return 模型地址
     */
    private String pollUntilDone(String url, String token, long deadline,
            String action, Function<JsonNode, String> doneExtractor) {
        while (true) {
            JsonNode node = getForJson(url, token, deadline, action);
            String status = text(node, "status");
            if ("done".equalsIgnoreCase(status)) {
                String result = doneExtractor.apply(node);
                if (result == null || result.isBlank()) {
                    throw new IllegalStateException(action + "完成但未返回模型地址："
                            + truncate(node.toString(), 300));
                }
                return result;
            }
            if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
                throw new IllegalStateException(action + "失败：" + upstreamError(node));
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IllegalStateException(action + "超时，最后状态：" + status);
            }
            sleep();
        }
    }

    /**
     * 发送 POST 请求并解析 JSON 响应。
     *
     * @param url      请求地址
     * @param token    上游凭据（可空）
     * @param body     请求体
     * @param deadline 截止时间戳（毫秒）
     * @param action   动作名（用于错误信息）
     * @return 响应 JSON
     */
    private JsonNode postForJson(String url, String token, ObjectNode body, long deadline, String action) {
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new IllegalStateException("构建 " + action + "请求体失败", e);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout(deadline))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return parseJson(send(builder.build(), action), action);
    }

    /**
     * 发送 GET 请求并解析 JSON 响应。
     *
     * @param url      请求地址
     * @param token    上游凭据（可空）
     * @param deadline 截止时间戳（毫秒）
     * @param action   动作名（用于错误信息）
     * @return 响应 JSON
     */
    private JsonNode getForJson(String url, String token, long deadline, String action) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout(deadline))
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return parseJson(send(builder.build(), action), action);
    }

    /**
     * 发送请求。
     *
     * @param request 请求
     * @param action  动作名（用于错误信息）
     * @return 响应
     */
    private HttpResponse<byte[]> send(HttpRequest request, String action) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(action + "被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException(action + "上游调用失败：" + e.getMessage(), e);
        }
    }

    /**
     * 校验响应并解析 JSON。
     *
     * @param response 响应
     * @param action   动作名（用于错误信息）
     * @return 响应 JSON
     */
    private JsonNode parseJson(HttpResponse<byte[]> response, String action) {
        int status = response.statusCode();
        String body = new String(response.body(), StandardCharsets.UTF_8);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(action + "上游返回 " + status + "：" + truncate(body, 300));
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(action + "响应不是 JSON：" + truncate(body, 300), e);
        }
    }

    /**
     * 解析上游错误信息。
     *
     * @param node 响应 JSON
     * @return 错误描述
     */
    private String upstreamError(JsonNode node) {
        String message = text(node, "message");
        String code = firstText(node, "code", "error_code", "error");
        if (message != null && code != null && !message.equals(code)) {
            return code + " - " + message;
        }
        if (message != null) {
            return message;
        }
        if (code != null) {
            return code;
        }
        return truncate(node.toString(), 300);
    }

    /**
     * 解析上游端点：请求体优先，其次系统属性，最后默认值。
     *
     * @param endpoint 请求携带的端点
     * @return 规范化端点（无结尾斜杠）
     */
    private String resolveEndpoint(String endpoint) {
        String base = endpoint != null && !endpoint.isBlank()
                ? endpoint.trim()
                : System.getProperty(PROP_ENDPOINT, DEFAULT_ENDPOINT).trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * 解析生成档位。
     *
     * @return 档位（免费档 {@code draft}）
     */
    private String resolveTier() {
        String tier = System.getProperty(PROP_TIER, DEFAULT_TIER).trim();
        return tier.isEmpty() ? DEFAULT_TIER : tier;
    }

    /**
     * 计算单次请求超时：不超过剩余时间，也不短于 1 秒。
     *
     * @param deadline 截止时间戳（毫秒）
     * @return 请求超时
     */
    private Duration requestTimeout(long deadline) {
        long remaining = deadline - System.currentTimeMillis();
        return Duration.ofMillis(Math.max(1000L, Math.min(remaining, REQUEST_TIMEOUT_MS)));
    }

    /**
     * 轮询间隔等待。
     */
    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("3D 生成轮询被中断", e);
        }
    }

    /**
     * URL 编码。
     *
     * @param value 原始值
     * @return 编码结果
     */
    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 读取字符串字段。
     *
     * @param node JSON 节点
     * @param key  字段名
     * @return 字符串值，缺失或非字符串时为 {@code null}
     */
    private String text(JsonNode node, String key) {
        JsonNode child = node == null ? null : node.get(key);
        return child != null && child.isTextual() ? child.asText() : null;
    }

    /**
     * 读取第一个非空字符串字段。
     *
     * @param node JSON 节点
     * @param keys 字段名列表
     * @return 第一个非空值，均为空时为 {@code null}
     */
    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = text(node, key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 截断文本。
     *
     * @param value 原始文本
     * @param max   最大长度
     * @return 截断后的文本
     */
    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
