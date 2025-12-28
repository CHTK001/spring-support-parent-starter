package com.chua.starter.queue.configuration;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.properties.QueueProperties;
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
        log.info("[Queue] 创建内存消息队列模板, 队列容量: {}", highlight(queueProperties.getMemory().getQueueCapacity()));
        return new MemoryMessageTemplate(queueProperties.getMemory());
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
