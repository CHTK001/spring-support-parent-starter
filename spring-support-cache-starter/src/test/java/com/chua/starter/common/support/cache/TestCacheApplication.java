package com.chua.starter.common.support.cache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;

/**
 * 缓存模块测试启动器 — 启用缓存自动配置
 *
 * @author CH
 * @since 2026/8/2
 */
@SpringBootApplication(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
public class TestCacheApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestCacheApplication.class, args);
    }
}
