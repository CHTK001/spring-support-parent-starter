// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.spi.impl;

import com.chua.common.support.ai.generation.ImageGenerationResult;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.spi.ImageChannelProvider;
import com.chua.starter.common.support.text.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pollinations 文生图渠道 —— 免登录、零依赖的兜底出图渠道。
 *
 * <p>不走 ChatClient SPI，直接拼 Pollinations 的免鉴权出图地址：
 * {@code https://image.pollinations.ai/prompt/{prompt}?width&height&model&seed&nologo}。
 * 服务端「按需出图」，浏览器首次加载该 URL 时才开始渲染（通常 10~40 秒），
 * 因此后端只负责拼 URL，不阻塞等待图片字节，前端用图片占位骨架承载等待过程。</p>
 *
 * <p>链路不依赖任何密钥 / 本机登录态，作为「进入优先」的默认出图渠道（优先级 100）；
 * 若网络不可达或上游限流，渠道卡片仍可见，只是出图会失败并给出明确错误。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi("image-pollinations")
public class PollinationsImageChannel implements ImageChannelProvider {

    /**
     * 渠道标识
     */
    public static final String CHANNEL = "image-pollinations";

    /**
     * 出图网关
     */
    private static final String BASE_URL = "https://image.pollinations.ai/prompt/";

    /**
     * 默认出图模型
     */
    private static final String DEFAULT_MODEL = "flux";

    /**
     * 图生图（img2img）编辑接口：按「参考图 + 提示词」重绘，必须携带 API Key
     * （Bearer 或 {@code ?key=}）。匿名链路会像素级忽略参考图，属假成功，禁用。
     */
    private static final String EDIT_URL = "https://gen.pollinations.ai/v1/images/edits";

    /**
     * 默认图生图模型
     */
    private static final String DEFAULT_IMG2IMG_MODEL = "kontext";

    /**
     * 支持参考图的模型集合：上游 edits 接口只认这些模型；
     * flux / turbo 是纯文生图模型，传参考图会被上游明确拒绝。
     */
    private static final Set<String> IMG2IMG_MODELS = Set.of(
            "kontext", "seedream-4.0", "gemini-2.5-flash-image", "flux.2-klein-4b");

    /**
     * 图生图编辑接口超时（秒）：带参考图重绘比文生图更慢，留足余量。
     */
    private static final long EDIT_TIMEOUT_SECONDS = 180L;

    /**
     * edits 接口 HTTP 客户端
     */
    private static final HttpClient EDIT_HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 宽高比 → 像素尺寸（与上游「按 ratio 收敛到常规分辨率」的习惯一致）
     */
    private static final Map<String, int[]> RATIO_SIZES = Map.of(
            "1:1", new int[]{1024, 1024},
            "16:9", new int[]{1280, 720},
            "9:16", new int[]{720, 1280},
            "4:3", new int[]{1024, 768},
            "3:4", new int[]{768, 1024},
            "3:2", new int[]{1216, 832},
            "2:3", new int[]{832, 1216});

    /**
     * 渠道标识。
     *
     * @return {@code image-pollinations}
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
        return "Pollinations 文生图";
    }

    /**
     * 本渠道不依赖 ChatClient 扩展（自带 URL 拼装与出图语义）。
     *
     * @return {@code null}
     */
    @Override
    public String extension() {
        return null;
    }

    /**
     * 优先级：免登录、无需任何配置，作为默认出图渠道置顶。
     *
     * @return {@code 100}
     */
    @Override
    public int priority() {
        return 100;
    }

    /**
     * 可用出图模型。
     *
     * @return 模型列表
     */
    @Override
    public List<Map<String, Object>> listModels() {
        List<Map<String, Object>> models = new ArrayList<>(6);
        Map<String, Object> flux = new LinkedHashMap<>(3);
        flux.put("id", "flux");
        flux.put("name", "Flux（默认）");
        models.add(flux);
        Map<String, Object> turbo = new LinkedHashMap<>(3);
        turbo.put("id", "turbo");
        turbo.put("name", "Turbo（更快）");
        models.add(turbo);
        Map<String, Object> kontext = new LinkedHashMap<>(3);
        kontext.put("id", "kontext");
        kontext.put("name", "Kontext（图生图）");
        models.add(kontext);
        Map<String, Object> seedream = new LinkedHashMap<>(3);
        seedream.put("id", "seedream-4.0");
        seedream.put("name", "Seedream 4.0（图生图）");
        models.add(seedream);
        Map<String, Object> geminiImage = new LinkedHashMap<>(3);
        geminiImage.put("id", "gemini-2.5-flash-image");
        geminiImage.put("name", "Gemini 2.5 Flash Image（图生图）");
        models.add(geminiImage);
        Map<String, Object> klein = new LinkedHashMap<>(3);
        klein.put("id", "flux.2-klein-4b");
        klein.put("name", "Flux.2 Klein 4B（图生图）");
        models.add(klein);
        return models;
    }

    /**
     * 拼装出图地址（每张图片一个独立 URL，带不同 seed 避免出图完全一致）。
     *
     * @param prompt 提示词
     * @param ratio  宽高比
     * @param n      张数
     * @param width  宽度
     * @param height 高度
     * @param model  模型（flux / turbo）
     * @return 生成结果（图片项为可直接访问的 URL）
     */
    @Override
    public ImageGenerationResult generate(String prompt, String ratio, int n, int width, int height, String model) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        int[] size = resolveSize(ratio, width, height);
        String effectiveModel = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        int count = Math.max(1, Math.min(n, MAX_IMAGES_PER_CALL));
        List<ImageGenerationResult.GeneratedImage> images = new ArrayList<>(count);
        String encodedPrompt = encode(prompt.trim());
        long seed = Math.abs(System.nanoTime() % 1_000_000_000L);
        for (int i = 0; i < count; i++) {
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append(encodedPrompt)
                    .append("?width=").append(size[0])
                    .append("&height=").append(size[1])
                    .append("&model=").append(encode(effectiveModel))
                    .append("&seed=").append(seed + i)
                    .append("&nologo=true");
            String link = url.toString();
            images.add(new ImageGenerationResult.GeneratedImage(link, link, link, link, size[0], size[1], "jpeg"));
        }
        return new ImageGenerationResult(images, prompt);
    }

    /**
     * 渠道具备图生图能力：由 {@link #supportsReferenceImage(String)} 按模型细分。
     *
     * @return {@code true}
     */
    @Override
    public boolean supportsReferenceImage() {
        return true;
    }

    /**
     * 仅 edits 目录里的编辑模型支持参考图；flux / turbo 等纯文生图模型不支持。
     *
     * @param model 模型标识
     * @return true 表示该模型支持参考图
     */
    @Override
    public boolean supportsReferenceImage(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        for (String candidate : IMG2IMG_MODELS) {
            if (candidate.equalsIgnoreCase(model.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为图生图模型声明参考图能力，前端据此展示参考图入口。
     *
     * @param model 模型标识
     * @return generate 能力节点；非图生图模型返回 {@code null}
     */
    @Override
    public Map<String, Object> generateCapability(String model) {
        if (!supportsReferenceImage(model)) {
            return null;
        }
        Map<String, Object> generate = new LinkedHashMap<>(2);
        generate.put("referenceImage", Boolean.TRUE);
        return generate;
    }

    /**
     * 生成图片（图生图重载）：带参考图时调用上游 edits 接口重绘。
     *
     * <p>img2img 必须携带 API Key（匿名链路会像素级忽略参考图，属假成功）；
     * Key 为空时直接拒绝，绝不降级成文生图。</p>
     *
     * @param prompt         提示词
     * @param ratio          宽高比
     * @param n              张数
     * @param width          宽度
     * @param height         高度
     * @param model          出图模型
     * @param referenceImage 参考图字节
     * @param apiKey         渠道 API Key
     * @return 生成结果
     */
    @Override
    public ImageGenerationResult generate(String prompt, String ratio, int n, int width, int height, String model,
            byte[] referenceImage, String apiKey) {
        if (referenceImage == null || referenceImage.length == 0) {
            return generate(prompt, ratio, n, width, height, model);
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        String effectiveModel = model == null || model.isBlank() ? DEFAULT_IMG2IMG_MODEL : model.trim();
        if (!supportsReferenceImage(effectiveModel)) {
            throw new IllegalStateException("Pollinations 模型 " + effectiveModel
                    + " 不支持参考图（图生图），可选：" + String.join(" / ", IMG2IMG_MODELS));
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Pollinations 图生图需要 API Key："
                    + "请在渠道管理中为「Pollinations 文生图」渠道配置密钥后重试");
        }
        int[] size = resolveSize(ratio, width, height);
        int count = Math.max(1, Math.min(n, MAX_IMAGES_PER_CALL));
        List<ImageGenerationResult.GeneratedImage> images = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String link = editImage(prompt.trim(), effectiveModel, referenceImage, apiKey.trim());
            images.add(new ImageGenerationResult.GeneratedImage(
                    effectiveModel, link, link, link, size[0], size[1], "png"));
        }
        return new ImageGenerationResult(images, prompt);
    }

    /**
     * 调用上游 edits 接口做图生图（multipart/form-data，携带参考图与 API Key）。
     *
     * @param prompt         提示词
     * @param model          图生图模型
     * @param referenceImage 参考图字节
     * @param apiKey         渠道 API Key
     * @return 图片地址：公网 URL 或 base64 内联 data URI（由 download 统一解码）
     */
    private static String editImage(String prompt, String model, byte[] referenceImage, String apiKey) {
        String boundary = "----pollinations" + Long.toHexString(System.nanoTime());
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        appendField(body, boundary, "model", model);
        appendField(body, boundary, "prompt", prompt);
        body.writeBytes(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(("Content-Disposition: form-data; name=\"image\"; filename=\"reference.png\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(referenceImage);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(EDIT_URL))
                .timeout(Duration.ofSeconds(EDIT_TIMEOUT_SECONDS))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        try {
            HttpResponse<byte[]> response = EDIT_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Pollinations 图生图失败 HTTP " + response.statusCode()
                        + "：" + snippet(response.body()));
            }
            return extractImage(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pollinations 图生图被中断", e);
        } catch (IOException e) {
            throw new IllegalStateException("Pollinations 图生图请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 edits 响应提取图片地址，三重兜底：
     * {@code data[0].url} → {@code data[0].b64_json} → 裸图片字节转 data URI。
     *
     * @param body 响应字节
     * @return 图片地址（URL 或 data URI）
     */
    private static String extractImage(byte[] body) {
        if (body == null || body.length == 0) {
            throw new IllegalStateException("Pollinations 图生图返回空响应");
        }
        if (!isJsonObject(body)) {
            // 兜底三：上游直接回图片字节
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(body);
        }
        Map<String, Object> root = Json.getJsonObject(new String(body, StandardCharsets.UTF_8));
        Object data = root == null ? null : root.get("data");
        if (data instanceof List && !((List<?>) data).isEmpty()) {
            Object first = ((List<?>) data).get(0);
            if (first instanceof Map) {
                Object url = ((Map<?, ?>) first).get("url");
                if (url != null && !String.valueOf(url).isBlank()) {
                    // 兜底一：公网 URL，交给 download() 取回字节
                    return String.valueOf(url).trim();
                }
                Object b64 = ((Map<?, ?>) first).get("b64_json");
                if (b64 != null && !String.valueOf(b64).isBlank()) {
                    // 兜底二：base64 内联结果
                    return "data:image/png;base64," + String.valueOf(b64).trim();
                }
            }
        }
        throw new IllegalStateException("Pollinations 图生图响应缺少图片数据：" + snippet(body));
    }

    /**
     * 追加 multipart 文本字段。
     *
     * @param body     请求体缓冲
     * @param boundary 分隔边界
     * @param name     字段名
     * @param value    字段值
     */
    private static void appendField(ByteArrayOutputStream body, String boundary, String name, String value) {
        body.writeBytes(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 响应是否为 JSON 对象（首个非空白字节是否为 {@code {}）。
     *
     * @param body 响应字节
     * @return true 表示 JSON
     */
    private static boolean isJsonObject(byte[] body) {
        for (byte b : body) {
            if (b == ' ' || b == '\t' || b == '\r' || b == '\n') {
                continue;
            }
            return b == '{';
        }
        return false;
    }

    /**
     * 截取响应片段用于错误信息（避免把整段 base64 刷进日志与前端）。
     *
     * @param body 响应字节
     * @return 片段文本
     */
    private static String snippet(byte[] body) {
        if (body == null || body.length == 0) {
            return "(空响应)";
        }
        String text = new String(body, 0, Math.min(body.length, 200), StandardCharsets.UTF_8);
        return body.length > 200 ? text + "...(" + body.length + " 字节)" : text;
    }

    /**
     * 解析输出尺寸。
     *
     * @param ratio  宽高比
     * @param width  宽度
     * @param height 高度
     * @return {宽, 高}
     */
    private static int[] resolveSize(String ratio, int width, int height) {
        if (width > 0 && height > 0) {
            return new int[]{width, height};
        }
        if (ratio != null && RATIO_SIZES.containsKey(ratio.trim())) {
            return RATIO_SIZES.get(ratio.trim());
        }
        return RATIO_SIZES.get("1:1");
    }

    /**
     * URL 编码（把 {@code +} 还原成 {@code %20}，避免提示词里的空格变成加号）。
     *
     * @param value 原始文本
     * @return 编码后的文本
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}