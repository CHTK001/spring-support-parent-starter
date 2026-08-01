package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiPricing;
import com.chua.starter.ai.support.mapper.SysAiPricingMapper;
import com.chua.starter.ai.support.service.SysAiPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 定价服务实现类
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiPricingServiceImpl implements SysAiPricingService {

    /**
     * 定价 Mapper
     */
    private final SysAiPricingMapper pricingMapper;

    @Override
    public SysAiPricing getLatestPricing(String provider, String model) {
        LambdaQueryWrapper<SysAiPricing> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiPricing::getProvider, provider)
                .eq(SysAiPricing::getModel, model)
                .orderByDesc(SysAiPricing::getEffectiveDate)
                .last("limit 1");
        return pricingMapper.selectOne(wrapper);
    }

    @Override
    public void syncOfficialPricing() {
        // 集成 datasearch-starter 同步官方定价（暂无实现）
        log.info("[Pricing] 同步官方定价（暂无实现）");
    }
}
