package com.chua.starter.queue.configuration;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.properties.QueueProperties;
import com.chua.starter.queue.template.LocalMessageTemplate;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
 * 消息队列自动配置
 *
 * @since 2025-12-25
 * @author CH
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(QueueProperties.class)
@ConditionalOnProperty(prefix = QueueProperties.PREFIX, name = "enable", havingValue = "true")
@ComponentScan("com.chua.starter.queue")
public class QueueAutoConfiguration {

    @Autowired
    /**
     * 队列属性
    */
    private QueueProperties queueProperties;

    /**
     * 本地消息队列模板（基于Guava 事件bus）
     * <p>
     * 当配置类型=本地时启用，基于Guava 事件bus实现。
     * </p>
     * @return 本地消息template的结果
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(MessageTemplate.class)
    @ConditionalOnProperty(prefix = QueueProperties.PREFIX, name = "type", havingValue = "local")
    public MessageTemplate localMessageTemplate() {
        log.info("[Queue] 创建本地消息队列模板 (基于Guava EventBus)");
        return new LocalMessageTemplate(queueProperties.getLocal());
    }

    /**
     * 消息监听注解处理器
     *
     * @param messageTemplates 消息模板列表（可空）
     * @param eventPublisher   事件发布器
     * @return 消息监听器 Bean 后置处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageListenerBeanPostProcessor messageListenerBeanPostProcessor(
            @Autowired(required = false) List<MessageTemplate> messageTemplates,
            ApplicationEventPublisher eventPublisher) {
        return new MessageListenerBeanPostProcessor(messageTemplates, queueProperties, eventPublisher);
    }

    @Bean
    /**
     * 队列服务信息contributor
     *
     * @return 队列服务信息contributor的结果
     */
    public ServiceInfoContributor queueServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("queue", "消息队列", true, "消息队列模块");
        };
    }
}
