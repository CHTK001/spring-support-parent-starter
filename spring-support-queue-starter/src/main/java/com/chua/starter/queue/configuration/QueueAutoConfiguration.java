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
    @Bean("memoryMessageTemplate")
    @ConditionalOnProperty(prefix = QueueProperties.PREFIX, name = "type", havingValue = "memory", matchIfMissing = true)
    public MessageTemplate memoryMessageTemplate() {
        log.info(">>>>> 创建内存消息队列模板");
        return new MemoryMessageTemplate(queueProperties.getMemory());
    }

    /**
     * 死信队列模板
     * <p>
     * 当配置type=dead-letter时启用，基于内存队列实现，提供自动重试和死信处理功能。
     * </p>
     */
    @Bean("deadLetterMessageTemplate")
    @ConditionalOnProperty(prefix = QueueProperties.PREFIX, name = "type", havingValue = "dead-letter")
    public MessageTemplate deadLetterMessageTemplate() {
        QueueProperties.DeadLetterConfig dlConfig = queueProperties.getDeadLetter();

        // 创建底层内存队列
        QueueProperties.MemoryConfig memoryConfig = new QueueProperties.MemoryConfig();
        memoryConfig.setQueueCapacity(dlConfig.getQueueCapacity());
        memoryConfig.setSendTimeout(dlConfig.getSendTimeout());
        memoryConfig.setDelayThreads(dlConfig.getDelayThreads());
        MessageTemplate underlyingTemplate = new MemoryMessageTemplate(memoryConfig);

        DeadLetterTemplate.DeadLetterConfig config = DeadLetterTemplate.DeadLetterConfig.builder()
                .maxRetries(dlConfig.getMaxRetries())
                .retryDelay(java.time.Duration.ofSeconds(dlConfig.getRetryDelaySeconds()))
                .maxRetryDelay(java.time.Duration.ofSeconds(dlConfig.getMaxRetryDelaySeconds()))
                .exponentialBackoff(dlConfig.isExponentialBackoff())
                .backoffMultiplier(dlConfig.getBackoffMultiplier())
                .build();

        log.info(">>>>> 创建死信队列模板, maxRetries: {}, queueCapacity: {}",
                dlConfig.getMaxRetries(), dlConfig.getQueueCapacity());
        return new DeadLetterTemplate(underlyingTemplate, underlyingTemplate, config);
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
