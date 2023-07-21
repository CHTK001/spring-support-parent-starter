package com.chua.starter.task.support.task;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.annotations.SpiOption;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.task.support.params.TaskParam;
import com.chua.starter.task.support.pojo.SysTask;

/**
 * @author CH
 */
@Spi("task")
@SpiOption(value = "测试任务", type = "empty")
public class EmptyTask extends Task{
    @Override
    protected void execute(long offset, TaskParam taskParam) {
        log.info("空任务: {}", offset);
        ThreadUtils.sleepSecondsQuietly(2);
        step(1);
    }

    @Override
    protected void finish(SysTask sysTask) {

    }
}
