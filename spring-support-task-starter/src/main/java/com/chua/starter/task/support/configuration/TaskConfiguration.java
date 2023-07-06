package com.chua.starter.task.support.configuration;

import ch.rasc.sse.eventbus.config.EnableSseEventBus;
import com.chua.starter.common.support.annotations.EnableAutoTable;
import com.chua.starter.common.support.configuration.ZzeroAutoTableConfiguration;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.task.support.manager.TaskManager;
import com.chua.starter.task.support.mapper.SystemTaskMapper;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.properties.TaskProperties;
import com.chua.starter.task.support.service.SystemTaskService;
import com.chua.starter.task.support.service.impl.SystemTaskServiceImpl;
import com.google.common.eventbus.AsyncEventBus;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.util.concurrent.Executor;

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
    @Lazy
    @DependsOn({"systemTaskService"})
    public TaskManager taskManager(@Qualifier(com.chua.common.support.protocol.server.Constant.STRING_REDIS) StringRedisTemplate redisTemplate, SystemTaskService systemTaskService) {
        return new TaskManager(redisTemplate, systemTaskService);
    }

    @Bean
    @Lazy
    @DependsOn("systemTaskMapper")
    public SystemTaskService systemTaskService(SystemTaskMapper systemTaskMapper) {
        return new SystemTaskServiceImpl(systemTaskMapper);
    }

    @Bean
    @Lazy
    @DependsOn("taskManager")
    @ConditionalOnMissingBean
    public AsyncEventBus asyncEventBus(@Qualifier(Constant.DEFAULT_EXECUTOR2) Executor executor, TaskManager taskManager) {
        AsyncEventBus asyncEventBus = new AsyncEventBus(executor);
        asyncEventBus.register(taskManager);
        return asyncEventBus;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RedisMessageReceiver receiver) {
        return new MessageListenerAdapter(receiver, "receiveMessage");
    }


}
