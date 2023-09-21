package com.chua.starter.gen.support.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * gen配置
 *
 * @author CH
 * @since 2023/09/21
 */
@ComponentScan("com.chua.starter.gen.support")
@MapperScan("com.chua.starter.gen.support.mapper")
public class GenConfiguration {
}
