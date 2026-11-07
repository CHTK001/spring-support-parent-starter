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
            // Moss 有自有的 MossImageClient（utils-support-mosi-starter），
            // 不能用 openai 兜底：OpenAiImageClient 未覆写 negativePrompt/seed/steps，
            // 且默认 readTimeout 只有 30s，而出图官方建议 ≥900s。
            Map.entry("moss", "mosi"),
            Map.entry("mosi", "mosi"),
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
     * <p>与 {@link #IMAGE_SPI} 同构：同一个供应商，图像与视频各自归一。
     * 绝大多数 OpenAI 兼容渠道两种媒体的端点形状一致，统一归到 {@code openai}；
     * 形状特殊的（如智谱自有视频接口）才单独映射。库中 platform 存成
     * {@code openai} 的聚合渠道（如 Agnes）也归到 openai，由
     * {@code OpenAiVideoClient} 先打标准路径、404 时降级到独立的
     * {@code AgnesVideoClient} —— 与它对图像靠 {@code baseUrl} 直通来适配
     * 聚合站的做法保持一致。降级判据只取 404：401/403 是密钥问题、429 是限流、
     * 5xx 是服务端故障，必须原样暴露，不能被掩盖成「路径不对」。
     *
     * <p>{@code alibaba}（万相）已占位，待 {@code AlibabaVideoClient} 落地后即可生效。
     * 在此之前前端按 {@link #supportsVideo(String)} 的结果隐藏视频入口。</p>
     */
    private static final Map<String, String> VIDEO_SPI = Map.ofEntries(
            Map.entry("zhipu", "zhipu"),
            Map.entry("bigmodel", "zhipu"),
            Map.entry("qwen", "alibaba"),
            Map.entry("aliyun", "alibaba"),
            Map.entry("alibaba", "alibaba"),
            Map.entry("dashscope", "alibaba"),
            Map.entry("bailian", "alibaba"),
            Map.entry("openai", "openai"),
            Map.entry("agnes", "openai"),
            Map.entry("openrouter", "openai"),
            Map.entry("siliconflow", "openai")
    );

    /**
     * 已登记图像 SPI 名集合（用于前端能力提示与自检）
     */
    private static final Set<String> KNOWN_IMAGE_SPI = Set.of(
            "alibaba", "openai", "azure", "microsoft", "google", "gemini",
            "zhipu", "baidu", "tencent-hunyuan", "modelscope", "zai", "amazon",
            "onnx", "pytorch", "safetensors", "mage", "ollama",
            // Moss 自有实现（MossImageClient，utils-support-mosi-starter）
            "mosi"
    );

    /**
     * 已登记视频 SPI 名集合
     *
     * <p>{@code openai} 对应 {@code OpenAiVideoClient}（轮询端点标准的渠道），
     * {@code agnes} 对应 {@code AgnesVideoClient}（轮询挪到根路径 {@code /agnesapi}），
     * {@code zhipu} 对应 {@code ZhipuVideoClient}。
     * {@code alibaba} 尚未落地实现，故<b>刻意不列入</b>——列进去前端会放出视频入口，
     * 用户提交后才失败。</p>
     */
    private static final Set<String> KNOWN_VIDEO_SPI = Set.of("openai", "agnes", "zhipu");

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
