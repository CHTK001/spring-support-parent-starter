package com.chua.starter.ai.support.generate;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 生成能力（图像 / 视频）的 SPI 路由表。
 *
 * <p>问题背景：{@code sys_ai_provider.platform} 存的是「业务侧供应商标识」
 * （如 {@code qwen}、{@code tencent}、{@code baidu-wenxin}），而
 * {@code ImageClient} / {@code VideoClient} 的 SPI 注册名是「实现侧厂商名」
 * （如 {@code alibaba}、{@code tencent-hunyuan}、{@code baidu}）。两者命名体系不一致，
 * 直接拿 platform 去 {@code ServiceProvider} 取实现会取不到。</p>
 *
 * <p>本类把业务标识归一到 SPI 名，未登记的标识原样透传（支持用户自建实现直接以
 * platform 名注册 SPI 的场景）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class GenerateSpiRouter {

    /**
     * 业务供应商标识 → 图像生成 SPI 名
     *
     * <p>除各厂商自有实现外，凡协议为 OpenAI 兼容（{@code protocol=openai}）的
     * 供应商标识一律归到 {@code openai}——{@code OpenAiImageClient} 走的是
     * {@code POST {baseUrl}/v1/images/generations} 标准接口，聚合站与代理网关
     * （agnes / openrouter / siliconflow 等）同样实现了该端点，因此可复用。
     * 名单与前端 {@code model.constants.ts} 的 {@code PROVIDER_TEMPLATES} 保持一致，
     * 新增供应商时两边同步补。</p>
     */
    private static final Map<String, String> IMAGE_SPI = Map.ofEntries(
            // 厂商自有实现
            Map.entry("qwen", "alibaba"),
            Map.entry("aliyun", "alibaba"),
            Map.entry("alibaba", "alibaba"),
            Map.entry("dashscope", "alibaba"),
            Map.entry("bailian", "alibaba"),
            Map.entry("tencent", "tencent-hunyuan"),
            Map.entry("hunyuan", "tencent-hunyuan"),
            Map.entry("tencent-hunyuan", "tencent-hunyuan"),
            Map.entry("baidu", "baidu"),
            Map.entry("baidu-wenxin", "baidu"),
            Map.entry("wenxin", "baidu"),
            Map.entry("qianfan", "baidu"),
            Map.entry("google", "google"),
            Map.entry("gemini", "gemini"),
            Map.entry("zhipu", "zhipu"),
            Map.entry("bigmodel", "zhipu"),
            Map.entry("modelscope", "modelscope"),
            Map.entry("zai", "zai"),
            Map.entry("amazon", "amazon"),
            Map.entry("aws-bedrock", "amazon"),
            Map.entry("bedrock", "amazon"),
            Map.entry("ollama", "ollama"),
            // OpenAI 兼容协议（标准 /v1/images/generations）
            Map.entry("openai", "openai"),
            Map.entry("openai-response", "openai"),
            Map.entry("azure", "azure"),
            Map.entry("microsoft", "microsoft"),
            Map.entry("agnes", "openai"),
            Map.entry("openrouter", "openai"),
            Map.entry("commandcode", "openai"),
            Map.entry("opencode", "openai"),
            Map.entry("siliconflow", "openai"),
            Map.entry("deepseek", "openai"),
            Map.entry("moonshot", "openai"),
            Map.entry("kimi", "openai"),
            Map.entry("minimax", "openai"),
            Map.entry("baichuan", "openai"),
            Map.entry("stepfun", "openai"),
            Map.entry("xai", "openai"),
            Map.entry("mistral", "openai"),
            Map.entry("groq", "openai"),
            Map.entry("together", "openai"),
            Map.entry("cerebras", "openai"),
            Map.entry("cohere", "openai"),
            Map.entry("perplexity", "openai"),
            Map.entry("huggingface", "openai"),
            Map.entry("nvidia", "openai"),
            Map.entry("ai302", "openai"),
            Map.entry("302.ai", "openai"),
            Map.entry("bai", "openai"),
            Map.entry("longcat", "openai"),
            Map.entry("huiqing", "openai"),
            Map.entry("xqapi", "openai"),
            Map.entry("custom", "openai")
    );

    /**
     * 业务供应商标识 → 视频生成 SPI 名。
     *
     * <p>当前 SPI 侧仅 {@code zhipu} 落地了 {@code VideoClient}；阿里（万相）
     * 已归一到 {@code alibaba} 占位，待 {@code AlibabaVideoClient} 落地后即可生效，
     * 在此之前前端按 {@link #supportsVideo(String)} 的结果隐藏视频入口。</p>
     */
    private static final Map<String, String> VIDEO_SPI = Map.ofEntries(
            Map.entry("zhipu", "zhipu"),
            Map.entry("bigmodel", "zhipu"),
            Map.entry("qwen", "alibaba"),
            Map.entry("aliyun", "alibaba"),
            Map.entry("alibaba", "alibaba"),
            Map.entry("dashscope", "alibaba"),
            Map.entry("bailian", "alibaba")
    );

    /**
     * 已登记图像 SPI 名集合（用于前端能力提示与自检）
     */
    private static final Set<String> KNOWN_IMAGE_SPI = Set.of(
            "alibaba", "openai", "azure", "microsoft", "google", "gemini",
            "zhipu", "baidu", "tencent-hunyuan", "modelscope", "zai", "amazon",
            "onnx", "pytorch", "safetensors", "mage", "ollama"
    );

    /**
     * 已登记视频 SPI 名集合
     */
    private static final Set<String> KNOWN_VIDEO_SPI = Set.of("zhipu");

    /**
     * 工具类不允许实例化
     */
    private GenerateSpiRouter() {
    }

    /**
     * 归一化供应商标识。
     *
     * @param platform 业务供应商标识，可为空
     * @return 小写去空后的标识
     */
    private static String normalize(String platform) {
        return platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 解析图像生成 SPI 名。
     *
     * @param platform 业务供应商标识，可为空
     * @return SPI 名；未登记时返回归一化后的原标识（允许自建实现同名注册）
     */
    public static String imageSpi(String platform) {
        String key = normalize(platform);
        if (key.isEmpty()) {
            return "";
        }
        return IMAGE_SPI.getOrDefault(key, key);
    }

    /**
     * 解析视频生成 SPI 名。
     *
     * @param platform 业务供应商标识，可为空
     * @return SPI 名；未登记时返回归一化后的原标识
     */
    public static String videoSpi(String platform) {
        String key = normalize(platform);
        if (key.isEmpty()) {
            return "";
        }
        return VIDEO_SPI.getOrDefault(key, key);
    }

    /**
     * 该供应商标识是否已有图像生成实现。
     *
     * @param platform 业务供应商标识
     * @return true 表示 SPI 侧存在对应实现
     */
    public static boolean supportsImage(String platform) {
        return KNOWN_IMAGE_SPI.contains(imageSpi(platform));
    }

    /**
     * 该供应商标识是否已有视频生成实现。
     *
     * @param platform 业务供应商标识
     * @return true 表示 SPI 侧存在对应实现
     */
    public static boolean supportsVideo(String platform) {
        return KNOWN_VIDEO_SPI.contains(videoSpi(platform));
    }
}
