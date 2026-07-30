package com.chua.starter.ai.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 模块独立启动器
 *
 * <p>内置 H2 内存数据库，可直接运行 {@code mvn spring-boot:run} 启动测试。
 * 默认关闭 API 鉴权（app_key_enabled=false），便于本地联调。</p>
 *
 * @author CH
 * @since 2026/7/29
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.chua.starter.ai.support")
@MapperScan("com.chua.starter.ai.support.mapper")
public class AiStarterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiStarterApplication.class, args);
    }
}
