package com.chua.starter.task.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.task.support.pojo.SystemTask;

/**
 * @author CH
 */
public interface SystemTaskService extends IService<SystemTask> {

    /**
     * 更新数据集
     *
     * @param task 任务
     * @return 结果
     */
    int deleteWithId(SystemTask task);

    /**
     * 更新数据集
     *
     * @param task 任务
     * @return 结果
     */
    int updateWithId(SystemTask task);

    /**
     * 更新数据集
     *
     * @param task 任务
     * @return 结果
     */
    boolean save(SystemTask task);

    /**
     * 查询
     *
     * @param page 分页
     * @return 结果
     */
    IPage<SystemTask> withPage(Page<SystemTask> page);
}
