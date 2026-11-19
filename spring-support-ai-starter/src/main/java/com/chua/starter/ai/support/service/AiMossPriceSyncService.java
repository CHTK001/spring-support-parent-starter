package com.chua.starter.ai.support.service;

/**
 * Moss 模型价格同步服务接口。
 *
 * <p>免密钥抓取 Moss 开放平台公开模型页（{@code platform.moss.cn/docs/models}），
 * 解析各模型的官方计费口径后写入 {@code sys_ai_pricing}，并刷新 Moss 的厂商指标系数。
 * 替代旧价格同步服务（只回写模型名、无真实价格）。</p>
 *
 * @since 4.0.0.58
 * @author CH
 */
public interface AiMossPriceSyncService {

    /**
     * 同步 Moss 模型价格。
     *
     * <p>{@code code} 为空或 {@code moss} 时同步模型页全部模型；否则只同步编码命中的那个模型。</p>
     *
     * @param code 厂商 / 模型编码
     */
    void syncPrices(String code);

    /**
     * 用 Moss 公开模型页替换本地模型清单（缺失补插，保留已有），并顺带刷新价格。
     *
     * @param code 厂商 / 模型编码
     */
    void replaceModels(String code);
}
