package com.chua.starter.task.support.creator;

import com.chua.starter.task.support.pojo.SystemTask;
import com.chua.starter.task.support.service.SystemTaskService;

import javax.annotation.Resource;
import java.util.concurrent.Executor;

/**
 * 任务创建器
 *
 * @author CH
 */
public abstract class AbstractTaskCreator<I, O> implements TaskCreator<I, O> {

    @Resource
    private SystemTaskService systemTaskService;

    @Resource
    private Executor executor;

    /**
     * 任务更新
     *
     * @param systemTask 任务更新
     */
    protected void update(SystemTask systemTask) {
        if (systemTask.getTaskStatus() == 1 || systemTask.getTaskCurrent() >= systemTask.getTaskTotal()) {
            try {
                systemTaskService.updateById(systemTask);
            } catch (Exception e) {
                doFailure(systemTask, e);
                return;
            }
            doFinish(systemTask);
            return;
        }

        executor.execute(() -> {
            try {
                systemTaskService.updateById(systemTask);
            } catch (Exception e) {
                doFailure(systemTask, e);
                return;
            }
        });

    }
}
