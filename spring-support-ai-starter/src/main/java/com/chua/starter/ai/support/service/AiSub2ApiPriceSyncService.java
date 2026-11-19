package com.chua.starter.ai.support.service;

/**
 * sub2api 模型价格同步服务接口。
 *
 * <p>抓取 sub2api 站点的模型广场接口（{@code GET {base}/api/v1/model-plaza}），
 * 解析各分组下的模型定价（USD / token）后折算入库到 {@code sys_ai_pricing}（每 1k
 * 单价，币种 USD）。sub2api 是自建中转，站点地址随部署而变：优先用调用方指定的
 * 站点地址，缺省从库内 sub2api 供应商行的上游地址解析。只写 {@code sys_ai_pricing}，
 * 不刷新 {@code sys_ai_provider_metric}。</p>
 *
 * @since 4.0.0.60
 * @author CH
 */
public interface AiSub2ApiPriceSyncService {

    /**
     * 同步 sub2api 模型价格。
     *
     * <p>{@code code} 为空或 {@code sub2api} 时同步模型广场全部模型；否则只同步编码
     * 命中的那个模型。抓取失败或未解析到价格时不改动库内数据，只记日志。</p>
     *
     * @param code    厂商 / 模型编码
     * @param baseUrl sub2api 站点根地址（可空，缺省取库内 sub2api 供应商行）
     */
    void syncPrices(String code, String baseUrl);

    /**
     * 用 sub2api 模型广场补插本地模型清单（缺失才插，保留已有），并顺带刷新价格。
     *
     * @param code    厂商 / 模型编码
     * @param baseUrl sub2api 站点根地址（可空，缺省取库内 sub2api 供应商行）
     */
    void replaceModels(String code, String baseUrl);
}
