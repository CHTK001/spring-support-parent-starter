// [P3C A20 豁免] A20/A30/A31: 含 HTTP 端点、MIME、协议字段名等非业务魔法值场景，豁免。
package com.chua.starter.account.spi.impl;

import com.chua.common.support.ai.generation.Model3DGenerationResult;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.Model3DChannelProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 Forge 风格图生 3D 渠道。
 *
 * <p>不绑定具体厂商：按项目既有的 {@code chua.deeplearning.core} 协议约定，
 * 向 {@code {endpoint}/generate} POST 一张参考图，取回 3D 模型字节或模型地址。
 * 上游端点与凭据优先取请求体，其次回落到系统属性：</p>
 * <ul>
 *   <li>{@code chua.deeplearning.core.endpoint}</li>
 *   <li>{@code chua.deeplearning.core.api-key}</li>
 * </ul>
 *
 * <p>这样既能把自建的 TRELLIS / TripoSR 服务接进来，也能对接任何实现了同一
 * 协议的第三方 3D 服务，而本模块不新增对具体深度学习 starter 的编译依赖。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Spi("model3d-forge")
public class ForgeModel3DChannel implements Model3DChannelProvider {

    /**
     * 渠道标识
     */
    public static final String CHANNEL = "model3d-forge";

    /**
     * 端点系统属性
     */
    private static final String PROP_ENDPOINT = "chua.deeplearning.core.endpoint";

    /**
     * 凭据系统属性
     */
    private static final String PROP_API_KEY = "chua.deeplearning.core.api-key";

    /**
     * 默认端点（与既有 3D 模块保持一致）
     */
    private static final String DEFAULT_ENDPOINT = "https://api.trellis.ai/v1";

    /**
     * 默认模型格式
     */
    private static final String DEFAULT_FORMAT = "glb";

    /**
     * 单张参考图大小上限（8MB）
     */
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    /**
     * JSON 媒体类型
     */
    private static final String CONTENT_TYPE_JSON = "application/json";

    /**
     * 模型数据 URI 的 MIME
     */
    private static final String MODEL_MIME = "model/gltf-binary";

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
     * @return {@code model3d-forge}
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
        return "图生 3D（通用 Forge 接口）";
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
     * 可用格式。
     *
     * @return 格式列表
     */
    @Override
    public List<Map<String, Object>> listModels() {
        return List.of(
                Map.of("id", "glb", "name", "GLB（推荐）"),
                Map.of("id", "gltf", "name", "glTF"),
                Map.of("id", "obj", "name", "OBJ"),
                Map.of("id", "usdz", "name", "USDZ"));
    }

