package com.chua.starter.ai.support.configuration.usage;

import com.chua.starter.ai.support.interceptor.UsageRecorderInterceptor;
import com.chua.starter.ai.support.mapper.SysAiPricingMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageDailyMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.starter.ai.support.schedule.UsageAggregationTask;
import com.chua.starter.ai.support.service.SysAiPricingService;
import com.chua.starter.ai.support.service.SysAiUsageService;
import com.chua.starter.ai.support.service.impl.SysAiPricingServiceImpl;
import com.chua.starter.ai.support.service.impl.SysAiUsageServiceImpl;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 用量模块自动配置
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
@EnableAsync
@EnableScheduling
@ConditionalOnClass({SysAiUsageMapper.class})
@ConditionalOnProperty(prefix = "spring.ai.usage", name = "enabled", havingValue = "true", matchIfMissing = true)
@MapperScan("com.chua.starter.ai.support.mapper")
public class UsageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SysAiUsageService.class)
    public SysAiUsageService sysAiUsageService(SysAiUsageMapper usageMapper,
                                               SysAiPricingService pricingService) {
        return new SysAiUsageServiceImpl(usageMapper, pricingService);
    }

    @Bean
    @ConditionalOnMissingBean(SysAiPricingService.class)
    public SysAiPricingService sysAiPricingService(SysAiPricingMapper pricingMapper) {
        return new SysAiPricingServiceImpl(pricingMapper);
    }

    @Bean
    @ConditionalOnMissingBean(UsageRecorderInterceptor.class)
    public UsageRecorderInterceptor usageRecorderInterceptor(SysAiUsageService usageService) {
        return new UsageRecorderInterceptor(usageService);
    }

    @Bean
    @ConditionalOnMissingBean(UsageAggregationTask.class)
    public UsageAggregationTask usageAggregationTask(SysAiUsageMapper usageMapper,
                                                     SysAiUsageDailyMapper dailyMapper,
                                                     GlobalSettingReader settingReader) {
        return new UsageAggregationTask(settingReader, usageMapper, dailyMapper);
    }
}
