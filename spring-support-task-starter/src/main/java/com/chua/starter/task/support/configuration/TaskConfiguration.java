package com.chua.starter.task.support.configuration;

import ch.rasc.sse.eventbus.config.EnableSseEventBus;
import com.chua.starter.common.support.annotations.EnableAutoTable;
import com.chua.starter.common.support.configuration.ZzeroAutoTableConfiguration;
import com.chua.starter.task.support.manager.TaskManager;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.properties.TaskProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * @author CH
 */
@AutoConfigureAfter({DataSourceAutoConfiguration.class, ZzeroAutoTableConfiguration.class})
@ComponentScan(value = "com.chua.starter.task.support")
@MapperScan("com.chua.starter.task.support.mapper")
@EnableAutoTable(packageType = SysTask.class)
@EnableConfigurationProperties(TaskProperties.class)
@EnableSseEventBus
@Lazy
public class TaskConfiguration {


    @Bean
    public TaskManager taskManager(@Qualifier(com.chua.common.support.protocol.server.Constant.STRING_REDIS) StringRedisTemplate redisTemplate) {
        return new TaskManager(redisTemplate);
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RedisMessageReceiver receiver) {
        return new MessageListenerAdapter(receiver, "receiveMessage");
    }


}
