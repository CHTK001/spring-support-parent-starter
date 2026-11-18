package com.chua.starter.datasearch.provider.limit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 限制探测统一入口：把 {@link ProviderLimitMechanisms} 里的机制串成一条可解释的链路。
 *
 * <p>输出结构（直接落 {@code extra.provider_limits} 并原样回给前端）：</p>
 * <pre>
 * {
 *   "declared": { ... },       // 手动配置 / 官方文档登记表里核对过的口径
 *   "observed": { ... },       // 上游响应头动态观测到的限制
 *   "doc_url": "https://...",  // 官方文档入口（200+ 家登记表提供，不代表页面写了数值）
 *   "doc_source": "moonshotai",
 *   "source": "deepseek",
 *   "source_url": "https://...",
 *   "captured_at": 1700000000000,
 *   "attempted": [ {"kind": "manual", "status": "miss"}, ... ]
 * }
 * </pre>
 *
 * <p>与余额查询一样，链路必须可解释：哪条命中、哪条未命中、哪条报错都要能看见，
 * 不能只给一个空对象让用户猜。文档入口与数值分开回传 —— 有文档不等于有限制。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class ProviderLimitCollector {

    /**
     * 工具类，禁止实例化。
     */
    private ProviderLimitCollector() {
    }

    /**
     * 内置机制清单（经 SPI 装载，含外部扩展）。
     *
     * @return 机制实现
     */
    public static List<ProviderLimitMechanism> mechanisms() {
        return ProviderLimitMechanisms.mechanisms();
    }

    /**
     * 机制标识清单。
     *
     * @return 机制标识
     */
    public static List<String> kinds() {
        return ProviderLimitMechanisms.kinds();
    }

    /**
     * 执行探测。
     *
     * @param context 探测上下文
     * @return 结果报告（declared / observed / doc_url / attempted / captured_at）
     */
    public static Map<String, Object> collect(ProviderLimitContext context) {
        Map<String, Object> declared = null;
        Map<String, Object> observed = null;
        List<ProviderLimitMechanism> mechanisms = mechanisms();
        List<Map<String, Object>> attempted = new ArrayList<>(mechanisms.size());
        for (ProviderLimitMechanism mechanism : mechanisms) {
            Map<String, Object> row = new LinkedHashMap<>(4);
            row.put("kind", mechanism.kind());
            try {
                ProviderLimit limit = mechanism.query(context);
                if (limit == null || !limit.hasValues()) {
                    row.put("status", "miss");
                } else {
                    row.put("status", "hit");
                    row.put("source", limit.getSource());
                    Map<String, Object> map = limit.toMap();
                    if ("header".equals(mechanism.kind())) {
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
        Map<String, Object> out = new LinkedHashMap<>(14);
        out.put("declared", declared);
        out.put("observed", observed);
        Object source = declared != null ? declared.get("source")
                : observed != null ? observed.get("source") : null;
        Object sourceUrl = declared != null ? declared.get("source_url")
                : observed != null ? observed.get("source_url") : null;
        out.put("source", source);
        out.put("source_url", sourceUrl);
        // 登记表覆盖 200+ 家：官方文档入口单独回传，只有文档没有数值时也能在前端给链接
        if (context != null) {
            String docUrl = ProviderLimitPresets.docUrl(context.getPlatform(), context.getBaseUrl());
            String docSource = ProviderLimitPresets.docSource(context.getPlatform(), context.getBaseUrl());
            if (docUrl != null) {
                out.put("doc_url", docUrl);
            }
            if (docSource != null) {
                out.put("doc_source", docSource);
            }
            attachDoc(attempted, "doc", docUrl);
        }
        out.put("captured_at", now);
        out.put("attempted", attempted);
        return out;
    }

    /**
     * 把文档入口挂到对应机制的尝试记录上，避免「未命中」看不出查过哪份文档。
     *
     * @param attempted 尝试记录
     * @param kind      机制标识
     * @param docUrl    文档地址
     */
    private static void attachDoc(List<Map<String, Object>> attempted, String kind, String docUrl) {
        if (docUrl == null) {
            return;
        }
        for (Map<String, Object> row : attempted) {
            if (kind.equals(row.get("kind"))) {
                row.put("doc", docUrl);
                return;
            }
        }
    }
}
