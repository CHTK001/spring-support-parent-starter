package com.chua.starter.eduai.configuration;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 教育AI模块自动配置
 * <p>
 * 自动扫描教育AI模块的 Controller 与 Service，并注册 Mapper，
 * 提供题库管理、AI组卷、AI阅卷、知识库、错题库等 AI 教学能力。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@AutoConfiguration
@MapperScan({
        "com.chua.starter.eduai.mapper",
        "com.chua.starter.eduai.dimension.mapper"
})
@ComponentScan({
        "com.chua.starter.eduai.controller",
        "com.chua.starter.eduai.dimension.controller",
        "com.chua.starter.eduai.service.impl",
        "com.chua.starter.eduai.dimension.service",
        "com.chua.starter.eduai.ai"
})
@EnableConfigurationProperties(EduAiProperties.class)
@EnableAsync
public class EduAiAutoConfiguration {

    /**
     * 注册教育AI模块的服务信息贡献者。
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
            registry.registerEndpoint("教育AI", "维度管理", "/v2/edu/dimension/**", "学科/年级/班级/学生/教师维度字典");
            registry.registerEndpoint("教育AI", "调试", "/v2/edu/debug/**", "AI 健康检查、队列连通性");
            registry.registerFlag("eduai", "教育AI", true, "教育AI辅助教学模块 (AI 组卷/阅卷/语义检索/维度管理)");
        };
    }
}
