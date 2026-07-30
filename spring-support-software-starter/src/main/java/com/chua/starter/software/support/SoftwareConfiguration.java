package com.chua.starter.software.support;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 软件管理配置
 *
 * @author CH
 * @since 2026/7/25
 */
@Slf4j
@AutoConfiguration
@MapperScan("com.chua.starter.software.support.mapper")
@ComponentScan("com.chua.starter.software.support")
public class SoftwareConfiguration {
}