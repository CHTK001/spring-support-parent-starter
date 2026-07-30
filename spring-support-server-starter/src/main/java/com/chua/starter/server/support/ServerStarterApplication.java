package com.chua.starter.server.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 服务器管理模块独立启动器
 *
 * @author CH
 * @since 2026/7/30
 */
@SpringBootApplication(exclude = {
        com.chua.starter.ai.support.configuration.RagAutoConfiguration.class
})
@ComponentScan(basePackages = {"com.chua.starter.server.support", "com.chua.starter.ai.support"})
@MapperScan("com.chua.starter.server.support.mapper")
public class ServerStarterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerStarterApplication.class, args);
    }
}
