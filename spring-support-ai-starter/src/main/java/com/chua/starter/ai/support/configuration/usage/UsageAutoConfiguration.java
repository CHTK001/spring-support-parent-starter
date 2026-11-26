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
import com.chua.starter.ai.support.manage.service.SysAiModelPriceService;
import com.chua.starter.ai.support.manage.service.SysAiProviderMetricService;
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
     * 注册用量服务 Bean。
     *
     * @param usageMapper        用量明细映射器
     * @param modelAliasService  模型别名服务
     * @param dailyMapper        用量日汇总映射器
     * @param usageLiveStreamHub 实时推送中枢
     * @param pricingService     计费服务
     * @param modelMetricsService 模型指标服务
     * @param iptablesService    iptables 服务
     * @return 用量服务实现
     */
    public SysAiUsageService sysAiUsageService(SysAiUsageMapper usageMapper,
                                               ModelAliasService modelAliasService,
                                               SysAiUsageDailyMapper dailyMapper,
                                               UsageLiveStreamHub usageLiveStreamHub,
                                               SysAiPricingService pricingService,
                                               SysAiModelMetricsService modelMetricsService,
                                               IptablesService iptablesService) {
        /**
         * sysaiusage服务impl。
         * @param usageMapper usage映射器
         * @param modelAliasService 模型别名服务
         * @param dailyMapper daily映射器
         * @param usageLiveStreamHub 实时推送中枢
         * @param pricingService pricing服务
         * @param modelMetricsService 模型指标服务
         * @param iptablesService iptables服务
         * @return sysAIusage服务impl的结果
         */
        return new SysAiUsageServiceImpl(usageMapper, modelAliasService, dailyMapper, usageLiveStreamHub, pricingService, modelMetricsService, iptablesService);
    }

    @Bean
    @ConditionalOnMissingBean(SysAiPricingService.class)
    /**
     * 注册计费服务 Bean。
     *
     * @param pricingMapper         定价映射器
     * @param modelMetricsService   全量指标库服务
     * @param modelPriceService     模型价格服务
     * @param providerMetricService 供应商指标系数服务
     * @return 计费服务实现
     */
    public SysAiPricingService sysAiPricingService(SysAiPricingMapper pricingMapper,
                                                   SysAiModelMetricsService modelMetricsService,
                                                   SysAiModelPriceService modelPriceService,
                                                   SysAiProviderMetricService providerMetricService) {
        /**
         * sysaipricing服务impl。
         * @param pricingMapper pricing映射器
         * @param modelMetricsService 全量指标库服务
         * @param modelPriceService 模型价格服务
         * @param providerMetricService 供应商指标系数服务
         * @return sysAIpricing服务impl的结果
         */
        return new SysAiPricingServiceImpl(pricingMapper, modelMetricsService, modelPriceService, providerMetricService);
    }

    @Bean
    @ConditionalOnMissingBean(UsageRecorderInterceptor.class)
    /**
     * 注册用量拦截器 Bean。
     *
     * @param usageService  用量服务
     * @param settingReader 全局设置读取器
     * @return 用量记录拦截器
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
     * 注册用量聚合任务 Bean。
     *
     * @param usageMapper          用量明细映射器
     * @param dailyMapper          用量日汇总映射器
     * @param settingReader        全局设置读取器
     * @param pricingService       计费服务
     * @param syncLogMapper        用量同步日志映射器
     * @param modelMetricsService  模型指标库服务（回填本地平均输出速度）
     * @return 用量聚合任务
     */
    public UsageAggregationTask usageAggregationTask(SysAiUsageMapper usageMapper,
                                                     SysAiUsageDailyMapper dailyMapper,
                                                     GlobalSettingReader settingReader,
                                                     SysAiPricingService pricingService,
                                                     SysAiUsageSyncLogMapper syncLogMapper,
                                                     SysAiModelMetricsService modelMetricsService) {
        /**
         * usageaggregation任务。
         * @param settingReader setting读取
         * @param usageMapper usage映射器
         * @param dailyMapper daily映射器
         * @param pricingService pricing服务
         * @param syncLogMapper 同步日志映射器
         * @param modelMetricsService 模型指标服务
         * @return usageaggregation任务的结果
         */
        return new UsageAggregationTask(settingReader, usageMapper, dailyMapper, pricingService, syncLogMapper,
                modelMetricsService);
    }
}
