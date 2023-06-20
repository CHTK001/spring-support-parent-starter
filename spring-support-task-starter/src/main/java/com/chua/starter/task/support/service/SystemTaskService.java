package com.chua.starter.task.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.task.support.pojo.SysTask;

/**
 * @author CH
 */
public interface SystemTaskService extends IService<SysTask> {

    /**
     * 更新数据集
     *
     * @param task 任务
     * @return 结果
     */
    int deleteWithId(SysTask task);

    /**
     * 更新数据集
     *
     * @param task 任务
     * @return 结果
     */
    int updateWithId(SysTask task);

    /**
     * 更新数据集
     *
     * @param task 任务
     * @return 结果
     */
    boolean save(SysTask task);

    /**
     * 查询
     *
     * @param page 分页
     * @return 结果
     */
    IPage<SysTask> withPage(Page<SysTask> page);
}