    /**
     * 生成 3D 模型。
     *
     * @param prompt         提示词（可空）
     * @param refImageUrl    参考图 URL 或 data URI
     * @param format         目标格式
     * @param timeoutSeconds 超时秒数
     * @param model          模型标识（本渠道忽略）
     * @param endpoint       上游端点（可空）
     * @param token          上游凭据（可空）
     * @return 生成结果
     */
    @Override
    public Model3DGenerationResult generate(
            String prompt, String refImageUrl, String format, int timeoutSeconds,
            String model, String endpoint, String token) {
        if (refImageUrl == null || refImageUrl.isBlank()) {
            throw new IllegalArgumentException("图生 3D 必须提供 refImageUrl");
        }
        String targetFormat = format == null || format.isBlank() ? DEFAULT_FORMAT : format.trim();
        int timeout = timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        byte[] image = fetchImage(refImageUrl, timeout);
        String mimeType = resolveMimeType(refImageUrl);
        String baseUrl = resolveEndpoint(endpoint);
        String apiKey = resolveToken(token);
        byte[] body = buildBody(image, targetFormat, mimeType);
        HttpResponse<byte[]> response = post(baseUrl, apiKey, body, timeout);
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("3D 上游返回 " + response.statusCode() + "："
                    + truncate(new String(response.body(), StandardCharsets.UTF_8), 300));
        }
        String modelUrl = extractModelUrl(response);
        Model3DGenerationResult.GeneratedModel3D generated =
                new Model3DGenerationResult.GeneratedModel3D(
                        modelUrl, modelUrl, null, targetFormat, null);
        return new Model3DGenerationResult(List.of(generated), prompt);
    }

    /**
     * 读取参考图字节。
     *
     * @param refImageUrl 参考图 URL 或 data URI
     * @param timeout     超时秒数
     * @return 图片字节
     */
    private byte[] fetchImage(String refImageUrl, int timeout) {
        String url = refImageUrl.trim();
        if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma < 0) {
                throw new IllegalArgumentException("非法的 data URI 参考图");
            }
            byte[] bytes = Base64.getDecoder().decode(url.substring(comma + 1));
            checkImageSize(bytes);
            return bytes;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("下载参考图失败，HTTP " + response.statusCode());
            }
            checkImageSize(response.body());
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("下载参考图被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("下载参考图失败：" + e.getMessage(), e);
        }
    }

    /**
     * 校验参考图大小。
     *
     * @param image 图片字节
     */
    private void checkImageSize(byte[] image) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("参考图为空");
        }
        if (image.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("参考图超过 8MB 上限");
        }
    }

    /**
     * 构建上游请求体。
     *
     * @param image     图片字节
     * @param format    目标格式
     * @param mimeType  图片 MIME
     * @return 请求体 JSON
     */
    private byte[] buildBody(byte[] image, String format, String mimeType) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("image", Base64.getEncoder().encodeToString(image));
            node.put("format", format);
            node.put("mime_type", mimeType);
            node.put("quality", "standard");
            return objectMapper.writeValueAsBytes(node);
        } catch (Exception e) {
            throw new IllegalStateException("构建 3D 请求体失败", e);
        }
    }

    /**
     * 调用上游生成接口。
     *
     * @param baseUrl 端点
     * @param apiKey  凭据
     * @param body    请求体
     * @param timeout 超时秒数
     * @return 响应
     */
    private HttpResponse<byte[]> post(String baseUrl, String apiKey, byte[] body, int timeout) {
        String url = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/generate";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }
        try {
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("3D 生成被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("3D 上游调用失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从响应中提取模型地址。
     *
     * <p>优先取 JSON 里的 {@code url} / {@code model_url}；只有内联字节时转成 data URI，
     * 保证接口仍能返回可下载的模型。</p>
     *
     * @param response 上游响应
     * @return 模型地址
     */
    private String extractModelUrl(HttpResponse<byte[]> response) {
        byte[] body = response.body();
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (contentType.contains("json")) {
            try {
                JsonNode root = objectMapper.readTree(body);
                JsonNode urlNode = root.get("url");
                if (urlNode != null && urlNode.isTextual()) {
                    return urlNode.asText();
                }
                JsonNode modelUrlNode = root.get("model_url");
                if (modelUrlNode != null && modelUrlNode.isTextual()) {
                    return modelUrlNode.asText();
                }
                byte[] data = decodeInline(root);
                if (data != null) {
                    return dataUri(data);
                }
            } catch (Exception e) {
                throw new IllegalStateException("解析 3D 上游响应失败：" + e.getMessage(), e);
            }
            throw new IllegalStateException("3D 上游响应缺少模型地址");
        }
        return dataUri(body == null ? new byte[0] : body);
    }

    /**
     * 解码响应里的内联模型数据。
     *
     * @param root 响应 JSON
     * @return 模型字节；不存在返回 {@code null}
     */
    private byte[] decodeInline(JsonNode root) {
        for (String field : new String[]{"data", "result", "model"}) {
            JsonNode node = root.get(field);
            if (node != null && node.isTextual()) {
                try {
                    return Base64.getDecoder().decode(node.asText());
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 把模型字节包成 data URI。
     *
     * @param data 模型字节
     * @return data URI
     */
    private String dataUri(byte[] data) {
        return "data:" + MODEL_MIME + ";base64," + Base64.getEncoder().encodeToString(data);
    }

    /**
     * 解析端点。
     *
     * @param endpoint 请求体端点
     * @return 最终端点
     */
    private String resolveEndpoint(String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            return endpoint.trim();
        }
        String property = System.getProperty(PROP_ENDPOINT);
        return property == null || property.isBlank() ? DEFAULT_ENDPOINT : property.trim();
    }

    /**
     * 解析凭据。
     *
     * @param token 请求体凭据
     * @return 最终凭据；未配置返回 {@code null}
     */
    private String resolveToken(String token) {
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        String property = System.getProperty(PROP_API_KEY);
        return property == null || property.isBlank() ? null : property.trim();
    }

    /**
     * 推断参考图 MIME。
     *
     * @param refImageUrl 参考图 URL 或 data URI
     * @return MIME
     */
    private String resolveMimeType(String refImageUrl) {
        String lower = refImageUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:image/png") || lower.contains(".png")) {
            return "image/png";
        }
        if (lower.startsWith("data:image/webp") || lower.contains(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    /**
     * 截断文本。
     *
     * @param text 文本
     * @param max  最大长度
     * @return 截断结果
     */
    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
