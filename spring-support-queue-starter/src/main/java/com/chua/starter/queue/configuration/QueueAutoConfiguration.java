package com.chua.starter.queue.configuration;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.properties.QueueProperties;
import com.chua.starter.queue.template.DeadLetterTemplate;
import com.chua.starter.queue.template.MemoryMessageTemplate;
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
     * 内存消息队列模板
     * <p>
     * 当配置type=memory时启用，基于JDK的BlockingQueue实现。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(MessageTemplate.class)
    @ConditionalOnProperty(prefix = QueueProperties.PREFIX, name = "type", havingValue = "memory", matchIfMissing = true)
    public MessageTemplate memoryMessageTemplate() {
        log.info(">>>>> 创建内存消息队列模板");
        return new MemoryMessageTemplate(queueProperties.getMemory());
    }

    /**
     * 死信队列模板
     * <p>
     * 提供消息重试和死信队列功能。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = QueueProperties.PREFIX + ".dead-letter", name = "enable", havingValue = "true", matchIfMissing = true)
    public DeadLetterTemplate deadLetterTemplate(MessageTemplate messageTemplate) {
        QueueProperties.DeadLetterConfig dlConfig = queueProperties.getDeadLetter();
        DeadLetterTemplate.DeadLetterConfig config = DeadLetterTemplate.DeadLetterConfig.builder()
                .maxRetries(dlConfig.getMaxRetries())
                .retryDelay(java.time.Duration.ofSeconds(dlConfig.getRetryDelaySeconds()))
                .maxRetryDelay(java.time.Duration.ofSeconds(dlConfig.getMaxRetryDelaySeconds()))
                .exponentialBackoff(dlConfig.isExponentialBackoff())
                .backoffMultiplier(dlConfig.getBackoffMultiplier())
                .build();
        log.info(">>>>> 创建死信队列模板, maxRetries: {}", dlConfig.getMaxRetries());
        return new DeadLetterTemplate(messageTemplate, config);
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
