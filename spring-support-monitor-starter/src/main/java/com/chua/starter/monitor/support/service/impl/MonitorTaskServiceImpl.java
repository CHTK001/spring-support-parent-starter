package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.monitor.support.entity.MsMonitorTask;
import com.chua.starter.monitor.support.mapper.MsMonitorTaskMapper;
import com.chua.starter.monitor.support.service.MonitorTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控任务 Service 实现
 *
 * @author CH
 * @since 2026/8/4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorTaskServiceImpl implements MonitorTaskService {

    /**
     * 任务缓存名
     */
    public static final String CACHE = "monitorTask";

    /**
     * Mapper
     */
    private final MsMonitorTaskMapper taskMapper;

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public MsMonitorTask create(MsMonitorTask task) {
        if (task.getMonitorTaskEnabled() == null) {
            task.setMonitorTaskEnabled(1);
        }
        if (task.getMonitorTaskTimeout() == null) {
            task.setMonitorTaskTimeout(5000);
        }
        taskMapper.insert(task);
        log.info("[MonitorTask] 创建任务 id={} name={}", task.getId(), task.getMonitorTaskName());
        return task;
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public MsMonitorTask update(MsMonitorTask task) {
        taskMapper.updateById(task);
        log.info("[MonitorTask] 更新任务 id={}", task.getId());
        return task;
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public void delete(Long id) {
        taskMapper.deleteById(id);
        log.info("[MonitorTask] 删除任务 id={}", id);
    }

    @Override
    @Cacheable(cacheNames = CACHE, key = "'monitor.task:id:' + #id")
    public MsMonitorTask findById(Long id) {
        return taskMapper.selectById(id);
    }

    @Override
    @Cacheable(cacheNames = CACHE, key = "'monitor.task:enabled'")
    public List<MsMonitorTask> listEnabled() {
        LambdaQueryWrapper<MsMonitorTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsMonitorTask::getMonitorTaskEnabled, 1);
        return taskMapper.selectList(wrapper);
    }

    @Override
    @Cacheable(cacheNames = CACHE, key = "'monitor.task:monitor:' + #monitorId")
    public List<MsMonitorTask> listByMonitor(Long monitorId) {
        LambdaQueryWrapper<MsMonitorTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsMonitorTask::getMonitorId, monitorId);
        return taskMapper.selectList(wrapper);
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public void setEnabled(Long id, Integer enabled) {
        MsMonitorTask task = new MsMonitorTask();
        task.setId(id);
        task.setMonitorTaskEnabled(enabled);
        taskMapper.updateById(task);
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public void markLastRunTime(Long id) {
        MsMonitorTask task = new MsMonitorTask();
        task.setId(id);
        task.setLastRunTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }
}