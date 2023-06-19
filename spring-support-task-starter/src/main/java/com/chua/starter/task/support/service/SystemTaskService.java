package com.chua.starter.task.support.service;

import com.chua.starter.task.support.pojo.SystemTask;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author CH
 */
public interface SystemTaskService extends IService<SystemTask> {
    /**
     * 创建系统任务
     *
     * @param type 创建方式
     * @return 系统任务
     */
    String createSystemTask(String type);
}
