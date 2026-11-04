package com.chua.starter.channel.spi.impl;

import com.chua.common.support.ai.generation.ImageGenerationResult;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.channel.spi.ImageChannelProvider;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        List<Map<String, Object>> models = new ArrayList<>(2);
        Map<String, Object> flux = new LinkedHashMap<>(3);
        flux.put("id", "flux");
        flux.put("name", "Flux（默认）");
        models.add(flux);
        Map<String, Object> turbo = new LinkedHashMap<>(3);
        turbo.put("id", "turbo");
        turbo.put("name", "Turbo（更快）");
        models.add(turbo);
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
