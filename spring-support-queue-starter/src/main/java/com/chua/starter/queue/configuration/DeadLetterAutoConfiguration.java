package com.chua.starter.queue.configuration;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.properties.DeadLetterProperties;
import com.chua.starter.queue.properties.QueueProperties;
import com.chua.starter.queue.template.DeadLetterTemplate;
import com.chua.starter.queue.template.LocalMessageTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeadLetterAutoConfiguration.class);

    @Autowired
    private DeadLetterProperties deadLetterProperties;

    /**
     * 死信队列消息模板
     */
    @Bean(destroyMethod = "close")
    public DeadLetterTemplate deadLetterTemplate(@Autowired(required = false) MessageTemplate mainMessageTemplate) {
        // 创建死信队列底层存储
        MessageTemplate dlqStorage = createDlqStorage();

        // 使用 AllArgsConstructor 创建配置对象
        var config = new DeadLetterTemplate.DeadLetterConfig(
                deadLetterProperties.getMaxRetries(),
                java.time.Duration.ofSeconds(deadLetterProperties.getRetryDelaySeconds()),
                java.time.Duration.ofSeconds(deadLetterProperties.getMaxRetryDelaySeconds()),
                deadLetterProperties.isExponentialBackoff(),
                deadLetterProperties.getBackoffMultiplier()
        );

        // 如果有主队列，使用主队列作为消息源；否则使用死信队列存储
        MessageTemplate sourceTemplate = mainMessageTemplate != null ? mainMessageTemplate : dlqStorage;

        log.info("[Queue] 创建死信队列模板, 类型: {}, 最大重试: {}",
                deadLetterProperties.getType(), deadLetterProperties.getMaxRetries());
        return new DeadLetterTemplate(sourceTemplate, dlqStorage, config);
    }

    /**
     * 创建死信队列底层存储
     */
    private MessageTemplate createDlqStorage() {
        String type = deadLetterProperties.getType();
        return switch (type != null ? type.toLowerCase() : "local") {
            case "local" -> {
                QueueProperties.LocalConfig localConfig = new QueueProperties.LocalConfig();
                localConfig.setDelayThreads(deadLetterProperties.getDelayThreads());
                yield new LocalMessageTemplate(localConfig);
            }
            default -> {
                log.warn("Unsupported dead-letter queue type: {}, using local", type);
                QueueProperties.LocalConfig localConfig = new QueueProperties.LocalConfig();
                localConfig.setDelayThreads(deadLetterProperties.getDelayThreads());
                yield new LocalMessageTemplate(localConfig);
            }
        };
    }
}
