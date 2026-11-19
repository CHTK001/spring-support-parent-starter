package com.chua.starter.ai.support.service;

/**
 * DeepSeek 模型价格同步服务接口。
 *
 * <p>免密钥抓取 DeepSeek 官方文档定价页
 * （{@code api-docs.deepseek.com/zh-cn/quick_start/pricing/}），解析各模型的官方
 * 计费口径后写入 {@code sys_ai_pricing}（高峰时段价，币种 CNY）。DeepSeek 已被
 * Models.dev 指标同步覆盖，本服务不刷新 {@code sys_ai_provider_metric}，避免覆盖
 * 指标同步写入的行。</p>
 *
 * @since 4.0.0.59
 * @author CH
 */
public interface AiDeepSeekPriceSyncService {

    /**
     * 同步 DeepSeek 模型价格。
     *
     * <p>{@code code} 为空或 {@code deepseek} 时同步定价页全部模型；否则只同步编码命中的那个模型。</p>
     *
     * @param code 厂商 / 模型编码
     */
    void syncPrices(String code);

    /**
     * 用 DeepSeek 定价页替换本地模型清单（缺失补插，保留已有），并顺带刷新价格。
     *
     * @param code 厂商 / 模型编码
     */
    void replaceModels(String code);
}
