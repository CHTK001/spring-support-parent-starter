package com.chua.starter.task.support.configuration;

import com.chua.starter.common.support.annotations.EnableAutoTable;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.properties.TaskProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import javax.annotation.Resource;

/**
 * @author CH
 */
@ComponentScan("com.chua.starter.task.support")
@MapperScan("com.chua.starter.task.support.mapper")
@EnableAutoTable(packageType = SysTask.class)
@EnableConfigurationProperties(TaskProperties.class)
public class TaskConfiguration {

    @Resource
    RedisConnectionFactory factory;

    /**
     * redis 订阅频道
     * <p>
     * //RedisConnectionFactory connectionFactory
     *
     * @param listenerAdapter
     * @return
     */
    @Bean
    public RedisMessageListenerContainer container(MessageListenerAdapter listenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        // 订阅通道，key过期时通知
        container.addMessageListener(listenerAdapter, new PatternTopic("__keyevent@0__:expired"));
        container.addMessageListener(listenerAdapter, new PatternTopic("__keyevent@1__:expired"));
        // 可以订阅多个通道

        return container;
    }

    /**
     * 配置redis事件监听处理器
     *
     * @param receiver
     * @return
     */
    @Bean
    public MessageListenerAdapter listenerAdapter(RedisMessageReceiver receiver) {
        return new MessageListenerAdapter(receiver, "receiveMessage");
    }
}
