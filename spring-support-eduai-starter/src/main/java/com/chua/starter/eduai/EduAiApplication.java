package com.chua.starter.eduai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;

/**
 * 教育 AI 工作台 后端入口。
 *
 * <p>聚合各 starter 自动配置，提供题库 / 组卷 / 阅卷 / 知识库 / 错题 / 考试 / 采集 全模块 API。
 *
 * <p>Redisson 自动配置在 application.yml 中排除（Boot 4 不兼容旧 RedisProperties）。
 * 保留 DataRedisAutoConfiguration 以便注册 DataRedisProperties（RedisConfiguration 依赖）。
 *
 * @author CH
 * @since 4.0.0.42
 */
@EnableCaching
@SpringBootApplication
@Import({
    com.chua.starter.eduai.configuration.EduAiAutoConfiguration.class
})
public class EduAiApplication {

    /**
     * main。
     * @param args 参数
     */
    public static void main(String[] args) {
        SpringApplication.run(EduAiApplication.class, args);
    }
}