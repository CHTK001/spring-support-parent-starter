package com.chua.starter.message.support.configuration;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.message.support.push.StationMessagePush;
import com.chua.socket.support.session.SocketSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 消息推送模块自动配置
 * <p>
   * 自动扫描消息模块的 控制器 与 服务，并注册 映射器，提供消息提供商、
   * 消息模板管理及统一消息发送能力。引入 套接字 服务时，额外注入站内消息
 * 渠道的推送执行器。
 * </p>
 *
 * @since 2026/08/03
 * @author CH
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

    /**
      * 注入站内消息渠道的 套接字 推送执行器
     * <p>
      * 仅在引入 套接字 服务（存在 {@link SocketSessionTemplate}）时生效，
     * 将站内消息转发到目标用户的所有在线会话。
     * </p>
     *
     * @param socketSessionTemplate 套接字 会话操作模板
     * @return 站内推送执行器初始化器
     */
    @Bean
    @ConditionalOnClass(name = "com.chua.socket.support.session.SocketSessionTemplate")
    @ConditionalOnBean(SocketSessionTemplate.class)
    public ApplicationRunner stationMessagePusherInitializer(SocketSessionTemplate socketSessionTemplate) {
        return args -> StationMessagePush.setPusher((userId, payload) ->
                socketSessionTemplate.sendToUser(userId, "message", payload));
    }
}
