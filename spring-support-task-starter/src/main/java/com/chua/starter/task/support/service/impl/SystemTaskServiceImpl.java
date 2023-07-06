package com.chua.starter.task.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.eventbus.EventbusType;
import com.chua.common.support.eventbus.Subscribe;
import com.chua.common.support.log.Log;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.CacheConfiguration;
import com.chua.starter.common.support.eventbus.EventbusTemplate;
import com.chua.starter.task.support.mapper.SystemTaskMapper;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.pojo.TaskStatus;
import com.chua.starter.task.support.service.SystemTaskService;
import com.chua.starter.task.support.task.Task;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * @author CH
 */
@Service
public class SystemTaskServiceImpl implements SystemTaskService, CommandLineRunner {

    private static final Log log = Log.getLogger(Task.class);
    @Resource
    private EventbusTemplate eventbusTemplate;

    private SystemTaskMapper baseMapper;

    @Resource(name = com.chua.common.support.protocol.server.Constant.STRING_REDIS)
    private StringRedisTemplate redisTemplate;

    public SystemTaskServiceImpl(SystemTaskMapper systemTaskMapper) {
        this.baseMapper = systemTaskMapper;
    }

    @Override
    @CacheEvict(cacheManager = CacheConfiguration.DEFAULT_CACHE_MANAGER, cacheNames = "task", key = "#task.taskId")
    public int deleteWithId(String taskTid) {
        int i = baseMapper.delete(Wrappers.<SysTask>lambdaQuery().eq(SysTask::getTaskTid, taskTid));
        if (i > 0) {
            eventbusTemplate.post("task", taskTid);
        }
        return i;
    }

    @Override
    @CacheEvict(cacheManager = CacheConfiguration.DEFAULT_CACHE_MANAGER, cacheNames = "task", key = "#task.taskId")
    public int updateWithId(SysTask task) {
        task.setTaskTid(null);
        task.setTaskTotal(null);
        task.setTaskCurrent(null);
        task.setTaskStatus(null);
        int i = baseMapper.updateById(task);
        if (i > 0) {
            eventbusTemplate.post("task", task);
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
            eventbusTemplate.post("task", task);
        }
        return b;
    }

    @Override
    public Page<SysTask> withPage(Page<SysTask> page) {
        Page<SysTask> sysTaskPage = baseMapper.selectPage(page, Wrappers.lambdaQuery());
        List<SysTask> records = sysTaskPage.getRecords();
        ListOperations listOperations = redisTemplate.opsForList();
        for (SysTask record : records) {
            Long size = listOperations.size(record.getKey());
            if(null != size) {
                record.setTaskCurrent(Math.toIntExact(size));
            }
        }
        return sysTaskPage;
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

    @Override
    public void reset(String taskTid) {
        SysTask systemTask = getTaskByTaskTid(taskTid);
        if (null == systemTask) {
            return;
        }

        systemTask.setTaskStatus(0);
        systemTask.setTaskCurrent(0);
        baseMapper.updateById(systemTask);
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
            systemTask.setTaskCurrent(systemTask.getTaskTotal());
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

    @Override
    public void run(String... args) throws Exception {
        log.info("开始装载任务");
        List<SysTask> sysTasks = list(Wrappers.<SysTask>lambdaQuery()
                .in(SysTask::getTaskStatus, 0, 2));
        for (SysTask sysTask : sysTasks) {
            eventbusTemplate.post("task", sysTask);
        }
        log.info("装载完成");
    }


    @Subscribe(type = EventbusType.GUAVA, name = "reset")
    public void forReset(String taskTid) {
        try {
            reset(taskTid);
        } catch (Exception ignored) {
        }
    }

    @Subscribe(type = EventbusType.GUAVA, name = "update")
    public void update(Map.Entry<String, Integer> entry) {
        try {
            SysTask taskByTaskTid = getTaskByTaskTid(entry.getKey());
            taskByTaskTid.setTaskCurrent(entry.getValue());
            updateWithId(taskByTaskTid);
        } catch (Exception ignored) {
        }
    }
}
