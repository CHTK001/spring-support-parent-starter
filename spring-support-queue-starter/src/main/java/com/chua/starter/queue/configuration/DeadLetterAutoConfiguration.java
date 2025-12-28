package com.chua.starter.queue.configuration;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.properties.DeadLetterProperties;
import com.chua.starter.queue.properties.QueueProperties;
import com.chua.starter.queue.template.DeadLetterTemplate;
import com.chua.starter.queue.template.MemoryMessageTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
 * 死信队列自动配置
 * <p>
 * 独立于主队列的死信队列配置，使用 plugin.dead-letter 前缀。
 * </p>
 *
 * @author CH
 * @since 2025-12-25
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DeadLetterProperties.class)
@ConditionalOnProperty(prefix = DeadLetterProperties.PREFIX, name = "enable", havingValue = "true")
public class DeadLetterAutoConfiguration {

    @Autowired
    private DeadLetterProperties deadLetterProperties;

    /**
     * 死信队列消息模板
     */
    @Bean
    public DeadLetterTemplate deadLetterTemplate(@Autowired(required = false) MessageTemplate mainMessageTemplate) {
        // 创建死信队列底层存储
        MessageTemplate dlqStorage = createDlqStorage();

        DeadLetterTemplate.DeadLetterConfig config = DeadLetterTemplate.DeadLetterConfig.builder()
                .maxRetries(deadLetterProperties.getMaxRetries())
                .retryDelay(java.time.Duration.ofSeconds(deadLetterProperties.getRetryDelaySeconds()))
                .maxRetryDelay(java.time.Duration.ofSeconds(deadLetterProperties.getMaxRetryDelaySeconds()))
                .exponentialBackoff(deadLetterProperties.isExponentialBackoff())
                .backoffMultiplier(deadLetterProperties.getBackoffMultiplier())
                .build();

        // 如果有主队列，使用主队列作为消息源；否则使用死信队列存储
        MessageTemplate sourceTemplate = mainMessageTemplate != null ? mainMessageTemplate : dlqStorage;

        log.info("[Queue] 创建死信队列模板, 类型: {}, 最大重试: {}, 队列容量: {}",
                highlight(deadLetterProperties.getType()), highlight(deadLetterProperties.getMaxRetries()), highlight(deadLetterProperties.getQueueCapacity()));
        return new DeadLetterTemplate(sourceTemplate, dlqStorage, config);
    }

    /**
     * 创建死信队列底层存储
     */
    private MessageTemplate createDlqStorage() {
        String type = deadLetterProperties.getType();
        return switch (type != null ? type.toLowerCase() : "memory") {
            case "memory" -> {
                QueueProperties.MemoryConfig memoryConfig = new QueueProperties.MemoryConfig();
                memoryConfig.setQueueCapacity(deadLetterProperties.getQueueCapacity());
                memoryConfig.setSendTimeout(deadLetterProperties.getSendTimeout());
                memoryConfig.setDelayThreads(deadLetterProperties.getDelayThreads());
                yield new MemoryMessageTemplate(memoryConfig);
            }
            default -> {
                log.warn("Unsupported dead-letter queue type: {}, using memory", type);
                QueueProperties.MemoryConfig memoryConfig = new QueueProperties.MemoryConfig();
                memoryConfig.setQueueCapacity(deadLetterProperties.getQueueCapacity());
                memoryConfig.setSendTimeout(deadLetterProperties.getSendTimeout());
                memoryConfig.setDelayThreads(deadLetterProperties.getDelayThreads());
                yield new MemoryMessageTemplate(memoryConfig);
            }
        };
    }
}
