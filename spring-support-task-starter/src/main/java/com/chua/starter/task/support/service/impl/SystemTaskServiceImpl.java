package com.chua.starter.task.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.task.support.mapper.SystemTaskMapper;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.service.SystemTaskService;
import com.google.common.eventbus.AsyncEventBus;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author CH
 */
@Service
public class SystemTaskServiceImpl extends ServiceImpl<SystemTaskMapper, SysTask> implements SystemTaskService {


    @Resource
    private AsyncEventBus eventBus;

    @Override
    public int deleteWithId(SysTask task) {
        int i = baseMapper.deleteById(task);
        if (i > 0) {
            eventBus.post(task.getTaskTid());
        }
        return i;
    }

    @Override
    public int updateWithId(SysTask task) {
        int i = baseMapper.updateById(task);
        if (i > 0) {
            eventBus.post(task);
        }
        return i;
    }

    @Override
    public boolean save(SysTask task) {
        boolean b = 1 == baseMapper.insert(task);
        if (b) {
            eventBus.post(task);
        }
        return b;
    }

    @Override
    public IPage<SysTask> withPage(Page<SysTask> page) {
        return baseMapper.selectPage(page, Wrappers.lambdaQuery());
    }

    @Override
    public void forUpdate(String taskId, int status) {
        doUpdate(taskId, status, 3);
    }

    @Override
    public void forUpdateCurrent(Integer taskId, int size) {
        doUpdateForSize(taskId, size, 3);
    }

    @Override
    public SysTask getTaskByTaskTid(String taskTid) {
        return baseMapper.selectOne(Wrappers.<SysTask>lambdaQuery().eq(SysTask::getTaskTid, taskTid));
    }

    private synchronized void doUpdateForSize(Integer taskId, int size, int count) {
        if (count <= 0) {
            return;
        }

        SysTask systemTask = getById(taskId);
        if (null == systemTask) {
            return;
        }

        systemTask.setTaskCurrent(size);
        if (size >= systemTask.getTaskTotal()) {
            systemTask.setTaskStatus(1);
        }

        try {
            updateWithId(systemTask);
        } catch (Exception e) {
            e.printStackTrace();
            doUpdateForSize(taskId, size, count--);
        }
    }

    public synchronized void doUpdate(String taskId, int status, int count) {
        if (count <= 0) {
            return;
        }

        SysTask systemTask = getById(taskId);
        if (null == systemTask) {
            return;
        }

        systemTask.setTaskStatus(status);
        try {
            updateWithId(systemTask);
        } catch (Exception e) {
            e.printStackTrace();
            doUpdate(taskId, status, count--);
        }
    }
}
