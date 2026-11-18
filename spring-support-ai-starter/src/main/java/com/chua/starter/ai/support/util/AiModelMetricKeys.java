package com.chua.starter.ai.support.util;

/**
 * 模型指标归一化键工具：把「provider/model 全路径」或「带版本后缀的模型 id」
 * 折叠成同一个匹配键，供指标库（{@code sys_ai_model_metrics.model_key}）与
 * 用量回填按模型名对齐。
 *
 * <p>与目录侧（datasearch）的折叠口径相互独立：这里保留中文并合并连续分隔符，
 * 便于本地自建模型（中文名）也能建行；目录侧只留字母数字。两侧键不互换使用。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
public final class AiModelMetricKeys {

    /**
     * 工具类，禁止实例化。
     */
    private AiModelMetricKeys() {
    }

    /**
     * 归一化：小写、去除厂商前缀、非字母数字合并为连字符。
     *
     * <p>口径与原 {@code AiModelMetricsEnrichmentService.normalize} 逐字一致，
     * 保证历史 {@code model_key} 数据继续命中：</p>
     * <ol>
     *   <li>整体转小写；</li>
     *   <li>取最后一个 {@code /} 之后的段（去掉 {@code provider/} 前缀）；</li>
     *   <li>连续的非「小写字母 / 数字 / 中文」字符合并成单个 {@code -}。</li>
     * </ol>
     *
     * @param modelId 原始模型标识
     * @return 归一化键；入参为 {@code null} 返回空串
     */
    public static String normalizeKey(String modelId) {
        if (modelId == null) {
            return "";
        }
        String t = modelId.toLowerCase();
        int slash = t.lastIndexOf('/');
        if (slash >= 0) {
            t = t.substring(slash + 1);
        }
        return t.replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-");
    }
}
