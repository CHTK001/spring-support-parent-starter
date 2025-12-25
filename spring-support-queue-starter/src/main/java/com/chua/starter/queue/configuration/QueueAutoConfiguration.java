package com.chua.starter.queue.configuration;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.properties.QueueProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 消息队列自动配置
 *
 * @author CH
 * @since 2025-12-25
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(QueueProperties.class)
@ConditionalOnProperty(prefix = QueueProperties.PREFIX, name = "enable", havingValue = "true")
@ComponentScan("com.chua.starter.queue")
public class QueueAutoConfiguration {

    @Autowired
    private QueueProperties queueProperties;

    /**
     * 消息监听注解处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageListenerBeanPostProcessor messageListenerBeanPostProcessor(
            @Autowired(required = false) List<MessageTemplate> messageTemplates) {
        return new MessageListenerBeanPostProcessor(messageTemplates, queueProperties);
    }
}
