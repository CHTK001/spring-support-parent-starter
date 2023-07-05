package com.chua.starter.task.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.CacheConfiguration;
import com.chua.starter.task.support.mapper.SystemTaskMapper;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.pojo.TaskStatus;
import com.chua.starter.task.support.service.SystemTaskService;
import com.google.common.eventbus.AsyncEventBus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author CH
 */
@Service
public class SystemTaskServiceImpl implements SystemTaskService {


    @Resource
    private AsyncEventBus eventBus;

    @Resource
    private SystemTaskMapper baseMapper;

    @Resource
    private RedisTemplate redisTemplate;

    @Override
    @CacheEvict(cacheManager = CacheConfiguration.DEFAULT_CACHE_MANAGER, cacheNames = "task", key = "#task.taskTid")
    public int deleteWithId(String taskTid) {
        int i = baseMapper.delete(Wrappers.<SysTask>lambdaQuery().eq(SysTask::getTaskTid, taskTid));
        if (i > 0) {
            eventBus.post(taskTid);
        }
        return i;
    }

    @Override
    @CacheEvict(cacheManager = CacheConfiguration.DEFAULT_CACHE_MANAGER, cacheNames = "task", key = "#task.taskTid")
    public int updateWithId(SysTask task) {
        task.setTaskTid(null);
        task.setTaskTotal(null);
        int i = baseMapper.updateById(task);
        if (i > 0) {
            eventBus.post(task);
        }
        return i;
    }

    @Override
    @CacheEvict(cacheManager = CacheConfiguration.DEFAULT_CACHE_MANAGER, cacheNames = "task", key = "#task.taskTid")
    public boolean save(SysTask task) {
        if(null == task.getTaskTotal() || task.getTaskTotal() <= 0) {
            throw new RuntimeException("总量不能为空");
        }

        if(StringUtils.isEmpty(task.getTaskType())) {
            throw new RuntimeException("任务类型不能为空");
        }


        if(StringUtils.isEmpty(task.getTaskCid())) {
            throw new RuntimeException("任务实现不能为空");
        }

        task.setTaskTid(
                DigestUtils.md5Hex(
                        task.getTaskTotal() +
                        task.getTaskType() +
                        task.getTaskBusiness() +
                        task.getTaskCid() +
                        task.getTaskParams()));
        SysTask byTaskTid = getTaskByTaskTid(task.getTaskTid());
        if(null != byTaskTid) {
            throw new RuntimeException("有相同任务: " + task.getTaskTid());
        }

        boolean b = 1 == baseMapper.insert(task);
        if (b) {
            eventBus.post(task);
        }
        return b;
    }

    @Override
    public Page<SysTask> withPage(Page<SysTask> page) {
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
    @Cacheable(cacheManager = CacheConfiguration.DEFAULT_CACHE_MANAGER, cacheNames = "task", key = "#taskTid")
    public SysTask getTaskByTaskTid(String taskTid) {
        return baseMapper.selectOne(Wrappers.<SysTask>lambdaQuery().eq(SysTask::getTaskTid, taskTid));
    }

    @Override
    public TaskStatus getTaskByTaskStatus(String taskTid) {
        SysTask taskByTaskTid = getTaskByTaskTid(taskTid);
        return new TaskStatus(taskTid, Math.toIntExact(redisTemplate.opsForList().size(taskByTaskTid.getKey())), taskByTaskTid.getTaskTotal());
    }

    @Override
    public List<SysTask> list(LambdaQueryWrapper<SysTask> wrapper) {
        return baseMapper.selectList(wrapper);
    }

    private synchronized void doUpdateForSize(Integer taskId, int size, int count) {
        if (count <= 0) {
            return;
        }

        SysTask systemTask = baseMapper.selectById(taskId);
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

        SysTask systemTask = baseMapper.selectById(taskId);
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
