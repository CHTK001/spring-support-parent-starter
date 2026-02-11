package com.chua.starter.sync.data.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * 同步数据配置
 * @author CH
 * @since 2024/12/19
 */
@ComponentScan(basePackages = {
        "com.chua.starter.sync.data.support.service",
        "com.chua.starter.sync.data.support.controller",
        "com.chua.starter.sync.data.support.configuration"
})
@MapperScan("com.chua.starter.sync.data.support.mapper")
public class SyncDataConfiguration {
}
