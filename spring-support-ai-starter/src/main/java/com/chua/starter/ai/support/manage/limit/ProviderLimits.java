package com.chua.starter.ai.support.manage.limit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 限制探测统一入口：把各 {@link ProviderLimitProvider} 串成一条可解释的链路。
 *
 * <p>输出结构（直接落 {@code extra.provider_limits} 并原样回给前端）：</p>
 * <pre>
 * {
 *   "declared": { ... },       // 手动配置 / 官方文档声明的限制
 *   "observed": { ... },       // 上游响应头动态观测到的限制
 *   "source": "deepseek",
 *   "source_url": "https://...",
 *   "captured_at": 1700000000000,
 *   "attempted": [ {"kind": "manual", "status": "miss"}, ... ]
 * }
 * </pre>
 *
 * <p>与余额查询一样，链路必须可解释：哪条命中、哪条未命中、哪条报错都要能看见，
 * 不能只给一个空对象让用户猜。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class ProviderLimits {

    /**
     * 内置机制，顺序即优先级（手动 > 官方文档 > 响应头）。
     */
    private static final List<ProviderLimitProvider> PROVIDERS = List.of(
            new ManualProviderLimitProvider(),
            new DocProviderLimitProvider(),
            new HeaderProviderLimitProvider()
    );

    private ProviderLimits() {
    }

    /**
     * 内置机制清单。
     *
     * @return 机制实现
     */
    public static List<ProviderLimitProvider> providers() {
        return PROVIDERS;
    }

    /**
     * 机制标识清单。
     *
     * @return 机制标识
     */
    public static List<String> kinds() {
        List<String> kinds = new ArrayList<>(PROVIDERS.size());
        for (ProviderLimitProvider provider : PROVIDERS) {
            kinds.add(provider.kind());
        }
        return kinds;
    }

    /**
     * 执行探测。
     *
     * @param context 探测上下文
     * @return 结果报告（declared / observed / attempted / captured_at）
     */
    public static Map<String, Object> collect(ProviderLimitContext context) {
        Map<String, Object> declared = null;
        Map<String, Object> observed = null;
        List<Map<String, Object>> attempted = new ArrayList<>(PROVIDERS.size());
        for (ProviderLimitProvider provider : PROVIDERS) {
            Map<String, Object> row = new LinkedHashMap<>(4);
            row.put("kind", provider.kind());
            try {
                ProviderLimit limit = provider.query(context);
                if (limit == null || !limit.hasValues()) {
                    row.put("status", "miss");
                } else {
                    row.put("status", "hit");
                    row.put("source", limit.getSource());
                    Map<String, Object> map = limit.toMap();
                    if ("header".equals(provider.kind())) {
                        observed = map;
                    } else if (declared == null) {
                        declared = map;
                    }
                }
            } catch (Exception e) {
                row.put("status", "error");
                row.put("message", e.getMessage() == null ? e.toString() : e.getMessage());
            }
            attempted.add(row);
        }

        long now = System.currentTimeMillis();
        Map<String, Object> out = new LinkedHashMap<>(10);
        out.put("declared", declared);
        out.put("observed", observed);
        Object source = declared != null ? declared.get("source")
                : observed != null ? observed.get("source") : null;
        Object sourceUrl = declared != null ? declared.get("source_url")
                : observed != null ? observed.get("source_url") : null;
        out.put("source", source);
        out.put("source_url", sourceUrl);
        out.put("captured_at", now);
        out.put("attempted", attempted);
        return out;
    }
}
