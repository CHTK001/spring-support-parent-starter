package com.chua.starter.ai.support;

import java.util.Map;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 模块独立启动器
 *
 * <p>本模块同时是库：被 monitor / system / oauth 等宿主以 jar 依赖。库 jar 里不能放
 * {@code application.yml}——它会以类路径首匹配遮蔽宿主的同名配置文件（端口、数据源、
 * profile 整份被换掉），故配置文件名为 {@code ai-starter.yml}，只由本启动器显式导入。</p>
 *
 * <p>本地自测用 {@code --spring.profiles.active=local}（连本机 MySQL/Redis），
 * 对应配置在 {@code ai-starter.yml} 末尾的 local 文档段。</p>
 *
 * <p>{@code @MapperScan} 同时覆盖业务 Mapper（{@code ai.support.mapper}）与
 * nomifun 前端持久化 映射器（{@code ai.support.manage.mapper}）。</p>
 *
 * @since 2026/7/29
 * @author CH
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.chua.starter.ai.support")
@MapperScan({"com.chua.starter.ai.support.mapper", "com.chua.starter.ai.support.manage.mapper"})
public class AiStarterApplication {

    /**
     * Main
     *
     * <p>命令行参数与 {@code -D} 系统属性的优先级仍高于导入文件，覆盖端口/数据源照旧生效。</p>
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AiStarterApplication.class);
        application.setDefaultProperties(Map.of(
                "spring.config.import", "optional:classpath:/ai-starter.yml"));
        application.run(args);
    }
}
