package com.chua.starter.strategy.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 策略模块自动配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Configuration
@ComponentScan(basePackages = {
        "com.chua.starter.strategy.controller",
        "com.chua.starter.strategy.service"
})
@MapperScan("com.chua.starter.strategy.mapper")
public class StrategyAutoConfiguration {
}
