package com.chua.starter.protocol.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 协议管理自动配置：在开启 {@code plugin.protocol.enable} 时注册
 * Mapper、服务实现与 REST 控制器。
 *
 * @author CH
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "plugin.protocol", name = "enable", havingValue = "true")
@ComponentScan(basePackages = {
        "com.chua.starter.protocol.support.service.impl",
        "com.chua.starter.protocol.support.filter",
        "com.chua.starter.protocol.support.controller.server"
})
public class ProtocolManagementConfiguration {
}
