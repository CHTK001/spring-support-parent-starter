package com.chua.starter.ai.support.service;

/**
 * new-api 模型价格同步服务接口。
 *
 * <p>抓取 new-api / one-api 系站点的公开定价接口（{@code GET {base}/api/pricing}），解析
 * 模型倍率（{@code model_ratio} / {@code completion_ratio} / {@code model_price} /
 * {@code quota_type}）与分组倍率（{@code group_ratio}），按站点配额口径折算成美元单价入库到
 * {@code sys_ai_pricing}（每 1k 单价，币种 USD）。new-api 默认 {@code 1 美元 = 500000 配额}，
 * 即倍率 1 = 每 1k 令牌 0.002 美元；{@code quota_type=1} 的按次计费模型取
 * {@code model_price}（美元/次）。new-api 是自建中转，站点地址随部署而变：优先用调用方
 * 指定的站点地址，缺省从库内 newapi 供应商行的上游地址解析。只写 {@code sys_ai_pricing}，
 * 不刷新 {@code sys_ai_provider_metric}。</p>
 *
 * @since 4.0.0.60
 * @author CH
 */
public interface AiNewApiPriceSyncService {

    /**
     * 同步 new-api 模型价格。
     *
     * <p>{@code code} 为空或 {@code newapi} / {@code new-api} 时同步定价接口全部模型；
     * 否则只同步编码命中的那个模型。抓取失败或未解析到价格时不改动库内数据，只记日志。</p>
     *
     * @param code    厂商 / 模型编码
     * @param baseUrl new-api 站点根地址（可空，缺省取库内 newapi 供应商行）
     */
    void syncPrices(String code, String baseUrl);

    /**
     * 用 new-api 定价接口补插本地模型清单（缺失才插，保留已有），并顺带刷新价格。
     *
     * @param code    厂商 / 模型编码
     * @param baseUrl new-api 站点根地址（可空，缺省取库内 newapi 供应商行）
     */
    void replaceModels(String code, String baseUrl);
}
