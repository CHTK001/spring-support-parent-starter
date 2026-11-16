package com.chua.starter.datasearch.provider.limit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 响应头观测机制：从上一次上游请求的响应头里读限流信息。
 *
 * <p>这是覆盖面最广的一条 —— 主流 OpenAI 兼容网关都会在响应头下发
 * {@code x-ratelimit-*}（OpenAI 风格）、{@code ratelimit-*}（IETF 草案）或
 * {@code anthropic-ratelimit-*}（Anthropic 风格），因此 200+ 提供商不需要
 * 逐家写预设，只要能观测到就落库。同步流程复用「拉模型」的同一次
 * {@code GET /models} 请求，不额外发请求、不伪造探测。</p>
 *
 * <p>只记录实际出现的头；缺哪项就是 null。剩余量 / 重置时间等不稳定的值
 * 存进 {@code observed} 供排查，不作为固定限制。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public class HeaderLimitMechanism implements ProviderLimitMechanism {

    @Override
    public String kind() {
        return "header";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public ProviderLimit query(ProviderLimitContext context) {
        if (context == null || context.getResponseHeaders() == null
                || context.getResponseHeaders().isEmpty()) {
            return null;
        }
        Map<String, String> headers = normalized(context.getResponseHeaders());
        Integer rpm = firstInt(headers, "x-ratelimit-limit-requests",
                "anthropic-ratelimit-requests-limit");
        Integer tpm = firstInt(headers, "x-ratelimit-limit-tokens",
                "anthropic-ratelimit-tokens-limit");
        Integer concurrency = firstInt(headers, "x-ratelimit-concurrency",
                "x-concurrency-limit", "concurrency-limit");

        Integer requestsPerHour = null;
        String policy = headers.get("ratelimit-policy");
        if (rpm == null && policy != null) {
            int[] parsed = parsePolicy(policy);
            if (parsed[1] > 0) {
                if (parsed[1] <= 60) {
                    rpm = parsed[0];
                } else if (parsed[1] <= 3600) {
                    requestsPerHour = parsed[0];
                }
            }
        }
        if (rpm == null) {
            rpm = firstInt(headers, "ratelimit-limit");
        }

        Map<String, Object> observed = new LinkedHashMap<>(8);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (key.contains("ratelimit") || key.contains("retry-after")) {
                observed.put(key, entry.getValue());
            }
        }
        if (observed.isEmpty() && rpm == null && tpm == null && concurrency == null) {
            return null;
        }
        ProviderLimit limit = ProviderLimit.builder()
                .kind(kind())
                .source("header")
                .capturedAt(System.currentTimeMillis())
                .verified(true)
                .concurrency(concurrency)
                .requestsPerMinute(rpm)
                .requestsPerHour(requestsPerHour)
                .tokensPerMinute(tpm)
                .observed(observed.isEmpty() ? null : observed)
                .notes("数值来自上游 /models 响应头，随账户等级 / 时间窗动态变化；"
                        + "remaining / reset 等瞬时值见 observed。")
                .build();
        return limit.hasValues() ? limit : null;
    }

    /**
     * 响应头转小写键索引（同名多值时取第一个）。
     *
     * @param headers 原始响应头
     * @return 小写键 → 值
     */
    private static Map<String, String> normalized(Map<String, List<String>> headers) {
        Map<String, String> out = new LinkedHashMap<>(16);
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!out.containsKey(key) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                out.put(key, entry.getValue().getFirst());
            }
        }
        return out;
    }

    /**
     * 依次尝试若干头名，返回第一个可解析的整数。
     *
     * @param headers 小写键索引
     * @param keys    候选头名
     * @return 整数；都没有返回 null
     */
    private static Integer firstInt(Map<String, String> headers, String... keys) {
        for (String key : keys) {
            String value = headers.get(key);
            if (value == null) {
                continue;
            }
            Integer parsed = intOf(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    /**
     * 解析 IETF {@code ratelimit-policy}：{@code "100;w=60"} 或 {@code "100;w=60, 5000;w=3600"}。
     *
     * @param policy 头值
     * @return [数量, 窗口秒]；无法解析返回 [0, 0]
     */
    private static int[] parsePolicy(String policy) {
        String first = policy.split(",")[0].trim();
        String[] parts = first.split(";");
        Integer quota = intOf(parts[0]);
        int window = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.startsWith("w=")) {
                Integer parsed = intOf(part.substring(2));
                if (parsed != null) {
                    window = parsed;
                }
            }
        }
        return quota == null ? new int[]{0, 0} : new int[]{quota, window};
    }

    /**
     * 宽松转整数（允许 {@code "5000"}、{@code "5000.0"}、{@code "1,000"}）。
     *
     * @param value 文本
     * @return 整数；无法解析返回 null
     */
    private static Integer intOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().replace(",", "");
        try {
            return (int) Math.min((long) Double.parseDouble(text), Integer.MAX_VALUE);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
