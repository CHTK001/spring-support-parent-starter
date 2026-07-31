package com.chua.starter.server.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 服务器管理模块独立启动器
 *
 * @author CH
 * @since 2026/7/30
 */
@SpringBootApplication(exclude = {
        com.chua.starter.ai.support.configuration.RagAutoConfiguration.class,
        com.chua.starter.redis.support.CacheConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class,
        com.baomidou.lock.spring.boot.autoconfigure.LockAutoConfiguration.class,
        com.baomidou.lock.spring.boot.autoconfigure.RedisTemplateLockAutoConfiguration.class
})
@ComponentScan(
        basePackages = {"com.chua.starter.server.support", "com.chua.starter.ai.support"},
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com\\.chua\\.starter\\.ai\\.support\\.controller\\.RagController"
                )
        }
)
@MapperScan("com.chua.starter.server.support.mapper")
public class ServerStarterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerStarterApplication.class, args);
    }
}
