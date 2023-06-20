package com.chua.starter.task.support.creator;

import com.chua.common.support.utils.IdUtils;
import com.chua.starter.task.support.pojo.SysTask;

/**
 * 任务创建器
 *
 * @author CH
 */
public abstract class AbstractSimpleTaskCreator<I> extends AbstractTaskCreator<I, String> {

    @Override
    public String createTaskCode(I input) {
        return IdUtils.createTid();
    }

    @Override
    public void doFinish(SysTask task) {

    }

    @Override
    public void doFailure(SysTask task, Exception e) {

    }
}
