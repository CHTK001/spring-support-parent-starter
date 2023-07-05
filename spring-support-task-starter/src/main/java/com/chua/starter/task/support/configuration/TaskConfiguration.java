package com.chua.starter.task.support.configuration;

import com.chua.starter.common.support.annotations.EnableAutoTable;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.task.support.manager.TaskManager;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.properties.TaskProperties;
import com.google.common.eventbus.AsyncEventBus;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import javax.annotation.Resource;
import java.util.concurrent.Executor;

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
     * TaskManager
     * <p>
     *
     * @param redisTemplate TaskManager
     * @return
     */
    @Bean
    public TaskManager taskManager(@Qualifier(com.chua.common.support.protocol.server.Constant.STRING_REDIS) StringRedisTemplate redisTemplate) {
        return new TaskManager(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncEventBus eventBus(@Qualifier(Constant.DEFAULT_EXECUTOR2) Executor executor) {
        return new AsyncEventBus(executor);
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
