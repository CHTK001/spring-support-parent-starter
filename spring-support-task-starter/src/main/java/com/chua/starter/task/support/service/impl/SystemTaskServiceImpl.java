package com.chua.starter.task.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.eventbus.EventbusType;
import com.chua.common.support.eventbus.Subscribe;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.log.Log;
import com.chua.common.support.utils.*;
import com.chua.starter.common.support.configuration.CacheConfiguration;
import com.chua.starter.common.support.eventbus.EventbusTemplate;
import com.chua.starter.task.support.mapper.SystemTaskMapper;
import com.chua.starter.task.support.params.TaskStatus;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.properties.TaskProperties;
import com.chua.starter.task.support.service.SystemTaskService;
import com.chua.starter.task.support.task.Task;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
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
    @Resource
    private TaskProperties taskProperties;

    @Resource(name = com.chua.common.support.protocol.server.Constant.STRING_REDIS)
    private StringRedisTemplate redisTemplate;

    public SystemTaskServiceImpl(SystemTaskMapper systemTaskMapper) {
        this.baseMapper = systemTaskMapper;
    }

    @Override
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
            eventbusTemplate.post("task", baseMapper.selectById(task.getTaskId()));
        }
        return i;
    }
    @Override
    @CacheEvict(cacheManager = CacheConfiguration.DEFAULT_CACHE_MANAGER, cacheNames = "task", key = "#task.taskTid")
    public int forceUpdateWithId(SysTask task) {
        task.setTaskTotal(null);
        int i = baseMapper.updateById(task);
        if (i > 0) {
            eventbusTemplate.post("task", baseMapper.selectById(task.getTaskId()));
        }
        return i;
    }

    @Override
    @CacheEvict(cacheManager = CacheConfiguration.DEFAULT_CACHE_MANAGER, cacheNames = "task", key = "#task.taskTid")
    public boolean save(SysTask task, String userId) {
        if(null == task.getTaskTotal() || task.getTaskTotal() <= 0) {
            throw new RuntimeException("总量不能为空");
        }

        if(StringUtils.isEmpty(task.getTaskType())) {
            throw new RuntimeException("任务类型不能为空");
        }


        if(StringUtils.isEmpty(task.getTaskCid())) {
            throw new RuntimeException("任务实现不能为空");
        }
        task.setCreateTime(LocalDateTime.now());
        if(null == task.getTaskExpire()) {
            task.setTaskExpire(taskProperties.getTaskExpire());
        }
        task.setTaskTid(
                (taskProperties.isCanSame() ? (DigestUtils.md5Hex(IdUtils.createTid()) + "_") : "") +
                DigestUtils.md5Hex(
                        task.getTaskTotal() +
                                task.getTaskType() +
                                task.getTaskBusiness() +
                                task.getTaskCid() +
                                task.getTaskParams()));
        SysTask byTaskTid = getTaskByTaskTid(task.getTaskTid());
        boolean b = false;
        if (null != byTaskTid) {
            String s = redisTemplate.opsForValue().get(task.getFileKey(userId));
            if(null != s) {
                if (null == task.getTaskOver() || task.getTaskOver() == 0) {
                    task.setTaskFinishFile(s);
                    return true;
//                    throw new RuntimeException("有相同任务: " + task.getTaskTid());
                }
            }

            if (null != task.getTaskStatus() && task.getTaskStatus() != 1) {
                throw new RuntimeException("任务未完成不能覆盖: " + task.getTaskTid());
            }
            task.setTaskId(byTaskTid.getTaskId());
            task.setTaskStatus(task.getTaskTotal() == 0 ? 2: 3);
            task.setTaskCurrent(0L);
            b = 1 == baseMapper.updateById(task);
        } else {
            task.setTaskStatus(task.getTaskTotal() == 0 ? 2: 3);
            b = 1 == baseMapper.insert(task);
        }

        if (b) {
            eventbusTemplate.post(EventbusType.GUAVA, "task", task);
        }
        return b;
    }

    @Override
    public Page<SysTask> withPage(Page<SysTask> page, boolean onlyFinish) {
        Page<SysTask> sysTaskPage = baseMapper.selectPage(page, Wrappers.<SysTask>lambdaQuery()
                .eq(onlyFinish, SysTask::getTaskStatus, 1).orderByDesc(SysTask::getCreateTime)
        );
        List<SysTask> records = sysTaskPage.getRecords();
        ValueOperations<String, String> opsForValue = redisTemplate.opsForValue();
        for (SysTask record : records) {
            long toInt = NumberUtils.toLong(opsForValue.get(record.getKey()));
            if(toInt != 0L) {
                record.setTaskCurrent(toInt);
            }
            if(record.getTaskCurrent() >= record.getTaskTotal() && record.getTaskStatus() != 1) {
                record.setTaskCurrent(record.getTaskTotal());
                record.setTaskStatus(1);
                baseMapper.updateById(record);
            }

            if(record.getTaskStatus() == 1) {
                record.setTaskFinishFile(opsForValue.get(record.getKey()));
            }

        }
        return sysTaskPage;
    }

    @Override
    public void forUpdate(String taskId, int status) {
        doUpdate(taskId, status, 3);
    }

    @Override
    public void forUpdateCurrent(SysTask task) {
        doUpdateForSize(task, 3);
    }

    @Override
    @Cacheable(cacheManager = CacheConfiguration.DEFAULT_CACHE_MANAGER, cacheNames = "task", key = "#taskTid")
    public SysTask getTaskByTaskTid(String taskTid) {
        SysTask sysTask = baseMapper.selectOne(Wrappers.<SysTask>lambdaQuery().eq(SysTask::getTaskTid, taskTid));
        if(null != sysTask && sysTask.getTaskCurrent() >= sysTask.getTaskTotal() && sysTask.getTaskStatus() != 1) {
            sysTask.setTaskCurrent(sysTask.getTaskTotal());
            sysTask.setTaskStatus(1);
            baseMapper.updateById(sysTask);
        }
        return sysTask;
    }

    @Override
    public TaskStatus getTaskByTaskStatus(String taskTid) {
        SysTask taskByTaskTid = getTaskByTaskTid(taskTid);
        return new TaskStatus(taskTid, NumberUtils.toLong(redisTemplate.opsForValue().get(taskByTaskTid.getKey())), taskByTaskTid.getTaskTotal());
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
        systemTask.setTaskCurrent(0L);
        baseMapper.updateById(systemTask);
    }

    @Override
    public void expire(String taskTid) {
        SysTask taskByTaskTid = getTaskByTaskTid(taskTid);
        if(null == taskByTaskTid) {
            return;
        }
        if(taskByTaskTid.getTaskDelete() == null || taskByTaskTid.getTaskDelete() == 1) {
            deleteWithId(taskTid);
        }
        String taskDeleteList = taskByTaskTid.getTaskDeleteList();
        if(StringUtils.isNotEmpty(taskDeleteList)) {
            String[] split = taskDeleteList.split(",");
            for (String s : split) {
                try {
                    FileUtils.forceDelete(new File(s));
                } catch (IOException ignored) {
                }
            }
        }
    }

    private synchronized void doUpdateForSize(SysTask task, int count) {
        if (count <= 0) {
            return;
        }

        SysTask systemTask = baseMapper.selectById(task.getTaskId());
        if (null == systemTask) {
            return;
        }

        systemTask.setTaskCurrent(task.getTaskCurrent());
        if (task.getTaskCurrent() >= systemTask.getTaskTotal()) {
            try {
                systemTask.setTaskCost(System.currentTimeMillis() - DateUtils.toEpochMilli(systemTask.getCreateTime()));
            } catch (Exception ignored) {
            }
            systemTask.setTaskStatus(1);
            systemTask.setTaskCurrent(systemTask.getTaskTotal());
        }

        try {
            baseMapper.updateById(systemTask);
            task.setTaskCost(systemTask.getTaskCost());
        } catch (Exception e) {
            e.printStackTrace();
            doUpdateForSize(task, count--);
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
                .in(SysTask::getTaskStatus, 0, 3));
        ValueOperations<String, String> forValue = redisTemplate.opsForValue();
        for (SysTask sysTask : sysTasks) {
            Long size = NumberUtils.toLong(forValue.get(sysTask.getKey()));
            if (sysTask.getTaskCurrent().equals(size)) {
                sysTask.setTaskCurrent(size);
            }
            sysTask.setTaskStatus(3);
            try {
                forceUpdateWithId(sysTask);
            } catch (Exception ignored) {
                eventbusTemplate.post("task", sysTask);
            }
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
    public void update(Map.Entry<String, Long> entry) {
        try {
            SysTask taskByTaskTid = getTaskByTaskTid(entry.getKey());
            if(1 == taskByTaskTid.getTaskStatus()) {
                return;
            }
            taskByTaskTid.setTaskCurrent(entry.getValue());
            updateWithId(taskByTaskTid);
        } catch (Exception ignored) {
        }
    }
}
