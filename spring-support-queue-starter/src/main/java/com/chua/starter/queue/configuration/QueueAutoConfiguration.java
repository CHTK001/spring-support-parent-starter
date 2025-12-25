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
     * 死信队列模板（作为主队列类型）
     * <p>
     * 当配置type=dead-letter时启用，提供自动重试和死信处理功能。
     * </p>
     */
    @Bean("deadLetterMessageTemplate")
    @ConditionalOnProperty(prefix = QueueProperties.PREFIX, name = "type", havingValue = "dead-letter")
    public MessageTemplate deadLetterMessageTemplate() {
        QueueProperties.DeadLetterConfig dlConfig = queueProperties.getDeadLetter();
        DeadLetterTemplate.DeadLetterConfig config = DeadLetterTemplate.DeadLetterConfig.builder()
                .maxRetries(dlConfig.getMaxRetries())
                .retryDelay(java.time.Duration.ofSeconds(dlConfig.getRetryDelaySeconds()))
                .maxRetryDelay(java.time.Duration.ofSeconds(dlConfig.getMaxRetryDelaySeconds()))
                .exponentialBackoff(dlConfig.isExponentialBackoff())
                .backoffMultiplier(dlConfig.getBackoffMultiplier())
                .build();

        // 创建底层消息模板
        MessageTemplate underlyingTemplate = createUnderlyingTemplate(dlConfig.getType());
        log.info(">>>>> 创建死信队列消息模板, maxRetries: {}, underlyingType: {}",
                dlConfig.getMaxRetries(), underlyingTemplate.getType());
        return new DeadLetterTemplate(underlyingTemplate, underlyingTemplate, config);
    }

    /**
     * 死信队列模板（作为辅助Bean）
     * <p>
     * 当主队列不是dead-letter类型时，提供独立的死信队列功能。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterTemplate.class)
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

        // 创建死信队列底层消息模板
        MessageTemplate dlqMessageTemplate = createDlqMessageTemplate(dlConfig, messageTemplate);
        log.info(">>>>> 创建死信队列辅助模板, maxRetries: {}, dlqType: {}",
                dlConfig.getMaxRetries(), dlqMessageTemplate.getType());
        return new DeadLetterTemplate(messageTemplate, dlqMessageTemplate, config);
    }

    /**
     * 创建底层消息模板
     */
    private MessageTemplate createUnderlyingTemplate(String type) {
        String targetType = (type == null || type.isBlank()) ? "memory" : type.toLowerCase();
        return switch (targetType) {
            case "memory" -> new MemoryMessageTemplate(queueProperties.getMemory());
            default -> {
                log.warn("Unsupported underlying type: {}, using memory", type);
                yield new MemoryMessageTemplate(queueProperties.getMemory());
            }
        };
    }

    /**
     * 创建死信队列消息模板
     */
    private MessageTemplate createDlqMessageTemplate(QueueProperties.DeadLetterConfig dlConfig, MessageTemplate defaultTemplate) {
        String dlqType = dlConfig.getType();
        if (dlqType == null || dlqType.isBlank() || dlqType.equals(defaultTemplate.getType())) {
            return defaultTemplate;
        }
        return createUnderlyingTemplate(dlqType);
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
