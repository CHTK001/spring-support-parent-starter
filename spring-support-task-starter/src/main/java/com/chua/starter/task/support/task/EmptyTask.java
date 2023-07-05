package com.chua.starter.task.support.task;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.annotations.SpiOption;
import com.chua.starter.task.support.manager.TaskManager;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.pojo.TaskParam;

/**
 * @author CH
 */
@Spi("task")
@SpiOption(value = "测试任务", type = "empty")
public class EmptyTask extends Task{
    public EmptyTask(SysTask sysTask, TaskManager taskManager) {
        super(sysTask, taskManager);
    }

    @Override
    void execute(long offset, TaskParam taskParam) {
        log.info("空任务");
        step("1");
    }
}
