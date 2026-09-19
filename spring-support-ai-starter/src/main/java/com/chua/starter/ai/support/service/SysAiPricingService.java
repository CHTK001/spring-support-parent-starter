package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiPricing;

/**
 * AI 定价服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysAiPricingService {

    /**
     * 获取最新定价
     *
     * @param provider 服务商
     * @param model 模型
     * @return 定价信息
     */
    SysAiPricing getLatestPricing(String provider, String model);

    /**
     * 同步官方定价
     */
    void syncOfficialPricing();
}
