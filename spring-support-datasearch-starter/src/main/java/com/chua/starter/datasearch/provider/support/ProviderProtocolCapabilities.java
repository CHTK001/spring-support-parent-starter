package com.chua.starter.datasearch.provider.support;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 协议级能力判定：把「流式 / 异步」从公开目录拿不到的维度，按<b>可辩护的口径</b>补齐。
 *
 * <p>口径（2026-11 由用户拍板，A′）：</p>
 * <ul>
 *   <li><b>流式</b>：按接入协议判。OpenAI 兼容（{@code stream:true} 的 SSE）、
 *       Anthropic Messages（{@code stream}）、Gemini（{@code streamGenerateContent}）
 *       三种协议的流式都是协议本身规定的必备行为，命中即 {@code true}，
 *       来源标 {@link #SOURCE_PROTOCOL}。协议未知则返回 {@code null}（未收录），
 *       不猜。</li>
 *   <li><b>异步（批量）</b>：<b>不按协议判</b>。中转 / 聚合服务商即使自称 OpenAI 兼容，
 *       多数也没有实现批量端点，按协议推断会把不支持的标成支持。只有官方端点
 *       实测存在（收到鉴权类响应即说明端点存在）的厂商才给 {@code true}，
 *       来源标 {@link #SOURCE_PROBE}，其余一律 {@code null}。</li>
 * </ul>
 *
 * <p>已实测的批量端点：OpenAI {@code POST /v1/batches}、Anthropic
 * {@code POST /v1/messages/batches}、Gemini {@code POST :batchGenerateContent}
 * —— 三者用非法 Key 请求分别得到 401 / 401 / 400(API key not valid)，
 * 500 / 404 / 连接拒绝都不算支持。</p>
 *
 * <p>调用方必须区分 {@code Boolean.FALSE}（明确不支持）与 {@code null}（未收录）：
 * 本类只产出 {@code true} 与 {@code null}，从不写 {@code false}。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
public final class ProviderProtocolCapabilities {

    /**
     * 流式结论来自协议规定
     */
    public static final String SOURCE_PROTOCOL = "protocol";

    /**
     * 异步结论来自端点实测
     */
    public static final String SOURCE_PROBE = "probe";

    /**
     * 协议本身规定必须支持流式的三种
     */
    private static final Set<String> STREAMING_PROTOCOLS = Set.of("openai", "anthropic", "gemini");

    /**
     * 官方批量端点已实测存在的厂商 → 该厂商使用的协议
     */
    private static final Map<String, String> BATCH_VENDORS = Map.of(
            "openai", "openai",
            "anthropic", "anthropic",
            "google", "gemini"
    );

    private ProviderProtocolCapabilities() {
    }

    /**
     * 是否支持流式（按协议判定）。
     *
     * @param protocol 接入协议（openai / anthropic / gemini ...）
     * @return {@code true} 支持；协议未知或非上述三种时返回 {@code null}（未收录）
     */
    public static Boolean supportsStreaming(String protocol) {
        String key = normalize(protocol);
        return key != null && STREAMING_PROTOCOLS.contains(key) ? Boolean.TRUE : null;
    }

    /**
     * 流式结论的来源标识。
     *
     * @param protocol 接入协议
     * @return {@link #SOURCE_PROTOCOL}；协议不足以判定时返回 {@code null}
     */
    public static String streamingSource(String protocol) {
        return supportsStreaming(protocol) == null ? null : SOURCE_PROTOCOL;
    }

    /**
     * 是否支持异步（批量）调用 —— 只认官方端点实测过的厂商。
     *
     * @param platform 厂商 / 平台标识（openai、anthropic、google ...）
     * @param protocol 接入协议（用于确认厂商用的确实是自己的协议）
     * @return {@code true} 已实测；其余返回 {@code null}（未收录，不代表不支持）
     */
    public static Boolean supportsAsync(String platform, String protocol) {
        String vendor = normalize(platform);
        String current = normalize(protocol);
        if (vendor == null || current == null) {
            return null;
        }
        String expected = BATCH_VENDORS.get(vendor);
        return expected != null && expected.equals(current) ? Boolean.TRUE : null;
    }

    /**
     * 异步结论的来源标识。
     *
     * @param platform 厂商 / 平台标识
     * @param protocol 接入协议
     * @return {@link #SOURCE_PROBE}；未实测时返回 {@code null}
     */
    public static String asyncSource(String platform, String protocol) {
        return supportsAsync(platform, protocol) == null ? null : SOURCE_PROBE;
    }

    /**
     * 协议 / 厂商标识规范化（去空白、转小写）；空值返回 null。
     *
     * @param value 原始值
     * @return 规范化后的值
     */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        return text.isEmpty() ? null : text;
    }
}
