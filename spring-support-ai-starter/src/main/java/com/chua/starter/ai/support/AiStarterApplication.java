package com.chua.starter.ai.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 模块独立启动器
 *
 * <p>内置 H2 内存数据库，可直接运行 {@code mvn spring-boot:run} 启动测试。
   * 默认关闭 API 鉴权（app_钥匙_已启用=false），便于本地联调。</p>
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
     * @param args 参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiStarterApplication.class, args);
    }
}