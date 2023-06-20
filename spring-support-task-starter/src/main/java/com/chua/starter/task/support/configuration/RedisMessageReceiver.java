package com.chua.starter.task.support.configuration;

import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.service.SystemTaskService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * redis
 *
 * @author CH
 */
@Component
public class RedisMessageReceiver {

    @Resource
    private SystemTaskService systemTaskService;

    /**
     * 接收redis消息，并处理
     *
     * @param message 过期的redis key
     */
    public void receiveMessage(String message) {
        System.out.println("通知的key是：" + message);
        if (!message.endsWith("$000000000000000000")) {
            return;
        }
        message = message.replace("$000000000000000000", "");
        SysTask systemTask = systemTaskService.getById(message);
        if (null != systemTask) {
            if (systemTask.isFinish()) {
                systemTask.setTaskStatus(1);
            } else {
                systemTask.setTaskStatus(0);
            }
            systemTaskService.updateById(systemTask);
        }
    }
}
