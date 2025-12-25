package com.chua.starter.queue.rocketmq;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.properties.QueueProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QueueProperties.class)
public class RocketQueueAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MessageTemplate rocketMessageTemplate(RocketMQTemplate rocketMQTemplate, QueueProperties props) {
        return new RocketMessageTemplate(rocketMQTemplate, props);
    }
}
