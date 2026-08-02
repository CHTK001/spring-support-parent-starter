package com.chua.starter.message.support.configuration;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 消息推送模块自动配置
 * <p>
 * 自动扫描消息模块的 Controller 与 Service，并注册 Mapper，提供消息提供商、
 * 消息模板管理及统一消息发送能力。
 * </p>
 *
 * @author CH
 * @since 2026/08/03
 */
@AutoConfiguration
@MapperScan("com.chua.starter.message.support.mapper")
@ComponentScan("com.chua.starter.message.support.controller")
@EnableConfigurationProperties(MessageTrackProperties.class)
public class MessageAutoConfiguration {

    /**
     * 注册消息模块的服务信息贡献者
     *
     * @return 服务信息贡献者
     */
    @Bean
    public ServiceInfoContributor messageServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("消息", "消息推送", "/v2/message/**", "消息推送模块");
            registry.registerFlag("message", "消息", true, "消息推送模块");
        };
    }
}
