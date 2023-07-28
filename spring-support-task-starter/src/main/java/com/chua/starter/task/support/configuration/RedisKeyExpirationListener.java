package com.chua.starter.task.support.configuration;

import com.chua.starter.task.support.service.SystemTaskService;
import com.chua.starter.task.support.task.Task;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import javax.annotation.Resource;

/**
 * redis
 *
 * @author CH
 */
@Slf4j
public class RedisKeyExpirationListener extends KeyExpirationEventMessageListener {

    @Resource
    private SystemTaskService systemTaskService;
    public RedisKeyExpirationListener(@Autowired RedisMessageListenerContainer listenerContainer) {
        super(listenerContainer);
    }

    /**
     * 针对redis数据失效事件，进行数据处理
     */
    @SneakyThrows
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String redisId = message.toString();
        if(redisId.startsWith(Task.SUBSCRIBE)) {
            systemTaskService.expire(redisId.substring(redisId.lastIndexOf(":") + 1));
        }
        log.info("redis失效id:{}",redisId);
    }
}
