package com.chua.spider.support.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 爬虫中心 Starter 自动配置。
 * <p>启用本 Starter 后自动注册爬虫中心 Service/Controller/Mapper 的 Spring Bean。
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
@ComponentScan("com.chua.spider.support")
@MapperScan("com.chua.spider.support.mapper")
public class SpiderAutoConfiguration {
}