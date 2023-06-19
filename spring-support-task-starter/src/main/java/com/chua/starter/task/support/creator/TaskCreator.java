package com.chua.starter.task.support.creator;

import com.chua.starter.task.support.pojo.SystemTask;

/**
 * 任务创建器
 *
 * @author CH
 */
public interface TaskCreator<I, O> {
    /**
     * 创建任务ID
     *
     * @param input 输入
     * @return 任务ID
     */
    String createTaskCode(I input);

    /**
     * 执行
     *
     * @param task 任务
     */
    void execute(SystemTask task);

    /**
     * 完成
     *
     * @param task 任务
     */
    void doFinish(SystemTask task);

    /**
     * 失败
     *
     * @param task 任务
     * @param e    e
     */
    void doFailure(SystemTask task, Exception e);
}
