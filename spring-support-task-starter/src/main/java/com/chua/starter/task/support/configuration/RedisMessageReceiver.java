package com.chua.starter.task.support.configuration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.service.SystemTaskService;
import com.chua.starter.task.support.task.Task;
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
        if (!message.startsWith(Task.PRE)) {
            return;
        }
        message = message.replace(Task.PRE, "");
        SysTask systemTask = systemTaskService.getOne(Wrappers.<SysTask>lambdaQuery().eq(SysTask::getTaskTid, message).last("limit 1"));
        if (null != systemTask) {
            systemTaskService.forUpdate(message, systemTask.isFinish() ? 1 : 0);
        }
    }
}
