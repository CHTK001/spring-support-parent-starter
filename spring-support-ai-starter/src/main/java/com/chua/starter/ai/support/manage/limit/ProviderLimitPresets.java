package com.chua.starter.ai.support.manage.limit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 官方限制文档预设表。
 *
 * <p><b>收录口径</b>：只收「官方文档明确写了」的内容。数值来自文档的标
 * {@code verified=true}；文档只说明限制机制（按账户等级、按响应头动态下发）
 * 而没有固定数值的，标 {@code verified=false}，精确数值交给
 * {@link HeaderProviderLimitProvider} 从上游响应头观测，不在这里编造。</p>
 *
 * <p>收录范围是「机制 + 少数已核对的官方口径」，不是逐家服务商硬编码 ——
 * 200+ 提供商里的绝大多数由响应头观测（{@link HeaderProviderLimitProvider}）
 * 与手动配置（{@link ManualProviderLimitProvider}）覆盖。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class ProviderLimitPresets {

    private ProviderLimitPresets() {
    }

    /**
     * 按平台 / 地址匹配预设。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 预设；无匹配返回 null
     */
    public static ProviderLimit find(String platform, String baseUrl) {
        String p = platform == null ? "" : platform.toLowerCase(Locale.ROOT).trim();
        String host = hostOf(baseUrl);
        if (p.contains("deepseek") || host.contains("deepseek.com")) {
            return deepseek();
        }
        if (p.contains("moss") || host.contains("moss.cn")) {
            return moss();
        }
        if (p.contains("openai") && host.contains("openai.com")) {
            return openAi();
        }
        if ((p.contains("anthropic") || p.contains("claude")) && host.contains("anthropic.com")) {
            return anthropic();
        }
        if ((p.contains("gemini") || p.contains("google")) && host.contains("googleapis.com")) {
            return gemini();
        }
        return null;
    }

    /**
     * DeepSeek：账号级并发，官方按模型给出固定值。
     *
     * @return 限制快照
     */
    private static ProviderLimit deepseek() {
        Map<String, Integer> perModel = new LinkedHashMap<>(2);
        perModel.put("deepseek-flash", 2500);
        perModel.put("deepseek-v4-pro", 500);
        return ProviderLimit.builder()
                .source("deepseek")
                .sourceUrl("https://api-docs.deepseek.com/quick_start/rate_limit")
                .verified(true)
                .modelConcurrency(perModel)
                .notes("账号级并发：同一账号下所有 API Key 合并计算，请求从发出到响应结束算一个并发；"
                        + "超出返回 HTTP 429。官方未公布固定 RPM/TPM。")
                .build();
    }

    /**
     * Moss：官方未公布固定并发/RPM，计费口径按模态区分（计费页已核对）。
     *
     * @return 限制快照
     */
    private static ProviderLimit moss() {
        return ProviderLimit.builder()
                .source("moss")
                .sourceUrl("https://platform.moss.cn/docs/errors-and-limits/limits")
                .verified(true)
                .billingBasis("语音合成 / 音色设计按输入字符；转写按输入音视频时长；"
                        + "生图按张（按总像素分档）；提示词增强按次加收")
                .currency("CNY")
                .notes("公开 API 无固定并发/RPM 数值：请求限制由网关、文件服务、模型服务与计费系统共同决定，"
                        + "实际限制以服务端错误码为准。文本模型 Nex 当前限时免费（计费来源：platform.moss.cn/app/pricing）。")
                .build();
    }

    /**
     * OpenAI：按 usage tier 分级，具体数值随账户等级变化。
     *
     * @return 限制快照
     */
    private static ProviderLimit openAi() {
        return ProviderLimit.builder()
                .source("openai")
                .sourceUrl("https://platform.openai.com/docs/guides/rate-limits")
                .verified(false)
                .billingBasis("按 token（输入 / 输出分别计价）")
                .currency("USD")
                .notes("按账户 usage tier 分级限制 RPM / TPM，数值随等级提升；"
                        + "网关在响应头返回 x-ratelimit-*（limit / remaining / reset）。本次未逐档核对固定数值。")
                .build();
    }

    /**
     * Anthropic：按 usage tier 分级。
     *
     * @return 限制快照
     */
    private static ProviderLimit anthropic() {
        return ProviderLimit.builder()
                .source("anthropic")
                .sourceUrl("https://docs.anthropic.com/en/api/rate-limits")
                .verified(false)
                .billingBasis("按 token（输入 / 输出分别计价）")
                .currency("USD")
                .notes("按 usage tier 分级限制 RPM / ITPM / OTPM；响应头返回 anthropic-ratelimit-*。"
                        + "本次未逐档核对固定数值。")
                .build();
    }

    /**
     * Google Gemini：按项目 / 账户等级设置配额。
     *
     * @return 限制快照
     */
    private static ProviderLimit gemini() {
        return ProviderLimit.builder()
                .source("gemini")
                .sourceUrl("https://ai.google.dev/gemini-api/docs/rate-limits")
                .verified(false)
                .billingBasis("按 token（部分模型按请求）")
                .currency("USD")
                .notes("按项目 / 账户等级设置 RPM / TPM / RPD 配额，数值随等级与模型变化。"
                        + "本次未逐档核对固定数值。")
                .build();
    }

    /**
     * 提取 URL 主机名（小写，失败返回空串）。
     *
     * @param url 地址
     * @return 主机名
     */
    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }
}
