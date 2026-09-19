package com.chua.starter.software.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 软件管理模块独立启动器
 *
 * <p>内置 H2 内存数据库，可直接运行 mvn spring-boot:run 启动测试。</p>
 *
 * @since 2026/7/25
 * @author CH
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.chua.starter.software.support")
@MapperScan("com.chua.starter.software.support.mapper")
public class SoftStarterApplication {

    /**
     * Main
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SoftStarterApplication.class, args);
    }
}
