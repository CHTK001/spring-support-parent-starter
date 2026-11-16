package com.chua.starter.ai.support.manage.limit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 手动限制机制：读取服务商 {@code extra.limits_manual}。
 *
 * <p>用于官方没有机器可读限制、响应头也不下发的服务商 —— 管理员按后台账单 /
 * 控制台页面把并发、RPM、配额、计费口径填进来，同步时原样落库并参与展示，
 * 不需要为每家服务商改代码。可配置键（均可选）：</p>
 * <ul>
 *   <li>{@code concurrency} / {@code requests_per_minute} / {@code requests_per_hour} /
 *       {@code requests_per_day}</li>
 *   <li>{@code tokens_per_minute} / {@code tokens_per_day}</li>
 *   <li>{@code monthly_request_quota} / {@code monthly_token_quota}</li>
 *   <li>{@code billing_basis} / {@code currency} / {@code notes} / {@code source_url}</li>
 *   <li>{@code model_concurrency}: {@code {"模型名": 上限}}</li>
 * </ul>
 *
 * @since 4.0.0.51
 * @author CH
 */
public class ManualProviderLimitProvider implements ProviderLimitProvider {

    /**
     * 手动配置在 extra 中的键名。
     */
    public static final String EXTRA_KEY = "limits_manual";

    @Override
    public String kind() {
        return "manual";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderLimit query(ProviderLimitContext context) {
        if (context == null || context.getExtra() == null) {
            return null;
        }
        Object raw = context.getExtra().get(EXTRA_KEY);
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        Map<String, Object> source = (Map<String, Object>) map;
        ProviderLimit.ProviderLimitBuilder builder = ProviderLimit.builder()
                .kind(kind())
                .source("manual")
                .capturedAt(System.currentTimeMillis())
                .verified(true)
                .concurrency(integerOf(source, "concurrency"))
                .requestsPerMinute(integerOf(source, "requests_per_minute"))
                .requestsPerHour(integerOf(source, "requests_per_hour"))
                .requestsPerDay(integerOf(source, "requests_per_day"))
                .tokensPerMinute(integerOf(source, "tokens_per_minute"))
                .tokensPerDay(integerOf(source, "tokens_per_day"))
                .monthlyRequestQuota(longOf(source, "monthly_request_quota"))
                .monthlyTokenQuota(longOf(source, "monthly_token_quota"))
                .billingBasis(textOf(source, "billing_basis"))
                .currency(textOf(source, "currency"))
                .notes(textOf(source, "notes"))
                .sourceUrl(textOf(source, "source_url"));
        Map<String, Integer> perModel = new LinkedHashMap<>(4);
        if (source.get("model_concurrency") instanceof Map<?, ?> models) {
            for (Map.Entry<?, ?> entry : models.entrySet()) {
                Integer value = integerOf(entry.getValue());
                if (entry.getKey() != null && value != null) {
                    perModel.put(String.valueOf(entry.getKey()), value);
                }
            }
        }
        if (!perModel.isEmpty()) {
            builder.modelConcurrency(perModel);
        }
        ProviderLimit limit = builder.build();
        return limit.hasValues() ? limit : null;
    }

    /**
     * 读字符串值。
     *
     * @param map 来源
     * @param key 键
     * @return 去空白后的文本；不存在返回 null
     */
    private static String textOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 读整数值。
     *
     * @param map 来源
     * @param key 键
     * @return 整数；无法解析返回 null
     */
    private static Integer integerOf(Map<String, Object> map, String key) {
        return integerOf(map.get(key));
    }

    /**
     * 宽松转整数。
     *
     * @param value 值
     * @return 整数；无法解析返回 null
     */
    private static Integer integerOf(Object value) {
        Long parsed = longOf(value);
        if (parsed == null) {
            return null;
        }
        return (int) Math.min(parsed, Integer.MAX_VALUE);
    }

    /**
     * 读长整数。
     *
     * @param map 来源
     * @param key 键
     * @return 长整数；无法解析返回 null
     */
    private static Long longOf(Map<String, Object> map, String key) {
        return longOf(map.get(key));
    }

    /**
     * 宽松转长整数。
     *
     * @param value 值
     * @return 长整数；无法解析返回 null
     */
    private static Long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof List<?> || value instanceof Map<?, ?>) {
            return null;
        }
        if (value == null) {
            return null;
        }
        try {
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
