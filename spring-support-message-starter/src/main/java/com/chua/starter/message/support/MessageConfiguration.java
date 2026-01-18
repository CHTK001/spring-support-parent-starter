package com.chua.starter.message.support;

import com.chua.starter.message.support.properties.MessageProperties;
import com.chua.starter.message.support.template.MessageTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 消息推送自动配置
 *
 * @author CH
 * @since 2024/12/26
 */
@Slf4j
@EnableConfigurationProperties(MessageProperties.class)
@ConditionalOnProperty(prefix = MessageProperties.PREFIX, name = "enable", havingValue = "true")
public class MessageConfiguration {

    /**
     * 创建消息模板Bean
     *
     * @param properties 消息配置属性
     * @return MessageTemplate
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public MessageTemplate messageTemplate(MessageProperties properties) {
        log.info("[Message] 初始化消息推送模板");
        return new MessageTemplate(properties);
    }
}
