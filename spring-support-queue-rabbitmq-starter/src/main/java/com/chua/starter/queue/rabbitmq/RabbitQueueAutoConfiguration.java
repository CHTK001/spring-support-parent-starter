package com.chua.starter.queue.rabbitmq;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.properties.QueueProperties;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QueueProperties.class)
public class RabbitQueueAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CachingConnectionFactory rabbitConnectionFactory(QueueProperties props) {
        QueueProperties.RabbitMQConfig conf = props.getRabbitmq();
        CachingConnectionFactory factory = new CachingConnectionFactory(conf.getHost(), conf.getPort());
        factory.setUsername(conf.getUsername());
        factory.setPassword(conf.getPassword());
        factory.setVirtualHost(conf.getVirtualHost());
        factory.setChannelCheckoutTimeout(conf.getConnectionTimeout());
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean
    public AmqpTemplate amqpTemplate(CachingConnectionFactory factory) {
        return new RabbitTemplate(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageTemplate rabbitMessageTemplate(AmqpTemplate amqpTemplate, ConnectionFactory connectionFactory, QueueProperties props) {
        return new RabbitMessageTemplate(amqpTemplate, connectionFactory, props);
    }
}
