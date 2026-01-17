package com.chua.starter.queue.configuration;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.properties.QueueProperties;
import com.chua.starter.queue.template.LocalMessageTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QueueAutoConfiguration.class);

    @Autowired
    private QueueProperties queueProperties;

    /**
     * 本地消息队列模板（基于Guava EventBus）
     * <p>
     * 当配置type=local时启用，基于Guava EventBus实现。
     * 默认使用本地消息队列（当未指定type或type=local时）。
     * </p>
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(MessageTemplate.class)
    @ConditionalOnProperty(prefix = QueueProperties.PREFIX, name = "type", havingValue = "local", matchIfMissing = true)
    public MessageTemplate localMessageTemplate() {
        log.info("[Queue] 创建本地消息队列模板 (基于Guava EventBus)");
        return new LocalMessageTemplate(queueProperties.getLocal());
    }

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
