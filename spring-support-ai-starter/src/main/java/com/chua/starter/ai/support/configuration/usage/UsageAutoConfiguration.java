package com.chua.starter.ai.support.configuration.usage;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.controller.UsageLiveStreamHub;
import com.chua.starter.ai.support.interceptor.UsageRecorderInterceptor;
import com.chua.starter.ai.support.mapper.SysAiPricingMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageDailyMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageSyncLogMapper;
import com.chua.starter.ai.support.schedule.UsageAggregationTask;
import com.chua.starter.ai.support.manage.service.SysAiModelMetricsService;
import com.chua.starter.ai.support.service.AiModelMetricsEnrichmentService;
import com.chua.starter.ai.support.service.ModelAliasService;
import com.chua.starter.ai.support.service.SysAiPricingService;
import com.chua.starter.ai.support.service.SysAiUsageService;
import com.chua.starter.ai.support.service.impl.SysAiPricingServiceImpl;
import com.chua.starter.ai.support.service.impl.SysAiUsageServiceImpl;
import com.chua.starter.common.support.service.IptablesService;
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
 * @since 4.0.0.42
 * @author CH
 */
@AutoConfiguration
@EnableAsync
@EnableScheduling
@ConditionalOnClass({SysAiUsageMapper.class})
@ConditionalOnProperty(prefix = "plugin.ai.usage", name = "enabled", havingValue = "true", matchIfMissing = true)
@MapperScan("com.chua.starter.ai.support.mapper")
public class UsageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SysAiUsageService.class)
    /**
     * sysaiusage服务
     * @param usageMapper usage映射器
     * @param dailyMapper daily映射器
     * @param pricingService pricing服务
     */
    public SysAiUsageService sysAiUsageService(SysAiUsageMapper usageMapper,
                                               ModelAliasService modelAliasService,
                                               SysAiUsageDailyMapper dailyMapper,
                                               UsageLiveStreamHub usageLiveStreamHub,
                                               SysAiPricingService pricingService,
                                               IptablesService iptablesService) {
        /**
         * sysaiusage服务impl。
         * @param usageMapper usage映射器
         * @param modelAliasService 模型别名服务
         * @param dailyMapper daily映射器
         * @param usageLiveStreamHub 实时推送中枢
         * @param pricingService pricing服务
         * @param iptablesService iptables服务
         * @return sysAIusage服务impl的结果
         */
        return new SysAiUsageServiceImpl(usageMapper, modelAliasService, dailyMapper, usageLiveStreamHub, pricingService, iptablesService);
    }

    @Bean
    @ConditionalOnMissingBean(SysAiPricingService.class)
    /**
     * sysaipricing服务
     * @param pricingMapper     定价 映射器
     * @param metricsEnrichment AA 模型指标服务（含定价缓存）
     */
    public SysAiPricingService sysAiPricingService(SysAiPricingMapper pricingMapper,
                                                   AiModelMetricsEnrichmentService metricsEnrichment,
                                                   SysAiModelMetricsService modelMetricsService) {
        /**
         * sysaipricing服务impl。
         * @param pricingMapper pricing映射器
         * @param metricsEnrichment 指标enrichment
         * @param modelMetricsService 全量指标库服务
         * @return sysAIpricing服务impl的结果
         */
        return new SysAiPricingServiceImpl(pricingMapper, metricsEnrichment, modelMetricsService);
    }

    @Bean
    @ConditionalOnMissingBean(UsageRecorderInterceptor.class)
    /**
     * usagerecorder拦截器
     *
     * @param usageService usage服务
     * @return usagerecorder拦截器的结果
     */
    public UsageRecorderInterceptor usageRecorderInterceptor(SysAiUsageService usageService,
                                                               GlobalSettingReader settingReader) {
        /**
         * usagerecorder拦截器。
         * @param usageService usage服务
         * @param settingReader setting读取
         * @return usagerecorder拦截器的结果
         */
        return new UsageRecorderInterceptor(usageService, settingReader);
    }

    @Bean
    @ConditionalOnMissingBean(UsageAggregationTask.class)
    /**
     * usageaggregation任务
     * @param usageMapper usage映射器
     * @param dailyMapper daily映射器
     * @param settingReader setting读取
     * @param pricingService pricing服务
     * @param syncLogMapper 用量同步日志 映射器
     */
    public UsageAggregationTask usageAggregationTask(SysAiUsageMapper usageMapper,
                                                     SysAiUsageDailyMapper dailyMapper,
                                                     GlobalSettingReader settingReader,
                                                     SysAiPricingService pricingService,
                                                     SysAiUsageSyncLogMapper syncLogMapper) {
        /**
         * usageaggregation任务。
         * @param settingReader setting读取
         * @param usageMapper usage映射器
         * @param dailyMapper daily映射器
         * @param pricingService pricing服务
         * @param syncLogMapper 同步日志映射器
         * @return usageaggregation任务的结果
         */
        return new UsageAggregationTask(settingReader, usageMapper, dailyMapper, pricingService, syncLogMapper);
    }
}
