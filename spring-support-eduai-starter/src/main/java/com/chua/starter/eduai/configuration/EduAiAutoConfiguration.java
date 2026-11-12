package com.chua.starter.eduai.configuration;

import com.chua.starter.ai.support.paper.pipeline.config.PaperPipelineProperties;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * 教育AI模块自动配置
 * <p>
 * 自动扫描教育AI模块的 控制器 与 服务，并注册 映射器，
 * 提供题库管理、AI组卷、AI阅卷、知识库、错题库等 AI 教学能力。
 * </p>
 *
 * @since 4.0.0.42
 */
@Slf4j
@AutoConfiguration
@MapperScan({
        "com.chua.starter.eduai.mapper",
        "com.chua.starter.eduai.dimension.mapper",
        // 采集调度依赖 JobOperator -> MybatisJobRepository -> JobMapper
        // （JobAutoConfiguration.MAPPER_PACKAGE，业务主配置需自行扫描）
        "com.chua.starter.job.support.store"
})
@ComponentScan({
        "com.chua.starter.eduai.controller",
        "com.chua.starter.eduai.dimension.controller",
        "com.chua.starter.eduai.service.impl",
        "com.chua.starter.eduai.dimension.service",
        "com.chua.starter.eduai.collect",
        "com.chua.starter.eduai.queue",
        // 同步至统一模板库（paper → template_info）
        "com.chua.starter.eduai.template"
})
@EnableConfigurationProperties(EduAiProperties.class)
/**
 * eduaiauto配置类。
 *
 * @author CH
 * @since 4.0.0
 */
@Import(EduAiCachePrimaryConfiguration.class)
public class EduAiAutoConfiguration {

    /**
     * 把教育业务配置里的「试卷管线」参数映射给 ai-starter。
     *
     * <p>{@code plugin.eduai.*} 属于业务前缀，不能出现在 ai-starter 里；
     * 管线包只认自己的 {@link PaperPipelineProperties}。加 {@code @Primary}：
     * ai-starter 自带一份默认 Bean，宿主引入 eduai 时以这里的映射为准。</p>
     *
     * @param source 教育 AI 配置
     * @return 管线参数
     */
    @Bean
    @Primary
    public PaperPipelineProperties paperPipelineProperties(EduAiProperties source) {
        PaperPipelineProperties target = new PaperPipelineProperties();
        EduAiProperties.Marking marking = source.getMarking();
        if (marking != null) {
            target.getMarking().setCallTimeout(marking.getCallTimeout());
            target.getMarking().setPreprocess(marking.isPreprocess());
            target.getMarking().setLayoutCorrection(marking.isLayoutCorrection());
            target.getMarking().setExtractStrategy(marking.getExtractStrategy());
        }
        EduAiProperties.Ai ai = source.getAi();
        if (ai != null) {
            target.getAi().setEnabled(ai.isEnabled());
            EduAiProperties.Retry retry = ai.getRetry();
            if (retry != null) {
                PaperPipelineProperties.Retry copy = target.getAi().getRetry();
                copy.setTimes(retry.getTimes());
                copy.setBackoffMillis(retry.getBackoffMillis());
                copy.setBackoffMultiplier(retry.getBackoffMultiplier());
                copy.setMaxBackoffMillis(retry.getMaxBackoffMillis());
                copy.setRetryOnTransient(retry.isRetryOnTransient());
            }
        }
        EduAiProperties.Alert alert = source.getAlert();
        if (alert != null) {
            target.getAlert().setEnabled(alert.isEnabled());
            target.getAlert().setWebhookUrl(alert.getWebhookUrl());
            target.getAlert().setTimeoutMillis(alert.getTimeoutMillis());
        }
        return target;
    }

    /**
     * 注册教育AI模块的服务信息贡献者。
     * @return eduAI服务信息contributor的结果
     */
    @Bean
    public ServiceInfoContributor eduAiServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("教育AI", "工作台", "/edu", "教育AI能力总览 (6 卡片)");
            registry.registerEndpoint("教育AI", "题库管理", "/v2/edu/question/**", "题目录入、查询、导入与删除");
            registry.registerEndpoint("教育AI", "试卷管理", "/v2/edu/paper/**", "AI组卷、试卷详情与题目替换");
            registry.registerEndpoint("教育AI", "阅卷管理", "/v2/edu/marking/**", "答题卡提交、阅卷结果与教师确认");
            registry.registerEndpoint("教育AI", "知识点管理", "/v2/edu/knowledge-point/**", "知识点树形维护");
            registry.registerEndpoint("教育AI", "知识库管理", "/v2/edu/knowledge-doc/**", "文档上传、语义检索与删除");
            registry.registerEndpoint("教育AI", "错题库管理", "/v2/edu/wrongbook/**", "错题查询、复习卷与掌握度更新");
            registry.registerEndpoint("教育AI", "考试管理", "/v2/edu/exam/**", "考试创建、更新与删除");
            // 原先这里注册过「题库采集 /v2/edu/collect/**」。该控制器已随采集管理一起移除，
            // 采集能力改由采集平台的 /v2/collect/** 提供，归属 collect 模块，不该由 eduai 代报。
            registry.registerEndpoint("教育AI", "维度管理", "/v2/edu/dimension/**", "学科/年级/班级/学生/教师维度字典");
            registry.registerEndpoint("教育AI", "调试", "/v2/edu/debug/**", "AI 健康检查、队列连通性");
            registry.registerFlag("eduai", "教育AI", true, "教育AI辅助教学模块 (AI 组卷/阅卷/语义检索/维度管理)");
        };
    }
}
